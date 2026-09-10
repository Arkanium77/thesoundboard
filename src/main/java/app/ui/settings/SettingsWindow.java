package app.ui.settings;

import app.AppRestarter;
import app.localization.LocalizationAlreadyInstalledException;
import app.localization.LocalizationDescriptor;
import app.localization.LocalizationPackageInstaller;
import app.localization.LocalizationService;
import app.localization.TextKey;
import app.localization.Texts;
import app.project.LastProjectPreferences;
import app.skin.SkinDescriptor;
import app.skin.SkinAlreadyInstalledException;
import app.skin.SkinPackageInstaller;
import app.skin.SkinService;
import app.ui.EmojiText;
import app.ui.UiIcons;
import app.waveform.WaveformDisplaySettings;
import app.waveform.WaveformDisplayMode;
import app.waveform.WaveformPreferences;
import javafx.css.PseudoClass;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import javafx.scene.Cursor;

public class SettingsWindow {
    private static final PseudoClass WINDOW_INACTIVE = PseudoClass.getPseudoClass("window-inactive");
    private final Stage owner;
    private final SkinService skinService;
    private final SkinPackageInstaller skinPackageInstaller;
    private final LocalizationService localizationService;
    private final LocalizationPackageInstaller localizationPackageInstaller;
    private final LastProjectPreferences lastProjectPreferences;
    private final WaveformPreferences waveformPreferences;
    private final Runnable localizationChanged;
    private final AppRestarter appRestarter = new AppRestarter();
    private final Stage stage = new Stage();
    private final BorderPane content = new BorderPane();
    private final ListView<SettingsSection> sections = new ListView<>();
    private final Set<String> pendingDeletionPackages = new LinkedHashSet<>();
    private final Set<String> selectedSkinPackages = new LinkedHashSet<>();
    private boolean skinSelectionMode;
    private boolean packageOperationRunning;
    private final Consumer<BooleanSupplier> prepareRestart;
    private Node skinsPage;
    private volatile PackageCatalog packageCatalog;

    public SettingsWindow(Stage owner, SkinService skinService, LocalizationService localizationService,
                          LastProjectPreferences lastProjectPreferences, WaveformPreferences waveformPreferences,
                          Runnable localizationChanged, Consumer<BooleanSupplier> prepareRestart) {
        this.owner = owner;
        this.prepareRestart = prepareRestart;
        this.skinService = skinService;
        this.skinPackageInstaller = new SkinPackageInstaller(skinService.getRepository());
        this.localizationService = localizationService;
        this.lastProjectPreferences = lastProjectPreferences;
        this.waveformPreferences = waveformPreferences;
        this.localizationChanged = localizationChanged;
        this.localizationPackageInstaller = new LocalizationPackageInstaller(localizationService.getRepository());
        configureStage();
    }

    public void show() {
        stage.show();
        stage.toFront();
    }

