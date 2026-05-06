package app.ui.tile;

import app.config.UiConfig;
import app.model.PlaybackStatus;
import app.ui.UiIcons;
import app.ui.waveform.WaveformSeekView;
import app.ui.workspace.WorkspaceInsertionMarker;
import app.ui.workspace.WorkspaceItemView;
import app.ui.workspace.WorkspaceTrackItem;
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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
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
import java.util.Objects;
import java.util.UUID;

public class TrackTileView extends StackPane implements WorkspaceItemView {
    private static final String BASE_STYLE = "-fx-background-color: #f5f7fb; -fx-border-color: #c9d1e3; -fx-border-radius: 8; -fx-background-radius: 8;";
    private static final String LOOP_BUTTON_STYLE = "-fx-font-size: 14px; -fx-padding: 2 8 2 8;";
    private static final String MUTE_BUTTON_STYLE = "-fx-font-size: 13px; -fx-padding: 2 8 2 8;";
    private static final String INSERT_SEGMENT_STYLE = "-fx-background-color: #4a83d8;";
    private final WorkspaceTrackItem workspaceTrackItem;
    private final WaveformService waveformService;
    private final Runnable removeAction;
    private final Runnable persistenceChangeAction;
    private final Timeline refreshTimeline;
    private final BorderPane contentPane = new BorderPane();
    private final AnchorPane leftInsertionMarker = createInsertionMarker(true);
    private final AnchorPane rightInsertionMarker = createInsertionMarker(false);
    private final Pane titleViewport = new Pane();
    private final Rectangle titleClip = new Rectangle();
    private final Label titleLabel = new Label();
    private final Circle statusIndicator = new Circle(5d, Color.web("#97a3b6"));

    private final Button playPauseButton = new Button(UiIcons.PLAY);
    private final Button stopButton = new Button(UiIcons.STOP);
    private final Button removeButton = new Button("Remove");
    private final ToggleButton loopButton = new ToggleButton(UiIcons.LOOP);
    private final ToggleButton muteButton = new ToggleButton(UiIcons.UNMUTED);
    private final Slider volumeSlider = new Slider(0d, 100d, 80d);
    private final WaveformSeekView waveformSeekView = new WaveformSeekView();
    private final Label currentTimeLabel = new Label("00:00");
    private final Label totalTimeLabel = new Label("00:00");

    private double currentScale = 1d;
    private final double baseWaveformHeight;
    private Path currentWaveformPath;
    private long waveformRequestGeneration;
    private SequentialTransition titleAnimation;

