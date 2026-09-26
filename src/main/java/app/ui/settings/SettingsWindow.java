package app.ui.settings;

import app.AppRestarter;
import app.localization.LocalizationDescriptor;
import app.localization.LocalizationPackageInstaller;
import app.localization.LocalizationService;
import app.localization.TextKey;
import app.localization.Texts;
import app.project.LastProjectPreferences;
import app.skin.SkinDescriptor;
import app.skin.SkinPackageInstaller;
import app.skin.SkinService;
import app.ui.UiIcons;
import app.waveform.WaveformPreferences;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
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
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.concurrent.Callable;
import javafx.scene.Cursor;

public class SettingsWindow {
    private static final PseudoClass WINDOW_INACTIVE = PseudoClass.getPseudoClass("window-inactive");
    private final Stage owner;
    private final SkinService skinService;
    private final SkinPackageInstaller skinPackageInstaller;
    private final LocalizationService localizationService;
    private final LocalizationPackageInstaller localizationPackageInstaller;
    private final Runnable localizationChanged;
    private final AppRestarter appRestarter = new AppRestarter();
    private final Stage stage = new Stage();
    private final BorderPane content = new BorderPane();
    private final ListView<SettingsSection> sections = new ListView<>();
    private final SettingsOperationRunner operations = new SettingsOperationRunner(this::setPackageBusy);
    private final Consumer<BooleanSupplier> prepareRestart;
    private volatile PackageCatalog packageCatalog;
    private final GeneralSettingsPage generalPage;
    private final SkinSettingsPage skinPage;
    private final LocalizationSettingsPage localizationPage;

