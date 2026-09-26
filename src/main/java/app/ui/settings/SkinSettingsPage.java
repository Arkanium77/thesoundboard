package app.ui.settings;

import app.localization.LocalizationService;
import app.localization.TextKey;
import app.skin.SkinDescriptor;
import app.skin.SkinAlreadyInstalledException;
import app.skin.SkinPackageInstaller;
import app.skin.SkinService;
import app.ui.EmojiText;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Owns skin selection and deferred-deletion UI state. Loaded skin resources retain restart-safe replacement semantics; package workers and dialogs remain coordinated by the window. */
final class SkinSettingsPage {
    private final SettingsWindow host;
    private final Stage stage;
    private final LocalizationService localizationService;
    private final SkinService skinService;
    private final SkinPackageInstaller skinPackageInstaller;
    private final Set<String> pendingDeletionPackages = new LinkedHashSet<>();
    private final Set<String> selectedSkinPackages = new LinkedHashSet<>();
    private boolean skinSelectionMode;
    private Node skinsPage;

    SkinSettingsPage(SettingsWindow host, Stage stage, LocalizationService localizationService,
                     SkinService skinService, SkinPackageInstaller skinPackageInstaller) {
        this.host = host;
        this.stage = stage;
        this.localizationService = localizationService;
        this.skinService = skinService;
        this.skinPackageInstaller = skinPackageInstaller;
    }

    void invalidate() { skinsPage = null; }

    void showSkins() {
        if (host.packageCatalog() == null) { host.runPackageOperation(() -> null, ignored -> showSkins()); return; }
        if (skinsPage != null) {
            host.showPage(skinsPage);
            return;
        }
        HBox header = host.createPackageSectionHeader(TextKey.SETTINGS_SKINS, this::refreshSkins,
                () -> host.showPackageSourceDirectories(true));
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
        selectedSkinPackages.retainAll(skins.stream().map(host::packageKey).toList());
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
                skinCheckBox.setSelected(selectedSkinPackages.contains(host.packageKey(skin)));
                skinCheckBox.setDisable(!canBulkDeleteSkin(skin));
                skinCheckBox.setOnAction(event -> {
                    if (skinCheckBox.isSelected()) selectedSkinPackages.add(host.packageKey(skin));
                    else selectedSkinPackages.remove(host.packageKey(skin));
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
            boolean pendingDeletion = pendingDeletionPackages.contains(host.packageKey(skin));
            List<Path> skinSources = host.packageCatalog().skins().getOrDefault(host.packageKey(skin), List.of());
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
        host.showPage(skinsPage);
    }

    void rebuildSkins() {
        skinsPage = null;
        showSkins();
    }

    /**
     * Discovery is explicit and runs outside the FX thread. Navigation only reads the repository snapshot; the
     * selected skin is reapplied after the worker finishes because resource binding is confined to the UI thread.
     */
    void refreshSkins() {
        host.runPackageRefresh(skinService::synchronizePackageSources, () -> {
            skinService.refreshSelectedSkin();
            rebuildSkins();
        });
    }

    private void selectSkin(SkinDescriptor skin) {
        boolean skinChanged = skinService.selectSkin(skin.manifest().getUid(), skin.manifest().getVersion());
        rebuildSkins();
        if (!skinChanged) {
            return;
        }
        host.showRestartPromptIfRequired(skin);
    }

    private void installSkin() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(localizationService.text(TextKey.DIALOG_INSTALL_SKIN_TITLE));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("The Soundboard skins (*.tsbs)", "*.tsbs"));
        host.setInitialPackageDirectory(fileChooser, skinService.getPackageSourceDirectories());
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(stage);
        if (selectedFiles == null) {
            return;
        }
        new PackageInstallBatch<>(selectedFiles, this::installPackage).start();
    }