    public TrackTileView(
            UiConfig uiConfig,
            int progressRefreshMillis,
            WorkspaceTrackItem workspaceTrackItem,
            WaveformService waveformService,
            Runnable removeAction,
            Runnable persistenceChangeAction
    ) {
        this.workspaceTrackItem = workspaceTrackItem;
        this.waveformService = waveformService;
        this.removeAction = removeAction;
        this.persistenceChangeAction = persistenceChangeAction;
        this.baseWaveformHeight = uiConfig.getWaveformHeight();
        this.refreshTimeline = new Timeline(new KeyFrame(Duration.millis(progressRefreshMillis), event -> refresh()));

        configureLayout(uiConfig);
        configureActions();
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

    public void updateTileSize(double tileWidth, double tileHeight, double tileScale) {
        setMinWidth(tileWidth);
        setPrefWidth(tileWidth);
        setMaxWidth(tileWidth);
        setMinHeight(tileHeight);
        setPrefHeight(tileHeight);
        setMaxHeight(tileHeight);
        applyScale(tileScale);
    }

    private void configureLayout(UiConfig uiConfig) {
        updateTileSize(uiConfig.getTrackTileWidth(), uiConfig.getTrackTileHeight(), 1d);
        setStyle(BASE_STYLE);

        titleLabel.setText(workspaceTrackItem.getAudioFile().getDisplayName());
        titleLabel.setWrapText(false);
        titleViewport.setClip(titleClip);
        titleViewport.getChildren().add(titleLabel);
        titleViewport.widthProperty().addListener((observable, oldValue, newValue) -> updateTitleAnimation());
        titleViewport.heightProperty().addListener((observable, oldValue, newValue) -> updateTitleAnimation());
        titleLabel.layoutBoundsProperty().addListener((observable, oldValue, newValue) -> updateTitleAnimation());

        HBox titleRow = new HBox(8, titleViewport);
        titleRow.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(titleViewport, Priority.ALWAYS);

        HBox controlsRow = new HBox(8, playPauseButton, stopButton, removeButton);
        controlsRow.setAlignment(Pos.CENTER_LEFT);
        configureActionButton(playPauseButton);
        configureActionButton(stopButton);
        configureActionButton(removeButton);

        playPauseButton.setTooltip(new Tooltip("Play / Pause"));
        stopButton.setTooltip(new Tooltip("Stop"));
        loopButton.setTooltip(new Tooltip("Loop"));
        loopButton.setStyle(LOOP_BUTTON_STYLE);
        muteButton.setTooltip(new Tooltip("Mute"));
        muteButton.setStyle(MUTE_BUTTON_STYLE);
        volumeSlider.setValue(workspaceTrackItem.getWorkspaceTrack().getVolume() * 100d);
        HBox detailsRow = new HBox(8, loopButton, muteButton, volumeSlider);
        detailsRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(volumeSlider, Priority.ALWAYS);

        HBox progressTimeRow = new HBox(8, currentTimeLabel, createSpacer(), totalTimeLabel);
        progressTimeRow.setAlignment(Pos.CENTER_LEFT);

        HBox progressRow = new HBox(waveformSeekView);
        progressRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(waveformSeekView, Priority.ALWAYS);

        VBox content = new VBox(8, titleRow, controlsRow, detailsRow, progressTimeRow, progressRow);
        content.setFillWidth(true);
        contentPane.setCenter(content);

        HBox insertionMarkers = new HBox(createMarkerHolder(leftInsertionMarker), createSpacer(), createMarkerHolder(rightInsertionMarker));
        insertionMarkers.setMouseTransparent(true);
        insertionMarkers.setAlignment(Pos.CENTER);

        StackPane.setAlignment(statusIndicator, Pos.TOP_RIGHT);
        getChildren().setAll(contentPane, insertionMarkers, statusIndicator);
        waveformSeekView.setWaveformData(WaveformData.empty());
        updateTitleAnimation();
    }

    private void configureActions() {
        playPauseButton.setOnAction(event -> {
            workspaceTrackItem.togglePlayPause();
            refresh();
        });

        stopButton.setOnAction(event -> {
            workspaceTrackItem.stop();
            refresh();
        });

        removeButton.setOnAction(event -> removeAction.run());

        loopButton.setSelected(workspaceTrackItem.getWorkspaceTrack().isLoop());
        loopButton.selectedProperty().addListener((observable, oldValue, newValue) -> {
            workspaceTrackItem.setLoop(newValue);
            persistenceChangeAction.run();
        });

        muteButton.setSelected(workspaceTrackItem.isMuted());
        muteButton.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (workspaceTrackItem.isMuted() != newValue) {
                workspaceTrackItem.toggleMuted();
            }
            refreshMuteButton();
        });

