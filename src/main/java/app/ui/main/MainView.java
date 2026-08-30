package app.ui.main;

import app.audio.AudioEngine;
import app.config.AppConfig;
import app.model.AudioFile;
import app.model.ProjectState;
import app.model.QueueTrack;
import app.model.WorkspaceQueue;
import app.model.WorkspaceTrack;
import app.model.WorkspaceVirtualTile;
import app.model.VirtualTileLayout;
import app.localization.LocalizationService;
import app.localization.TextKey;
import app.localization.Texts;
import app.project.ProjectLoadResult;
import app.project.LastProjectPreferences;
import app.project.ProjectStateCopySupport;
import app.project.ProjectService;
import app.project.ProjectStateEditor;
import app.skin.SkinService;
import app.ui.UiIcons;
import app.ui.drag.DragPayload;
import app.ui.queue.QueueView;
import app.ui.settings.SettingsWindow;
import app.ui.tile.TrackTileView;
import app.ui.tile.VirtualTileView;
import app.ui.tree.TreeNodeType;
import app.ui.tree.TreeNodeValue;
import app.ui.workspace.WorkspaceQueueItem;
import app.ui.workspace.PlaybackTransfer;
import app.ui.workspace.WorkspaceTrackItem;
import app.ui.workspace.WorkspaceVirtualTileItem;
import app.waveform.WaveformService;
import app.waveform.WaveformPreferences;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
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
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
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
import java.util.stream.Stream;

public class MainView extends BorderPane {
    private static final Logger LOGGER = LoggerFactory.getLogger(MainView.class);
    private final Stage stage;
    private final AppConfig appConfig;
    private final ProjectService projectService;
    private final ProjectStateEditor projectStateEditor;
    private final AudioEngine audioEngine;
    private final WaveformService waveformService;
    private final DoubleProperty uiScale;
    private final SkinService skinService;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final LastProjectPreferences lastProjectPreferences;

    private final Node settingsGraphic = UiIcons.settings();
    private final Button settingsButton = new Button(null, settingsGraphic);
    private final Button openFolderButton = new Button(Texts.text(TextKey.MAIN_OPEN_FOLDER));
    private final Button rescanButton = new Button(Texts.text(TextKey.MAIN_RESCAN));
    private final Button saveButton = new Button(Texts.text(TextKey.MAIN_SAVE));
    private final Button clearWorkspaceButton = new Button(Texts.text(TextKey.MAIN_CLEAR_WORKSPACE));
    private final Button rebuildStateButton = new Button(Texts.text(TextKey.MAIN_REBUILD_SAVE));
    private final Label projectPathLabel = new Label(Texts.text(TextKey.MAIN_NO_FOLDER));
    private final Label statusLabel = new Label(Texts.text(TextKey.MAIN_READY));
    private final Label projectTreeLabel = new Label(Texts.text(TextKey.MAIN_PROJECT_TREE));
    private final Label workspaceLabel = new Label(Texts.text(TextKey.MAIN_WORKSPACE));
    private final Label masterVolumeLabel = new Label(Texts.text(TextKey.MAIN_MASTER_VOLUME));
    private final TreeView<TreeNodeValue> treeView = new TreeView<>();
    private final Slider masterVolumeSlider = new Slider(0d, 100d, 100d);
    private final Label masterVolumeValueLabel = new Label("100%");
    private final Node workspacePlayGraphic = UiIcons.play();
    private final Node workspacePauseGraphic = UiIcons.pause();
    private final Node workspaceStopGraphic = UiIcons.stop();
    private final Button workspacePlayPauseButton = new Button(null, workspacePauseGraphic);
    private final Button workspaceStopButton = new Button(null, workspaceStopGraphic);
    private final ProgressIndicator waveformLoadingIndicator = new ProgressIndicator();
    private final Button decreaseScaleButton = new Button("-");
    private final Label scaleValueLabel = new Label();
    private final Button increaseScaleButton = new Button("+");
    private final ToggleButton interfaceScaleTargetButton = new ToggleButton("UI");
    private final ToggleButton workspaceScaleTargetButton = new ToggleButton("WS");
    private final SettingsWindow settingsWindow;
    private final WorkspaceView workspaceView;
    private VBox leftPane;
    private VBox rightPane;
    private HBox workspaceHeader;
    private HBox statusBar;
    private HBox scaleControls;
    private VBox scaleTargetControls;

    private final Map<UUID, WorkspaceTrackItem> workspaceTrackItems = new LinkedHashMap<>();
    private final Map<UUID, TrackTileView> trackTileViews = new LinkedHashMap<>();
    private final Map<UUID, WorkspaceQueueItem> workspaceQueueItems = new LinkedHashMap<>();
    private final Map<UUID, QueueView> queueViews = new LinkedHashMap<>();
    private final Map<UUID, WorkspaceVirtualTileItem> workspaceVirtualTileItems = new LinkedHashMap<>();
    private final Map<UUID, VirtualTileView> virtualTileViews = new LinkedHashMap<>();
    private final AtomicInteger saveGeneration = new AtomicInteger();
    private final double baseTrackTileWidth;
    private final double baseTrackTileHeight;
    private final Timeline waveformLoadingIndicatorTimeline = new Timeline(new KeyFrame(Duration.millis(150), event -> refreshWaveformLoadingIndicator()));

    private Path currentRootPath;
    private ProjectState projectState;
    private boolean loading;
    private boolean workspacePauseLatched;
    private boolean rebuildingWorkspace;
    private double masterVolume;
    private ScaleTarget scaleTarget = ScaleTarget.INTERFACE;

