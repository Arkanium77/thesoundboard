package app.ui.main;

import app.audio.AudioEngine;
import app.config.AppConfig;
import app.model.AudioFile;
import app.model.ProjectState;
import app.model.QueueTrack;
import app.model.WorkspaceQueue;
import app.model.WorkspaceTrack;
import app.project.ProjectLoadResult;
import app.project.ProjectStateCopySupport;
import app.project.ProjectService;
import app.project.ProjectStateEditor;
import app.ui.UiIcons;
import app.ui.drag.DragPayload;
import app.ui.queue.QueueView;
import app.ui.tile.TrackTileView;
import app.ui.tree.TreeNodeType;
import app.ui.tree.TreeNodeValue;
import app.ui.workspace.WorkspaceQueueItem;
import app.ui.workspace.WorkspaceTrackItem;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
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
import javafx.scene.control.ToolBar;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final Button workspacePlayPauseButton = new Button(UiIcons.PAUSE);
    private final Button workspaceStopButton = new Button(UiIcons.STOP);
    private final WorkspaceView workspaceView;

    private final Map<UUID, WorkspaceTrackItem> workspaceTrackItems = new LinkedHashMap<>();
    private final Map<UUID, TrackTileView> trackTileViews = new LinkedHashMap<>();
    private final Map<UUID, WorkspaceQueueItem> workspaceQueueItems = new LinkedHashMap<>();
    private final Map<UUID, QueueView> queueViews = new LinkedHashMap<>();
    private final AtomicInteger saveGeneration = new AtomicInteger();
    private final double baseTrackTileWidth;
    private final double baseTrackTileHeight;

    private Path currentRootPath;
    private ProjectState projectState;
    private boolean loading;
    private boolean workspacePauseLatched;
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
        this.baseTrackTileWidth = appConfig.getUi().getTrackTileWidth();
        this.baseTrackTileHeight = appConfig.getUi().getTrackTileHeight();
        this.workspaceView = new WorkspaceView(appConfig.getUi(), new WorkspaceView.WorkspaceDropHandler() {
            @Override
            public void moveWorkspaceItem(UUID workspaceItemId, UUID targetWorkspaceItemId, boolean placeAfter) {
                MainView.this.moveWorkspaceItem(workspaceItemId, targetWorkspaceItemId, placeAfter);
            }

            @Override
            public void moveWorkspaceItemToEnd(UUID workspaceItemId) {
                MainView.this.moveWorkspaceItemToEnd(workspaceItemId);
            }

            @Override
            public void addAudioFilesAsSolo(List<UUID> audioFileIds, UUID targetWorkspaceItemId, boolean placeAfter) {
                MainView.this.addAudioFilesToWorkspace(audioFileIds, targetWorkspaceItemId, placeAfter);
            }

            @Override
            public void moveQueueTrackToWorkspace(UUID sourceQueueId, UUID queueTrackId, UUID targetWorkspaceItemId, boolean placeAfter) {
                MainView.this.moveQueueTrackToWorkspace(sourceQueueId, queueTrackId, targetWorkspaceItemId, placeAfter);
            }

            @Override
            public void adjustWorkspaceZoom(double deltaY) {
                MainView.this.adjustWorkspaceZoom(deltaY);
            }

            @Override
            public void createQueue() {
                MainView.this.createQueue();
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
        saveCurrentProjectSynchronously();
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
        workspacePlayPauseButton.setFocusTraversable(false);
        workspaceStopButton.setFocusTraversable(false);

        HBox header = new HBox(
                12,
                workspaceLabel,
                workspacePlayPauseButton,
                workspaceStopButton,
                createSpacer(),
                masterVolumeLabel,
                masterVolumeSlider,
                masterVolumeValueLabel
        );
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
        saveButton.setOnAction(event -> saveCurrentProjectSynchronously());
        clearWorkspaceButton.setOnAction(event -> clearWorkspaceTracks());
        rebuildStateButton.setOnAction(event -> rebuildStateFile());
        workspacePlayPauseButton.setOnAction(event -> toggleWorkspacePlayPause());
        workspaceStopButton.setOnAction(event -> stopWorkspacePlayback());
        masterVolumeSlider.valueProperty().addListener((observable, oldValue, newValue) -> updateMasterVolume(newValue.doubleValue() / 100d));
        updateMasterVolume(masterVolume);
        refreshWorkspaceTransportButtons();
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
                    } else if (item.getType() == TreeNodeType.REAL_ROOT) {
                        setStyle("-fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            };

            cell.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !cell.isEmpty()) {
                    TreeNodeValue item = cell.getItem();
                    if (item.getType() == TreeNodeType.REAL_AUDIO_FILE) {
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
                content.putString(DragPayload.audioFiles(selectedAudioFileIds));
                dragboard.setContent(content);
                event.consume();
            });

            return cell;
        });
    }

    private ContextMenu createContextMenu(TreeNodeValue nodeValue) {
        if (nodeValue.getType() != TreeNodeType.REAL_AUDIO_FILE) {
            return null;
        }

        MenuItem addToWorkspaceItem = new MenuItem("Add To Workspace");
        addToWorkspaceItem.setOnAction(event -> addSelectedAudioFilesToWorkspace(nodeValue.getAudioFileId()));

        Menu addToQueueMenu = new Menu("Add To Queue");
        List<WorkspaceQueue> workspaceQueues = sortedWorkspaceQueues();
        for (WorkspaceQueue workspaceQueue : workspaceQueues) {
            MenuItem queueItem = new MenuItem(workspaceQueue.getName());
            queueItem.setOnAction(event -> addSelectedAudioFilesToQueue(workspaceQueue.getId(), nodeValue.getAudioFileId()));
            addToQueueMenu.getItems().add(queueItem);
        }
        addToQueueMenu.setDisable(workspaceQueues.isEmpty());

        return new ContextMenu(addToWorkspaceItem, addToQueueMenu);
    }

    private void chooseFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Open Soundboard Project Folder");
        if (currentRootPath != null) {
            directoryChooser.setInitialDirectory(currentRootPath.toFile());
        }

        File selectedDirectory = directoryChooser.showDialog(stage);
        if (selectedDirectory != null) {
            loadProject(selectedDirectory.toPath(), true);
        }
    }

    private void loadProject(Path rootPath, boolean saveBeforeSwitch) {
        if (loading) {
            return;
        }

        if (saveBeforeSwitch && currentRootPath != null) {
            saveCurrentProjectSynchronously();
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
            updateMasterVolume(projectState.getMasterVolume());
            masterVolumeSlider.setValue(masterVolume * 100d);
            projectPathLabel.setText(currentRootPath.toString());
            statusLabel.setText("Loaded " + currentRootPath);
            rescanButton.setDisable(false);
            saveButton.setDisable(false);

            rebuildTree();
            rebuildWorkspace();
            if (loadResult.getPersistenceLoadException() == null) {
                requestProjectSave();
            }

            if (loadResult.getPersistenceLoadException() != null) {
                showError(
                        "Failed to read saved project state",
                        "The folder was opened without fully persisted state. See logs for details.",
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
        TreeItem<TreeNodeValue> rootItem = new TreeItem<>(new TreeNodeValue(TreeNodeType.ROOT, "Root", null, false));
        buildRealTree(rootItem);
        sortTree(rootItem);
        treeView.setRoot(rootItem);
        rootItem.setExpanded(true);
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
                            folderItem = new TreeItem<>(new TreeNodeValue(TreeNodeType.REAL_FOLDER, pathParts[index], null, false));
                            folderIndex.put(folderPathKey, folderItem);
                            parentItem.getChildren().add(folderItem);
                        }
                        parentItem = folderItem;
                    }

                    parentItem.getChildren().add(new TreeItem<>(new TreeNodeValue(
                            TreeNodeType.REAL_AUDIO_FILE,
                            audioFile.getDisplayName(),
                            audioFile.getId(),
                            false
                    )));
                });
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
            case REAL_ROOT, REAL_FOLDER -> 0;
            default -> 1;
        };
    }

    private void rebuildWorkspace() {
        clearWorkspace();

        for (WorkspaceTrack workspaceTrack : sortedWorkspaceTracks()) {
            createWorkspaceTrackTile(workspaceTrack);
        }
        for (WorkspaceQueue workspaceQueue : sortedWorkspaceQueues()) {
            createQueueView(workspaceQueue);
        }

        refreshWorkspaceOrder();
        workspaceView.refreshTileMetrics(
                appConfig.getUi().getTrackTileWidth(),
                appConfig.getUi().getTrackTileHeight(),
                currentTileScale()
        );
    }

    private void createQueue() {
        if (currentRootPath == null) {
            return;
        }

        WorkspaceQueue workspaceQueue = projectStateEditor.createWorkspaceQueue(
                projectState,
                nextQueueName(),
                appConfig.getWorkspace().getDefaultVolume()
        );
        createQueueView(workspaceQueue);
        refreshWorkspaceOrder();
        requestProjectSave();
    }

    private void createQueueView(WorkspaceQueue workspaceQueue) {
        WorkspaceQueueItem workspaceQueueItem = new WorkspaceQueueItem(
                currentRootPath,
                workspaceQueue,
                projectState.getAudioFiles(),
                audioEngine,
                masterVolume,
                exception -> showError("Playback error", "Queue track could not be initialized.", exception)
        );
        workspaceQueueItems.put(workspaceQueue.getId(), workspaceQueueItem);

        QueueView queueView = new QueueView(
                appConfig.getUi(),
                workspaceQueueItem,
                appConfig.getWorkspace().getProgressRefreshMillis(),
                () -> removeQueue(workspaceQueue.getId()),
                this::requestProjectSave,
                (audioFileIds, targetQueueTrackId, placeAfter) -> addAudioFilesToQueue(workspaceQueue.getId(), audioFileIds, targetQueueTrackId, placeAfter),
                (workspaceTrackId, targetQueueTrackId, placeAfter) -> addWorkspaceTrackToQueue(workspaceQueue.getId(), workspaceTrackId, targetQueueTrackId, placeAfter),
                queueTrack -> removeQueueTrack(workspaceQueue.getId(), queueTrack.getId()),
                (sourceQueueId, queueTrackId, targetQueueTrackId, placeAfter) -> moveQueueTrack(sourceQueueId, workspaceQueue.getId(), queueTrackId, targetQueueTrackId, placeAfter),
                this::moveWorkspaceItem
        );
        queueViews.put(workspaceQueue.getId(), queueView);
    }

    private void addSelectedAudioFilesToQueue(UUID queueId, UUID preferredAudioFileId) {
        addAudioFilesToQueue(queueId, collectSelectedAudioFileIds(preferredAudioFileId), null, false);
    }

    private void addAudioFilesToQueue(UUID queueId, List<UUID> audioFileIds) {
        addAudioFilesToQueue(queueId, audioFileIds, null, false);
    }

    private void addAudioFilesToQueue(UUID queueId, List<UUID> audioFileIds, UUID targetQueueTrackId, boolean placeAfter) {
        if (audioFileIds == null || audioFileIds.isEmpty()) {
            return;
        }

        List<QueueTrack> createdTracks = projectStateEditor.addQueueTracks(projectState, queueId, audioFileIds, targetQueueTrackId, placeAfter);
        if (createdTracks.isEmpty()) {
            return;
        }

        WorkspaceQueueItem workspaceQueueItem = workspaceQueueItems.get(queueId);
        QueueView queueView = queueViews.get(queueId);
        if (workspaceQueueItem != null) {
            workspaceQueueItem.refreshAfterMutation();
        }
        if (queueView != null) {
            queueView.rebuildChips();
        }
        requestProjectSave();
    }

    private void addWorkspaceTrackToQueue(UUID queueId, UUID workspaceTrackId, UUID targetQueueTrackId, boolean placeAfter) {
        if (workspaceTrackId == null) {
            return;
        }

        boolean moved = projectStateEditor.moveWorkspaceTrackToQueue(
                projectState,
                workspaceTrackId,
                queueId,
                targetQueueTrackId,
                placeAfter
        );
        if (!moved) {
            return;
        }

        WorkspaceTrackItem workspaceTrackItem = workspaceTrackItems.remove(workspaceTrackId);
        if (workspaceTrackItem != null) {
            workspaceTrackItem.dispose();
        }

        TrackTileView trackTileView = trackTileViews.remove(workspaceTrackId);
        if (trackTileView != null) {
            trackTileView.dispose();
        }

        WorkspaceQueueItem workspaceQueueItem = workspaceQueueItems.get(queueId);
        QueueView queueView = queueViews.get(queueId);
        if (workspaceQueueItem != null) {
            workspaceQueueItem.refreshAfterMutation();
        }
        if (queueView != null) {
            queueView.rebuildChips();
        }
        refreshWorkspaceOrder();
        requestProjectSave();
    }

    private void removeQueueTrack(UUID queueId, UUID queueTrackId) {
        projectStateEditor.removeQueueTrack(projectState, queueId, queueTrackId);

        WorkspaceQueueItem workspaceQueueItem = workspaceQueueItems.get(queueId);
        QueueView queueView = queueViews.get(queueId);
        if (workspaceQueueItem != null) {
            workspaceQueueItem.refreshAfterMutation();
        }
        if (queueView != null) {
            queueView.rebuildChips();
        }
        requestProjectSave();
    }

    private void moveQueueTrack(UUID sourceQueueId, UUID targetQueueId, UUID queueTrackId, UUID targetQueueTrackId, boolean placeAfter) {
        if (!projectStateEditor.moveQueueTrackToQueue(projectState, sourceQueueId, targetQueueId, queueTrackId, targetQueueTrackId, placeAfter)) {
            return;
        }

        refreshQueueView(sourceQueueId);
        if (!Objects.equals(sourceQueueId, targetQueueId)) {
            refreshQueueView(targetQueueId);
        }
        requestProjectSave();
    }

    private void moveQueueTrackToWorkspace(UUID sourceQueueId, UUID queueTrackId, UUID targetWorkspaceItemId, boolean placeAfter) {
        WorkspaceTrack workspaceTrack = projectStateEditor.moveQueueTrackToWorkspace(
                projectState,
                sourceQueueId,
                queueTrackId,
                appConfig.getWorkspace().getDefaultVolume(),
                targetWorkspaceItemId,
                placeAfter
        );
        if (workspaceTrack == null) {
            return;
        }

        createWorkspaceTrackTile(workspaceTrack);
        refreshQueueView(sourceQueueId);
        refreshWorkspaceOrder();
        requestProjectSave();
    }

    private void removeQueue(UUID queueId) {
        WorkspaceQueueItem workspaceQueueItem = workspaceQueueItems.remove(queueId);
        if (workspaceQueueItem != null) {
            workspaceQueueItem.dispose();
        }

        QueueView queueView = queueViews.remove(queueId);
        if (queueView != null) {
            queueView.dispose();
        }

        projectStateEditor.removeWorkspaceQueue(projectState, queueId);
        refreshWorkspaceOrder();
        refreshWorkspaceTransportButtons();
        requestProjectSave();
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
                .filter(value -> value.getType() == TreeNodeType.REAL_AUDIO_FILE)
                .map(TreeNodeValue::getAudioFileId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private void addAudioFilesToWorkspace(List<UUID> audioFileIds, UUID targetWorkspaceItemId, boolean placeAfter) {
        if (currentRootPath == null || audioFileIds == null || audioFileIds.isEmpty()) {
            return;
        }

        List<WorkspaceTrack> createdTracks = projectStateEditor.addWorkspaceTracks(
                projectState,
                audioFileIds,
                appConfig.getWorkspace().getDefaultVolume(),
                appConfig.getWorkspace().isDefaultLoop(),
                targetWorkspaceItemId,
                placeAfter
        );

        createdTracks.forEach(this::createWorkspaceTrackTile);
        refreshWorkspaceOrder();
        requestProjectSave();
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
        refreshWorkspaceTransportButtons();
        requestProjectSave();
    }

    private void clearWorkspaceTracks() {
        if (currentRootPath == null || workspaceItemCount() == 0) {
            return;
        }

        Alert alert = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Remove all solo tracks and queues from the workspace?",
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
        projectStateEditor.clearWorkspace(projectState);
        refreshWorkspaceOrder();
        requestProjectSave();
    }

    private void rebuildStateFile() {
        if (currentRootPath == null) {
            return;
        }

        Alert alert = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Rebuild the saved project state from the current file scan? This will clear the workspace queues and solo tracks.",
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
            updateMasterVolume(projectState.getMasterVolume());
            masterVolumeSlider.setValue(masterVolume * 100d);
            rebuildTree();
            rebuildWorkspace();
            requestProjectSave();
            statusLabel.setText("Rebuilt save file for " + currentRootPath);
        } catch (IOException exception) {
            LOGGER.error("Failed to rebuild project state for root folder {}", currentRootPath, exception);
            showError("Rebuild failed", "The project state file could not be rebuilt.", exception);
        }
    }

    private void moveWorkspaceItem(UUID workspaceItemId, UUID targetWorkspaceItemId, boolean placeAfter) {
        if (projectStateEditor.moveWorkspaceItem(projectState, workspaceItemId, targetWorkspaceItemId, placeAfter)) {
            refreshWorkspaceOrder();
            requestProjectSave();
        }
    }

    private void moveWorkspaceItemToEnd(UUID workspaceItemId) {
        List<WorkspaceTopLevelItem> items = collectOrderedWorkspaceItems();
        if (items.isEmpty() || items.getLast().id().equals(workspaceItemId)) {
            return;
        }

        moveWorkspaceItem(workspaceItemId, items.getLast().id(), true);
    }

    private void createWorkspaceTrackTile(WorkspaceTrack workspaceTrack) {
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
                this::requestProjectSave
        );
        trackTileViews.put(workspaceTrack.getId(), trackTileView);
    }

    private void rebuildWorkspaceViews() {
        clearWorkspace();
        rebuildWorkspace();
    }

    private void refreshQueueView(UUID queueId) {
        WorkspaceQueueItem workspaceQueueItem = workspaceQueueItems.get(queueId);
        QueueView queueView = queueViews.get(queueId);
        if (workspaceQueueItem != null) {
            workspaceQueueItem.refreshAfterMutation();
        }
        if (queueView != null) {
            queueView.rebuildChips();
        }
    }

    private void refreshWorkspaceOrder() {
        List<Node> workspaceNodes = collectOrderedWorkspaceItems().stream()
                .map(WorkspaceTopLevelItem::node)
                .filter(Objects::nonNull)
                .toList();
        workspaceView.setWorkspaceNodes(workspaceNodes);
        workspaceView.refreshTileMetrics(
                appConfig.getUi().getTrackTileWidth(),
                appConfig.getUi().getTrackTileHeight(),
                currentTileScale()
        );
    }

    private void adjustWorkspaceZoom(double deltaY) {
        double currentScale = currentTileScale();
        double nextScale = currentScale + (deltaY > 0d ? appConfig.getUi().getTileZoomStep() : -appConfig.getUi().getTileZoomStep());
        nextScale = Math.max(appConfig.getUi().getMinTileScale(), Math.min(appConfig.getUi().getMaxTileScale(), nextScale));
        if (Math.abs(nextScale - currentScale) < 0.0001d) {
            return;
        }

        double nextWidth = Math.round(baseTrackTileWidth * nextScale);
        double nextHeight = Math.round(baseTrackTileHeight * nextScale);
        appConfig.getUi().setTrackTileWidth(nextWidth);
        appConfig.getUi().setTrackTileHeight(nextHeight);
        workspaceView.refreshTileMetrics(nextWidth, nextHeight, nextScale);
    }

    private double currentTileScale() {
        return appConfig.getUi().getTrackTileWidth() / baseTrackTileWidth;
    }

    private List<WorkspaceTopLevelItem> collectOrderedWorkspaceItems() {
        List<WorkspaceTopLevelItem> items = new ArrayList<>();
        for (WorkspaceTrack workspaceTrack : projectState.getWorkspaceTracks()) {
            items.add(new WorkspaceTopLevelItem(
                    workspaceTrack.getId(),
                    workspaceTrack.getOrder(),
                    trackTileViews.get(workspaceTrack.getId())
            ));
        }
        for (WorkspaceQueue workspaceQueue : projectState.getWorkspaceQueues()) {
            items.add(new WorkspaceTopLevelItem(
                    workspaceQueue.getId(),
                    workspaceQueue.getOrder(),
                    queueViews.get(workspaceQueue.getId())
            ));
        }
        items.sort(Comparator.comparingInt(WorkspaceTopLevelItem::order));
        return items;
    }

    private List<WorkspaceTrack> sortedWorkspaceTracks() {
        List<WorkspaceTrack> workspaceTracks = new ArrayList<>(projectState.getWorkspaceTracks());
        workspaceTracks.sort(Comparator.comparingInt(WorkspaceTrack::getOrder));
        return workspaceTracks;
    }

    private List<WorkspaceQueue> sortedWorkspaceQueues() {
        List<WorkspaceQueue> workspaceQueues = new ArrayList<>(projectState.getWorkspaceQueues());
        workspaceQueues.sort(Comparator.comparingInt(WorkspaceQueue::getOrder));
        return workspaceQueues;
    }

    private int workspaceItemCount() {
        return projectState.getWorkspaceTracks().size() + projectState.getWorkspaceQueues().size();
    }

    private String nextQueueName() {
        int queueNumber = projectState.getWorkspaceQueues().size() + 1;
        return "Queue " + queueNumber;
    }

    private AudioFile findAudioFileForWorkspace(UUID audioFileId) {
        return projectState.getAudioFiles().stream()
                .filter(audioFile -> audioFile.getId().equals(audioFileId))
                .findFirst()
                .orElseGet(() -> new AudioFile(audioFileId, "", "Unknown file", true));
    }

    private void clearWorkspace() {
        workspaceView.clearAndReturnCurrentNodes().forEach(node -> {
            if (node instanceof TrackTileView trackTileView) {
                trackTileView.dispose();
            } else if (node instanceof QueueView queueView) {
                queueView.dispose();
            }
        });
        disposeWorkspaceTrackItems();
        disposeWorkspaceQueueItems();
        trackTileViews.clear();
        queueViews.clear();
        workspacePauseLatched = false;
        refreshWorkspaceTransportButtons();
    }

    private void disposeWorkspaceTrackItems() {
        Collection<WorkspaceTrackItem> items = new ArrayList<>(workspaceTrackItems.values());
        workspaceTrackItems.clear();
        for (WorkspaceTrackItem item : items) {
            item.dispose();
        }
    }

    private void disposeWorkspaceQueueItems() {
        Collection<WorkspaceQueueItem> items = new ArrayList<>(workspaceQueueItems.values());
        workspaceQueueItems.clear();
        for (WorkspaceQueueItem item : items) {
            item.dispose();
        }
    }

    private void updateMasterVolume(double masterVolume) {
        this.masterVolume = Math.max(0d, Math.min(1d, masterVolume));
        projectState.setMasterVolume(this.masterVolume);
        masterVolumeValueLabel.setText(Math.round(this.masterVolume * 100d) + "%");
        workspaceTrackItems.values().forEach(item -> item.setMasterVolume(this.masterVolume));
        workspaceQueueItems.values().forEach(item -> item.setMasterVolume(this.masterVolume));
    }

    private void toggleWorkspacePlayPause() {
        if (workspacePauseLatched) {
            resumePausedWorkspaceItems();
            workspacePauseLatched = false;
        } else {
            pausePlayingWorkspaceItems();
            workspacePauseLatched = true;
        }
        refreshWorkspaceTransportButtons();
    }

    private void pausePlayingWorkspaceItems() {
        for (WorkspaceTrackItem workspaceTrackItem : workspaceTrackItems.values()) {
            workspaceTrackItem.pauseIfPlaying();
        }

        for (WorkspaceQueueItem workspaceQueueItem : workspaceQueueItems.values()) {
            workspaceQueueItem.pauseIfPlaying();
        }
    }

    private void resumePausedWorkspaceItems() {
        for (WorkspaceTrackItem workspaceTrackItem : workspaceTrackItems.values()) {
            workspaceTrackItem.resumeIfPaused();
        }

        for (WorkspaceQueueItem workspaceQueueItem : workspaceQueueItems.values()) {
            workspaceQueueItem.resumeIfPaused();
        }
    }

    private void stopWorkspacePlayback() {
        workspaceTrackItems.values().forEach(WorkspaceTrackItem::stop);
        workspaceQueueItems.values().forEach(WorkspaceQueueItem::stop);
        workspacePauseLatched = false;
        refreshWorkspaceTransportButtons();
    }

    private void refreshWorkspaceTransportButtons() {
        workspacePlayPauseButton.setText(workspacePauseLatched ? UiIcons.PLAY : UiIcons.PAUSE);
    }

    private void requestProjectSave() {
        if (currentRootPath == null) {
            return;
        }

        Path rootPath = currentRootPath;
        ProjectState snapshot = ProjectStateCopySupport.copy(projectState);
        int generation = saveGeneration.incrementAndGet();
        statusLabel.setText("Saving " + rootPath);
        executorService.submit(() -> {
            try {
                if (generation != saveGeneration.get()) {
                    return;
                }
                projectService.saveProject(rootPath, snapshot);
            } catch (IOException exception) {
                LOGGER.error("Failed to save project state for root folder {}", rootPath, exception);
                Platform.runLater(() -> showError("Save failed", "The project state could not be saved.", exception));
            }
        });
    }

    private boolean saveCurrentProjectSynchronously() {
        if (currentRootPath == null) {
            return true;
        }

        try {
            saveGeneration.incrementAndGet();
            projectService.saveProject(currentRootPath, projectState);
            statusLabel.setText("Saved " + currentRootPath);
            return true;
        } catch (IOException exception) {
            LOGGER.error("Failed to save project state for root folder {}", currentRootPath, exception);
            showError("Save failed", "The project state could not be saved.", exception);
            return false;
        }
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

    private record WorkspaceTopLevelItem(UUID id, int order, Node node) {
    }
}