    private void configureStage() {
        sections.getStyleClass().add("settings-navigation");
        sections.getItems().addAll(SettingsSection.GENERAL, SettingsSection.SKINS, SettingsSection.LOCALIZATION);
        sections.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        sections.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected == SettingsSection.GENERAL) {
                showGeneral();
            } else if (selected == SettingsSection.LOCALIZATION) {
                showLocalization();
            } else {
                showSkins();
            }
        });
        sections.getSelectionModel().selectFirst();
        sections.setPrefWidth(150d);

        SplitPane splitPane = new SplitPane(sections, content);
        splitPane.getStyleClass().add("settings-window");
        splitPane.pseudoClassStateChanged(WINDOW_INACTIVE, true);
        stage.focusedProperty().addListener((observable, oldValue, focused) ->
                splitPane.pseudoClassStateChanged(WINDOW_INACTIVE, !focused));
        content.getStyleClass().add("settings-content");
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.setDividerPositions(0.25d);
        Scene scene = new Scene(splitPane, 640d, 440d);
        skinService.apply(scene, splitPane);

        stage.setOnCloseRequest(event -> { if (packageOperationRunning) event.consume(); });
        stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        stage.setTitle(localizationService.text(TextKey.SETTINGS_TITLE));
        stage.setMinWidth(520d);
        stage.setMinHeight(360d);
        stage.setScene(scene);
        URL iconResource = SettingsWindow.class.getResource("/icons/app-icon.png");
        if (iconResource != null) {
            stage.getIcons().add(new Image(iconResource.toExternalForm()));
        }
    }

    private void showGeneral() {
        Label title = new Label(localizationService.text(TextKey.SETTINGS_GENERAL));
        title.getStyleClass().add("settings-title");
        CheckBox restoreSession = new CheckBox(localizationService.text(TextKey.SETTINGS_RESTORE_SESSION));
        restoreSession.setSelected(lastProjectPreferences.isRestoreOnStart());
        restoreSession.selectedProperty().addListener((observable, oldValue, selected) ->
                lastProjectPreferences.setRestoreOnStart(selected)
        );
        Label waveformModeLabel = new Label(localizationService.text(TextKey.SETTINGS_WAVEFORM_MODE));
        ComboBox<WaveformModeOption> waveformMode = new ComboBox<>();
        waveformMode.getItems().addAll(
                new WaveformModeOption(WaveformDisplayMode.PEAK_LINEAR,
                        localizationService.text(TextKey.SETTINGS_WAVEFORM_MODE_PEAK_LINEAR)),
                new WaveformModeOption(WaveformDisplayMode.RMS_LINEAR,
                        localizationService.text(TextKey.SETTINGS_WAVEFORM_MODE_RMS_LINEAR)),
                new WaveformModeOption(WaveformDisplayMode.RMS_DB,
                        localizationService.text(TextKey.SETTINGS_WAVEFORM_MODE_RMS_DB))
        );
        waveformMode.getSelectionModel().select(waveformMode.getItems().stream()
                .filter(option -> option.mode() == WaveformDisplaySettings.getMode())
                .findFirst()
                .orElse(waveformMode.getItems().get(1)));
        waveformMode.valueProperty().addListener((observable, oldValue, selected) -> {
            if (selected == null) return;
            WaveformDisplaySettings.setMode(selected.mode());
            waveformPreferences.saveMode(selected.mode());
        });
        VBox waveformModeBox = new VBox(6d, waveformModeLabel, waveformMode);
        VBox body = new VBox(16d, title, restoreSession, waveformModeBox);
        body.setPadding(new Insets(16d));
        content.setCenter(body);
    }

    private void showSkins() {
        if (packageCatalog == null) { runPackageOperation(() -> null, ignored -> showSkins()); return; }
        if (skinsPage != null) {
            content.setCenter(skinsPage);
            return;
        }
        HBox header = createPackageSectionHeader(TextKey.SETTINGS_SKINS, this::refreshSkins,
                () -> showPackageSourceDirectories(true));
        Button selectionButton = new Button(localizationService.text(skinSelectionMode
                ? TextKey.SETTINGS_FINISH_SELECTION : TextKey.SETTINGS_SELECT_PACKAGES));
        selectionButton.setOnAction(event -> {
            skinSelectionMode = !skinSelectionMode;
            if (!skinSelectionMode) selectedSkinPackages.clear();
            rebuildSkins();
        });
        header.getChildren().add(header.getChildren().size() - 2, selectionButton);
        VBox skinList = new VBox(8d);
        ToggleGroup toggleGroup = new ToggleGroup();
        UUID selectedSkinUid = skinService.getSelectedSkinUid();
        int selectedSkinVersion = skinService.getSelectedSkinVersion();
        List<SkinDescriptor> skins = skinService.getAvailableSkins();
        Button updateSelectedButton = new Button(localizationService.text(TextKey.SETTINGS_UPDATE_SELECTED));
        Button deleteSelectedButton = new Button(localizationService.text(TextKey.SETTINGS_DELETE_SELECTED));
        selectedSkinPackages.retainAll(skins.stream().map(this::packageKey).toList());
        for (SkinDescriptor skin : skins) {
            ButtonBase skinButton = skinSelectionMode ? new CheckBox() : new RadioButton();
            String skinDisplayName = skin.manifest().getName();
            if (skins.stream().filter(candidate -> candidate.manifest().getUid()
                    .equals(skin.manifest().getUid())).count() > 1) {
                skinDisplayName += " (v" + skin.manifest().getVersion() + ")";
            }
            HBox skinName = new HBox(EmojiText.create(skinDisplayName));
            skinName.setPadding(new Insets(0d, 0d, 0d, 8d));
            skinButton.setGraphic(skinName);
            skinButton.setAccessibleText(skin.manifest().getName());
            if (skinSelectionMode) {
                CheckBox skinCheckBox = (CheckBox) skinButton;
                skinCheckBox.setSelected(selectedSkinPackages.contains(packageKey(skin)));
                skinCheckBox.setDisable(!canBulkDeleteSkin(skin));
                skinCheckBox.setOnAction(event -> {
                    if (skinCheckBox.isSelected()) selectedSkinPackages.add(packageKey(skin));
                    else selectedSkinPackages.remove(packageKey(skin));
                    refreshBulkSkinActionButtons(skins, updateSelectedButton, deleteSelectedButton);
                });
            } else {
                RadioButton skinRadioButton = (RadioButton) skinButton;
                skinRadioButton.setToggleGroup(toggleGroup);
                skinRadioButton.setSelected(skin.manifest().getUid().equals(selectedSkinUid)
                        && skin.manifest().getVersion() == selectedSkinVersion);
                skinRadioButton.setOnAction(event -> selectSkin(skin));
            }
            skinButton.getStyleClass().add("package-choice");
            skinButton.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(skinButton, Priority.ALWAYS);
            Button exportButton = new Button(localizationService.text(TextKey.SETTINGS_EXPORT));
            exportButton.setDisable(skin.isBuiltIn());
            exportButton.setOnAction(event -> exportSkin(skin));
            Button deleteButton = new Button(localizationService.text(TextKey.SETTINGS_DELETE));
            boolean activeSkin = skin.manifest().getUid().equals(skinService.getActiveSkin().manifest().getUid())
                    && skin.manifest().getVersion() == skinService.getActiveSkin().manifest().getVersion();
            boolean pendingDeletion = pendingDeletionPackages.contains(packageKey(skin));
            List<Path> skinSources = packageCatalog.skins().getOrDefault(packageKey(skin), List.of());
            Button updateButton = new Button(localizationService.text(TextKey.SETTINGS_UPDATE_INSTALLED));
            updateButton.setDisable(pendingDeletion || skinSources.isEmpty());
            updateButton.setOnAction(event -> updateInstalledSkin(skin));
            deleteButton.setText(pendingDeletion
                    ? localizationService.text(TextKey.SETTINGS_PENDING)
                    : localizationService.text(TextKey.SETTINGS_DELETE));
            deleteButton.setDisable(skin.isBuiltIn() || pendingDeletion || activeSkin && skinSources.size() < 2);
            if (activeSkin && !skin.isBuiltIn()) {
                deleteButton.setTooltip(new Tooltip(localizationService.text(TextKey.SETTINGS_SELECT_OTHER_SKIN)));
            }
            deleteButton.setOnAction(event -> requestSkinDeletion(skin, skinSources));
            updateButton.setVisible(!skinSelectionMode);
            updateButton.setManaged(!skinSelectionMode);
            exportButton.setVisible(!skinSelectionMode);
            exportButton.setManaged(!skinSelectionMode);
            deleteButton.setVisible(!skinSelectionMode);
            deleteButton.setManaged(!skinSelectionMode);
            HBox skinRow = new HBox(8d, skinButton, updateButton, exportButton, deleteButton);
            skinRow.setAlignment(Pos.CENTER_LEFT);
            skinRow.setPadding(new Insets(0d, 6d, 0d, 6d));
            skinList.getChildren().add(skinRow);
        }

        ScrollPane scrollPane = new ScrollPane(skinList);
        scrollPane.setFitToWidth(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        Button installButton = new Button(localizationService.text(TextKey.SETTINGS_INSTALL_SKIN));
        installButton.setOnAction(event -> installSkin());
        Button createPackageButton = new Button(localizationService.text(TextKey.SETTINGS_CREATE_PACKAGE));
        createPackageButton.setOnAction(event -> createSkinPackage());
        updateSelectedButton.setOnAction(event -> updateSelectedSkins(skins));
        deleteSelectedButton.setTooltip(new Tooltip(
                localizationService.text(TextKey.SETTINGS_BULK_DELETE_ORPHANS_ONLY)
        ));
        deleteSelectedButton.setOnAction(event -> deleteSelectedSkins(skins));
        refreshBulkSkinActionButtons(skins, updateSelectedButton, deleteSelectedButton);
        HBox packageActions = skinSelectionMode
                ? new HBox(8d, updateSelectedButton, deleteSelectedButton)
                : new HBox(8d, installButton, createPackageButton);
        VBox body = new VBox(12d, header, scrollPane, packageActions);
        body.setPadding(new Insets(16d));
        skinsPage = body;
        content.setCenter(skinsPage);
    }

    private void rebuildSkins() {
        skinsPage = null;
        showSkins();
    }

    /**
     * Discovery is explicit and runs outside the FX thread. Navigation only reads the repository snapshot; the
     * selected skin is reapplied after the worker finishes because resource binding is confined to the UI thread.
     */
    private void refreshSkins() {
        runPackageRefresh(skinService::synchronizePackageSources, () -> {
            skinService.refreshSelectedSkin();
            rebuildSkins();
        });
    }

    private void refreshLocalizations() {
        runPackageRefresh(localizationService::refreshPackageSources, () -> {
            localizationService.reloadSelected();
            refreshLocalization();
        });
    }

    @FunctionalInterface
    private interface PackageOperation {
        void run() throws IOException;
    }

    public boolean isPackageOperationRunning() {
        return packageOperationRunning;
    }

    /**
     * Serializes explicit rescans by disabling package controls while work is running. A non-daemon worker and close
     * guards prevent normal exit from abandoning a filesystem replacement halfway through. Dialogs and scene updates
     * remain on the FX thread, including failure recovery; a failed scan always re-enables the controls.
     */
    private void runPackageRefresh(PackageOperation operation, Runnable completed) {
        runPackageOperation(() -> { operation.run(); return null; }, ignored -> completed.run());
    }

    private <T> void runPackageOperation(Callable<T> operation, Consumer<T> completed) {
        runPackageOperation(operation, completed, failure -> showOperationError(
                "Package Operation Failed", "The package operation could not be completed", new IOException(failure)));
    }

    private <T> void runPackageOperation(Callable<T> operation, Consumer<T> completed, Consumer<Throwable> failed) {
        if (packageOperationRunning) return;
        packageOperationRunning = true;
        content.setDisable(true);
        sections.setDisable(true);
        stage.getScene().setCursor(Cursor.WAIT);
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                T result = operation.call();
                packageCatalog = readPackageCatalog();
                return result;
            }
        };
        Runnable finished = () -> {
            packageOperationRunning = false;
            content.setDisable(false);
            sections.setDisable(false);
            stage.getScene().setCursor(Cursor.DEFAULT);
        };
        task.setOnSucceeded(event -> { finished.run(); completed.accept(task.getValue()); });
        task.setOnFailed(event -> { finished.run(); failed.accept(task.getException()); });
        new Thread(task, "package-operation").start();
    }

    /** Package rows and selection toggles only read this immutable snapshot. Inspecting source ZIPs on each FX
     * render can be as slow as installation on a network folder; workers refresh availability after each operation.
     * Installers still validate sources immediately before mutation, since files may change after this snapshot. */
    private PackageCatalog readPackageCatalog() {
        Map<String, List<Path>> skins = new LinkedHashMap<>();
        for (SkinDescriptor skin : skinService.getAvailableSkins()) skins.put(packageKey(skin), skinPackageInstaller.findSources(skin));
        Map<String, List<Path>> localizations = new LinkedHashMap<>();
        for (LocalizationDescriptor localization : localizationService.getAvailable()) {
            localizations.put(packageKey(localization), localizationPackageInstaller.findSources(localization));
        }
        return new PackageCatalog(Map.copyOf(skins), Map.copyOf(localizations));
    }

    private record PackageCatalog(Map<String, List<Path>> skins, Map<String, List<Path>> localizations) { }

    private String packageKey(LocalizationDescriptor localization) {
        return localization.manifest().getUid() + ":" + localization.manifest().getVersion();
    }

    private void showLocalization() {
        if (packageCatalog == null) { runPackageOperation(() -> null, ignored -> showLocalization()); return; }
        HBox header = createPackageSectionHeader(TextKey.SETTINGS_LOCALIZATION, this::refreshLocalizations,
                () -> showPackageSourceDirectories(false));
        VBox localizationList = new VBox(8d);
        ToggleGroup toggleGroup = new ToggleGroup();
        UUID selectedUid = localizationService.getSelectedUid();
        int selectedVersion = localizationService.getSelectedVersion();
        List<LocalizationDescriptor> localizations = localizationService.getAvailable();
        for (LocalizationDescriptor localization : localizations) {
            String localizationName = localization.builtIn()
                    ? localizationService.text(TextKey.LOCALIZATION_ENGLISH)
                    : localization.manifest().getName();
            if (localizations.stream().filter(candidate -> candidate.manifest().getUid()
                    .equals(localization.manifest().getUid())).count() > 1) {
                localizationName += " (v" + localization.manifest().getVersion() + ")";
            }
            RadioButton localizationButton = new RadioButton();
            HBox localizedName = new HBox(EmojiText.create(localizationName));
            localizedName.setPadding(new Insets(0d, 0d, 0d, 8d));
            localizationButton.setGraphic(localizedName);
            localizationButton.setAccessibleText(localizationName);
            localizationButton.setToggleGroup(toggleGroup);
            localizationButton.setSelected(localization.manifest().getUid().equals(selectedUid)
                    && localization.manifest().getVersion() == selectedVersion);
            localizationButton.setOnAction(event -> selectLocalization(localization));
            localizationButton.getStyleClass().add("package-choice");
            localizationButton.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(localizationButton, Priority.ALWAYS);
            Button exportButton = new Button(localizationService.text(TextKey.SETTINGS_EXPORT));
            exportButton.setDisable(localization.builtIn());
            exportButton.setOnAction(event -> exportLocalization(localization));
            Button updateInstalledButton = new Button(localizationService.text(TextKey.SETTINGS_UPDATE_INSTALLED));
            updateInstalledButton.setDisable(packageCatalog.localizations().getOrDefault(packageKey(localization), List.of()).isEmpty());
            updateInstalledButton.setOnAction(event -> updateInstalledLocalization(localization));
            Button deleteButton = new Button(localizationService.text(TextKey.SETTINGS_DELETE));
            List<Path> localizationSources = packageCatalog.localizations().getOrDefault(packageKey(localization), List.of());
            boolean selectedLocalization = localization.manifest().getUid().equals(selectedUid)
                    && localization.manifest().getVersion() == selectedVersion;
            deleteButton.setDisable(localization.builtIn()
                    || selectedLocalization && localizationSources.size() < 2);
            deleteButton.setOnAction(event -> requestLocalizationDeletion(localization, localizationSources));
            HBox row = new HBox(8d, localizationButton, updateInstalledButton, exportButton, deleteButton);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(0d, 6d, 0d, 6d));
            localizationList.getChildren().add(row);
        }
        ScrollPane scrollPane = new ScrollPane(localizationList);
        scrollPane.setFitToWidth(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        Button installButton = new Button(localizationService.text(TextKey.LOCALIZATION_INSTALL));
        installButton.setOnAction(event -> installLocalization());
        Button createButton = new Button(localizationService.text(TextKey.LOCALIZATION_CREATE_PACKAGE));
        createButton.setOnAction(event -> createLocalizationPackage());
        Button exportStringsButton = new Button(localizationService.text(TextKey.LOCALIZATION_EXPORT_STRINGS));
        exportStringsButton.setOnAction(event -> exportEnglishStrings());
        Button updateButton = new Button(localizationService.text(TextKey.LOCALIZATION_UPDATE));
        updateButton.setOnAction(event -> updateLocalization());
        HBox actions = new HBox(8d, installButton, createButton, updateButton, exportStringsButton);
        VBox body = new VBox(12d, header, scrollPane, actions);
        body.setPadding(new Insets(16d));
        content.setCenter(body);
    }

    private void selectLocalization(LocalizationDescriptor localization) {
        if (!localizationService.select(localization.manifest().getUid(), localization.manifest().getVersion())) return;
        refreshLocalization();
    }

    private HBox createPackageSectionHeader(TextKey titleKey, Runnable refreshAction, Runnable sourcesAction) {
        Label title = new Label(localizationService.text(titleKey));
        title.getStyleClass().add("settings-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        String refreshText = localizationService.text(TextKey.SETTINGS_REFRESH_UPDATE_AVAILABILITY);
        Button sourcesButton = new Button(localizationService.text(TextKey.SETTINGS_SOURCE_FOLDERS));
        sourcesButton.setOnAction(event -> sourcesAction.run());
        Button refreshButton = new Button(null, UiIcons.refresh());
        refreshButton.setAccessibleText(refreshText);
        refreshButton.setTooltip(new Tooltip(refreshText));
        refreshButton.setMinSize(28d, 28d);
        refreshButton.setPrefSize(28d, 28d);
        refreshButton.setMaxSize(28d, 28d);
        refreshButton.setOnAction(event -> refreshAction.run());
        HBox header = new HBox(8d, title, spacer, sourcesButton, refreshButton);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private void installLocalization() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(localizationService.text(TextKey.LOCALIZATION_INSTALL_TITLE));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("The Soundboard localizations (*.tsbl)", "*.tsbl"));
        setInitialPackageDirectory(chooser, localizationService.getPackageSourceDirectories());
        List<File> selectedFiles = chooser.showOpenMultipleDialog(stage);
        if (selectedFiles == null) return;
        installLocalizations(selectedFiles, 0);
    }

    private void installLocalizations(List<File> files, int index) {
        if (index >= files.size()) return;
        File selected = files.get(index);
        Runnable next = () -> installLocalizations(files, index + 1);
        runPackageOperation(() -> localizationPackageInstaller.install(selected.toPath()), installed -> {
            showLocalization();
            next.run();
        }, failure -> {
            if (failure instanceof LocalizationAlreadyInstalledException) {
                Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                        localizationService.text(TextKey.LOCALIZATION_REPLACE_QUESTION), ButtonType.CANCEL, ButtonType.OK);
                confirmation.initOwner(stage);
                styleDialog(confirmation, "settings-confirmation-dialog");
                if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                    runPackageOperation(() -> localizationPackageInstaller.install(selected.toPath(), true), replacement -> {
                        localizationService.reloadSelected();
                        refreshLocalization();
                        next.run();
                    }, replacementFailure -> {
                        showOperationError("Localization Replacement Failed", "The localization could not be replaced", new IOException(replacementFailure));
                        next.run();
                    });
                    return;
                }
            } else showOperationError("Localization Installation Failed", "The localization could not be installed", new IOException(failure));
            next.run();
        });
    }

    private void updateInstalledLocalization(LocalizationDescriptor localization) {
        runPackageOperation(() -> localizationPackageInstaller.update(localization), replacement -> {
            if (isSelected(replacement)) { localizationService.reloadSelected(); refreshLocalization(); }
            else showLocalization();
        });
    }

    private void refreshLocalization() {
        Texts.configure(localizationService);
        skinsPage = null;
        stage.setTitle(localizationService.text(TextKey.SETTINGS_TITLE));
        sections.refresh();
        showLocalization();
        localizationChanged.run();
    }

    private void exportLocalization(LocalizationDescriptor localization) {
        File target = chooseLocalizationTarget(localizationService.text(TextKey.LOCALIZATION_EXPORT_TITLE), localization.manifest().getName() + ".tsbl");
        if (target == null) return;
        runPackageRefresh(() -> localizationPackageInstaller.export(localization, target.toPath()), () -> { });
    }

    private void deleteLocalization(LocalizationDescriptor localization) {
        runPackageRefresh(() -> localizationPackageInstaller.delete(localization), () -> {
            showLocalization();
        });
    }

    private void deleteLocalizationFromSource(LocalizationDescriptor localization, Path source, boolean deleteInstalled) throws IOException {
        runPackageRefresh(() -> {
            localizationPackageInstaller.deleteSourceFile(localization, source);
            if (deleteInstalled) localizationPackageInstaller.delete(localization);
            localizationService.refreshPackageSources();
        }, this::showLocalization);
    }

    private void createLocalizationPackage() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(localizationService.text(TextKey.LOCALIZATION_SELECT_FOLDER));
        File source = chooser.showDialog(stage);
        if (source == null) return;
        File target = chooseLocalizationTarget(localizationService.text(TextKey.LOCALIZATION_CREATE_TITLE), source.getName() + ".tsbl");
        if (target == null) return;
        runPackageRefresh(() -> localizationPackageInstaller.createPackage(source.toPath(), target.toPath()), () -> { });
    }

    private void exportEnglishStrings() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(localizationService.text(TextKey.LOCALIZATION_EXPORT_STRINGS_TITLE));
        chooser.setInitialFileName("strings.yml");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("YAML files (*.yml)", "*.yml"));
        File target = chooser.showSaveDialog(stage);
        if (target == null) return;
        runPackageRefresh(() -> localizationPackageInstaller.exportEnglishStrings(target.toPath()), () -> { });
    }

    private void updateLocalization() {
        ButtonType folderButton = new ButtonType(
                localizationService.text(TextKey.LOCALIZATION_UPDATE_FOLDER),
                ButtonBar.ButtonData.OTHER
        );
        ButtonType packageButton = new ButtonType(
                localizationService.text(TextKey.LOCALIZATION_UPDATE_PACKAGE),
                ButtonBar.ButtonData.OTHER
        );
        ButtonType cancelButton = new ButtonType(
                localizationService.text(TextKey.DIALOG_CANCEL),
                ButtonBar.ButtonData.CANCEL_CLOSE
        );
        Alert choice = new Alert(
                Alert.AlertType.CONFIRMATION,
                localizationService.text(TextKey.LOCALIZATION_UPDATE_PROMPT),
                folderButton,
                packageButton,
                cancelButton
        );
        choice.initOwner(stage);
        styleDialog(choice, "settings-confirmation-dialog");
        choice.setTitle(localizationService.text(TextKey.LOCALIZATION_UPDATE_TITLE));
        choice.setHeaderText(localizationService.text(TextKey.LOCALIZATION_UPDATE_TITLE));
        ButtonType selected = choice.showAndWait().orElse(cancelButton);
        if (selected == folderButton) {
            updateLocalizationFolder();
        } else if (selected == packageButton) {
            updateLocalizationPackage();
        }
    }

    private void updateLocalizationFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(localizationService.text(TextKey.LOCALIZATION_SELECT_FOLDER));
        File source = chooser.showDialog(stage);
        if (source == null) return;
        runPackageOperation(() -> localizationPackageInstaller.updateSource(source.toPath()), this::showLocalizationUpdated);
    }

    private void updateLocalizationPackage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(localizationService.text(TextKey.LOCALIZATION_UPDATE_TITLE));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("The Soundboard localizations (*.tsbl)", "*.tsbl"));
        File packageFile = chooser.showOpenDialog(stage);
        if (packageFile == null) return;
        runPackageOperation(() -> localizationPackageInstaller.updatePackage(packageFile.toPath()), this::showLocalizationUpdated);
    }

    private void showLocalizationUpdated(int addedStrings) {
        Alert alert = new Alert(
                Alert.AlertType.INFORMATION,
                localizationService.format(TextKey.LOCALIZATION_UPDATE_COMPLETE, addedStrings),
                ButtonType.OK
        );
        alert.initOwner(stage);
        styleDialog(alert, "settings-information-dialog");
        alert.setTitle(localizationService.text(TextKey.LOCALIZATION_UPDATE_TITLE));
        alert.setHeaderText(localizationService.text(TextKey.LOCALIZATION_UPDATE_TITLE));
        alert.showAndWait();
    }

    private File chooseLocalizationTarget(String title, String initialName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.setInitialFileName(initialName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("The Soundboard localizations (*.tsbl)", "*.tsbl"));
        return chooser.showSaveDialog(stage);
    }

    private void selectSkin(SkinDescriptor skin) {
        boolean skinChanged = skinService.selectSkin(skin.manifest().getUid(), skin.manifest().getVersion());
        rebuildSkins();
        if (!skinChanged) {
            return;
        }
        showRestartPromptIfRequired(skin);
    }

    private void installSkin() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(localizationService.text(TextKey.DIALOG_INSTALL_SKIN_TITLE));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("The Soundboard skins (*.tsbs)", "*.tsbs"));
        setInitialPackageDirectory(fileChooser, skinService.getPackageSourceDirectories());
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(stage);
        if (selectedFiles == null) {
            return;
        }
        installSkins(selectedFiles, 0);
    }

    private void installSkins(List<File> files, int index) {
        if (index >= files.size()) return;
        File selectedFile = files.get(index);
        Runnable next = () -> installSkins(files, index + 1);
        runPackageOperation(() -> skinPackageInstaller.install(selectedFile.toPath()), installed -> {
            showInstallationCompleted(installed, false);
            next.run();
        }, failure -> {
            if (failure instanceof SkinAlreadyInstalledException conflict) {
                Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                        "This skin version is already installed. Only its installed copy will be replaced.", ButtonType.CANCEL, ButtonType.OK);
                confirmation.initOwner(stage);
                styleDialog(confirmation, "settings-confirmation-dialog");
                confirmation.setHeaderText("Replace '" + conflict.getSkin().getName() + "'?");
                if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                    if (skinService.wasLoadedThisSession(conflict.getSkin().getUid(), conflict.getSkin().getVersion())) {
                        runPackageOperation(() -> skinPackageInstaller.scheduleReplacement(selectedFile.toPath()), replacement -> {
                            rebuildSkins();
                            showPendingOperationRestartPrompt("Skin '" + replacement.getName() + "' will be replaced on restart.");
                            next.run();
                        });
                    } else runPackageOperation(() -> skinPackageInstaller.install(selectedFile.toPath(), true), replacement -> {
                        showInstallationCompleted(replacement, true);
                        next.run();
                    });
                    return;
                }
            } else showOperationError("Skin Installation Failed", "The skin could not be installed", new IOException(failure));
            next.run();
        });
    }

    private void requestSkinDeletion(SkinDescriptor skin, List<Path> sources) {
        if (sources.isEmpty()) {
            deleteSkin(skin, true);
        } else if (sources.size() == 1) {
            showSingleSourceChoice(sources.getFirst(), () -> deleteSkin(skin, false),
                    source -> deleteSkinFromSource(skin, source, true));
        } else {
            showPackageSources(skin.manifest().getName(), sources,
                    source -> deleteSkinFromSource(skin, source, false), () -> deleteSkin(skin, true),
                    !isActive(skin));
        }
    }

    private void requestLocalizationDeletion(LocalizationDescriptor localization, List<Path> sources) {
        if (sources.isEmpty()) {
            deleteLocalization(localization);
        } else if (sources.size() == 1) {
            showSingleSourceChoice(sources.getFirst(), () -> deleteLocalization(localization),
                    source -> deleteLocalizationFromSource(localization, source, true));
        } else {
            showPackageSources(localization.manifest().getName(), sources,
                    source -> deleteLocalizationFromSource(localization, source, false),
                    () -> deleteLocalization(localization), !isSelected(localization));
        }
    }

    private void showSingleSourceChoice(Path source, Runnable installedDeletion, PackageSourceDeletion sourceDeletion) {
        ButtonType deleteInstalled = new ButtonType(localizationService.text(TextKey.SETTINGS_DELETE),
                ButtonBar.ButtonData.OK_DONE);
        ButtonType deleteSource = new ButtonType(localizationService.text(TextKey.SETTINGS_DELETE_SOURCE),
                ButtonBar.ButtonData.OTHER);
        ButtonType cancel = new ButtonType(localizationService.text(TextKey.DIALOG_CANCEL),
                ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert choice = new Alert(Alert.AlertType.CONFIRMATION,
                localizationService.format(TextKey.DIALOG_DELETE_SOURCE_FIRST, source),
                deleteInstalled, deleteSource, cancel);
        choice.initOwner(stage);
        styleDialog(choice, "settings-confirmation-dialog");
        choice.setTitle(localizationService.text(TextKey.DIALOG_SOURCE_FILES_TITLE));
        choice.setHeaderText(localizationService.text(TextKey.SETTINGS_DELETE));
        Button sourceButton = (Button) choice.getDialogPane().lookupButton(deleteSource);
        boolean protectedSource = isProtectedSource(source);
        sourceButton.setDisable(protectedSource);
        if (protectedSource) {
            sourceButton.setText(localizationService.text(TextKey.SETTINGS_PROTECTED_SOURCE));
        }
        Button cancelButton = (Button) choice.getDialogPane().lookupButton(cancel);
        cancelButton.setDefaultButton(true);
        ((Button) choice.getDialogPane().lookupButton(deleteInstalled)).setDefaultButton(false);
        ButtonType result = choice.showAndWait().orElse(cancel);
        if (result == deleteInstalled) {
            installedDeletion.run();
        } else if (result == deleteSource && confirmPermanentSourceDeletion(source)) {
            try {
                sourceDeletion.delete(source);
            } catch (IOException | IllegalArgumentException exception) {
                showOperationError("Source Deletion Failed", "The source package could not be deleted", exception);
            }
        }
    }

    private void showPackageSources(String packageName, List<Path> sources,
                                    PackageSourceDeletion sourceDeletion, Runnable installedDeletion,
                                    boolean allowInstalledDeletion) {
        Stage dialog = new Stage();
        dialog.initOwner(stage);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(localizationService.text(TextKey.DIALOG_SOURCE_FILES_TITLE));
        dialog.getIcons().setAll(stage.getIcons());
        Label title = new Label(packageName);
        title.getStyleClass().add("settings-title");
        VBox rows = new VBox();
        for (int index = 0; index < sources.size(); index++) {
            Path source = sources.get(index);
            Label path = new Label(source.toString());
            path.setWrapText(false);
            path.setMaxWidth(Double.MAX_VALUE);
            path.setTooltip(new Tooltip(source.toString()));
            HBox.setHgrow(path, Priority.ALWAYS);
            Button deleteButton = new Button(localizationService.text(TextKey.SETTINGS_DELETE_SOURCE));
            boolean protectedSource = isProtectedSource(source);
            deleteButton.setDisable(protectedSource);
            if (protectedSource) {
                deleteButton.setText(localizationService.text(TextKey.SETTINGS_PROTECTED_SOURCE));
            }
            deleteButton.setOnAction(event -> {
                if (!confirmSourceDeletionFromList(source)) {
                    return;
                }
                try {
                    sourceDeletion.delete(source);
                    dialog.close();
                } catch (IOException | IllegalArgumentException exception) {
                    showOperationError("Source Deletion Failed", "The source package could not be deleted", exception);
                }
            });
            HBox row = new HBox(8d, path, deleteButton);
            configurePackageSourceRow(row, index);
            row.setPadding(new Insets(0d, 8d, 0d, 8d));
            rows.getChildren().add(row);
        }
        ScrollPane scrollPane = new ScrollPane(rows);
        scrollPane.setFitToWidth(true);
        Button deleteInstalled = new Button(localizationService.text(TextKey.SETTINGS_DELETE));
        deleteInstalled.setDisable(!allowInstalledDeletion);
        deleteInstalled.setOnAction(event -> {
            dialog.close();
            installedDeletion.run();
        });
        Button cancel = new Button(localizationService.text(TextKey.DIALOG_CANCEL));
        cancel.setDefaultButton(true);
        cancel.setCancelButton(true);
        cancel.setOnAction(event -> dialog.close());
        HBox actions = new HBox(8d, deleteInstalled, cancel);
        VBox body = new VBox(12d, title, scrollPane, actions);
        body.getStyleClass().add("package-source-dialog");
        body.setPadding(new Insets(16d));
        Scene scene = new Scene(body, 720d, 280d);
        skinService.apply(scene, body);
        dialog.setMinWidth(520d);
        dialog.setMinHeight(220d);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private boolean confirmPermanentSourceDeletion(Path source) {
        Alert second = new Alert(Alert.AlertType.WARNING,
                localizationService.format(TextKey.DIALOG_DELETE_SOURCE_SECOND, source),
                ButtonType.CANCEL, ButtonType.OK);
        second.initOwner(stage);
        styleDialog(second, "settings-confirmation-dialog");
        second.setTitle(localizationService.text(TextKey.DIALOG_SOURCE_FILES_TITLE));
        second.setHeaderText(localizationService.text(TextKey.SETTINGS_DELETE_SOURCE));
        return second.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private boolean confirmSourceDeletionFromList(Path source) {
        Alert first = new Alert(Alert.AlertType.CONFIRMATION,
                localizationService.format(TextKey.DIALOG_DELETE_SOURCE_CONFIRM, source),
                ButtonType.CANCEL, ButtonType.OK);
        first.initOwner(stage);
        styleDialog(first, "settings-confirmation-dialog");
        first.setTitle(localizationService.text(TextKey.DIALOG_SOURCE_FILES_TITLE));
        first.setHeaderText(localizationService.text(TextKey.SETTINGS_DELETE_SOURCE));
        return first.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK
                && confirmPermanentSourceDeletion(source);
    }

    private boolean isProtectedSource(Path source) {
        return skinPackageInstaller.isProtectedSource(source)
                || localizationPackageInstaller.isProtectedSource(source);
    }

    private void showPackageSourceDirectories(boolean skins) {
        Stage dialog = new Stage();
        dialog.initOwner(stage);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(localizationService.text(TextKey.SETTINGS_SOURCE_FOLDERS_TITLE));
        dialog.getIcons().setAll(stage.getIcons());
        BorderPane dialogContent = new BorderPane();
        dialogContent.getStyleClass().add("package-source-dialog");
        dialogContent.disableProperty().bind(content.disableProperty());
        dialog.setOnCloseRequest(event -> { if (packageOperationRunning) event.consume(); });
        dialogContent.setPadding(new Insets(16d));
        refreshPackageSourceDirectoryDialog(dialog, dialogContent, skins);
        Scene scene = new Scene(dialogContent, 540d, 300d);
        skinService.apply(scene, dialogContent);
        dialog.setMinWidth(440d);
        dialog.setMinHeight(240d);
        dialog.setScene(scene);
        dialog.showAndWait();
        if (skins) {
            rebuildSkins();
        } else {
            showLocalization();
        }
    }

    private void refreshPackageSourceDirectoryDialog(Stage dialog, BorderPane dialogContent, boolean skins) {
        List<Path> directories = skins
                ? skinService.getPackageSourceDirectories()
                : localizationService.getPackageSourceDirectories();
        List<Path> bundledDirectories = skins
                ? skinService.getBundledPackageSourceDirectories()
                : localizationService.getBundledPackageSourceDirectories();
        VBox pathRows = new VBox();
        VBox actionRows = new VBox();
        for (int index = 0; index < directories.size(); index++) {
            Path directory = directories.get(index);
            boolean bundled = bundledDirectories.contains(directory);
            String pathText = bundled
                    ? directory + " (" + localizationService.text(TextKey.SETTINGS_SOURCE_FOLDERS_BUNDLED) + ")"
                    : directory.toString();
            Label path = new Label(pathText);
            path.setWrapText(false);
            path.setMinWidth(Region.USE_PREF_SIZE);
            HBox pathRow = new HBox(path);
            configurePackageSourceRow(pathRow, index);
            Button removeButton = new Button("Г—");
            removeButton.setDisable(bundled);
            removeButton.setMinWidth(36d);
            removeButton.setAccessibleText(localizationService.text(TextKey.SETTINGS_SOURCE_FOLDERS_REMOVE));
            removeButton.setTooltip(new Tooltip(localizationService.text(TextKey.SETTINGS_SOURCE_FOLDERS_REMOVE)));
            removeButton.setOnAction(event -> runPackageRefresh(() -> {
                if (skins) {
                    skinService.removePackageSourceDirectory(directory);
                    skinService.synchronizePackageSources();
                } else {
                    localizationService.removePackageSourceDirectory(directory);
                    localizationService.refreshPackageSources();
                }
            }, () -> {
                if (skins) skinService.refreshSelectedSkin();
                else localizationService.reloadSelected();
                refreshPackageSourceDirectoryDialog(dialog, dialogContent, skins);
            }));
            HBox actionRow = new HBox(removeButton);
            configurePackageSourceRow(actionRow, index);
            actionRow.setAlignment(Pos.CENTER);
            pathRows.getChildren().add(pathRow);
            actionRows.getChildren().add(actionRow);
        }
        ScrollPane pathScrollPane = new ScrollPane(pathRows);
        pathScrollPane.getStyleClass().add("package-source-path-scroll");
        pathScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pathScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pathScrollPane.setMinWidth(0d);
        HBox.setHgrow(pathScrollPane, Priority.ALWAYS);
        ScrollPane actionScrollPane = new ScrollPane(actionRows);
        actionScrollPane.getStyleClass().add("package-source-action-scroll");
        actionScrollPane.setFitToWidth(true);
        actionScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        actionScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        actionScrollPane.setMinWidth(56d);
        actionScrollPane.setPrefWidth(56d);
        actionScrollPane.setMaxWidth(56d);
        actionScrollPane.vvalueProperty().bind(pathScrollPane.vvalueProperty());
        HBox sourceList = new HBox(pathScrollPane, actionScrollPane);
        sourceList.getStyleClass().add("package-source-list");
        Button addButton = new Button(localizationService.text(TextKey.SETTINGS_SOURCE_FOLDERS_ADD));
        addButton.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(localizationService.text(TextKey.SETTINGS_SOURCE_FOLDERS_SELECT));
            setInitialPackageDirectory(chooser, directories);
            File selected = chooser.showDialog(dialog);
            if (selected == null) return;
            runPackageRefresh(() -> {
                if (skins) skinService.registerPackageSourceDirectory(selected.toPath());
                else localizationService.addPackageSourceDirectory(selected.toPath());
            }, () -> {
                if (skins) skinService.refreshSelectedSkin();
                else localizationService.reloadSelected();
                refreshPackageSourceDirectoryDialog(dialog, dialogContent, skins);
            });
        });
        HBox actions = new HBox(addButton);
        actions.setPadding(new Insets(12d, 0d, 0d, 0d));
        dialogContent.setCenter(sourceList);
        dialogContent.setBottom(actions);
    }

    private void configurePackageSourceRow(HBox row, int index) {
        row.getStyleClass().add("package-source-row");
        row.getStyleClass().add(index % 2 == 0 ? "package-source-row-even" : "package-source-row-odd");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinHeight(44d);
        row.setPrefHeight(44d);
        row.setMaxHeight(44d);
    }

    private void setInitialPackageDirectory(FileChooser chooser, List<Path> directories) {
        directories.stream().filter(Files::isDirectory).findFirst()
                .map(Path::toFile)
                .ifPresent(chooser::setInitialDirectory);
    }

    private void setInitialPackageDirectory(DirectoryChooser chooser, List<Path> directories) {
        directories.stream().filter(Files::isDirectory).findFirst()
                .map(Path::toFile)
                .ifPresent(chooser::setInitialDirectory);
    }

    private void updateInstalledSkin(SkinDescriptor skin) {
        if (skinService.wasLoadedThisSession(skin.manifest().getUid(), skin.manifest().getVersion())) {
            runPackageOperation(() -> skinPackageInstaller.scheduleUpdate(skin), replacement -> {
                rebuildSkins();
                showPendingOperationRestartPrompt("Skin '" + replacement.getName() + "' will be replaced on restart.");
            });
        } else runPackageOperation(() -> skinPackageInstaller.update(skin), replacement -> showInstallationCompleted(replacement, true));
    }

    private List<SkinDescriptor> selectedSkins(List<SkinDescriptor> skins) {
        return skins.stream().filter(skin -> selectedSkinPackages.contains(packageKey(skin))).toList();
    }

    private void refreshBulkSkinActionButtons(List<SkinDescriptor> skins, Button updateButton, Button deleteButton) {
        List<SkinDescriptor> selected = selectedSkins(skins);
        updateButton.setDisable(selected.stream().allMatch(skin -> packageCatalog.skins().getOrDefault(packageKey(skin), List.of()).isEmpty()));
        deleteButton.setDisable(selected.stream().noneMatch(this::canBulkDeleteSkin));
    }

    private boolean canBulkDeleteSkin(SkinDescriptor skin) {
        if (skin.isBuiltIn() || isActive(skin) || pendingDeletionPackages.contains(packageKey(skin))) return false;
        List<Path> sources = packageCatalog.skins().getOrDefault(packageKey(skin), List.of());
        return sources.isEmpty() || sources.size() == 1 && !isProtectedSource(sources.getFirst());
    }

    /**
     * Updates every eligible selection without repeating completion dialogs. Source-backed updates are unambiguous,
     * while loaded resources retain the existing restart-safe scheduling invariant; failures are accumulated so one
     * broken package does not prevent independent selected packages from updating.
     */
    private void updateSelectedSkins(List<SkinDescriptor> skins) {
        List<SkinDescriptor> selected = selectedSkins(skins);
        Set<String> loaded = selected.stream().filter(skin -> skinService.wasLoadedThisSession(
                skin.manifest().getUid(), skin.manifest().getVersion())).map(this::packageKey).collect(Collectors.toSet());
        runPackageOperation(() -> {
            List<String> updated = new ArrayList<>();
            List<String> scheduled = new ArrayList<>();
            List<String> failed = new ArrayList<>();
            for (SkinDescriptor skin : selected) {
                if (!skinPackageInstaller.canUpdate(skin)) continue;
                try {
                    if (loaded.contains(packageKey(skin))) {
                        skinPackageInstaller.scheduleUpdate(skin);
                        scheduled.add(packageKey(skin));
                    } else { skinPackageInstaller.update(skin); updated.add(packageKey(skin)); }
                } catch (IOException | IllegalArgumentException exception) { failed.add(skin.manifest().getName() + ": " + exception.getMessage()); }
            }
            return new PackageBatchResult(updated, scheduled, failed);
        }, result -> {
            selectedSkinPackages.removeAll(result.completed());
            selectedSkinPackages.removeAll(result.scheduled());
            rebuildSkins();
            if (!result.failed().isEmpty()) showOperationError("Skin Update Failed", "Some selected skins could not be updated",
                    new IllegalArgumentException(String.join("\n", result.failed())));
            else if (!result.scheduled().isEmpty()) showPendingOperationRestartPrompt(result.completed().size()
                    + " skin(s) updated; " + result.scheduled().size() + " skin(s) will be updated on restart.");
        });
    }

    private record PackageBatchResult(List<String> completed, List<String> scheduled, List<String> failed) { }

    /**
     * Bulk deletion accepts orphaned copies and packages with exactly one unprotected source. The latter is as
     * unambiguous as the single-item dialog, but deleting external files remains irreversible, so the batch requires
     * a second confirmation listing every affected source. Multiple and protected sources stay ineligible; future
     * changes must not infer a source or bypass bundled-package protection merely because selection mode is active.
     */
    private void deleteSelectedSkins(List<SkinDescriptor> skins) {
        List<BulkSkinDeletion> deletable = selectedSkins(skins).stream()
                .filter(this::canBulkDeleteSkin)
                .map(skin -> new BulkSkinDeletion(skin, packageCatalog.skins().getOrDefault(packageKey(skin), List.of()).stream().findFirst().orElse(null)))
                .toList();
        if (deletable.isEmpty()) return;
        long sourceCount = deletable.stream().filter(target -> target.source() != null).count();
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete " + deletable.size() + " selected skin(s)?"
                        + (sourceCount == 0 ? " Source files will not be changed."
                        : " " + sourceCount + " single-source package file(s) will also be deleted."),
                ButtonType.CANCEL, ButtonType.OK);
        confirmation.initOwner(stage);
        styleDialog(confirmation, "settings-confirmation-dialog");
        confirmation.setTitle(localizationService.text(TextKey.DIALOG_DELETE_SKIN_TITLE));
        confirmation.setHeaderText(localizationService.text(TextKey.SETTINGS_DELETE_SELECTED));
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        List<Path> sourcesToDelete = deletable.stream().map(BulkSkinDeletion::source)
                .filter(source -> source != null).toList();
        if (!sourcesToDelete.isEmpty() && !confirmBulkSourceDeletion(sourcesToDelete)) return;

        runPackageOperation(() -> {
            List<String> completed = new ArrayList<>();
            List<String> scheduled = new ArrayList<>();
            List<String> failed = new ArrayList<>();
            for (BulkSkinDeletion target : deletable) {
                SkinDescriptor skin = target.skin();
                try {
                    if (target.source() != null) skinPackageInstaller.deleteSourceFile(skin, target.source());
                    try { skinPackageInstaller.delete(skin); completed.add(packageKey(skin)); }
                    catch (IOException exception) { skinPackageInstaller.scheduleDeletion(skin); scheduled.add(packageKey(skin)); }
                } catch (IOException | IllegalArgumentException exception) { failed.add(skin.manifest().getName() + ": " + exception.getMessage()); }
            }
            skinService.synchronizePackageSources();
            return new PackageBatchResult(completed, scheduled, failed);
        }, result -> {
            selectedSkinPackages.removeAll(result.completed());
            selectedSkinPackages.removeAll(result.scheduled());
            pendingDeletionPackages.addAll(result.scheduled());
            skinService.refreshSelectedSkin();
            rebuildSkins();
            if (!result.failed().isEmpty()) showOperationError("Skin Deletion Failed", "Some selected skins could not be deleted",
                    new IllegalArgumentException(String.join("\n", result.failed())));
            else if (!result.scheduled().isEmpty()) showPendingOperationRestartPrompt("Some selected skins will be deleted on restart.");
        });
    }

    private boolean confirmBulkSourceDeletion(List<Path> sources) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Permanently delete these source package files? This cannot be undone.\n\n"
                        + String.join("\n", sources.stream().map(Path::toString).toList()),
                ButtonType.CANCEL, ButtonType.OK);
        confirmation.initOwner(stage);
        styleDialog(confirmation, "settings-confirmation-dialog");
        confirmation.setTitle(localizationService.text(TextKey.DIALOG_SOURCE_FILES_TITLE));
        confirmation.setHeaderText(localizationService.text(TextKey.SETTINGS_DELETE_SOURCE));
        return confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void exportSkin(SkinDescriptor skin) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(localizationService.text(TextKey.DIALOG_EXPORT_SKIN_TITLE));
        fileChooser.setInitialFileName(skin.manifest().getName() + ".tsbs");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("The Soundboard skins (*.tsbs)", "*.tsbs"));
        File targetFile = fileChooser.showSaveDialog(stage);
        if (targetFile == null) {
            return;
        }
        runPackageRefresh(() -> skinPackageInstaller.export(skin, targetFile.toPath()), () -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "Skin package saved to " + targetFile.getAbsolutePath(), ButtonType.OK);
            alert.initOwner(stage);
            styleDialog(alert, "settings-information-dialog");
            alert.setTitle("Skin Exported");
            alert.setHeaderText("Export completed");
            alert.showAndWait();
        });
    }

    private void createSkinPackage() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(localizationService.text(TextKey.DIALOG_SELECT_SKIN_FOLDER));
        File selectedDirectory = directoryChooser.showDialog(stage);
        if (selectedDirectory == null) {
            return;
        }

        Path skinDirectory = selectedDirectory.toPath();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(localizationService.text(TextKey.DIALOG_CREATE_SKIN_TITLE));
        fileChooser.setInitialFileName(selectedDirectory.getName() + ".tsbs");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("The Soundboard skins (*.tsbs)", "*.tsbs"));
        File targetFile = fileChooser.showSaveDialog(stage);
        if (targetFile == null) {
            return;
        }

        runPackageRefresh(() -> skinPackageInstaller.createPackage(skinDirectory, targetFile.toPath()), () -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "Skin package saved to " + targetFile.getAbsolutePath(), ButtonType.OK);
            alert.initOwner(stage);
            styleDialog(alert, "settings-information-dialog");
            alert.setTitle("Skin Package Created");
            alert.setHeaderText("Packaging completed");
            alert.showAndWait();
        });
    }

    private void deleteSkin(SkinDescriptor skin, boolean requireConfirmation) {
        if (skin.manifest().getUid().equals(skinService.getActiveSkin().manifest().getUid())
                && skin.manifest().getVersion() == skinService.getActiveSkin().manifest().getVersion()) {
            showOperationError(
                    "Skin Deletion Blocked",
                    "Select another skin before deleting the active skin",
                    new IllegalArgumentException("The active skin may still have fonts and images loaded")
            );
            return;
        }
        if (requireConfirmation) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                    "The installed files and stored package copy will be deleted.", ButtonType.CANCEL, ButtonType.OK);
            confirmation.initOwner(stage);
            styleDialog(confirmation, "settings-confirmation-dialog");
            confirmation.setTitle(localizationService.text(TextKey.DIALOG_DELETE_SKIN_TITLE));
            confirmation.setHeaderText("Delete '" + skin.manifest().getName() + "'?");
            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }
        runPackageOperation(() -> deleteOrSchedule(skin), scheduled -> {
            if (scheduled) pendingDeletionPackages.add(packageKey(skin));
            rebuildSkins();
            if (scheduled) showPendingOperationRestartPrompt("Skin '" + skin.manifest().getName() + "' will be deleted on restart.");
        });
    }

    private boolean deleteOrSchedule(SkinDescriptor skin) throws IOException {
        try { skinPackageInstaller.delete(skin); return false; }
        catch (IOException exception) { skinPackageInstaller.scheduleDeletion(skin); return true; }
    }

    private void deleteSkinFromSource(SkinDescriptor skin, Path source, boolean deleteInstalled) throws IOException {
        runPackageOperation(() -> {
            skinPackageInstaller.deleteSourceFile(skin, source);
            boolean scheduled = deleteInstalled && deleteOrSchedule(skin);
            skinService.synchronizePackageSources();
            return scheduled;
        }, scheduled -> {
            if (scheduled) pendingDeletionPackages.add(packageKey(skin));
            skinService.refreshSelectedSkin();
            rebuildSkins();
            if (scheduled) showPendingOperationRestartPrompt("Skin '" + skin.manifest().getName() + "' will be deleted on restart.");
        });
    }

    private void showInstallationCompleted(SkinDescriptor installedSkin, boolean replaced) {
        rebuildSkins();
        boolean selectedSkinReplaced = replaced
                && installedSkin.manifest().getUid().equals(skinService.getSelectedSkinUid())
                && installedSkin.manifest().getVersion() == skinService.getSelectedSkinVersion();
        if (selectedSkinReplaced) {
            skinService.refreshSelectedSkin();
            if (showRestartPromptIfRequired(installedSkin)) {
                return;
            }
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION,
                "Skin '" + installedSkin.manifest().getName() + "' is now available in the list.",
                ButtonType.OK);
        alert.initOwner(stage);
        styleDialog(alert, "settings-information-dialog");
        alert.setTitle(replaced ? "Skin Replaced" : "Skin Installed");
        alert.setHeaderText(replaced ? "Replacement completed" : "Installation completed");
        alert.showAndWait();
    }

    private boolean showRestartPromptIfRequired(SkinDescriptor skin) {
        List<String> changedSettings = skinService.getRestartRequiredChanges(
                skin.manifest().getUid(), skin.manifest().getVersion());
        if (changedSettings.isEmpty()) {
            return false;
        }
        ButtonType restartButton = new ButtonType(localizationService.text(TextKey.DIALOG_RESTART_NOW), ButtonBar.ButtonData.OK_DONE);
        ButtonType laterButton = new ButtonType(localizationService.text(TextKey.DIALOG_LATER), ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", laterButton, restartButton);
        alert.initOwner(stage);
        styleDialog(alert, "restart-confirmation-dialog");
        alert.setTitle(localizationService.text(TextKey.DIALOG_RESTART_TITLE));
        alert.setHeaderText(localizationService.text(TextKey.DIALOG_RESTART_SKIN_HEADER));
        alert.setContentText("Changed: " + String.join(", ", changedSettings) + ".");
        if (alert.showAndWait().orElse(laterButton) == restartButton) {
            prepareRestart.accept(() -> {
                try {
                    appRestarter.restart();
                    return true;
                } catch (IOException | IllegalStateException exception) {
                    showOperationError("Restart Failed", "The Soundboard could not be restarted automatically", exception);
                    return false;
                }
            });
        }
        return true;
    }

    private void showPendingOperationRestartPrompt(String message) {
        ButtonType restartButton = new ButtonType(localizationService.text(TextKey.DIALOG_RESTART_NOW), ButtonBar.ButtonData.OK_DONE);
        ButtonType laterButton = new ButtonType(localizationService.text(TextKey.DIALOG_LATER), ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, laterButton, restartButton);
        alert.initOwner(stage);
        styleDialog(alert, "restart-confirmation-dialog");
        alert.setTitle(localizationService.text(TextKey.DIALOG_RESTART_TITLE));
        alert.setHeaderText(localizationService.text(TextKey.DIALOG_RESTART_SAFE_HEADER));
        if (alert.showAndWait().orElse(laterButton) == restartButton) {
            prepareRestart.accept(() -> {
                try {
                    appRestarter.restart();
                    return true;
                } catch (IOException | IllegalStateException exception) {
                    showOperationError("Restart Failed", "The Soundboard could not be restarted automatically", exception);
                    return false;
                }
            });
        }
    }

    private void showOperationError(String title, String header, Exception exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR, exception.getMessage(), ButtonType.OK);
        alert.initOwner(stage);
        styleDialog(alert, "settings-error-dialog");
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.showAndWait();
    }

    private void styleDialog(Alert alert, String styleClass) {
        skinService.apply(alert.getDialogPane());
        alert.getDialogPane().getStyleClass().add("settings-dialog");
        alert.getDialogPane().getStyleClass().add(styleClass);
    }

    private boolean isSelected(LocalizationDescriptor localization) {
        return localization.manifest().getUid().equals(localizationService.getSelectedUid())
                && localization.manifest().getVersion() == localizationService.getSelectedVersion();
    }

    private boolean isActive(SkinDescriptor skin) {
        return skin.manifest().getUid().equals(skinService.getActiveSkin().manifest().getUid())
                && skin.manifest().getVersion() == skinService.getActiveSkin().manifest().getVersion();
    }

    private String packageKey(SkinDescriptor skin) {
        return skin.manifest().getUid() + ":" + skin.manifest().getVersion();
    }

    private enum SettingsSection {
        GENERAL(TextKey.SETTINGS_GENERAL), SKINS(TextKey.SETTINGS_SKINS), LOCALIZATION(TextKey.SETTINGS_LOCALIZATION);
        private final TextKey key;
        SettingsSection(TextKey key) { this.key = key; }
        @Override public String toString() { return Texts.text(key); }
    }

    @FunctionalInterface
    private interface PackageSourceDeletion {
        void delete(Path source) throws IOException;
    }

    private record WaveformModeOption(WaveformDisplayMode mode, String label) {
        @Override public String toString() { return label; }
    }

    private record BulkSkinDeletion(SkinDescriptor skin, Path source) {
    }
}