    public MainView(
            Stage stage,
            AppConfig appConfig,
            ProjectService projectService,
            ProjectStateEditor projectStateEditor,
            AudioEngine audioEngine,
            WaveformService waveformService,
            DoubleProperty uiScale,
            SkinService skinService,
            LocalizationService localizationService,
            LastProjectPreferences lastProjectPreferences,
            WaveformPreferences waveformPreferences
    ) {
        this.stage = stage;
        this.appConfig = appConfig;
        this.projectService = projectService;
        this.projectStateEditor = projectStateEditor;
        this.audioEngine = audioEngine;
        this.waveformService = waveformService;
        this.uiScale = uiScale;
        this.skinService = skinService;
        this.lastProjectPreferences = lastProjectPreferences;
        this.settingsWindow = new SettingsWindow(stage, skinService, localizationService, lastProjectPreferences,
                waveformPreferences, this::refreshLocalization);
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
            public void moveVirtualTileTrackToWorkspace(UUID tileId, UUID trackId,
                                                        UUID targetWorkspaceItemId, boolean placeAfter) {
                MainView.this.moveVirtualTileTrackToWorkspace(tileId, trackId, targetWorkspaceItemId, placeAfter);
            }

            @Override
            public void adjustWorkspaceZoom(double deltaY) {
                MainView.this.adjustWorkspaceZoom(deltaY);
            }

            @Override
            public void createQueue() {
                MainView.this.createQueue();
            }

            @Override
            public void createVirtualTile(VirtualTileLayout layout) { MainView.this.createVirtualTile(layout); }
        });

        configureLayout();
        configureTreeView();
        configureActions();
        rebuildTree();
        rebuildWorkspace();
        refreshInterfaceScale();
    }

    public void shutdown() {
        LOGGER.info("Shutting down application resources");
        saveCurrentProjectSynchronously();
        clearWorkspace();
        waveformLoadingIndicatorTimeline.stop();
        waveformService.shutdown();
        executorService.shutdownNow();
    }

    public void restoreLastProject(boolean forced) {
        if (forced || lastProjectPreferences.isRestoreOnStart()) {
            lastProjectPreferences.load().ifPresent(path -> loadProject(path, false));
        }
    }

    private void refreshLocalization() {
        stage.setTitle(Texts.text(TextKey.APP_TITLE));
        settingsButton.setTooltip(new Tooltip(Texts.text(TextKey.MAIN_SETTINGS)));
        openFolderButton.setText(Texts.text(TextKey.MAIN_OPEN_FOLDER));
        rescanButton.setText(Texts.text(TextKey.MAIN_RESCAN));
        saveButton.setText(Texts.text(TextKey.MAIN_SAVE));
        clearWorkspaceButton.setText(Texts.text(TextKey.MAIN_CLEAR_WORKSPACE));
        rebuildStateButton.setText(Texts.text(TextKey.MAIN_REBUILD_SAVE));
        projectTreeLabel.setText(Texts.text(TextKey.MAIN_PROJECT_TREE));
        workspaceLabel.setText(Texts.text(TextKey.MAIN_WORKSPACE));
        masterVolumeLabel.setText(Texts.text(TextKey.MAIN_MASTER_VOLUME));
        interfaceScaleTargetButton.setTooltip(new Tooltip(Texts.text(TextKey.MAIN_SCALE_INTERFACE)));
        workspaceScaleTargetButton.setTooltip(new Tooltip(Texts.text(TextKey.MAIN_SCALE_WORKSPACE)));
        workspaceView.refreshLocalization();
        trackTileViews.values().forEach(TrackTileView::refreshLocalization);
        queueViews.values().forEach(QueueView::refreshLocalization);
        virtualTileViews.values().forEach(VirtualTileView::refreshLocalization);
        statusLabel.setText(currentRootPath == null
                ? Texts.text(TextKey.MAIN_READY)
                : Texts.format(TextKey.STATUS_LOADED, currentRootPath));
    }

    private void configureLayout() {
        setPadding(Insets.EMPTY);
        setTop(createToolBar());

        Region projectTreeBackground = new Region();
        projectTreeBackground.getStyleClass().add("project-tree-background");
        projectTreeBackground.setMouseTransparent(true);
        StackPane projectTreeContainer = new StackPane(projectTreeBackground, treeView);
        projectTreeContainer.getStyleClass().add("project-tree-container");

        leftPane = new VBox(8, projectTreeLabel, projectTreeContainer);
        leftPane.getStyleClass().add("project-pane");
        treeView.getStyleClass().add("project-tree");
        leftPane.setPadding(new Insets(8));
        VBox.setVgrow(projectTreeContainer, Priority.ALWAYS);
        leftPane.setPrefWidth(appConfig.getUi().getTreeWidth());

        workspaceHeader = createWorkspaceHeader();
        workspaceHeader.getStyleClass().add("workspace-header");
        rightPane = new VBox(8, workspaceHeader, workspaceView);
        rightPane.getStyleClass().add("workspace-pane");
        rightPane.setPadding(new Insets(8));
        VBox.setVgrow(workspaceView, Priority.ALWAYS);

        SplitPane splitPane = new SplitPane(leftPane, rightPane);
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.setDividerPositions(0.3d);

        setCenter(splitPane);
        setBottom(createStatusBar());
    }

    private ToolBar createToolBar() {
        settingsButton.setTooltip(new Tooltip(Texts.text(TextKey.MAIN_SETTINGS)));
        ToolBar toolBar = new ToolBar(
                settingsButton,
                openFolderButton,
                rescanButton,
                saveButton,
                clearWorkspaceButton,
                rebuildStateButton,
                projectPathLabel
        );
        toolBar.getStyleClass().add("main-header");
        rescanButton.setDisable(true);
        saveButton.setDisable(true);
        clearWorkspaceButton.setDisable(true);
        rebuildStateButton.setDisable(true);
        return toolBar;
    }

    private HBox createStatusBar() {
        decreaseScaleButton.setFocusTraversable(false);
        increaseScaleButton.setFocusTraversable(false);
        decreaseScaleButton.getStyleClass().add("scale-adjust-button");
        increaseScaleButton.getStyleClass().add("scale-adjust-button");
        decreaseScaleButton.setMinWidth(26d);
        increaseScaleButton.setMinWidth(26d);
        decreaseScaleButton.setPrefHeight(26d);
        decreaseScaleButton.setMaxHeight(26d);
        increaseScaleButton.setPrefHeight(26d);
        increaseScaleButton.setMaxHeight(26d);
        scaleValueLabel.setMinWidth(40d);
        scaleValueLabel.setAlignment(Pos.CENTER);

        decreaseScaleButton.setOnAction(event -> adjustSelectedScale(-1d));
        increaseScaleButton.setOnAction(event -> adjustSelectedScale(1d));
        uiScale.addListener((observable, oldValue, newValue) -> refreshInterfaceScale());

        ToggleGroup scaleTargetGroup = new ToggleGroup();
        interfaceScaleTargetButton.setToggleGroup(scaleTargetGroup);
        workspaceScaleTargetButton.setToggleGroup(scaleTargetGroup);
        interfaceScaleTargetButton.setSelected(true);
        configureScaleTargetButton(interfaceScaleTargetButton, Texts.text(TextKey.MAIN_SCALE_INTERFACE), ScaleTarget.INTERFACE);
        configureScaleTargetButton(workspaceScaleTargetButton, Texts.text(TextKey.MAIN_SCALE_WORKSPACE), ScaleTarget.WORKSPACE);
        interfaceScaleTargetButton.prefHeightProperty().bind(increaseScaleButton.heightProperty().divide(2d));
        workspaceScaleTargetButton.prefHeightProperty().bind(increaseScaleButton.heightProperty().divide(2d));
        scaleTargetControls = new VBox(interfaceScaleTargetButton, workspaceScaleTargetButton);
        scaleTargetControls.prefHeightProperty().bind(increaseScaleButton.heightProperty());
        refreshScaleControls();
        refreshWorkspaceScaleAvailability();

        scaleControls = new HBox(
                4,
                decreaseScaleButton,
                scaleValueLabel,
                increaseScaleButton,
                scaleTargetControls
        );
        scaleControls.setAlignment(Pos.CENTER_RIGHT);

        statusBar = new HBox(8, statusLabel, createSpacer(), scaleControls);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(2, 10, 2, 10));
        return statusBar;
    }

    private void configureScaleTargetButton(ToggleButton button, String tooltip, ScaleTarget target) {
        button.setFocusTraversable(false);
        button.getStyleClass().add("scale-target-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setMinSize(24d, 0d);
        button.setMaxSize(24d, Double.MAX_VALUE);
        button.setStyle("-fx-font-size: 8px; -fx-padding: 0 2px;");
        button.setOnAction(event -> {
            button.setSelected(true);
            scaleTarget = target;
            refreshScaleControls();
        });
    }

    private void adjustSelectedScale(double direction) {
        if (scaleTarget == ScaleTarget.WORKSPACE) {
            adjustWorkspaceScale(direction);
        } else {
            adjustInterfaceScale(direction);
        }
    }

    private void adjustInterfaceScale(double direction) {
        double nextScale = uiScale.get() + direction * appConfig.getUi().getScaleStep();
        uiScale.set(Math.max(appConfig.getUi().getMinScale(), Math.min(nextScale, appConfig.getUi().getMaxScale())));
    }

    private void refreshScaleControls() {
        boolean workspaceSelected = scaleTarget == ScaleTarget.WORKSPACE;
        double currentScale = workspaceSelected ? currentTileScale() : uiScale.get();
        double minimumScale = workspaceSelected ? appConfig.getUi().getMinTileScale() : appConfig.getUi().getMinScale();
        double maximumScale = workspaceSelected ? appConfig.getUi().getMaxTileScale() : appConfig.getUi().getMaxScale();
        scaleValueLabel.setText(Math.round(currentScale * 100d) + "%");
        decreaseScaleButton.setDisable(currentScale <= minimumScale + 0.0001d);
        increaseScaleButton.setDisable(currentScale >= maximumScale - 0.0001d);
    }

    private void refreshInterfaceScale() {
        double scale = uiScale.get();
        refreshScaleControls();
        setPadding(Insets.EMPTY);
        leftPane.setSpacing(8d * scale);
        leftPane.setPadding(new Insets(8d * scale));
        leftPane.setPrefWidth(appConfig.getUi().getTreeWidth() * scale);
        rightPane.setSpacing(8d * scale);
        rightPane.setPadding(new Insets(8d * scale));
        workspaceHeader.setSpacing(12d * scale);
        masterVolumeSlider.setPrefWidth(180d * scale);
        masterVolumeValueLabel.setMinWidth(44d * scale);
        waveformLoadingIndicator.setPrefSize(18d * scale, 18d * scale);
        waveformLoadingIndicator.setMinSize(18d * scale, 18d * scale);
        waveformLoadingIndicator.setMaxSize(18d * scale, 18d * scale);
        UiIcons.resize(workspacePlayGraphic, scale);
        UiIcons.resize(workspacePauseGraphic, scale);
        UiIcons.resize(workspaceStopGraphic, scale);
        UiIcons.resize(settingsGraphic, scale);
        decreaseScaleButton.setMinWidth(26d * scale);
        increaseScaleButton.setMinWidth(26d * scale);
        decreaseScaleButton.setPrefHeight(26d * scale);
        decreaseScaleButton.setMaxHeight(26d * scale);
        increaseScaleButton.setPrefHeight(26d * scale);
        increaseScaleButton.setMaxHeight(26d * scale);
        scaleValueLabel.setMinWidth(40d * scale);
        interfaceScaleTargetButton.setMinWidth(24d * scale);
        interfaceScaleTargetButton.setMaxWidth(24d * scale);
        workspaceScaleTargetButton.setMinWidth(24d * scale);
        workspaceScaleTargetButton.setMaxWidth(24d * scale);
        interfaceScaleTargetButton.setStyle("-fx-font-size: " + (8d * scale) + "px; -fx-padding: 0 " + (2d * scale) + "px;");
        workspaceScaleTargetButton.setStyle("-fx-font-size: " + (8d * scale) + "px; -fx-padding: 0 " + (2d * scale) + "px;");
        scaleControls.setSpacing(4d * scale);
        statusBar.setSpacing(8d * scale);
        statusBar.setPadding(new Insets(2d * scale, 10d * scale, 2d * scale, 10d * scale));
        workspaceView.updateInterfaceScale(scale);
        refreshWorkspaceTileMetrics();
    }

    private HBox createWorkspaceHeader() {
        masterVolumeSlider.setValue(masterVolume * 100d);
        masterVolumeSlider.setPrefWidth(180d);
        masterVolumeValueLabel.setMinWidth(44d);
        workspacePlayPauseButton.setFocusTraversable(false);
        workspaceStopButton.setFocusTraversable(false);
        waveformLoadingIndicator.setVisible(false);
        waveformLoadingIndicator.setManaged(false);
        waveformLoadingIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        waveformLoadingIndicator.setPrefSize(18d, 18d);
        waveformLoadingIndicator.setMinSize(18d, 18d);
        waveformLoadingIndicator.setMaxSize(18d, 18d);

        HBox header = new HBox(
                12,
                workspaceLabel,
                workspacePlayPauseButton,
                workspaceStopButton,
                createSpacer(),
                masterVolumeLabel,
                masterVolumeSlider,
                masterVolumeValueLabel,
                waveformLoadingIndicator
        );
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private void configureActions() {
        settingsButton.setFocusTraversable(false);
        settingsButton.setOnAction(event -> settingsWindow.show());
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
        waveformLoadingIndicatorTimeline.setCycleCount(Timeline.INDEFINITE);
        waveformLoadingIndicatorTimeline.play();
        refreshWaveformLoadingIndicator();
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

        MenuItem addToWorkspaceItem = new MenuItem(Texts.text(TextKey.MAIN_ADD_TO_WORKSPACE));
        addToWorkspaceItem.setOnAction(event -> addSelectedAudioFilesToWorkspace(nodeValue.getAudioFileId()));

        Menu addToQueueMenu = new Menu(Texts.text(TextKey.MAIN_ADD_TO_QUEUE));
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
        directoryChooser.setTitle(Texts.text(TextKey.MAIN_OPEN_FOLDER_DIALOG));
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

        if (currentRootPath != null && currentRootPath.equals(rootPath)) {
            waveformService.clearUnderRoot(rootPath);
        } else {
            waveformService.clear();
        }
        refreshWaveformLoadingIndicator();

        clearWorkspace();
        projectState = ProjectState.empty(appConfig.getSchemaVersion());
        rebuildTree();
        rebuildWorkspace();

        loading = true;
        setControlsDisabled(true);
        statusLabel.setText(Texts.format(TextKey.STATUS_LOADING, rootPath));

        Task<ProjectLoadResult> loadTask = new Task<>() {
            @Override
            protected ProjectLoadResult call() throws Exception {
                return projectService.loadProject(rootPath);
            }
        };

        loadTask.setOnSucceeded(event -> {
            loading = false;
            ProjectLoadResult loadResult = loadTask.getValue();
            currentRootPath = loadResult.getRootPath();
            lastProjectPreferences.save(currentRootPath);
            setControlsDisabled(false);
            projectState = loadResult.getProjectState();
            updateMasterVolume(projectState.getMasterVolume());
            masterVolumeSlider.setValue(masterVolume * 100d);
            projectPathLabel.setText(currentRootPath.toString());
            statusLabel.setText(Texts.format(TextKey.STATUS_LOADED, currentRootPath));
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
            currentRootPath = null;
            setControlsDisabled(false);
            projectPathLabel.setText(Texts.text(TextKey.MAIN_NO_FOLDER));
            statusLabel.setText(Texts.text(TextKey.STATUS_LOAD_FAILED));
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
        rebuildingWorkspace = true;
        clearWorkspace();

        for (WorkspaceTrack workspaceTrack : sortedWorkspaceTracks()) {
            createWorkspaceTrackTile(workspaceTrack);
        }
        for (WorkspaceQueue workspaceQueue : sortedWorkspaceQueues()) {
            createQueueView(workspaceQueue);
        }
        for (WorkspaceVirtualTile tile : sortedWorkspaceVirtualTiles()) createVirtualTileView(tile);

        refreshWorkspaceOrder();
        workspaceView.refreshTileMetrics(
                scaledTrackTileWidth(),
                scaledTrackTileHeight(),
                combinedTileScale()
        );
        rebuildingWorkspace = false;
        preloadWorkspaceWaveforms();
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

    private void createVirtualTile(VirtualTileLayout layout) {
        if (currentRootPath == null) return;
        WorkspaceVirtualTile tile = projectStateEditor.createWorkspaceVirtualTile(projectState, layout);
        createVirtualTileView(tile);
        refreshWorkspaceOrder();
        requestProjectSave();
    }

    private void createVirtualTileView(WorkspaceVirtualTile tile) {
        WorkspaceVirtualTileItem item = new WorkspaceVirtualTileItem(
                currentRootPath, tile, projectState.getAudioFiles(), audioEngine, masterVolume,
                exception -> showError("Playback error", "Virtual tile track could not be initialized.", exception)
        );
        workspaceVirtualTileItems.put(tile.getId(), item);
        VirtualTileView view = new VirtualTileView(
                appConfig.getWorkspace().getProgressRefreshMillis(), item, waveformService,
                (audioFileIds, targetTrackId, placeAfter) -> addAudioFilesToVirtualTile(
                        tile.getId(), audioFileIds, targetTrackId, placeAfter),
                (workspaceTrackId, targetTrackId, placeAfter) -> moveWorkspaceTrackToVirtualTile(
                        workspaceTrackId, tile.getId(), targetTrackId, placeAfter),
                (sourceTileId, trackId, targetTrackId, placeAfter) -> moveVirtualTileTrack(
                        sourceTileId, tile.getId(), trackId, targetTrackId, placeAfter),
                trackId -> removeVirtualTileTrack(tile.getId(), trackId),
                (queueId, queueTrackId, targetTrackId, placeAfter) -> moveQueueTrackToVirtualTile(
                        queueId, queueTrackId, tile.getId(), targetTrackId, placeAfter),
                () -> removeVirtualTile(tile.getId()), this::requestProjectSave
        );
        virtualTileViews.put(tile.getId(), view);
    }

    private void addAudioFilesToVirtualTile(UUID tileId, List<UUID> audioFileIds) {
        addAudioFilesToVirtualTile(tileId, audioFileIds, null, false);
    }

    private void addAudioFilesToVirtualTile(UUID tileId, List<UUID> audioFileIds,
                                            UUID targetTrackId, boolean placeAfter) {
        if (projectStateEditor.addVirtualTileTracks(projectState, tileId, audioFileIds,
                appConfig.getWorkspace().getDefaultVolume(), targetTrackId, placeAfter).isEmpty()) return;
        refreshVirtualTile(tileId);
        requestProjectSave();
    }

    private void moveWorkspaceTrackToVirtualTile(UUID workspaceTrackId, UUID tileId,
                                                 UUID targetTrackId, boolean placeAfter) {
        WorkspaceTrackItem sourceItem = workspaceTrackItems.get(workspaceTrackId);
        List<UUID> previousTracks = virtualTrackIds(tileId);
        if (!projectStateEditor.moveWorkspaceTrackToVirtualTile(projectState, workspaceTrackId,
                tileId, targetTrackId, placeAfter)) return;
        PlaybackTransfer transfer = sourceItem == null ? null : sourceItem.detachPlayback();
        WorkspaceTrackItem item = workspaceTrackItems.remove(workspaceTrackId);
        if (item != null) item.dispose();
        TrackTileView view = trackTileViews.remove(workspaceTrackId);
        if (view != null) view.dispose();
        refreshVirtualTile(tileId);
        acceptVirtualPlayback(tileId, findAddedVirtualTrack(tileId, previousTracks), transfer);
        refreshWorkspaceOrder();
        requestProjectSave();
    }

    private void moveVirtualTileTrack(UUID sourceTileId, UUID targetTileId, UUID trackId,
                                      UUID targetTrackId, boolean placeAfter) {
        WorkspaceVirtualTileItem sourceItem = workspaceVirtualTileItems.get(sourceTileId);
        WorkspaceTrackItem sourceTrack = sourceItem == null ? null : sourceItem.getTrackItem(trackId);
        List<UUID> previousTracks = virtualTrackIds(targetTileId);
        if (!projectStateEditor.moveVirtualTileTrack(projectState, sourceTileId, targetTileId,
                trackId, targetTrackId, placeAfter)) return;
        PlaybackTransfer transfer = sourceTrack == null ? null : sourceTrack.detachPlayback();
        refreshVirtualTile(sourceTileId);
        if (!sourceTileId.equals(targetTileId)) refreshVirtualTile(targetTileId);
        UUID restoredTrackId = sourceTileId.equals(targetTileId) ? trackId
                : findAddedVirtualTrack(targetTileId, previousTracks);
        acceptVirtualPlayback(targetTileId, restoredTrackId, transfer);
        requestProjectSave();
    }

    private void moveVirtualTileTrackToWorkspace(UUID tileId, UUID trackId,
                                                 UUID targetWorkspaceItemId, boolean placeAfter) {
        WorkspaceVirtualTileItem sourceItem = workspaceVirtualTileItems.get(tileId);
        WorkspaceTrackItem sourceTrack = sourceItem == null ? null : sourceItem.getTrackItem(trackId);
        WorkspaceTrack track = projectStateEditor.moveVirtualTileTrackToWorkspace(projectState, tileId,
                trackId, targetWorkspaceItemId, placeAfter);
        if (track == null) return;
        PlaybackTransfer transfer = sourceTrack == null ? null : sourceTrack.detachPlayback();
        createWorkspaceTrackTile(track);
        WorkspaceTrackItem createdItem = workspaceTrackItems.get(track.getId());
        if (createdItem != null) createdItem.acceptPlayback(transfer);
        refreshVirtualTile(tileId);
        refreshWorkspaceOrder();
        requestProjectSave();
    }

    private void moveQueueTrackToVirtualTile(UUID queueId, UUID queueTrackId, UUID tileId,
                                             UUID targetTrackId, boolean placeAfter) {
        WorkspaceQueueItem queueItem = workspaceQueueItems.get(queueId);
        List<UUID> previousTracks = virtualTrackIds(tileId);
        if (!projectStateEditor.moveQueueTrackToVirtualTile(projectState, queueId, queueTrackId,
                tileId, targetTrackId, placeAfter)) return;
        PlaybackTransfer transfer = queueItem == null ? null : queueItem.detachPlayback(queueTrackId);
        refreshQueueView(queueId);
        refreshVirtualTile(tileId);
        acceptVirtualPlayback(tileId, findAddedVirtualTrack(tileId, previousTracks), transfer);
        requestProjectSave();
    }

    private void removeVirtualTileTrack(UUID tileId, UUID trackId) {
        projectStateEditor.removeVirtualTileTrack(projectState, tileId, trackId);
        refreshVirtualTile(tileId);
        requestProjectSave();
    }

    private void refreshVirtualTile(UUID tileId) {
        WorkspaceVirtualTileItem item = workspaceVirtualTileItems.get(tileId);
        if (item != null) item.refreshAfterMutation();
        VirtualTileView view = virtualTileViews.get(tileId);
        if (view != null) view.rebuild();
    }

    private List<UUID> virtualTrackIds(UUID tileId) {
        return projectStateEditor.findWorkspaceVirtualTile(projectState, tileId)
                .map(tile -> tile.getTracks().stream().map(track -> track.getId()).toList())
                .orElseGet(List::of);
    }

    private UUID findAddedVirtualTrack(UUID tileId, List<UUID> previousTracks) {
        return virtualTrackIds(tileId).stream().filter(id -> !previousTracks.contains(id)).findFirst().orElse(null);
    }

    private void acceptVirtualPlayback(UUID tileId, UUID trackId, PlaybackTransfer playback) {
        if (trackId == null) return;
        WorkspaceVirtualTileItem item = workspaceVirtualTileItems.get(tileId);
        WorkspaceTrackItem track = item == null ? null : item.getTrackItem(trackId);
        if (track != null) track.acceptPlayback(playback);
    }

    private List<UUID> queueTrackIds(UUID queueId) {
        return projectStateEditor.findWorkspaceQueue(projectState, queueId)
                .map(queue -> queue.getTracks().stream().map(track -> track.getId()).toList())
                .orElseGet(List::of);
    }

    private UUID findAddedQueueTrack(UUID queueId, List<UUID> previousTracks) {
        return queueTrackIds(queueId).stream().filter(id -> !previousTracks.contains(id)).findFirst().orElse(null);
    }

    private void acceptQueuePlayback(UUID queueId, UUID trackId, PlaybackTransfer playback) {
        if (trackId == null) return;
        WorkspaceQueueItem item = workspaceQueueItems.get(queueId);
        if (item != null) item.acceptPlayback(trackId, playback);
    }

    private void removeVirtualTile(UUID tileId) {
        WorkspaceVirtualTileItem item = workspaceVirtualTileItems.remove(tileId);
        if (item != null) item.dispose();
        VirtualTileView view = virtualTileViews.remove(tileId);
        if (view != null) view.dispose();
        projectStateEditor.removeWorkspaceVirtualTile(projectState, tileId);
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
                waveformService,
                appConfig.getWorkspace().getProgressRefreshMillis(),
                () -> removeQueue(workspaceQueue.getId()),
                this::requestProjectSave,
                (audioFileIds, targetQueueTrackId, placeAfter) -> addAudioFilesToQueue(workspaceQueue.getId(), audioFileIds, targetQueueTrackId, placeAfter),
                (workspaceTrackId, targetQueueTrackId, placeAfter) -> addWorkspaceTrackToQueue(workspaceQueue.getId(), workspaceTrackId, targetQueueTrackId, placeAfter),
                queueTrack -> removeQueueTrack(workspaceQueue.getId(), queueTrack.getId()),
                (sourceQueueId, queueTrackId, targetQueueTrackId, placeAfter) -> moveQueueTrack(sourceQueueId, workspaceQueue.getId(), queueTrackId, targetQueueTrackId, placeAfter),
                (tileId, trackId, targetQueueTrackId, placeAfter) -> moveVirtualTileTrackToQueue(
                        tileId, trackId, workspaceQueue.getId(), targetQueueTrackId, placeAfter),
                this::moveWorkspaceItem
        );
        queueViews.put(workspaceQueue.getId(), queueView);
        if (!rebuildingWorkspace) {
            preloadQueueWaveforms(workspaceQueueItem);
        }
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

        WorkspaceTrackItem sourceItem = workspaceTrackItems.get(workspaceTrackId);
        WorkspaceQueueItem targetItem = workspaceQueueItems.get(queueId);
        boolean targetHasPriority = targetItem != null && targetItem.hasActivePlayback();
        List<UUID> previousTracks = queueTrackIds(queueId);
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
        PlaybackTransfer transfer = targetHasPriority || sourceItem == null ? null : sourceItem.detachPlayback();

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
        acceptQueuePlayback(queueId, findAddedQueueTrack(queueId, previousTracks), transfer);
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
        WorkspaceQueueItem sourceItem = workspaceQueueItems.get(sourceQueueId);
        WorkspaceQueueItem targetItem = workspaceQueueItems.get(targetQueueId);
        boolean sameQueue = Objects.equals(sourceQueueId, targetQueueId);
        boolean targetHasPriority = !sameQueue && targetItem != null && targetItem.hasActivePlayback();
        List<UUID> previousTracks = queueTrackIds(targetQueueId);
        if (!projectStateEditor.moveQueueTrackToQueue(projectState, sourceQueueId, targetQueueId, queueTrackId, targetQueueTrackId, placeAfter)) {
            return;
        }
        PlaybackTransfer transfer = sameQueue || targetHasPriority || sourceItem == null
                ? null : sourceItem.detachPlayback(queueTrackId);

        refreshQueueView(sourceQueueId);
        if (!Objects.equals(sourceQueueId, targetQueueId)) {
            refreshQueueView(targetQueueId);
        }
        UUID restoredTrackId = sameQueue ? queueTrackId
                : findAddedQueueTrack(targetQueueId, previousTracks);
        acceptQueuePlayback(targetQueueId, restoredTrackId, transfer);
        requestProjectSave();
    }

    private void moveVirtualTileTrackToQueue(UUID tileId, UUID trackId, UUID queueId,
                                             UUID targetQueueTrackId, boolean placeAfter) {
        WorkspaceVirtualTileItem sourceItem = workspaceVirtualTileItems.get(tileId);
        WorkspaceTrackItem sourceTrack = sourceItem == null ? null : sourceItem.getTrackItem(trackId);
        WorkspaceQueueItem targetItem = workspaceQueueItems.get(queueId);
        boolean targetHasPriority = targetItem != null && targetItem.hasActivePlayback();
        List<UUID> previousTracks = queueTrackIds(queueId);
        if (!projectStateEditor.moveVirtualTileTrackToQueue(projectState, tileId, trackId, queueId,
                targetQueueTrackId, placeAfter)) return;
        PlaybackTransfer transfer = targetHasPriority || sourceTrack == null ? null : sourceTrack.detachPlayback();
        refreshVirtualTile(tileId);
        refreshQueueView(queueId);
        acceptQueuePlayback(queueId, findAddedQueueTrack(queueId, previousTracks), transfer);
        requestProjectSave();
    }

    private void moveQueueTrackToWorkspace(UUID sourceQueueId, UUID queueTrackId, UUID targetWorkspaceItemId, boolean placeAfter) {
        WorkspaceQueueItem sourceItem = workspaceQueueItems.get(sourceQueueId);
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
        PlaybackTransfer transfer = sourceItem == null ? null : sourceItem.detachPlayback(queueTrackId);

        createWorkspaceTrackTile(workspaceTrack);
        WorkspaceTrackItem createdItem = workspaceTrackItems.get(workspaceTrack.getId());
        if (createdItem != null) createdItem.acceptPlayback(transfer);
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
                Texts.text(TextKey.DIALOG_CLEAR_MESSAGE),
                ButtonType.OK,
                ButtonType.CANCEL
        );
        alert.initOwner(stage);
        alert.setTitle(Texts.text(TextKey.DIALOG_CLEAR_TITLE));
        alert.setHeaderText(Texts.text(TextKey.DIALOG_CLEAR_TITLE));
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
                Texts.text(TextKey.DIALOG_REBUILD_MESSAGE),
                ButtonType.OK,
                ButtonType.CANCEL
        );
        alert.initOwner(stage);
        alert.setTitle(Texts.text(TextKey.MAIN_REBUILD_SAVE));
        alert.setHeaderText(Texts.text(TextKey.DIALOG_REBUILD_TITLE));
        if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            waveformService.clearUnderRoot(currentRootPath);
            refreshWaveformLoadingIndicator();
            clearWorkspace();
            ProjectLoadResult loadResult = projectService.rebuildProjectState(currentRootPath);
            projectState = loadResult.getProjectState();
            updateMasterVolume(projectState.getMasterVolume());
            masterVolumeSlider.setValue(masterVolume * 100d);
            rebuildTree();
            rebuildWorkspace();
            requestProjectSave();
            statusLabel.setText(Texts.format(TextKey.STATUS_REBUILT, currentRootPath));
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
                waveformService,
                () -> removeWorkspaceTrack(workspaceTrack.getId()),
                this::requestProjectSave
        );
        trackTileViews.put(workspaceTrack.getId(), trackTileView);
        workspaceTrackItem.getAudioPath().ifPresent(waveformService::loadWaveform);
    }

    private void preloadWorkspaceWaveforms() {
        List<Path> audioPaths = new ArrayList<>();
        audioPaths.addAll(workspaceTrackItems.values().stream()
                .map(WorkspaceTrackItem::getAudioPath)
                .flatMap(Optional::stream)
                .toList());
        audioPaths.addAll(collectRoundRobinQueueWaveformPaths());
        audioPaths = audioPaths.stream()
                .distinct()
                .toList();
        waveformService.preloadWaveforms(audioPaths);
    }

    private void preloadQueueWaveforms(WorkspaceQueueItem workspaceQueueItem) {
        waveformService.preloadWaveforms(workspaceQueueItem.getAudioPaths());
    }

    private List<Path> collectRoundRobinQueueWaveformPaths() {
        List<List<Path>> queueAudioPathLists = sortedWorkspaceQueues().stream()
                .map(WorkspaceQueue::getId)
                .map(workspaceQueueItems::get)
                .filter(Objects::nonNull)
                .map(WorkspaceQueueItem::getAudioPaths)
                .toList();

        List<Path> orderedPaths = new ArrayList<>();
        int maxTrackCount = queueAudioPathLists.stream()
                .mapToInt(List::size)
                .max()
                .orElse(0);

        for (int trackIndex = 0; trackIndex < maxTrackCount; trackIndex++) {
            for (List<Path> queueAudioPaths : queueAudioPathLists) {
                if (trackIndex < queueAudioPaths.size()) {
                    orderedPaths.add(queueAudioPaths.get(trackIndex));
                }
            }
        }

        return orderedPaths;
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
                scaledTrackTileWidth(),
                scaledTrackTileHeight(),
                combinedTileScale()
        );
    }

    private void adjustWorkspaceZoom(double deltaY) {
        if (currentRootPath == null) {
            return;
        }
        adjustWorkspaceScale(deltaY > 0d ? 1d : -1d);
    }

    private void adjustWorkspaceScale(double direction) {
        if (currentRootPath == null) {
            return;
        }
        double currentScale = currentTileScale();
        double nextScale = currentScale + direction * appConfig.getUi().getTileZoomStep();
        nextScale = Math.max(appConfig.getUi().getMinTileScale(), Math.min(appConfig.getUi().getMaxTileScale(), nextScale));
        if (Math.abs(nextScale - currentScale) < 0.0001d) {
            return;
        }

        double nextWidth = Math.round(baseTrackTileWidth * nextScale);
        double nextHeight = Math.round(baseTrackTileHeight * nextScale);
        appConfig.getUi().setTrackTileWidth(nextWidth);
        appConfig.getUi().setTrackTileHeight(nextHeight);
        refreshWorkspaceTileMetrics();
        refreshScaleControls();
    }

    private double currentTileScale() {
        return appConfig.getUi().getTrackTileWidth() / baseTrackTileWidth;
    }

    private void refreshWorkspaceTileMetrics() {
        workspaceView.refreshTileMetrics(scaledTrackTileWidth(), scaledTrackTileHeight(), combinedTileScale());
    }

    private double scaledTrackTileWidth() {
        return appConfig.getUi().getTrackTileWidth() * uiScale.get();
    }

    private double scaledTrackTileHeight() {
        return appConfig.getUi().getTrackTileHeight() * uiScale.get();
    }

    private double combinedTileScale() {
        return currentTileScale() * uiScale.get();
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
        for (WorkspaceVirtualTile tile : projectState.getWorkspaceVirtualTiles()) {
            items.add(new WorkspaceTopLevelItem(tile.getId(), tile.getOrder(), virtualTileViews.get(tile.getId())));
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

    private List<WorkspaceVirtualTile> sortedWorkspaceVirtualTiles() {
        List<WorkspaceVirtualTile> tiles = new ArrayList<>(projectState.getWorkspaceVirtualTiles());
        tiles.sort(Comparator.comparingInt(WorkspaceVirtualTile::getOrder));
        return tiles;
    }

    private int workspaceItemCount() {
        return projectState.getWorkspaceTracks().size() + projectState.getWorkspaceQueues().size()
                + projectState.getWorkspaceVirtualTiles().size();
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
            } else if (node instanceof VirtualTileView virtualTileView) {
                virtualTileView.dispose();
            }
        });
        disposeWorkspaceTrackItems();
        disposeWorkspaceQueueItems();
        disposeWorkspaceVirtualTileItems();
        trackTileViews.clear();
        queueViews.clear();
        virtualTileViews.clear();
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

    private void disposeWorkspaceVirtualTileItems() {
        Collection<WorkspaceVirtualTileItem> items = new ArrayList<>(workspaceVirtualTileItems.values());
        workspaceVirtualTileItems.clear();
        items.forEach(WorkspaceVirtualTileItem::dispose);
    }

    private void updateMasterVolume(double masterVolume) {
        this.masterVolume = Math.max(0d, Math.min(1d, masterVolume));
        projectState.setMasterVolume(this.masterVolume);
        masterVolumeValueLabel.setText(Math.round(this.masterVolume * 100d) + "%");
        workspaceTrackItems.values().forEach(item -> item.setMasterVolume(this.masterVolume));
        workspaceQueueItems.values().forEach(item -> item.setMasterVolume(this.masterVolume));
        workspaceVirtualTileItems.values().forEach(item -> item.setMasterVolume(this.masterVolume));
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
        workspaceVirtualTileItems.values().forEach(WorkspaceVirtualTileItem::pauseIfPlaying);
    }

    private void resumePausedWorkspaceItems() {
        for (WorkspaceTrackItem workspaceTrackItem : workspaceTrackItems.values()) {
            workspaceTrackItem.resumeIfPaused();
        }

        for (WorkspaceQueueItem workspaceQueueItem : workspaceQueueItems.values()) {
            workspaceQueueItem.resumeIfPaused();
        }
        workspaceVirtualTileItems.values().forEach(WorkspaceVirtualTileItem::resumeIfPaused);
    }

    private void stopWorkspacePlayback() {
        workspaceTrackItems.values().forEach(WorkspaceTrackItem::stop);
        workspaceQueueItems.values().forEach(WorkspaceQueueItem::stop);
        workspaceVirtualTileItems.values().forEach(WorkspaceVirtualTileItem::stop);
        workspacePauseLatched = false;
        refreshWorkspaceTransportButtons();
    }

    private void refreshWorkspaceTransportButtons() {
        Node graphic = workspacePauseLatched ? workspacePlayGraphic : workspacePauseGraphic;
        if (workspacePlayPauseButton.getGraphic() != graphic) {
            workspacePlayPauseButton.setGraphic(graphic);
        }
    }

    private void refreshWaveformLoadingIndicator() {
        boolean loadingWaveforms = waveformService.getPendingLoadCount() > 0;
        waveformLoadingIndicator.setVisible(loadingWaveforms);
        waveformLoadingIndicator.setManaged(loadingWaveforms);
    }

    private void requestProjectSave() {
        if (currentRootPath == null) {
            return;
        }

        Path rootPath = currentRootPath;
        ProjectState snapshot = ProjectStateCopySupport.copy(projectState);
        int generation = saveGeneration.incrementAndGet();
        statusLabel.setText(Texts.format(TextKey.STATUS_SAVING, rootPath));
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
            statusLabel.setText(Texts.format(TextKey.STATUS_SAVED, currentRootPath));
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
        refreshWorkspaceScaleAvailability();
    }

    private void refreshWorkspaceScaleAvailability() {
        boolean projectClosed = currentRootPath == null;
        workspaceScaleTargetButton.setDisable(projectClosed);
        if (projectClosed && scaleTarget == ScaleTarget.WORKSPACE) {
            scaleTarget = ScaleTarget.INTERFACE;
            interfaceScaleTargetButton.setSelected(true);
            refreshScaleControls();
        }
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

    private enum ScaleTarget {
        INTERFACE,
        WORKSPACE
    }
}
