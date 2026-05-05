package app.ui.main;

import app.audio.AudioEngine;
import app.config.AppConfig;
import app.model.AudioFile;
import app.model.ProjectState;
import app.model.VirtualFolder;
import app.model.WorkspaceTrack;
import app.project.ProjectLoadResult;
import app.project.ProjectService;
import app.project.ProjectStateEditor;
import app.ui.tile.TrackTileView;
import app.ui.tree.TreeNodeType;
import app.ui.tree.TreeNodeValue;
import app.ui.workspace.WorkspaceTrackItem;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class MainView extends BorderPane {
    private static final Logger LOGGER = LoggerFactory.getLogger(MainView.class);

    private final Stage stage;
    private final AppConfig appConfig;
    private final ProjectService projectService;
    private final ProjectStateEditor projectStateEditor;
    private final AudioEngine audioEngine;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final Button openFolderButton = new Button("Open Folder");
    private final Button rescanButton = new Button("Rescan");
    private final Button saveButton = new Button("Save");
    private final Button clearWorkspaceButton = new Button("Clear Workspace");
    private final Button rebuildStateButton = new Button("Rebuild Save");
    private final Label projectPathLabel = new Label("No folder selected");
    private final Label statusLabel = new Label("Ready");
    private final TreeView<TreeNodeValue> treeView = new TreeView<>();
    private final Slider masterVolumeSlider = new Slider(0d, 100d, 100d);
    private final Label masterVolumeValueLabel = new Label("100%");
    private final WorkspaceView workspaceView;

    private final Map<UUID, WorkspaceTrackItem> workspaceTrackItems = new LinkedHashMap<>();
    private final Map<UUID, TrackTileView> trackTileViews = new LinkedHashMap<>();

    private Path currentRootPath;
    private ProjectState projectState;
    private boolean loading;
    private double masterVolume;

    public MainView(
            Stage stage,
            AppConfig appConfig,
            ProjectService projectService,
            ProjectStateEditor projectStateEditor,
            AudioEngine audioEngine
    ) {
        this.stage = stage;
        this.appConfig = appConfig;
        this.projectService = projectService;
        this.projectStateEditor = projectStateEditor;
        this.audioEngine = audioEngine;
        this.projectState = ProjectState.empty(appConfig.getSchemaVersion());
        this.masterVolume = appConfig.getWorkspace().getDefaultMasterVolume();
        this.workspaceView = new WorkspaceView(appConfig.getUi(), new WorkspaceView.WorkspaceDropHandler() {
            @Override
            public void moveTrack(UUID workspaceTrackId, UUID targetWorkspaceTrackId, boolean placeAfter) {
                moveWorkspaceTrack(workspaceTrackId, targetWorkspaceTrackId, placeAfter);
            }

            @Override
            public void moveTrackToEnd(UUID workspaceTrackId) {
                moveWorkspaceTrackToEnd(workspaceTrackId);
            }

            @Override
            public void addAudioFiles(List<UUID> audioFileIds, UUID targetWorkspaceTrackId, boolean placeAfter) {
                addAudioFilesToWorkspace(audioFileIds, targetWorkspaceTrackId, placeAfter);
            }
        });

        configureLayout();
        configureTreeView();
        configureActions();
        rebuildTree();
        rebuildWorkspace();
    }

    public void shutdown() {
        LOGGER.info("Shutting down application resources");
        saveCurrentProject();
        clearWorkspace();
        executorService.shutdownNow();
    }

    private void configureLayout() {
        setPadding(new Insets(8));
        setTop(createToolBar());

        VBox leftPane = new VBox(8, new Label("Project Tree"), treeView);
        leftPane.setPadding(new Insets(8));
        VBox.setVgrow(treeView, Priority.ALWAYS);
        leftPane.setPrefWidth(appConfig.getUi().getTreeWidth());

        VBox rightPane = new VBox(8, createWorkspaceHeader(), workspaceView);
        rightPane.setPadding(new Insets(8));
        VBox.setVgrow(workspaceView, Priority.ALWAYS);

        SplitPane splitPane = new SplitPane(leftPane, rightPane);
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.setDividerPositions(0.3d);

        setCenter(splitPane);
        setBottom(statusLabel);
        BorderPane.setMargin(statusLabel, new Insets(8, 12, 4, 12));
    }

    private ToolBar createToolBar() {
        ToolBar toolBar = new ToolBar(
                openFolderButton,
                rescanButton,
                saveButton,
                clearWorkspaceButton,
                rebuildStateButton,
                projectPathLabel
        );
        rescanButton.setDisable(true);
        saveButton.setDisable(true);
        clearWorkspaceButton.setDisable(true);
        rebuildStateButton.setDisable(true);
        return toolBar;
    }

    private HBox createWorkspaceHeader() {
        Label workspaceLabel = new Label("Workspace");
        Label masterVolumeLabel = new Label("Master Volume");

        masterVolumeSlider.setValue(masterVolume * 100d);
        masterVolumeSlider.setPrefWidth(180d);
        masterVolumeValueLabel.setMinWidth(44d);

        HBox header = new HBox(12, workspaceLabel, createSpacer(), masterVolumeLabel, masterVolumeSlider, masterVolumeValueLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private void configureActions() {
        openFolderButton.setOnAction(event -> chooseFolder());
        rescanButton.setOnAction(event -> {
            if (currentRootPath != null) {
                loadProject(currentRootPath, false);
            }
        });
        saveButton.setOnAction(event -> saveCurrentProject());
        clearWorkspaceButton.setOnAction(event -> clearWorkspaceTracks());
        rebuildStateButton.setOnAction(event -> rebuildStateFile());
        masterVolumeSlider.valueProperty().addListener((observable, oldValue, newValue) -> updateMasterVolume(newValue.doubleValue() / 100d));
        updateMasterVolume(masterVolume);
    }

    private void configureTreeView() {
        treeView.setShowRoot(false);
        treeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        treeView.setCellFactory(view -> {
            TreeCell<TreeNodeValue> cell = new TreeCell<>() {
                @Override
                protected void updateItem(TreeNodeValue item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setContextMenu(null);
                        setStyle("");
                        return;
                    }

                    setText(item.getLabel());
                    setContextMenu(createContextMenu(item));
                    if (item.isMissing()) {
                        setStyle("-fx-text-fill: #aa3b3b;");
                    } else if (item.getType() == TreeNodeType.REAL_ROOT || item.getType() == TreeNodeType.VIRTUAL_ROOT) {
                        setStyle("-fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            };

            cell.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !cell.isEmpty()) {
                    TreeNodeValue item = cell.getItem();
                    if (item.getType() == TreeNodeType.REAL_AUDIO_FILE || item.getType() == TreeNodeType.VIRTUAL_AUDIO_FILE) {
                        addSelectedAudioFilesToWorkspace(item.getAudioFileId());
                    }
                }
            });

            cell.setOnDragDetected(event -> {
                if (cell.isEmpty()) {
                    return;
                }

                if (!cell.isSelected()) {
                    treeView.getSelectionModel().clearSelection();
                    treeView.getSelectionModel().select(cell.getIndex());
                }

                List<UUID> selectedAudioFileIds = collectSelectedAudioFileIds(cell.getItem().getAudioFileId());
                if (selectedAudioFileIds.isEmpty()) {
                    return;
                }

                var dragboard = cell.startDragAndDrop(TransferMode.COPY);
                ClipboardContent content = new ClipboardContent();
                content.putString("audio-files:" + selectedAudioFileIds.stream()
                        .map(UUID::toString)
                        .collect(Collectors.joining(",")));
                dragboard.setContent(content);
                event.consume();
            });

            return cell;
        });
    }

    private ContextMenu createContextMenu(TreeNodeValue nodeValue) {
        ContextMenu contextMenu = new ContextMenu();
        List<MenuItem> menuItems = new ArrayList<>();

        if (nodeValue.getType() == TreeNodeType.REAL_AUDIO_FILE || nodeValue.getType() == TreeNodeType.VIRTUAL_AUDIO_FILE) {
            MenuItem addToWorkspaceItem = new MenuItem("Add To Workspace");
            addToWorkspaceItem.setOnAction(event -> addSelectedAudioFilesToWorkspace(nodeValue.getAudioFileId()));
            menuItems.add(addToWorkspaceItem);

            Menu addToVirtualFolderMenu = createAddToVirtualFolderMenu(nodeValue.getAudioFileId());
            if (addToVirtualFolderMenu != null) {
                menuItems.add(addToVirtualFolderMenu);
            }
        }

        if (nodeValue.getType() == TreeNodeType.VIRTUAL_ROOT) {
            MenuItem createFolderItem = new MenuItem("Create Virtual Folder");
            createFolderItem.setOnAction(event -> createVirtualFolder(null));
            menuItems.add(createFolderItem);
        }

        if (nodeValue.getType() == TreeNodeType.VIRTUAL_FOLDER) {
            MenuItem createChildFolderItem = new MenuItem("Create Child Virtual Folder");
            createChildFolderItem.setOnAction(event -> createVirtualFolder(nodeValue.getVirtualFolderId()));
            menuItems.add(createChildFolderItem);

            MenuItem renameFolderItem = new MenuItem("Rename Virtual Folder");
            renameFolderItem.setOnAction(event -> renameVirtualFolder(nodeValue.getVirtualFolderId()));
            menuItems.add(renameFolderItem);
        }

        if (nodeValue.getType() == TreeNodeType.VIRTUAL_AUDIO_FILE && nodeValue.getParentVirtualFolderId() != null) {
            MenuItem removeFromVirtualFolderItem = new MenuItem("Remove From Virtual Folder");
            removeFromVirtualFolderItem.setOnAction(event ->
                    removeAudioFileFromVirtualFolder(nodeValue.getParentVirtualFolderId(), nodeValue.getAudioFileId())
            );
            menuItems.add(removeFromVirtualFolderItem);
        }

        if (menuItems.isEmpty()) {
            return null;
        }

        contextMenu.getItems().setAll(menuItems);
        return contextMenu;
    }

    private Menu createAddToVirtualFolderMenu(UUID audioFileId) {
        Map<UUID, String> folderPaths = collectVirtualFolderPaths();
        if (folderPaths.isEmpty()) {
            return null;
        }

        Menu menu = new Menu("Add To Virtual Folder");
        folderPaths.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> {
                    MenuItem menuItem = new MenuItem(entry.getValue());
                    menuItem.setOnAction(event -> addAudioFileToVirtualFolder(entry.getKey(), audioFileId));
                    menu.getItems().add(menuItem);
                });
        return menu;
    }

    private void chooseFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Open Soundboard Project Folder");
        if (currentRootPath != null) {
            directoryChooser.setInitialDirectory(currentRootPath.toFile());
        }

        File selectedDirectory = directoryChooser.showDialog(stage);
        if (selectedDirectory != null) {
            LOGGER.info("Selected root folder {}", selectedDirectory.toPath());
            loadProject(selectedDirectory.toPath(), true);
        }
    }

    private void loadProject(Path rootPath, boolean saveBeforeSwitch) {
        if (loading) {
            return;
        }

        if (saveBeforeSwitch && currentRootPath != null) {
            saveCurrentProject();
        }

        clearWorkspace();
        projectState = ProjectState.empty(appConfig.getSchemaVersion());
        rebuildTree();
        rebuildWorkspace();

        loading = true;
        setControlsDisabled(true);
        statusLabel.setText("Loading " + rootPath + " ...");

        Task<ProjectLoadResult> loadTask = new Task<>() {
            @Override
            protected ProjectLoadResult call() throws Exception {
                return projectService.loadProject(rootPath);
            }
        };

        loadTask.setOnSucceeded(event -> {
            loading = false;
            setControlsDisabled(false);

            ProjectLoadResult loadResult = loadTask.getValue();
            currentRootPath = loadResult.getRootPath();
            projectState = loadResult.getProjectState();
            projectPathLabel.setText(currentRootPath.toString());
            statusLabel.setText("Loaded " + currentRootPath);
            rescanButton.setDisable(false);
            saveButton.setDisable(false);

            rebuildTree();
            rebuildWorkspace();
            if (loadResult.getPersistenceLoadException() == null) {
                saveCurrentProject();
            }

            if (loadResult.getPersistenceLoadException() != null) {
                showError(
                        "Failed to read saved project state",
                        "The folder was opened without persisted state. See logs for details.",
                        loadResult.getPersistenceLoadException()
                );
            }
        });

        loadTask.setOnFailed(event -> {
            loading = false;
            setControlsDisabled(false);
            currentRootPath = null;
            projectPathLabel.setText("No folder selected");
            statusLabel.setText("Failed to load project");
            showError("Failed to open folder", "The project folder could not be loaded.", asException(loadTask.getException()));
        });

        executorService.submit(loadTask);
    }

    private void rebuildTree() {
        TreeItem<TreeNodeValue> rootItem = new TreeItem<>(new TreeNodeValue(TreeNodeType.ROOT, "Root", null, null, null, false));
        TreeItem<TreeNodeValue> realRoot = new TreeItem<>(new TreeNodeValue(TreeNodeType.REAL_ROOT, "Real Files", null, null, null, false));
        TreeItem<TreeNodeValue> virtualRoot = new TreeItem<>(new TreeNodeValue(TreeNodeType.VIRTUAL_ROOT, "Virtual Folders", null, null, null, false));

        buildRealTree(realRoot);
        buildVirtualTree(virtualRoot);

        rootItem.getChildren().addAll(realRoot, virtualRoot);
        sortTree(rootItem);
        treeView.setRoot(rootItem);
        rootItem.setExpanded(true);
        realRoot.setExpanded(true);
        virtualRoot.setExpanded(true);
    }

    private void buildRealTree(TreeItem<TreeNodeValue> realRoot) {
        Map<String, TreeItem<TreeNodeValue>> folderIndex = new HashMap<>();
        folderIndex.put("", realRoot);

        projectState.getAudioFiles().stream()
                .filter(audioFile -> !audioFile.isMissing())
                .sorted(Comparator.comparing(AudioFile::getRelativePath, String.CASE_INSENSITIVE_ORDER))
                .forEach(audioFile -> {
                    String[] pathParts = audioFile.getRelativePath().split("/");
                    StringBuilder currentFolderPath = new StringBuilder();
                    TreeItem<TreeNodeValue> parentItem = realRoot;

                    for (int index = 0; index < pathParts.length - 1; index++) {
                        if (!currentFolderPath.isEmpty()) {
                            currentFolderPath.append('/');
                        }
                        currentFolderPath.append(pathParts[index]);

                        String folderPathKey = currentFolderPath.toString();
                        TreeItem<TreeNodeValue> folderItem = folderIndex.get(folderPathKey);
                        if (folderItem == null) {
                            folderItem = new TreeItem<>(new TreeNodeValue(TreeNodeType.REAL_FOLDER, pathParts[index], null, null, null, false));
                            folderIndex.put(folderPathKey, folderItem);
                            parentItem.getChildren().add(folderItem);
                        }
                        parentItem = folderItem;
                    }

                    parentItem.getChildren().add(new TreeItem<>(new TreeNodeValue(
                            TreeNodeType.REAL_AUDIO_FILE,
                            audioFile.getDisplayName(),
                            audioFile.getId(),
                            null,
                            null,
                            false
                    )));
                });
    }

    private void buildVirtualTree(TreeItem<TreeNodeValue> virtualRoot) {
        Map<UUID, VirtualFolder> folderById = new LinkedHashMap<>();
        Set<UUID> childFolderIds = new HashSet<>();
        for (VirtualFolder virtualFolder : projectState.getVirtualFolders()) {
            folderById.put(virtualFolder.getId(), virtualFolder);
            childFolderIds.addAll(virtualFolder.getChildFolderIds());
        }

        List<VirtualFolder> topLevelFolders = projectState.getVirtualFolders().stream()
                .filter(folder -> !childFolderIds.contains(folder.getId()))
                .sorted(Comparator.comparing(VirtualFolder::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        for (VirtualFolder topLevelFolder : topLevelFolders) {
            virtualRoot.getChildren().add(buildVirtualFolderItem(topLevelFolder, folderById));
        }
    }

    private TreeItem<TreeNodeValue> buildVirtualFolderItem(VirtualFolder virtualFolder, Map<UUID, VirtualFolder> folderById) {
        TreeItem<TreeNodeValue> folderItem = new TreeItem<>(new TreeNodeValue(
                TreeNodeType.VIRTUAL_FOLDER,
                virtualFolder.getName(),
                null,
                virtualFolder.getId(),
                null,
                false
        ));

        List<VirtualFolder> childFolders = virtualFolder.getChildFolderIds().stream()
                .map(folderById::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(VirtualFolder::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        for (VirtualFolder childFolder : childFolders) {
            folderItem.getChildren().add(buildVirtualFolderItem(childFolder, folderById));
        }

        List<AudioFile> audioFiles = virtualFolder.getAudioFileIds().stream()
                .map(this::findAudioFileForTree)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(AudioFile::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        for (AudioFile audioFile : audioFiles) {
            folderItem.getChildren().add(new TreeItem<>(new TreeNodeValue(
                    TreeNodeType.VIRTUAL_AUDIO_FILE,
                    audioFile.isMissing() ? audioFile.getDisplayName() + " (missing)" : audioFile.getDisplayName(),
                    audioFile.getId(),
                    null,
                    virtualFolder.getId(),
                    audioFile.isMissing()
            )));
        }

        folderItem.setExpanded(true);
        return folderItem;
    }

    private Optional<AudioFile> findAudioFileForTree(UUID audioFileId) {
        return projectState.getAudioFiles().stream()
                .filter(audioFile -> audioFile.getId().equals(audioFileId))
                .findFirst();
    }

    private void sortTree(TreeItem<TreeNodeValue> parentItem) {
        parentItem.getChildren().sort((left, right) -> {
            int leftRank = treeNodeRank(left.getValue().getType());
            int rightRank = treeNodeRank(right.getValue().getType());
            if (leftRank != rightRank) {
                return Integer.compare(leftRank, rightRank);
            }
            return String.CASE_INSENSITIVE_ORDER.compare(left.getValue().getLabel(), right.getValue().getLabel());
        });

        for (TreeItem<TreeNodeValue> childItem : parentItem.getChildren()) {
            sortTree(childItem);
        }
    }

    private int treeNodeRank(TreeNodeType treeNodeType) {
        return switch (treeNodeType) {
            case REAL_ROOT, VIRTUAL_ROOT, REAL_FOLDER, VIRTUAL_FOLDER -> 0;
            default -> 1;
        };
    }

    private void rebuildWorkspace() {
        clearWorkspace();

        List<WorkspaceTrack> sortedTracks = new ArrayList<>(projectState.getWorkspaceTracks());
        sortedTracks.sort(Comparator.comparingInt(WorkspaceTrack::getOrder));
        for (WorkspaceTrack workspaceTrack : sortedTracks) {
            createWorkspaceTile(workspaceTrack);
        }

        refreshWorkspaceOrder();
    }

    private AudioFile findAudioFileForWorkspace(UUID audioFileId) {
        return projectState.getAudioFiles().stream()
                .filter(audioFile -> audioFile.getId().equals(audioFileId))
                .findFirst()
                .orElseGet(() -> new AudioFile(audioFileId, "", "Unknown file", true));
    }

    private void addSelectedAudioFilesToWorkspace(UUID preferredAudioFileId) {
        addAudioFilesToWorkspace(collectSelectedAudioFileIds(preferredAudioFileId), null, false);
    }

    private List<UUID> collectSelectedAudioFileIds(UUID preferredAudioFileId) {
        List<TreeItem<TreeNodeValue>> selectedItems = new ArrayList<>(treeView.getSelectionModel().getSelectedItems());
        boolean includeSelection = preferredAudioFileId != null && selectedItems.stream()
                .map(TreeItem::getValue)
                .filter(Objects::nonNull)
                .anyMatch(value -> preferredAudioFileId.equals(value.getAudioFileId()));

        if (!includeSelection && preferredAudioFileId != null) {
            return List.of(preferredAudioFileId);
        }

        return selectedItems.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(treeView::getRow))
                .map(TreeItem::getValue)
                .filter(Objects::nonNull)
                .filter(value -> value.getType() == TreeNodeType.REAL_AUDIO_FILE || value.getType() == TreeNodeType.VIRTUAL_AUDIO_FILE)
                .map(TreeNodeValue::getAudioFileId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private void addAudioFilesToWorkspace(List<UUID> audioFileIds, UUID targetWorkspaceTrackId, boolean placeAfter) {
        if (currentRootPath == null || audioFileIds == null || audioFileIds.isEmpty()) {
            return;
        }

        List<WorkspaceTrack> createdTracks = projectStateEditor.addWorkspaceTracks(
                projectState,
                audioFileIds,
                appConfig.getWorkspace().getDefaultVolume(),
                appConfig.getWorkspace().isDefaultLoop(),
                targetWorkspaceTrackId,
                placeAfter
        );

        createdTracks.forEach(this::createWorkspaceTile);
        refreshWorkspaceOrder();
        saveCurrentProject();
    }

    private void removeWorkspaceTrack(UUID workspaceTrackId) {
        WorkspaceTrackItem workspaceTrackItem = workspaceTrackItems.remove(workspaceTrackId);
        if (workspaceTrackItem != null) {
            workspaceTrackItem.dispose();
        }

        TrackTileView trackTileView = trackTileViews.remove(workspaceTrackId);
        if (trackTileView != null) {
            trackTileView.dispose();
        }

        projectStateEditor.removeWorkspaceTrack(projectState, workspaceTrackId);
        refreshWorkspaceOrder();
        saveCurrentProject();
    }

    private void clearWorkspaceTracks() {
        if (currentRootPath == null || projectState.getWorkspaceTracks().isEmpty()) {
            return;
        }

        Alert alert = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Remove all tracks from the workspace?",
                ButtonType.OK,
                ButtonType.CANCEL
        );
        alert.initOwner(stage);
        alert.setTitle("Clear Workspace");
        alert.setHeaderText("Clear Workspace");
        if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        clearWorkspace();
        projectState.setWorkspaceTracks(List.of());
        refreshWorkspaceOrder();
        saveCurrentProject();
    }

    private void rebuildStateFile() {
        if (currentRootPath == null) {
            return;
        }

        Alert alert = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Rebuild the saved project state from the current file scan? This will clear workspace and virtual folders.",
                ButtonType.OK,
                ButtonType.CANCEL
        );
        alert.initOwner(stage);
        alert.setTitle("Rebuild Save");
        alert.setHeaderText("Rebuild Save File");
        if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            clearWorkspace();
            ProjectLoadResult loadResult = projectService.rebuildProjectState(currentRootPath);
            projectState = loadResult.getProjectState();
            rebuildTree();
            rebuildWorkspace();
            saveCurrentProject();
            statusLabel.setText("Rebuilt save file for " + currentRootPath);
        } catch (IOException exception) {
            LOGGER.error("Failed to rebuild project state for root folder {}", currentRootPath, exception);
            showError("Rebuild failed", "The project state file could not be rebuilt.", exception);
        }
    }

    private void moveWorkspaceTrack(UUID workspaceTrackId, UUID targetWorkspaceTrackId, boolean placeAfter) {
        if (projectStateEditor.moveWorkspaceTrack(projectState, workspaceTrackId, targetWorkspaceTrackId, placeAfter)) {
            refreshWorkspaceOrder();
            saveCurrentProject();
        }
    }

    private void moveWorkspaceTrackToEnd(UUID workspaceTrackId) {
        List<WorkspaceTrack> sortedTracks = new ArrayList<>(projectState.getWorkspaceTracks());
        sortedTracks.sort(Comparator.comparingInt(WorkspaceTrack::getOrder));
        WorkspaceTrack lastTrack = sortedTracks.isEmpty() ? null : sortedTracks.getLast();
        if (lastTrack == null || lastTrack.getId().equals(workspaceTrackId)) {
            return;
        }

        moveWorkspaceTrack(workspaceTrackId, lastTrack.getId(), true);
    }

    private void createVirtualFolder(UUID parentFolderId) {
        if (currentRootPath == null) {
            return;
        }

        Optional<String> folderName = promptForText("Create Virtual Folder", "Folder name", "");
        folderName.filter(name -> !name.isBlank()).ifPresent(name -> {
            projectStateEditor.createVirtualFolder(projectState, parentFolderId, name.trim());
            rebuildTree();
            saveCurrentProject();
        });
    }

    private void renameVirtualFolder(UUID folderId) {
        Optional<VirtualFolder> folder = projectStateEditor.findVirtualFolder(projectState, folderId);
        if (folder.isEmpty()) {
            return;
        }

        Optional<String> newName = promptForText("Rename Virtual Folder", "Folder name", folder.get().getName());
        newName.filter(name -> !name.isBlank()).ifPresent(name -> {
            projectStateEditor.renameVirtualFolder(projectState, folderId, name.trim());
            rebuildTree();
            saveCurrentProject();
        });
    }

    private void addAudioFileToVirtualFolder(UUID folderId, UUID audioFileId) {
        projectStateEditor.addAudioFileToVirtualFolder(projectState, folderId, audioFileId);
        rebuildTree();
        saveCurrentProject();
    }

    private void removeAudioFileFromVirtualFolder(UUID folderId, UUID audioFileId) {
        projectStateEditor.removeAudioFileFromVirtualFolder(projectState, folderId, audioFileId);
        rebuildTree();
        saveCurrentProject();
    }

    private Optional<String> promptForText(String title, String header, String initialValue) {
        TextInputDialog dialog = new TextInputDialog(initialValue);
        dialog.initOwner(stage);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText("Value:");
        return dialog.showAndWait();
    }

    private Map<UUID, String> collectVirtualFolderPaths() {
        Map<UUID, VirtualFolder> folderById = new HashMap<>();
        Set<UUID> childIds = new HashSet<>();
        for (VirtualFolder virtualFolder : projectState.getVirtualFolders()) {
            folderById.put(virtualFolder.getId(), virtualFolder);
            childIds.addAll(virtualFolder.getChildFolderIds());
        }

        Map<UUID, String> folderPaths = new LinkedHashMap<>();
        projectState.getVirtualFolders().stream()
                .filter(folder -> !childIds.contains(folder.getId()))
                .sorted(Comparator.comparing(VirtualFolder::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(folder -> collectVirtualFolderPaths(folder, folder.getName(), folderById, folderPaths));
        return folderPaths;
    }

    private void collectVirtualFolderPaths(
            VirtualFolder currentFolder,
            String currentPath,
            Map<UUID, VirtualFolder> folderById,
            Map<UUID, String> folderPaths
    ) {
        folderPaths.put(currentFolder.getId(), currentPath);
        currentFolder.getChildFolderIds().stream()
                .map(folderById::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(VirtualFolder::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(childFolder -> collectVirtualFolderPaths(childFolder, currentPath + " / " + childFolder.getName(), folderById, folderPaths));
    }

    private boolean saveCurrentProject() {
        if (currentRootPath == null) {
            return true;
        }

        try {
            projectService.saveProject(currentRootPath, projectState);
            statusLabel.setText("Saved " + currentRootPath);
            return true;
        } catch (IOException exception) {
            LOGGER.error("Failed to save project state for root folder {}", currentRootPath, exception);
            showError("Save failed", "The project state could not be saved.", exception);
            return false;
        }
    }

    private void clearWorkspace() {
        workspaceView.clearAndReturnCurrentTiles().forEach(TrackTileView::dispose);
        disposeWorkspaceTrackItems();
        trackTileViews.clear();
    }

    private void disposeWorkspaceTrackItems() {
        Collection<WorkspaceTrackItem> items = new ArrayList<>(workspaceTrackItems.values());
        workspaceTrackItems.clear();
        for (WorkspaceTrackItem item : items) {
            item.dispose();
        }
    }

    private void createWorkspaceTile(WorkspaceTrack workspaceTrack) {
        AudioFile audioFile = findAudioFileForWorkspace(workspaceTrack.getAudioFileId());
        WorkspaceTrackItem workspaceTrackItem = new WorkspaceTrackItem(
                currentRootPath,
                workspaceTrack,
                audioFile,
                audioEngine,
                masterVolume,
                exception -> showError("Playback error", "Audio track could not be initialized.", exception)
        );
        workspaceTrackItems.put(workspaceTrack.getId(), workspaceTrackItem);

        TrackTileView trackTileView = new TrackTileView(
                appConfig.getUi(),
                appConfig.getWorkspace().getProgressRefreshMillis(),
                workspaceTrackItem,
                () -> removeWorkspaceTrack(workspaceTrack.getId()),
                this::saveCurrentProject
        );
        trackTileViews.put(workspaceTrack.getId(), trackTileView);
    }

    private void refreshWorkspaceOrder() {
        List<TrackTileView> orderedTrackTiles = projectState.getWorkspaceTracks().stream()
                .sorted(Comparator.comparingInt(WorkspaceTrack::getOrder))
                .map(workspaceTrack -> trackTileViews.get(workspaceTrack.getId()))
                .filter(Objects::nonNull)
                .toList();
        workspaceView.setTrackTiles(orderedTrackTiles);
    }

    private void updateMasterVolume(double masterVolume) {
        this.masterVolume = Math.max(0d, Math.min(1d, masterVolume));
        masterVolumeValueLabel.setText(Math.round(this.masterVolume * 100d) + "%");
        workspaceTrackItems.values().forEach(item -> item.setMasterVolume(this.masterVolume));
    }

    private void setControlsDisabled(boolean disabled) {
        openFolderButton.setDisable(disabled);
        rescanButton.setDisable(disabled || currentRootPath == null);
        saveButton.setDisable(disabled || currentRootPath == null);
        clearWorkspaceButton.setDisable(disabled || currentRootPath == null);
        rebuildStateButton.setDisable(disabled || currentRootPath == null);
    }

    private void showError(String title, String message, Exception exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message + System.lineSeparator() + exception.getMessage(), ButtonType.OK);
        alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    private Exception asException(Throwable throwable) {
        return throwable instanceof Exception exception ? exception : new Exception(throwable);
    }

    private Region createSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}
