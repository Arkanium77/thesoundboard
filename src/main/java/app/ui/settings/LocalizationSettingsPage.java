package app.ui.settings;

import app.localization.LocalizationAlreadyInstalledException;
import app.localization.LocalizationDescriptor;
import app.localization.LocalizationPackageInstaller;
import app.localization.LocalizationService;
import app.localization.TextKey;
import app.ui.EmojiText;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Owns localization actions separately from skins because selection reloads text immediately and discovered packages must not overwrite local edits. */
final class LocalizationSettingsPage {
    private final SettingsWindow host;
    private final Stage stage;
    private final LocalizationService localizationService;
    private final LocalizationPackageInstaller localizationPackageInstaller;

    LocalizationSettingsPage(SettingsWindow host, Stage stage, LocalizationService localizationService,
                             LocalizationPackageInstaller localizationPackageInstaller) {
        this.host = host;
        this.stage = stage;
        this.localizationService = localizationService;
        this.localizationPackageInstaller = localizationPackageInstaller;
    }

    void showLocalization() {
        if (host.packageCatalog() == null) { host.runPackageOperation(() -> null, ignored -> showLocalization()); return; }
        HBox header = host.createPackageSectionHeader(TextKey.SETTINGS_LOCALIZATION, this::refreshLocalizations,
                () -> host.showPackageSourceDirectories(false));
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
            updateInstalledButton.setDisable(host.packageCatalog().localizations().getOrDefault(host.packageKey(localization), List.of()).isEmpty());
            updateInstalledButton.setOnAction(event -> updateInstalledLocalization(localization));
            Button deleteButton = new Button(localizationService.text(TextKey.SETTINGS_DELETE));
            List<Path> localizationSources = host.packageCatalog().localizations().getOrDefault(host.packageKey(localization), List.of());
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
        host.showPage(body);
    }

    void refreshLocalizations() {
        host.runPackageRefresh(localizationService::refreshPackageSources, () -> {
            localizationService.reloadSelected();
            host.refreshLocalization();
        });
    }

    private void selectLocalization(LocalizationDescriptor localization) {
        if (!localizationService.select(localization.manifest().getUid(), localization.manifest().getVersion())) return;
        host.refreshLocalization();
    }

    private void installLocalization() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(localizationService.text(TextKey.LOCALIZATION_INSTALL_TITLE));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("The Soundboard localizations (*.tsbl)", "*.tsbl"));
        host.setInitialPackageDirectory(chooser, localizationService.getPackageSourceDirectories());
        List<File> selectedFiles = chooser.showOpenMultipleDialog(stage);
        if (selectedFiles == null) return;
        new PackageInstallBatch<>(selectedFiles, this::installPackage).start();
    }

    private void installPackage(File selected, Runnable next) {
        host.runPackageOperation(() -> localizationPackageInstaller.install(selected.toPath()), installed -> {
            showLocalization();
            next.run();
        }, failure -> {
            if (failure instanceof LocalizationAlreadyInstalledException) {
                Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                        localizationService.text(TextKey.LOCALIZATION_REPLACE_QUESTION), ButtonType.CANCEL, ButtonType.OK);
                confirmation.initOwner(stage);
                host.styleDialog(confirmation, "settings-confirmation-dialog");
                if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                    host.runPackageOperation(() -> localizationPackageInstaller.install(selected.toPath(), true), replacement -> {
                        localizationService.reloadSelected();
                        host.refreshLocalization();
                        next.run();
                    }, replacementFailure -> {
                        host.showOperationError("Localization Replacement Failed", "The localization could not be replaced", new IOException(replacementFailure));
                        next.run();
                    });
                    return;
                }
            } else host.showOperationError("Localization Installation Failed", "The localization could not be installed", new IOException(failure));
            next.run();
        });
    }

    private void updateInstalledLocalization(LocalizationDescriptor localization) {
        host.runPackageOperation(() -> localizationPackageInstaller.update(localization), replacement -> {
            if (isSelected(replacement)) { localizationService.reloadSelected(); host.refreshLocalization(); }
            else showLocalization();
        });
    }

    private void exportLocalization(LocalizationDescriptor localization) {
        File target = chooseLocalizationTarget(localizationService.text(TextKey.LOCALIZATION_EXPORT_TITLE), localization.manifest().getName() + ".tsbl");
        if (target == null) return;
        host.runPackageRefresh(() -> localizationPackageInstaller.export(localization, target.toPath()), () -> { });
    }

    private void deleteLocalization(LocalizationDescriptor localization) {
        host.runPackageRefresh(() -> localizationPackageInstaller.delete(localization), () -> {
            showLocalization();
        });
    }

    private void deleteLocalizationFromSource(LocalizationDescriptor localization, Path source, boolean deleteInstalled) throws IOException {
        host.runPackageRefresh(() -> {
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
        host.runPackageRefresh(() -> localizationPackageInstaller.createPackage(source.toPath(), target.toPath()), () -> { });
    }

    private void exportEnglishStrings() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(localizationService.text(TextKey.LOCALIZATION_EXPORT_STRINGS_TITLE));
        chooser.setInitialFileName("strings.yml");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("YAML files (*.yml)", "*.yml"));
        File target = chooser.showSaveDialog(stage);
        if (target == null) return;
        host.runPackageRefresh(() -> localizationPackageInstaller.exportEnglishStrings(target.toPath()), () -> { });
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
        host.styleDialog(choice, "settings-confirmation-dialog");
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
        host.runPackageOperation(() -> localizationPackageInstaller.updateSource(source.toPath()), this::showLocalizationUpdated);
    }

    private void updateLocalizationPackage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(localizationService.text(TextKey.LOCALIZATION_UPDATE_TITLE));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("The Soundboard localizations (*.tsbl)", "*.tsbl"));
        File packageFile = chooser.showOpenDialog(stage);
        if (packageFile == null) return;
        host.runPackageOperation(() -> localizationPackageInstaller.updatePackage(packageFile.toPath()), this::showLocalizationUpdated);
    }

    private void showLocalizationUpdated(int addedStrings) {
        Alert alert = new Alert(
                Alert.AlertType.INFORMATION,
                localizationService.format(TextKey.LOCALIZATION_UPDATE_COMPLETE, addedStrings),
                ButtonType.OK
        );
        alert.initOwner(stage);
        host.styleDialog(alert, "settings-information-dialog");
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

    private void requestLocalizationDeletion(LocalizationDescriptor localization, List<Path> sources) {
        if (sources.isEmpty()) {
            deleteLocalization(localization);
        } else if (sources.size() == 1) {
            host.showSingleSourceChoice(sources.getFirst(), () -> deleteLocalization(localization),
                    source -> deleteLocalizationFromSource(localization, source, true));
        } else {
            host.showPackageSources(localization.manifest().getName(), sources,
                    source -> deleteLocalizationFromSource(localization, source, false),
                    () -> deleteLocalization(localization), !isSelected(localization));
        }
    }

    private boolean isSelected(LocalizationDescriptor localization) {
        return localization.manifest().getUid().equals(localizationService.getSelectedUid())
                && localization.manifest().getVersion() == localizationService.getSelectedVersion();
    }
}