        volumeSlider.valueChangingProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) {
                persistenceChangeAction.run();
            }
        });
        volumeSlider.setOnMouseReleased(event -> persistenceChangeAction.run());
        volumeSlider.valueProperty().addListener((observable, oldValue, newValue) ->
                workspaceTrackItem.setVolume(newValue.doubleValue() / 100d)
        );
        waveformSeekView.setSeekHandler(position -> {
            workspaceTrackItem.seek(position);
            refresh();
        });
    }

    private void refresh() {
        boolean missing = workspaceTrackItem.isMissing();
        PlaybackStatus playbackStatus = workspaceTrackItem.getStatus();
        Duration currentTime = workspaceTrackItem.getCurrentTime();
        Duration totalTime = workspaceTrackItem.getTotalDuration();
        refreshWaveformSource();

        playPauseButton.setDisable(missing || playbackStatus == PlaybackStatus.ERROR);
        stopButton.setDisable(missing || playbackStatus == PlaybackStatus.ERROR);
        loopButton.setDisable(missing || playbackStatus == PlaybackStatus.ERROR);
        volumeSlider.setDisable(missing || playbackStatus == PlaybackStatus.ERROR);
        waveformSeekView.setDisable(missing || playbackStatus == PlaybackStatus.ERROR);
        muteButton.setDisable(missing || playbackStatus == PlaybackStatus.ERROR);

        playPauseButton.setText(playbackStatus == PlaybackStatus.PLAYING ? UiIcons.PAUSE : UiIcons.PLAY);
        refreshMuteButton();
        statusIndicator.setFill(resolveStatusColor(missing, playbackStatus));
        waveformSeekView.setPlaybackPosition(currentTime, totalTime);
        currentTimeLabel.setText(formatDuration(waveformSeekView.getDisplayedPosition()));
        totalTimeLabel.setText(formatDuration(totalTime));
    }

    public UUID getWorkspaceTrackId() {
        return workspaceTrackItem.getWorkspaceTrack().getId();
    }

    @Override
    public UUID getWorkspaceItemId() {
        return getWorkspaceTrackId();
    }

    @Override
    public void setInsertionMarker(WorkspaceInsertionMarker insertionMarker) {
        leftInsertionMarker.setVisible(insertionMarker == WorkspaceInsertionMarker.LEFT);
        rightInsertionMarker.setVisible(insertionMarker == WorkspaceInsertionMarker.RIGHT);
    }

    private void refreshWaveformSource() {
        Path nextWaveformPath = workspaceTrackItem.getAudioPath().orElse(null);
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

    private String formatDuration(Duration duration) {
        long totalSeconds = (long) Math.max(duration.toSeconds(), 0d);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void configureActionButton(Button button) {
        button.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(button, Priority.ALWAYS);
    }

    private Region createSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
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

    private HBox createMarkerHolder(AnchorPane marker) {
        HBox holder = new HBox(marker);
        holder.setAlignment(Pos.CENTER);
        holder.setFillHeight(true);
        HBox.setHgrow(holder, Priority.NEVER);
        VBox.setVgrow(holder, Priority.ALWAYS);
        return holder;
    }

    private Region createMarkerSegment(double width, double height) {
        Region region = new Region();
        region.setStyle(INSERT_SEGMENT_STYLE);
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

    private void refreshMuteButton() {
        boolean muted = workspaceTrackItem.isMuted();
        muteButton.setSelected(muted);
        muteButton.setText(muted ? UiIcons.MUTED : UiIcons.UNMUTED);
    }

    private void applyScale(double tileScale) {
        currentScale = tileScale;
        contentPane.setPadding(new Insets(12d * tileScale));
        titleLabel.setStyle("-fx-font-size: " + (14d * tileScale) + "px; -fx-font-weight: bold;");
        titleViewport.setMinHeight(22d * tileScale);
        titleViewport.setPrefHeight(22d * tileScale);
        playPauseButton.setStyle("-fx-font-size: " + (12d * tileScale) + "px;");
        stopButton.setStyle("-fx-font-size: " + (12d * tileScale) + "px;");
        removeButton.setStyle("-fx-font-size: " + (12d * tileScale) + "px;");
        loopButton.setStyle("-fx-font-size: " + (14d * tileScale) + "px; -fx-padding: " + (2d * tileScale) + " " + (8d * tileScale) + " " + (2d * tileScale) + " " + (8d * tileScale) + ";");
        muteButton.setStyle("-fx-font-size: " + (13d * tileScale) + "px; -fx-padding: " + (2d * tileScale) + " " + (8d * tileScale) + " " + (2d * tileScale) + " " + (8d * tileScale) + ";");
        currentTimeLabel.setStyle("-fx-font-size: " + (12d * tileScale) + "px;");
        totalTimeLabel.setStyle("-fx-font-size: " + (12d * tileScale) + "px;");
        waveformSeekView.setMinHeight(baseWaveformHeight * tileScale);
        waveformSeekView.setPrefHeight(baseWaveformHeight * tileScale);
        waveformSeekView.setMaxHeight(baseWaveformHeight * tileScale);
        statusIndicator.setRadius(5d * tileScale);
        StackPane.setMargin(statusIndicator, new Insets(4d * tileScale, 4d * tileScale, 0, 0));
        updateTitleAnimation();
    }

    private Color resolveStatusColor(boolean missing, PlaybackStatus playbackStatus) {
        if (missing || playbackStatus == PlaybackStatus.ERROR) {
            return Color.web("#a94442");
        }

        return switch (playbackStatus) {
            case PLAYING -> Color.web("#2f9e44");
            case PAUSED -> Color.web("#f0b429");
            case FINISHED -> Color.web("#d64545");
            default -> Color.web("#97a3b6");
        };
    }

    private void updateTitleAnimation() {
        titleClip.setWidth(Math.max(titleViewport.getWidth(), 0d));
        titleClip.setHeight(Math.max(titleViewport.getHeight(), 22d * currentScale));

        double overflow = titleLabel.getLayoutBounds().getWidth() - titleViewport.getWidth();
        if (overflow <= 4d) {
            stopTitleAnimation();
            titleLabel.setTranslateX(0d);
            return;
        }

        if (titleAnimation != null && titleAnimation.getStatus() == Animation.Status.RUNNING) {
            return;
        }

        stopTitleAnimation();
        TranslateTransition moveLeft = new TranslateTransition(Duration.seconds(Math.max(overflow / 35d, 2.5d)), titleLabel);
        moveLeft.setFromX(0d);
        moveLeft.setToX(-overflow);

        TranslateTransition moveRight = new TranslateTransition(Duration.seconds(1.2d), titleLabel);
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
}