    public SettingsWindow(Stage owner, SkinService skinService, LocalizationService localizationService,
                          LastProjectPreferences lastProjectPreferences, WaveformPreferences waveformPreferences,
                          Runnable localizationChanged, Consumer<BooleanSupplier> prepareRestart) {
        this.owner = owner;
        this.prepareRestart = prepareRestart;
        this.skinService = skinService;
        this.skinPackageInstaller = new SkinPackageInstaller(skinService.getRepository());
        this.localizationService = localizationService;
        this.localizationChanged = localizationChanged;
        this.localizationPackageInstaller = new LocalizationPackageInstaller(localizationService.getRepository());
        generalPage = new GeneralSettingsPage(localizationService, lastProjectPreferences, waveformPreferences);
        skinPage = new SkinSettingsPage(this, stage, localizationService, skinService, skinPackageInstaller);
        localizationPage = new LocalizationSettingsPage(this, stage, localizationService, localizationPackageInstaller);
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

        stage.setOnCloseRequest(event -> { if (isPackageOperationRunning()) event.consume(); });
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

    @FunctionalInterface
    interface PackageOperation {
        void run() throws IOException;
    }

    public boolean isPackageOperationRunning() {
        return operations.isRunning();
    }

    /**
     * Serializes explicit rescans by disabling package controls while work is running. A non-daemon worker and close
     * guards prevent normal exit from abandoning a filesystem replacement halfway through. Dialogs and scene updates
     * remain on the FX thread, including failure recovery; a failed scan always re-enables the controls.
     */
    void runPackageRefresh(PackageOperation operation, Runnable completed) {
        runPackageOperation(() -> { operation.run(); return null; }, ignored -> completed.run());
    }

    <T> void runPackageOperation(Callable<T> operation, Consumer<T> completed) {
        runPackageOperation(operation, completed, failure -> showOperationError(
                "Package Operation Failed", "The package operation could not be completed", new IOException(failure)));
    }

    <T> void runPackageOperation(Callable<T> operation, Consumer<T> completed, Consumer<Throwable> failed) {
        operations.run(() -> {
            T result = operation.call();
            packageCatalog = readPackageCatalog();
            return result;
        }, completed, failed);
    }

    private void setPackageBusy(boolean busy) {
        content.setDisable(busy);
        sections.setDisable(busy);
        stage.getScene().setCursor(busy ? Cursor.WAIT : Cursor.DEFAULT);
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

    record PackageCatalog(Map<String, List<Path>> skins, Map<String, List<Path>> localizations) { }

    String packageKey(LocalizationDescriptor localization) {
        return localization.manifest().getUid() + ":" + localization.manifest().getVersion();
    }

    HBox createPackageSectionHeader(TextKey titleKey, Runnable refreshAction, Runnable sourcesAction) {
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

    void refreshLocalization() {
        Texts.configure(localizationService);
        skinPage.invalidate();
        stage.setTitle(localizationService.text(TextKey.SETTINGS_TITLE));
        sections.refresh();
        showLocalization();
        localizationChanged.run();
    }

    void showSingleSourceChoice(Path source, Runnable installedDeletion, PackageSourceDeletion sourceDeletion) {
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

    void showPackageSources(String packageName, List<Path> sources,
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

    boolean isProtectedSource(Path source) {
        return skinPackageInstaller.isProtectedSource(source)
                || localizationPackageInstaller.isProtectedSource(source);
    }

    void showPackageSourceDirectories(boolean skins) {
        Stage dialog = new Stage();
        dialog.initOwner(stage);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(localizationService.text(TextKey.SETTINGS_SOURCE_FOLDERS_TITLE));
        dialog.getIcons().setAll(stage.getIcons());
        BorderPane dialogContent = new BorderPane();
        dialogContent.getStyleClass().add("package-source-dialog");
        dialogContent.disableProperty().bind(content.disableProperty());
        dialog.setOnCloseRequest(event -> { if (isPackageOperationRunning()) event.consume(); });
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

    void setInitialPackageDirectory(FileChooser chooser, List<Path> directories) {
        directories.stream().filter(Files::isDirectory).findFirst()
                .map(Path::toFile)
                .ifPresent(chooser::setInitialDirectory);
    }

    void setInitialPackageDirectory(DirectoryChooser chooser, List<Path> directories) {
        directories.stream().filter(Files::isDirectory).findFirst()
                .map(Path::toFile)
                .ifPresent(chooser::setInitialDirectory);
    }

    boolean showRestartPromptIfRequired(SkinDescriptor skin) {
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

    void showPendingOperationRestartPrompt(String message) {
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

    void showOperationError(String title, String header, Exception exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR, exception.getMessage(), ButtonType.OK);
        alert.initOwner(stage);
        styleDialog(alert, "settings-error-dialog");
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.showAndWait();
    }

    void styleDialog(Alert alert, String styleClass) {
        skinService.apply(alert.getDialogPane());
        alert.getDialogPane().getStyleClass().add("settings-dialog");
        alert.getDialogPane().getStyleClass().add(styleClass);
    }

    String packageKey(SkinDescriptor skin) {
        return skin.manifest().getUid() + ":" + skin.manifest().getVersion();
    }

    private enum SettingsSection {
        GENERAL(TextKey.SETTINGS_GENERAL), SKINS(TextKey.SETTINGS_SKINS), LOCALIZATION(TextKey.SETTINGS_LOCALIZATION);
        private final TextKey key;
        SettingsSection(TextKey key) { this.key = key; }
        @Override public String toString() { return Texts.text(key); }
    }

    @FunctionalInterface
    interface PackageSourceDeletion {
        void delete(Path source) throws IOException;
    }

    void showPage(Node page) { content.setCenter(page); }
    PackageCatalog packageCatalog() { return packageCatalog; }
    private void showGeneral() { showPage(generalPage.build()); }
    private void showSkins() { skinPage.showSkins(); }
    private void rebuildSkins() { skinPage.rebuildSkins(); }
    private void refreshSkins() { skinPage.refreshSkins(); }
    private void showLocalization() { localizationPage.showLocalization(); }
    private void refreshLocalizations() { localizationPage.refreshLocalizations(); }
}
