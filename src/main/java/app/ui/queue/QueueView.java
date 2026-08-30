package app.ui.queue;

import app.config.UiConfig;
import app.model.AudioFile;
import app.model.PlaybackStatus;
import app.model.QueueTrack;
import app.localization.TextKey;
import app.localization.Texts;
import app.ui.UiIcons;
import app.ui.drag.DragPayload;
import app.ui.waveform.WaveformSeekView;
import app.ui.workspace.WorkspaceInsertionMarker;
import app.ui.workspace.WorkspaceItemView;
import app.ui.workspace.WorkspaceQueueItem;
import app.waveform.WaveformData;
import app.waveform.WaveformService;
import javafx.application.Platform;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public class QueueView extends StackPane implements WorkspaceItemView {
    private static final Color PLAYING_COLOR = Color.web("#2f9e44");
    private static final Color PAUSED_COLOR = Color.web("#f0b429");
    private static final Color FINISHED_COLOR = Color.web("#d64545");
    private static final Color READY_COLOR = Color.web("#97a3b6");

    private final WorkspaceQueueItem workspaceQueueItem;
    private final WaveformService waveformService;
    private final Runnable removeQueueAction;
    private final Runnable persistenceChangeAction;
    private final QueueAudioDropHandler addAudioFilesAction;
    private final QueueWorkspaceTrackDropHandler addWorkspaceTrackAction;
    private final Consumer<QueueTrack> removeQueueTrackAction;
    private final QueueTrackMoveHandler moveQueueTrackAction;
    private final VirtualTrackDropHandler addVirtualTrackAction;
    private final WorkspaceQueueMoveHandler moveWorkspaceQueueAction;
    private final Timeline refreshTimeline;

    private final AnchorPane leftInsertionMarker = createInsertionMarker(true);
    private final AnchorPane rightInsertionMarker = createInsertionMarker(false);
    private final Rectangle queueClip = new Rectangle();
    private final Circle statusIndicator = new Circle(5d, READY_COLOR);
    private final Pane titleViewport = new Pane();
    private final Rectangle titleClip = new Rectangle();
    private final Label queueNameLabel = new Label();
    private final TextField queueNameField = new TextField();
    private final Node previousGraphic = UiIcons.previous();
    private final Node playGraphic = UiIcons.play();
    private final Node pauseGraphic = UiIcons.pause();
    private final Node stopGraphic = UiIcons.stop();
    private final Node nextGraphic = UiIcons.next();
    private final Node loopTrackGraphic = UiIcons.loop();
    private final Node loopQueueGraphic = UiIcons.loop();
    private final Button previousButton = new Button(null, previousGraphic);
    private final Button playPauseButton = new Button(null, playGraphic);
    private final Button stopButton = new Button(null, stopGraphic);
    private final Button nextButton = new Button(null, nextGraphic);
    private final ToggleButton shuffleButton = new ToggleButton(Texts.text(TextKey.QUEUE_SHUFFLE));
    private final Button removeQueueButton = new Button(Texts.text(TextKey.QUEUE_REMOVE));
    private final ToggleButton loopTrackButton = new ToggleButton(Texts.text(TextKey.QUEUE_TRACK), loopTrackGraphic);
    private final ToggleButton loopQueueButton = new ToggleButton(Texts.text(TextKey.QUEUE_QUEUE), loopQueueGraphic);
    private final Slider volumeSlider = new Slider(0d, 100d, 80d);
    private final Slider trackVolumeSlider = new Slider(0d, 100d, 100d);
    private final WaveformSeekView waveformSeekView = new WaveformSeekView();
    private final Label currentTimeLabel = new Label("00:00");
    private final Label totalTimeLabel = new Label("00:00");
    private final Label volumeLabel = new Label(Texts.text(TextKey.QUEUE_VOLUME));
    private final Label trackVolumeLabel = new Label(Texts.text(TextKey.QUEUE_TRACK_VOLUME));
    private final MenuItem resetTrackVolumesItem = new MenuItem(Texts.text(TextKey.QUEUE_RESET_TRACK_VOLUMES));
    private final ContextMenu contextMenu = new ContextMenu(resetTrackVolumesItem);
    private final FlowPane chipContainer = new FlowPane();
    private final ScrollPane chipScrollPane = new ScrollPane(chipContainer);
    private final BorderPane content = new BorderPane();
    private final HBox titleRow = new HBox();
    private final HBox transportRow = new HBox();
    private final HBox volumeRow = new HBox();
    private final HBox trackVolumeRow = new HBox();
    private final VBox volumeControls = new VBox();
    private final HBox progressTimeRow = new HBox();
    private final HBox progressRow = new HBox();
    private final VBox body = new VBox();

    private boolean editingQueueName;
    private String editingOriginalQueueName;
    private QueueTrackChipView insertionTargetChipView;
    private boolean insertionAfter;
    private double currentScale = 1d;
    private final double baseWaveformHeight;
    private Path currentWaveformPath;
    private long waveformRequestGeneration;
    private SequentialTransition titleAnimation;

    public QueueView(
            UiConfig uiConfig,
            WorkspaceQueueItem workspaceQueueItem,
            WaveformService waveformService,
            int progressRefreshMillis,
            Runnable removeQueueAction,
            Runnable persistenceChangeAction,
            QueueAudioDropHandler addAudioFilesAction,
            QueueWorkspaceTrackDropHandler addWorkspaceTrackAction,
            Consumer<QueueTrack> removeQueueTrackAction,
            QueueTrackMoveHandler moveQueueTrackAction,
            VirtualTrackDropHandler addVirtualTrackAction,
            WorkspaceQueueMoveHandler moveWorkspaceQueueAction
    ) {
        getStyleClass().add("queue-tile");
        this.workspaceQueueItem = workspaceQueueItem;
        this.waveformService = waveformService;
        this.removeQueueAction = removeQueueAction;
        this.persistenceChangeAction = persistenceChangeAction;
        this.addAudioFilesAction = addAudioFilesAction;
        this.addWorkspaceTrackAction = addWorkspaceTrackAction;
        this.removeQueueTrackAction = removeQueueTrackAction;
        this.moveQueueTrackAction = moveQueueTrackAction;
        this.addVirtualTrackAction = addVirtualTrackAction;
        this.moveWorkspaceQueueAction = moveWorkspaceQueueAction;
        this.baseWaveformHeight = uiConfig.getWaveformHeight();
        this.refreshTimeline = new Timeline(new KeyFrame(Duration.millis(progressRefreshMillis), event -> refresh()));

        configureLayout(uiConfig);
        configureActions();
        rebuildChips();
        refresh();

        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    public void dispose() {
        refreshTimeline.stop();
        waveformRequestGeneration++;
        currentWaveformPath = null;
        waveformSeekView.setWaveformData(WaveformData.empty());
        stopTitleAnimation();
    }

    public void updateTileMetrics(double tileWidth, double tileHeight, double tileScale) {
        setMinHeight(tileHeight);
        setPrefHeight(tileHeight);
        setMaxHeight(tileHeight);
        applyScale(tileScale);
    }

    @Override
    public UUID getWorkspaceItemId() {
        return workspaceQueueItem.getWorkspaceQueue().getId();
    }

    @Override
    public void setInsertionMarker(WorkspaceInsertionMarker insertionMarker) {
        leftInsertionMarker.setVisible(insertionMarker == WorkspaceInsertionMarker.LEFT);
        rightInsertionMarker.setVisible(insertionMarker == WorkspaceInsertionMarker.RIGHT);
    }

    public void rebuildChips() {
        List<QueueTrackChipView> chipViews = new ArrayList<>();
        for (QueueTrack queueTrack : workspaceQueueItem.getTracks()) {
            AudioFile audioFile = workspaceQueueItem.getAudioFile(queueTrack.getAudioFileId()).orElse(null);
            QueueTrackChipView chipView = new QueueTrackChipView(
                    queueTrack,
                    audioFile,
                    () -> {
                        workspaceQueueItem.selectTrack(queueTrack.getId());
                        refresh();
                        persistenceChangeAction.run();
                    },
                    () -> {
                        workspaceQueueItem.playSelectedTrack(queueTrack.getId());
                        refresh();
                        persistenceChangeAction.run();
                    },
                    track -> {
                        removeQueueTrackAction.accept(track);
                        rebuildChips();
                        refresh();
                    },
                    volume -> workspaceQueueItem.setTrackVolume(queueTrack.getId(), volume),
                    persistenceChangeAction
            );
            chipView.updateScale(currentScale);
            configureChipDragAndDrop(chipView);
            chipViews.add(chipView);
        }
        chipContainer.getChildren().setAll(chipViews);
    }

    private void configureLayout(UiConfig uiConfig) {
        updateTileMetrics(uiConfig.getTrackTileWidth(), uiConfig.getTrackTileHeight(), 1d);
        setClip(queueClip);
        widthProperty().addListener((observable, oldValue, newValue) -> queueClip.setWidth(newValue.doubleValue()));
        heightProperty().addListener((observable, oldValue, newValue) -> queueClip.setHeight(newValue.doubleValue()));

        content.setPadding(new Insets(5));

        String queueName = workspaceQueueItem.getWorkspaceQueue().getName();
        queueNameLabel.setText(queueName);
        queueNameLabel.setWrapText(false);
        titleViewport.setClip(titleClip);
        titleViewport.getChildren().add(queueNameLabel);
        titleViewport.widthProperty().addListener((observable, oldValue, newValue) -> updateTitleAnimation());
        titleViewport.heightProperty().addListener((observable, oldValue, newValue) -> updateTitleAnimation());
        queueNameLabel.layoutBoundsProperty().addListener((observable, oldValue, newValue) -> updateTitleAnimation());

        queueNameField.setText(queueName);
        queueNameField.setPromptText(Texts.text(TextKey.QUEUE_NAME));
        queueNameField.setVisible(false);
        queueNameField.setManaged(false);
        widthProperty().addListener((observable, oldValue, newValue) -> updateTitleControlWidth());

        StackPane titleStack = new StackPane(titleViewport, queueNameField);
        titleStack.setAlignment(Pos.CENTER_LEFT);
        titleRow.getChildren().setAll(titleStack);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setPadding(new Insets(0, 18, 0, 0));
        HBox.setHgrow(titleStack, Priority.ALWAYS);

        loopTrackButton.setTooltip(new Tooltip(Texts.text(TextKey.QUEUE_LOOP_TRACK)));
        loopQueueButton.setTooltip(new Tooltip(Texts.text(TextKey.QUEUE_LOOP_QUEUE)));
        previousButton.setTooltip(new Tooltip(Texts.text(TextKey.QUEUE_PREVIOUS)));
        playPauseButton.setTooltip(new Tooltip(Texts.text(TextKey.TOOLTIP_PLAY_PAUSE)));
        stopButton.setTooltip(new Tooltip(Texts.text(TextKey.TOOLTIP_STOP)));
        nextButton.setTooltip(new Tooltip(Texts.text(TextKey.QUEUE_NEXT)));
        volumeSlider.setValue(workspaceQueueItem.getVolume() * 100d);
        transportRow.getChildren().setAll(
                previousButton,
                playPauseButton,
                stopButton,
                nextButton,
                shuffleButton,
                removeQueueButton,
                loopTrackButton,
                loopQueueButton,
                volumeControls
        );
        transportRow.setAlignment(Pos.CENTER_LEFT);

        volumeRow.getChildren().setAll(volumeLabel, volumeSlider);
        volumeRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(volumeSlider, Priority.ALWAYS);

        trackVolumeRow.getChildren().setAll(trackVolumeLabel, trackVolumeSlider);
        trackVolumeRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(trackVolumeSlider, Priority.ALWAYS);
        volumeControls.getChildren().setAll(volumeRow, trackVolumeRow);
        VBox.setVgrow(volumeRow, Priority.ALWAYS);
        VBox.setVgrow(trackVolumeRow, Priority.ALWAYS);
        HBox.setHgrow(volumeControls, Priority.ALWAYS);

        progressTimeRow.getChildren().setAll(currentTimeLabel, createSpacer(), totalTimeLabel);
        progressTimeRow.setAlignment(Pos.CENTER_LEFT);

        progressRow.getChildren().setAll(waveformSeekView);
        progressRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(waveformSeekView, Priority.ALWAYS);

        chipContainer.setAlignment(Pos.TOP_LEFT);
        chipScrollPane.setFitToHeight(true);
        chipScrollPane.setFitToWidth(true);
        chipScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        chipScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        chipScrollPane.setContent(chipContainer);
        chipScrollPane.setStyle("-fx-background-color: transparent;");
        chipScrollPane.viewportBoundsProperty().addListener((observable, oldValue, newValue) -> updateChipWrapLength());

        body.getChildren().setAll(titleRow, transportRow, progressTimeRow, progressRow, chipScrollPane);
        body.setFillWidth(true);
        VBox.setVgrow(chipScrollPane, Priority.ALWAYS);
        content.setCenter(body);

        HBox insertionMarkers = new HBox(createMarkerHolder(leftInsertionMarker), createSpacer(), createMarkerHolder(rightInsertionMarker));
        insertionMarkers.setMouseTransparent(true);
        insertionMarkers.setAlignment(Pos.CENTER);

        StackPane.setAlignment(statusIndicator, Pos.TOP_RIGHT);
        getChildren().setAll(content, insertionMarkers, statusIndicator);
        waveformSeekView.setWaveformData(WaveformData.empty());
        applyScale(1d);
        updateTitleAnimation();
        resetTrackVolumesItem.setOnAction(event -> {
            workspaceQueueItem.resetTrackVolumes();
            trackVolumeSlider.setValue(100d);
            persistenceChangeAction.run();
        });
        setOnContextMenuRequested(event -> {
            contextMenu.show(this, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    public void refreshLocalization() {
        shuffleButton.setText(Texts.text(TextKey.QUEUE_SHUFFLE));
        removeQueueButton.setText(Texts.text(TextKey.QUEUE_REMOVE));
        loopTrackButton.setText(Texts.text(TextKey.QUEUE_TRACK));
        loopQueueButton.setText(Texts.text(TextKey.QUEUE_QUEUE));
        volumeLabel.setText(Texts.text(TextKey.QUEUE_VOLUME));
        trackVolumeLabel.setText(Texts.text(TextKey.QUEUE_TRACK_VOLUME));
        resetTrackVolumesItem.setText(Texts.text(TextKey.QUEUE_RESET_TRACK_VOLUMES));
        queueNameField.setPromptText(Texts.text(TextKey.QUEUE_NAME));
        loopTrackButton.setTooltip(new Tooltip(Texts.text(TextKey.QUEUE_LOOP_TRACK)));
        loopQueueButton.setTooltip(new Tooltip(Texts.text(TextKey.QUEUE_LOOP_QUEUE)));
        previousButton.setTooltip(new Tooltip(Texts.text(TextKey.QUEUE_PREVIOUS)));
        playPauseButton.setTooltip(new Tooltip(Texts.text(TextKey.TOOLTIP_PLAY_PAUSE)));
        stopButton.setTooltip(new Tooltip(Texts.text(TextKey.TOOLTIP_STOP)));
        nextButton.setTooltip(new Tooltip(Texts.text(TextKey.QUEUE_NEXT)));
        rebuildChips();
    }

    private void configureActions() {
        playPauseButton.setOnAction(event -> {
            workspaceQueueItem.togglePlayPause();
            refresh();
            persistenceChangeAction.run();
        });

        stopButton.setOnAction(event -> {
            workspaceQueueItem.stop();
            refresh();
        });

        previousButton.setOnAction(event -> {
            workspaceQueueItem.previous();
            refresh();
            persistenceChangeAction.run();
        });

        nextButton.setOnAction(event -> {
            workspaceQueueItem.next();
            refresh();
            persistenceChangeAction.run();
        });

        shuffleButton.selectedProperty().addListener((observable, oldValue, newValue) -> {
            workspaceQueueItem.setShuffleEnabled(newValue);
            rebuildChips();
            refresh();
            persistenceChangeAction.run();
        });

        removeQueueButton.setOnAction(event -> removeQueueAction.run());

        queueNameField.textProperty().addListener((observable, oldValue, newValue) -> {
            queueNameLabel.setText(newValue == null ? "" : newValue);
            updateTitleAnimation();
        });
        queueNameField.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue && editingQueueName) {
                stopEditingQueueName(true);
            }
        });
        queueNameField.setOnAction(event -> stopEditingQueueName(true));
        queueNameField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stopEditingQueueName(false);
            }
        });
        titleViewport.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                startEditingQueueName();
            }
        });

        loopTrackButton.selectedProperty().addListener((observable, oldValue, newValue) -> {
            workspaceQueueItem.setLoopCurrentTrack(newValue);
            persistenceChangeAction.run();
        });

        loopQueueButton.selectedProperty().addListener((observable, oldValue, newValue) -> {
            workspaceQueueItem.setLoopQueue(newValue);
            persistenceChangeAction.run();
        });

        volumeSlider.valueChangingProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) {
                persistenceChangeAction.run();
            }
        });
        volumeSlider.setOnMouseReleased(event -> persistenceChangeAction.run());
        volumeSlider.valueProperty().addListener((observable, oldValue, newValue) ->
                workspaceQueueItem.setVolume(newValue.doubleValue() / 100d)
        );
        trackVolumeSlider.valueProperty().addListener((observable, oldValue, newValue) ->
                workspaceQueueItem.setSelectedTrackVolume(newValue.doubleValue() / 100d)
        );
        trackVolumeSlider.valueChangingProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) persistenceChangeAction.run();
        });
        trackVolumeSlider.setOnMouseReleased(event -> persistenceChangeAction.run());
        waveformSeekView.setSeekHandler(position -> {
            workspaceQueueItem.seek(position);
            refresh();
        });

        setOnDragOver(event -> {
            String payload = event.getDragboard().getString();
            if (!canAcceptQueueDrop(payload, null)) {
                return;
            }

            event.acceptTransferModes(resolveTransferMode(payload));
            event.consume();
        });

        setOnDragDropped(event -> {
            String payload = event.getDragboard().getString();
            if (!handleQueueDrop(payload, null, false)) {
                event.setDropCompleted(false);
                return;
            }
            event.setDropCompleted(true);
            event.consume();
        });
    }

    private void configureChipDragAndDrop(QueueTrackChipView chipView) {
        chipView.setOnDragDetected(event -> {
            var dragboard = chipView.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(DragPayload.queueTrack(getWorkspaceItemId(), chipView.getQueueTrack().getId()));
            dragboard.setContent(content);
            event.consume();
        });

        chipView.setOnDragOver(event -> {
            String payload = event.getDragboard().getString();
            boolean placeAfter = event.getX() >= chipView.getBoundsInLocal().getWidth() / 2d;
            if (!canAcceptQueueDrop(payload, chipView.getQueueTrack().getId())) {
                return;
            }

            updateChipInsertionMarker(chipView, placeAfter);
            event.acceptTransferModes(resolveTransferMode(payload));
            event.consume();
        });

        chipView.setOnDragExited(event -> {
            if (!isPointerInside(chipView, event.getSceneX(), event.getSceneY())) {
                clearChipInsertionMarker();
            }
        });

        chipView.setOnDragDropped(event -> {
            String payload = event.getDragboard().getString();
            boolean placeAfter = event.getX() >= chipView.getBoundsInLocal().getWidth() / 2d;
            boolean completed = handleQueueDrop(payload, chipView.getQueueTrack().getId(), placeAfter);
            event.setDropCompleted(completed);
            clearChipInsertionMarker();
            refresh();
            event.consume();
        });

        chipView.setOnDragDone(event -> clearChipInsertionMarker());
    }

    private void refresh() {
        PlaybackStatus activePlaybackStatus = workspaceQueueItem.getStatus();
        PlaybackStatus playbackStatus = workspaceQueueItem.getFocusedStatus();
        Duration currentTime = workspaceQueueItem.getFocusedCurrentTime();
        Duration totalTime = workspaceQueueItem.getFocusedTotalDuration();
        refreshWaveformSource();

        Node graphic = playbackStatus == PlaybackStatus.PLAYING ? pauseGraphic : playGraphic;
        if (playPauseButton.getGraphic() != graphic) {
            playPauseButton.setGraphic(graphic);
        }
        shuffleButton.setSelected(workspaceQueueItem.isShuffleEnabled());
        loopTrackButton.setSelected(workspaceQueueItem.isLoopCurrentTrack());
        loopQueueButton.setSelected(workspaceQueueItem.isLoopQueue());
        statusIndicator.setFill(resolveStatusColor(playbackStatus));
        waveformSeekView.setPlaybackPosition(currentTime, totalTime);
        currentTimeLabel.setText(formatDuration(waveformSeekView.getDisplayedPosition()));
        totalTimeLabel.setText(formatDuration(totalTime));

        UUID selectedTrackId = workspaceQueueItem.getFocusedTrack().map(QueueTrack::getId).orElse(null);
        UUID activeTrackId = workspaceQueueItem.getActiveTrackId();
        trackVolumeSlider.setDisable(selectedTrackId == null);
        double selectedVolume = workspaceQueueItem.getSelectedTrackVolume() * 100d;
        if (Math.abs(trackVolumeSlider.getValue() - selectedVolume) > 0.01d) {
            trackVolumeSlider.setValue(selectedVolume);
        }
        for (var node : chipContainer.getChildren()) {
            if (!(node instanceof QueueTrackChipView chipView)) {
                continue;
            }
            boolean selected = Objects.equals(chipView.getQueueTrack().getId(), selectedTrackId);
            chipView.refresh(selected, activePlaybackStatus,
                    Objects.equals(chipView.getQueueTrack().getId(), activeTrackId));
        }
        if (!editingQueueName) {
            updateTitleAnimation();
        }
    }

    private String formatDuration(Duration duration) {
        long totalSeconds = (long) Math.max(duration.toSeconds(), 0d);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private Color resolveStatusColor(PlaybackStatus playbackStatus) {
        return switch (playbackStatus) {
            case PLAYING -> PLAYING_COLOR;
            case PAUSED -> PAUSED_COLOR;
            case FINISHED, ERROR -> FINISHED_COLOR;
            default -> READY_COLOR;
        };
    }

    private AnchorPane createInsertionMarker(boolean leftSide) {
        AnchorPane marker = new AnchorPane();
        marker.setPrefWidth(12d);
        marker.setMinWidth(12d);
        marker.setMaxWidth(12d);
        marker.setVisible(false);
        marker.setMouseTransparent(true);

        Region vertical = createMarkerSegment(4d, -1d);
        Region top = createMarkerSegment(10d, 4d);
        Region bottom = createMarkerSegment(10d, 4d);

        if (leftSide) {
            AnchorPane.setLeftAnchor(vertical, 0d);
            AnchorPane.setLeftAnchor(top, 0d);
            AnchorPane.setLeftAnchor(bottom, 0d);
        } else {
            AnchorPane.setRightAnchor(vertical, 0d);
            AnchorPane.setRightAnchor(top, 0d);
            AnchorPane.setRightAnchor(bottom, 0d);
        }

        AnchorPane.setTopAnchor(vertical, 0d);
        AnchorPane.setBottomAnchor(vertical, 0d);
        AnchorPane.setTopAnchor(top, 0d);
        AnchorPane.setBottomAnchor(bottom, 0d);
        marker.getChildren().addAll(vertical, top, bottom);
        return marker;
    }

    private Region createMarkerSegment(double width, double height) {
        Region region = new Region();
        region.getStyleClass().add("insertion-marker");
        region.setMinWidth(width);
        region.setPrefWidth(width);
        region.setMaxWidth(width);
        if (height > 0d) {
            region.setMinHeight(height);
            region.setPrefHeight(height);
            region.setMaxHeight(height);
        }
        return region;
    }

    private HBox createMarkerHolder(AnchorPane marker) {
        HBox holder = new HBox(marker);
        holder.setAlignment(Pos.CENTER);
        holder.setFillHeight(true);
        return holder;
    }

    private Region createSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private List<UUID> parseAudioFileIds(String payload) {
        return DragPayload.parseAudioFileIds(payload);
    }

    private UUID parseWorkspaceTrackId(String payload) {
        return DragPayload.parseWorkspaceTrackId(payload);
    }

    private UUID parseWorkspaceQueueId(String payload) {
        return DragPayload.parseWorkspaceQueueId(payload);
    }

    private QueueTrackPayload parseQueueTrackPayload(String payload) {
        DragPayload.QueueTrackRef queueTrackRef = DragPayload.parseQueueTrack(payload);
        return queueTrackRef == null ? null : new QueueTrackPayload(queueTrackRef.queueId(), queueTrackRef.queueTrackId());
    }

    private boolean canAcceptQueueDrop(String payload, UUID targetQueueTrackId) {
        if (!parseAudioFileIds(payload).isEmpty()) {
            return true;
        }

        if (parseWorkspaceTrackId(payload) != null) {
            return true;
        }

        if (DragPayload.parseVirtualTileTrack(payload) != null) return true;

        QueueTrackPayload queueTrackPayload = parseQueueTrackPayload(payload);
        if (queueTrackPayload != null) {
            if (!queueTrackPayload.queueId().equals(getWorkspaceItemId())) {
                return true;
            }
            return !workspaceQueueItem.isShuffleEnabled()
                    && targetQueueTrackId != null
                    && !queueTrackPayload.queueTrackId().equals(targetQueueTrackId);
        }

        UUID workspaceQueueId = parseWorkspaceQueueId(payload);
        return workspaceQueueId != null && !workspaceQueueId.equals(getWorkspaceItemId());
    }

    private TransferMode resolveTransferMode(String payload) {
        return parseAudioFileIds(payload).isEmpty() ? TransferMode.MOVE : TransferMode.COPY;
    }

    private boolean handleQueueDrop(String payload, UUID targetQueueTrackId, boolean placeAfter) {
        List<UUID> audioFileIds = parseAudioFileIds(payload);
        if (!audioFileIds.isEmpty()) {
            addAudioFilesAction.addAudioFiles(audioFileIds, targetQueueTrackId, placeAfter);
            return true;
        }

        UUID workspaceTrackId = parseWorkspaceTrackId(payload);
        if (workspaceTrackId != null) {
            addWorkspaceTrackAction.addWorkspaceTrack(workspaceTrackId, targetQueueTrackId, placeAfter);
            return true;
        }

        DragPayload.VirtualTileTrackRef virtualTrack = DragPayload.parseVirtualTileTrack(payload);
        if (virtualTrack != null) {
            addVirtualTrackAction.add(virtualTrack.tileId(), virtualTrack.trackId(), targetQueueTrackId, placeAfter);
            return true;
        }

        QueueTrackPayload queueTrackPayload = parseQueueTrackPayload(payload);
        if (queueTrackPayload != null) {
            if (queueTrackPayload.queueId().equals(getWorkspaceItemId())) {
                if (targetQueueTrackId != null && !queueTrackPayload.queueTrackId().equals(targetQueueTrackId)) {
                    moveQueueTrackAction.moveQueueTrack(queueTrackPayload.queueId(), queueTrackPayload.queueTrackId(), targetQueueTrackId, placeAfter);
                    return true;
                }
                return false;
            }

            moveQueueTrackAction.moveQueueTrack(queueTrackPayload.queueId(), queueTrackPayload.queueTrackId(), targetQueueTrackId, placeAfter);
            return true;
        }

        UUID workspaceQueueId = parseWorkspaceQueueId(payload);
        if (workspaceQueueId != null && !workspaceQueueId.equals(getWorkspaceItemId())) {
            moveWorkspaceQueueAction.moveWorkspaceQueue(workspaceQueueId, getWorkspaceItemId(), placeAfter);
            return true;
        }

        return false;
    }

    private void updateChipInsertionMarker(QueueTrackChipView chipView, boolean placeAfter) {
        if (insertionTargetChipView == chipView && insertionAfter == placeAfter) {
            return;
        }

        clearChipInsertionMarker();
        insertionTargetChipView = chipView;
        insertionAfter = placeAfter;
        chipView.setInsertionMarker(placeAfter ? WorkspaceInsertionMarker.RIGHT : WorkspaceInsertionMarker.LEFT);
    }

    private void clearChipInsertionMarker() {
        if (insertionTargetChipView != null) {
            insertionTargetChipView.setInsertionMarker(WorkspaceInsertionMarker.NONE);
            insertionTargetChipView = null;
        }
        insertionAfter = false;
    }

    private boolean isPointerInside(QueueTrackChipView chipView, double sceneX, double sceneY) {
        var bounds = chipView.localToScene(chipView.getBoundsInLocal());
        return bounds != null && bounds.contains(sceneX, sceneY);
    }

    private void refreshWaveformSource() {
        Path nextWaveformPath = workspaceQueueItem.getSelectedAudioPath().orElse(null);
        if (Objects.equals(currentWaveformPath, nextWaveformPath)) {
            return;
        }

        currentWaveformPath = nextWaveformPath;
        long requestGeneration = ++waveformRequestGeneration;
        waveformSeekView.setWaveformData(WaveformData.empty());
        if (nextWaveformPath == null) {
            return;
        }

        waveformService.loadWaveform(nextWaveformPath).thenAccept(waveformData ->
                Platform.runLater(() -> applyWaveformData(requestGeneration, nextWaveformPath, waveformData))
        );
    }

    private void applyWaveformData(long requestGeneration, Path waveformPath, WaveformData waveformData) {
        if (requestGeneration != waveformRequestGeneration || !Objects.equals(currentWaveformPath, waveformPath)) {
            return;
        }
        waveformSeekView.setWaveformData(waveformData);
    }

    private void startEditingQueueName() {
        editingQueueName = true;
        editingOriginalQueueName = queueNameLabel.getText();
        stopTitleAnimation();
        queueNameField.setText(editingOriginalQueueName);
        titleViewport.setVisible(false);
        titleViewport.setManaged(false);
        queueNameField.setVisible(true);
        queueNameField.setManaged(true);
        queueNameField.requestFocus();
        queueNameField.selectAll();
    }

    private void stopEditingQueueName(boolean commit) {
        editingQueueName = false;
        String queueName = commit
                ? (queueNameField.getText() == null ? "" : queueNameField.getText())
                : (editingOriginalQueueName == null ? "" : editingOriginalQueueName);
        queueNameField.setText(queueName);
        queueNameLabel.setText(queueName);
        workspaceQueueItem.getWorkspaceQueue().setName(queueName);
        titleViewport.setVisible(true);
        titleViewport.setManaged(true);
        queueNameField.setVisible(false);
        queueNameField.setManaged(false);
        if (commit) {
            persistenceChangeAction.run();
        }
        updateTitleAnimation();
    }

    private void applyScale(double scale) {
        currentScale = scale;
        setStyle("-fx-border-radius: " + (8d * scale)
                + "; -fx-background-radius: " + (8d * scale) + ";");
        content.setPadding(new Insets(5d * scale));
        titleRow.setSpacing(8d * scale);
        titleRow.setPadding(new Insets(0, 18d * scale, 0, 0));
        transportRow.setSpacing(5d * scale);
        volumeControls.setSpacing(2d * scale);
        volumeRow.setSpacing(5d * scale);
        trackVolumeRow.setSpacing(5d * scale);
        progressTimeRow.setSpacing(1d * scale);
        body.setSpacing(2d * scale);
        queueNameLabel.setStyle("-fx-font-size: " + (14d * scale) + "px; -fx-font-weight: bold;");
        titleViewport.setMinHeight(22d * scale);
        titleViewport.setPrefHeight(22d * scale);
        queueNameField.setStyle("-fx-font-size: " + (12d * scale) + "px;");
        queueNameField.setMaxHeight(26d * scale);
        previousButton.setStyle(buttonStyle(11d * scale, 3d * scale, 8d * scale));
        playPauseButton.setStyle(buttonStyle(11d * scale, 3d * scale, 8d * scale));
        stopButton.setStyle(buttonStyle(11d * scale, 3d * scale, 8d * scale));
        nextButton.setStyle(buttonStyle(11d * scale, 3d * scale, 8d * scale));
        shuffleButton.setStyle(buttonStyle(11d * scale, 3d * scale, 8d * scale));
        removeQueueButton.setStyle(buttonStyle(11d * scale, 3d * scale, 8d * scale));
        loopTrackButton.setStyle(buttonStyle(11d * scale, 2d * scale, 7d * scale));
        loopQueueButton.setStyle(buttonStyle(11d * scale, 2d * scale, 7d * scale));
        volumeLabel.setMinWidth(82d * scale);
        volumeLabel.setPrefWidth(82d * scale);
        trackVolumeLabel.setMinWidth(82d * scale);
        trackVolumeLabel.setPrefWidth(82d * scale);
        volumeSlider.setMinWidth(136d * scale);
        volumeSlider.setPrefWidth(136d * scale);
        trackVolumeSlider.setMinWidth(136d * scale);
        trackVolumeSlider.setPrefWidth(136d * scale);
        currentTimeLabel.setStyle("-fx-font-size: " + (11d * scale) + "px;");
        totalTimeLabel.setStyle("-fx-font-size: " + (11d * scale) + "px;");
        chipContainer.setPadding(new Insets(0));
        chipContainer.setHgap(4d * scale);
        chipContainer.setVgap(4d * scale);
        chipScrollPane.setMinViewportHeight(62d * scale);
        chipScrollPane.setPrefViewportHeight(92d * scale);
        waveformSeekView.setMinHeight(baseWaveformHeight * scale);
        waveformSeekView.setPrefHeight(baseWaveformHeight * scale);
        waveformSeekView.setMaxHeight(baseWaveformHeight * scale);
        statusIndicator.setRadius(5d * scale);
        StackPane.setMargin(statusIndicator, new Insets(6d * scale, 6d * scale, 0, 0));
        UiIcons.resize(previousGraphic, scale);
        UiIcons.resize(playGraphic, scale);
        UiIcons.resize(pauseGraphic, scale);
        UiIcons.resize(stopGraphic, scale);
        UiIcons.resize(nextGraphic, scale);
        UiIcons.resize(loopTrackGraphic, scale);
        UiIcons.resize(loopQueueGraphic, scale);
        updateInsertionMarkerScale(leftInsertionMarker, scale);
        updateInsertionMarkerScale(rightInsertionMarker, scale);
        updateTitleControlWidth();
        updateChipWrapLength();
        for (var node : chipContainer.getChildren()) {
            if (node instanceof QueueTrackChipView chipView) {
                chipView.updateScale(scale);
            }
        }
        updateTitleAnimation();
    }

    private void updateTitleControlWidth() {
        double maxWidth = Math.max(getWidth() - 40d * currentScale, 0d);
        titleViewport.setMaxWidth(maxWidth);
        queueNameField.setMaxWidth(maxWidth);
    }

    private void updateChipWrapLength() {
        double viewportWidth = chipScrollPane.getViewportBounds().getWidth();
        chipContainer.setPrefWrapLength(Math.max(viewportWidth - 8d * currentScale, 160d * currentScale));
    }

    private void updateInsertionMarkerScale(AnchorPane marker, double scale) {
        setRegionWidth(marker, 12d * scale);
        setRegionWidth((Region) marker.getChildren().get(0), 4d * scale);
        setRegionSize((Region) marker.getChildren().get(1), 10d * scale, 4d * scale);
        setRegionSize((Region) marker.getChildren().get(2), 10d * scale, 4d * scale);
    }

    private void setRegionWidth(Region region, double width) {
        region.setMinWidth(width);
        region.setPrefWidth(width);
        region.setMaxWidth(width);
    }

    private void setRegionSize(Region region, double width, double height) {
        setRegionWidth(region, width);
        region.setMinHeight(height);
        region.setPrefHeight(height);
        region.setMaxHeight(height);
    }

    private String buttonStyle(double fontSize, double verticalPadding, double horizontalPadding) {
        return "-fx-font-size: " + fontSize + "px; -fx-padding: "
                + verticalPadding + " " + horizontalPadding + " "
                + verticalPadding + " " + horizontalPadding + ";";
    }

    private void updateTitleAnimation() {
        titleClip.setWidth(Math.max(titleViewport.getWidth(), 0d));
        titleClip.setHeight(Math.max(titleViewport.getHeight(), 22d * currentScale));

        double overflow = queueNameLabel.getLayoutBounds().getWidth() - titleViewport.getWidth();
        if (editingQueueName || overflow <= 4d) {
            stopTitleAnimation();
            queueNameLabel.setTranslateX(0d);
            return;
        }

        if (titleAnimation != null && titleAnimation.getStatus() == Animation.Status.RUNNING) {
            return;
        }

        stopTitleAnimation();
        TranslateTransition moveLeft = new TranslateTransition(Duration.seconds(Math.max(overflow / 35d, 2.5d)), queueNameLabel);
        moveLeft.setFromX(0d);
        moveLeft.setToX(-overflow);

        TranslateTransition moveRight = new TranslateTransition(Duration.seconds(1.2d), queueNameLabel);
        moveRight.setFromX(-overflow);
        moveRight.setToX(0d);

        titleAnimation = new SequentialTransition(
                new PauseTransition(Duration.seconds(1.0d)),
                moveLeft,
                new PauseTransition(Duration.seconds(0.8d)),
                moveRight,
                new PauseTransition(Duration.seconds(0.8d))
        );
        titleAnimation.setCycleCount(Timeline.INDEFINITE);
        titleAnimation.playFromStart();
    }

    private void stopTitleAnimation() {
        if (titleAnimation != null) {
            titleAnimation.stop();
            titleAnimation = null;
        }
    }

    @FunctionalInterface
    public interface QueueAudioDropHandler {
        void addAudioFiles(List<UUID> audioFileIds, UUID targetQueueTrackId, boolean placeAfter);
    }

    @FunctionalInterface
    public interface QueueWorkspaceTrackDropHandler {
        void addWorkspaceTrack(UUID workspaceTrackId, UUID targetQueueTrackId, boolean placeAfter);
    }

    @FunctionalInterface
    public interface QueueTrackMoveHandler {
        void moveQueueTrack(UUID sourceQueueId, UUID queueTrackId, UUID targetQueueTrackId, boolean placeAfter);
    }

    public interface VirtualTrackDropHandler {
        void add(UUID tileId, UUID trackId, UUID targetQueueTrackId, boolean placeAfter);
    }

    @FunctionalInterface
    public interface WorkspaceQueueMoveHandler {
        void moveWorkspaceQueue(UUID workspaceQueueId, UUID targetWorkspaceQueueId, boolean placeAfter);
    }

    private record QueueTrackPayload(UUID queueId, UUID queueTrackId) {
    }
}