    private void installPackage(File selectedFile, Runnable next) {
        host.runPackageOperation(() -> skinPackageInstaller.install(selectedFile.toPath()), installed -> {
            showInstallationCompleted(installed, false);
            next.run();
        }, failure -> {
            if (failure instanceof SkinAlreadyInstalledException conflict) {
                Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                        "This skin version is already installed. Only its installed copy will be replaced.", ButtonType.CANCEL, ButtonType.OK);
                confirmation.initOwner(stage);
                host.styleDialog(confirmation, "settings-confirmation-dialog");
                confirmation.setHeaderText("Replace '" + conflict.getSkin().getName() + "'?");
                if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                    if (skinService.wasLoadedThisSession(conflict.getSkin().getUid(), conflict.getSkin().getVersion())) {
                        host.runPackageOperation(() -> skinPackageInstaller.scheduleReplacement(selectedFile.toPath()), replacement -> {
                            rebuildSkins();
                            host.showPendingOperationRestartPrompt("Skin '" + replacement.getName() + "' will be replaced on restart.");
                            next.run();
                        }, replacementFailure -> {
                            host.showOperationError("Skin Replacement Failed", "The skin could not be replaced", new IOException(replacementFailure));
                            next.run();
                        });
                    } else host.runPackageOperation(() -> skinPackageInstaller.install(selectedFile.toPath(), true), replacement -> {
                        showInstallationCompleted(replacement, true);
                        next.run();
                    }, replacementFailure -> {
                        host.showOperationError("Skin Replacement Failed", "The skin could not be replaced", new IOException(replacementFailure));
                        next.run();
                    });
                    return;
                }
            } else host.showOperationError("Skin Installation Failed", "The skin could not be installed", new IOException(failure));
            next.run();
        });
    }

    private void requestSkinDeletion(SkinDescriptor skin, List<Path> sources) {
        if (sources.isEmpty()) {
            deleteSkin(skin, true);
        } else if (sources.size() == 1) {
            host.showSingleSourceChoice(sources.getFirst(), () -> deleteSkin(skin, false),
                    source -> deleteSkinFromSource(skin, source, true));
        } else {
            host.showPackageSources(skin.manifest().getName(), sources,
                    source -> deleteSkinFromSource(skin, source, false), () -> deleteSkin(skin, true),
                    !isActive(skin));
        }
    }

    private void updateInstalledSkin(SkinDescriptor skin) {
        if (skinService.wasLoadedThisSession(skin.manifest().getUid(), skin.manifest().getVersion())) {
            host.runPackageOperation(() -> skinPackageInstaller.scheduleUpdate(skin), replacement -> {
                rebuildSkins();
                host.showPendingOperationRestartPrompt("Skin '" + replacement.getName() + "' will be replaced on restart.");
            });
        } else host.runPackageOperation(() -> skinPackageInstaller.update(skin), replacement -> showInstallationCompleted(replacement, true));
    }

    private List<SkinDescriptor> selectedSkins(List<SkinDescriptor> skins) {
        return skins.stream().filter(skin -> selectedSkinPackages.contains(host.packageKey(skin))).toList();
    }

    private void refreshBulkSkinActionButtons(List<SkinDescriptor> skins, Button updateButton, Button deleteButton) {
        List<SkinDescriptor> selected = selectedSkins(skins);
        updateButton.setDisable(selected.stream().allMatch(skin -> host.packageCatalog().skins().getOrDefault(host.packageKey(skin), List.of()).isEmpty()));
        deleteButton.setDisable(selected.stream().noneMatch(this::canBulkDeleteSkin));
    }

    private boolean canBulkDeleteSkin(SkinDescriptor skin) {
        if (skin.isBuiltIn() || isActive(skin) || pendingDeletionPackages.contains(host.packageKey(skin))) return false;
        List<Path> sources = host.packageCatalog().skins().getOrDefault(host.packageKey(skin), List.of());
        return sources.isEmpty() || sources.size() == 1 && !host.isProtectedSource(sources.getFirst());
    }

    /**
     * Updates every eligible selection without repeating completion dialogs. Source-backed updates are unambiguous,
     * while loaded resources retain the existing restart-safe scheduling invariant; failures are accumulated so one
     * broken package does not prevent independent selected packages from updating.
     */
    private void updateSelectedSkins(List<SkinDescriptor> skins) {
        List<SkinDescriptor> selected = selectedSkins(skins);
        Set<String> loaded = selected.stream().filter(skin -> skinService.wasLoadedThisSession(
                skin.manifest().getUid(), skin.manifest().getVersion())).map(host::packageKey).collect(Collectors.toSet());
        host.runPackageOperation(() -> {
            List<String> updated = new ArrayList<>();
            List<String> scheduled = new ArrayList<>();
            List<String> failed = new ArrayList<>();
            for (SkinDescriptor skin : selected) {
                if (!skinPackageInstaller.canUpdate(skin)) continue;
                try {
                    if (loaded.contains(host.packageKey(skin))) {
                        skinPackageInstaller.scheduleUpdate(skin);
                        scheduled.add(host.packageKey(skin));
                    } else { skinPackageInstaller.update(skin); updated.add(host.packageKey(skin)); }
                } catch (IOException | IllegalArgumentException exception) { failed.add(skin.manifest().getName() + ": " + exception.getMessage()); }
            }
            return new PackageBatchResult(updated, scheduled, failed);
        }, result -> {
            selectedSkinPackages.removeAll(result.completed());
            selectedSkinPackages.removeAll(result.scheduled());
            rebuildSkins();
            if (!result.failed().isEmpty()) host.showOperationError("Skin Update Failed", "Some selected skins could not be updated",
                    new IllegalArgumentException(String.join("\n", result.failed())));
            else if (!result.scheduled().isEmpty()) host.showPendingOperationRestartPrompt(result.completed().size()
                    + " skin(s) updated; " + result.scheduled().size() + " skin(s) will be updated on restart.");
        });
    }

    /**
     * Bulk deletion accepts orphaned copies and packages with exactly one unprotected source. The latter is as
     * unambiguous as the single-item dialog, but deleting external files remains irreversible, so the batch requires
     * a second confirmation listing every affected source. Multiple and protected sources stay ineligible; future
     * changes must not infer a source or bypass bundled-package protection merely because selection mode is active.
     */
    private void deleteSelectedSkins(List<SkinDescriptor> skins) {
        List<BulkSkinDeletion> deletable = selectedSkins(skins).stream()
                .filter(this::canBulkDeleteSkin)
                .map(skin -> new BulkSkinDeletion(skin, host.packageCatalog().skins().getOrDefault(host.packageKey(skin), List.of()).stream().findFirst().orElse(null)))
                .toList();
        if (deletable.isEmpty()) return;
        long sourceCount = deletable.stream().filter(target -> target.source() != null).count();
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete " + deletable.size() + " selected skin(s)?"
                        + (sourceCount == 0 ? " Source files will not be changed."
                        : " " + sourceCount + " single-source package file(s) will also be deleted."),
                ButtonType.CANCEL, ButtonType.OK);
        confirmation.initOwner(stage);
        host.styleDialog(confirmation, "settings-confirmation-dialog");
        confirmation.setTitle(localizationService.text(TextKey.DIALOG_DELETE_SKIN_TITLE));
        confirmation.setHeaderText(localizationService.text(TextKey.SETTINGS_DELETE_SELECTED));
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        List<Path> sourcesToDelete = deletable.stream().map(BulkSkinDeletion::source)
                .filter(source -> source != null).toList();
        if (!sourcesToDelete.isEmpty() && !confirmBulkSourceDeletion(sourcesToDelete)) return;

        host.runPackageOperation(() -> {
            List<String> completed = new ArrayList<>();
            List<String> scheduled = new ArrayList<>();
            List<String> failed = new ArrayList<>();
            for (BulkSkinDeletion target : deletable) {
                SkinDescriptor skin = target.skin();
                try {
                    if (target.source() != null) skinPackageInstaller.deleteSourceFile(skin, target.source());
                    try { skinPackageInstaller.delete(skin); completed.add(host.packageKey(skin)); }
                    catch (IOException exception) { skinPackageInstaller.scheduleDeletion(skin); scheduled.add(host.packageKey(skin)); }
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
            if (!result.failed().isEmpty()) host.showOperationError("Skin Deletion Failed", "Some selected skins could not be deleted",
                    new IllegalArgumentException(String.join("\n", result.failed())));
            else if (!result.scheduled().isEmpty()) host.showPendingOperationRestartPrompt("Some selected skins will be deleted on restart.");
        });
    }

    private boolean confirmBulkSourceDeletion(List<Path> sources) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Permanently delete these source package files? This cannot be undone.\n\n"
                        + String.join("\n", sources.stream().map(Path::toString).toList()),
                ButtonType.CANCEL, ButtonType.OK);
        confirmation.initOwner(stage);
        host.styleDialog(confirmation, "settings-confirmation-dialog");
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
        host.runPackageRefresh(() -> skinPackageInstaller.export(skin, targetFile.toPath()), () -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "Skin package saved to " + targetFile.getAbsolutePath(), ButtonType.OK);
            alert.initOwner(stage);
            host.styleDialog(alert, "settings-information-dialog");
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

        host.runPackageRefresh(() -> skinPackageInstaller.createPackage(skinDirectory, targetFile.toPath()), () -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "Skin package saved to " + targetFile.getAbsolutePath(), ButtonType.OK);
            alert.initOwner(stage);
            host.styleDialog(alert, "settings-information-dialog");
            alert.setTitle("Skin Package Created");
            alert.setHeaderText("Packaging completed");
            alert.showAndWait();
        });
    }

    private void deleteSkin(SkinDescriptor skin, boolean requireConfirmation) {
        if (skin.manifest().getUid().equals(skinService.getActiveSkin().manifest().getUid())
                && skin.manifest().getVersion() == skinService.getActiveSkin().manifest().getVersion()) {
            host.showOperationError(
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
            host.styleDialog(confirmation, "settings-confirmation-dialog");
            confirmation.setTitle(localizationService.text(TextKey.DIALOG_DELETE_SKIN_TITLE));
            confirmation.setHeaderText("Delete '" + skin.manifest().getName() + "'?");
            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }
        host.runPackageOperation(() -> deleteOrSchedule(skin), scheduled -> {
            if (scheduled) pendingDeletionPackages.add(host.packageKey(skin));
            rebuildSkins();
            if (scheduled) host.showPendingOperationRestartPrompt("Skin '" + skin.manifest().getName() + "' will be deleted on restart.");
        });
    }

    private boolean deleteOrSchedule(SkinDescriptor skin) throws IOException {
        try { skinPackageInstaller.delete(skin); return false; }
        catch (IOException exception) { skinPackageInstaller.scheduleDeletion(skin); return true; }
    }

    private void deleteSkinFromSource(SkinDescriptor skin, Path source, boolean deleteInstalled) throws IOException {
        host.runPackageOperation(() -> {
            skinPackageInstaller.deleteSourceFile(skin, source);
            boolean scheduled = deleteInstalled && deleteOrSchedule(skin);
            skinService.synchronizePackageSources();
            return scheduled;
        }, scheduled -> {
            if (scheduled) pendingDeletionPackages.add(host.packageKey(skin));
            skinService.refreshSelectedSkin();
            rebuildSkins();
            if (scheduled) host.showPendingOperationRestartPrompt("Skin '" + skin.manifest().getName() + "' will be deleted on restart.");
        });
    }

    private void showInstallationCompleted(SkinDescriptor installedSkin, boolean replaced) {
        rebuildSkins();
        boolean selectedSkinReplaced = replaced
                && installedSkin.manifest().getUid().equals(skinService.getSelectedSkinUid())
                && installedSkin.manifest().getVersion() == skinService.getSelectedSkinVersion();
        if (selectedSkinReplaced) {
            skinService.refreshSelectedSkin();
            if (host.showRestartPromptIfRequired(installedSkin)) {
                return;
            }
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION,
                "Skin '" + installedSkin.manifest().getName() + "' is now available in the list.",
                ButtonType.OK);
        alert.initOwner(stage);
        host.styleDialog(alert, "settings-information-dialog");
        alert.setTitle(replaced ? "Skin Replaced" : "Skin Installed");
        alert.setHeaderText(replaced ? "Replacement completed" : "Installation completed");
        alert.showAndWait();
    }

    private boolean isActive(SkinDescriptor skin) {
        return skin.manifest().getUid().equals(skinService.getActiveSkin().manifest().getUid())
                && skin.manifest().getVersion() == skinService.getActiveSkin().manifest().getVersion();
    }

    private record PackageBatchResult(List<String> completed, List<String> scheduled, List<String> failed) { }

    private record BulkSkinDeletion(SkinDescriptor skin, Path source) {
    }
}
