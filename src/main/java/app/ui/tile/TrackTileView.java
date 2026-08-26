package app.ui.tile;

import app.config.UiConfig;
import app.localization.TextKey;
import app.localization.Texts;
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
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
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
    private static final Color ERROR_COLOR = Color.web("#a94442");
    private static final Color PLAYING_COLOR = Color.web("#2f9e44");
    private static final Color PAUSED_COLOR = Color.web("#f0b429");
    private static final Color FINISHED_COLOR = Color.web("#d64545");
    private static final Color READY_COLOR = Color.web("#97a3b6");
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
    private final Circle statusIndicator = new Circle(5d, READY_COLOR);

    private final Node playGraphic = UiIcons.play();
    private final Node pauseGraphic = UiIcons.pause();
    private final Node stopGraphic = UiIcons.stop();
    private final Node loopGraphic = UiIcons.loop();
    private final Node mutedGraphic = UiIcons.muted();
    private final Node unmutedGraphic = UiIcons.unmuted();
    private final Button playPauseButton = new Button(null, playGraphic);
    private final Button stopButton = new Button(null, stopGraphic);
    private final Button removeButton = new Button(Texts.text(TextKey.TRACK_REMOVE));
    private final ToggleButton loopButton = new ToggleButton(null, loopGraphic);
    private final ToggleButton muteButton = new ToggleButton(null, unmutedGraphic);
    private final Slider volumeSlider = new Slider(0d, 100d, 80d);
    private final WaveformSeekView waveformSeekView = new WaveformSeekView();
    private final Label currentTimeLabel = new Label("00:00");
    private final Label totalTimeLabel = new Label("00:00");
    private final HBox titleRow = new HBox();
    private final HBox controlsRow = new HBox();
    private final HBox detailsRow = new HBox();
    private final HBox progressTimeRow = new HBox();
    private final HBox progressRow = new HBox();
    private final VBox content = new VBox();

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
        getStyleClass().add("track-tile");
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

    public void refreshLocalization() {
        removeButton.setText(Texts.text(TextKey.TRACK_REMOVE));
        playPauseButton.setTooltip(new Tooltip(Texts.text(TextKey.TOOLTIP_PLAY_PAUSE)));
        stopButton.setTooltip(new Tooltip(Texts.text(TextKey.TOOLTIP_STOP)));
        loopButton.setTooltip(new Tooltip(Texts.text(TextKey.TOOLTIP_LOOP)));
        muteButton.setTooltip(new Tooltip(Texts.text(TextKey.TOOLTIP_MUTE)));
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

        titleLabel.setText(workspaceTrackItem.getAudioFile().getDisplayName());
        titleLabel.setWrapText(false);
        titleViewport.setClip(titleClip);
        titleViewport.getChildren().add(titleLabel);
        titleViewport.widthProperty().addListener((observable, oldValue, newValue) -> updateTitleAnimation());
        titleViewport.heightProperty().addListener((observable, oldValue, newValue) -> updateTitleAnimation());
        titleLabel.layoutBoundsProperty().addListener((observable, oldValue, newValue) -> updateTitleAnimation());

        titleRow.getChildren().setAll(titleViewport);
        titleRow.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(titleViewport, Priority.ALWAYS);

        controlsRow.getChildren().setAll(playPauseButton, stopButton, removeButton);
        controlsRow.setAlignment(Pos.CENTER_LEFT);
        configureActionButton(playPauseButton);
        configureActionButton(stopButton);
        configureActionButton(removeButton);

        playPauseButton.setTooltip(new Tooltip(Texts.text(TextKey.TOOLTIP_PLAY_PAUSE)));
        stopButton.setTooltip(new Tooltip(Texts.text(TextKey.TOOLTIP_STOP)));
        loopButton.setTooltip(new Tooltip(Texts.text(TextKey.TOOLTIP_LOOP)));
        muteButton.setTooltip(new Tooltip(Texts.text(TextKey.TOOLTIP_MUTE)));
        volumeSlider.setValue(workspaceTrackItem.getWorkspaceTrack().getVolume() * 100d);
        detailsRow.getChildren().setAll(loopButton, muteButton, volumeSlider);
        detailsRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(volumeSlider, Priority.ALWAYS);

        progressTimeRow.getChildren().setAll(currentTimeLabel, createSpacer(), totalTimeLabel);
        progressTimeRow.setAlignment(Pos.CENTER_LEFT);

        progressRow.getChildren().setAll(waveformSeekView);
        progressRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(waveformSeekView, Priority.ALWAYS);

        content.getChildren().setAll(titleRow, controlsRow, detailsRow, progressTimeRow, progressRow);
        content.setFillWidth(true);
        contentPane.setCenter(content);

        HBox insertionMarkers = new HBox(createMarkerHolder(leftInsertionMarker), createSpacer(), createMarkerHolder(rightInsertionMarker));
        insertionMarkers.setMouseTransparent(true);
        insertionMarkers.setAlignment(Pos.CENTER);

        StackPane.setAlignment(statusIndicator, Pos.TOP_RIGHT);
        getChildren().setAll(contentPane, insertionMarkers, statusIndicator);
        waveformSeekView.setWaveformData(WaveformData.empty());
        applyScale(1d);
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

        setGraphicIfChanged(playPauseButton, playbackStatus == PlaybackStatus.PLAYING ? pauseGraphic : playGraphic);
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

    private void refreshMuteButton() {
        boolean muted = workspaceTrackItem.isMuted();
        muteButton.setSelected(muted);
        setGraphicIfChanged(muteButton, muted ? mutedGraphic : unmutedGraphic);
    }

    private void applyScale(double tileScale) {
        currentScale = tileScale;
        setStyle("-fx-border-radius: " + (8d * tileScale)
                + "; -fx-background-radius: " + (8d * tileScale) + ";");
        contentPane.setPadding(new Insets(12d * tileScale));
        titleRow.setSpacing(8d * tileScale);
        controlsRow.setSpacing(8d * tileScale);
        detailsRow.setSpacing(8d * tileScale);
        progressTimeRow.setSpacing(8d * tileScale);
        content.setSpacing(8d * tileScale);
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
        UiIcons.resize(playGraphic, tileScale);
        UiIcons.resize(pauseGraphic, tileScale);
        UiIcons.resize(stopGraphic, tileScale);
        UiIcons.resize(loopGraphic, tileScale);
        UiIcons.resize(mutedGraphic, tileScale);
        UiIcons.resize(unmutedGraphic, tileScale);
        updateInsertionMarkerScale(leftInsertionMarker, tileScale);
        updateInsertionMarkerScale(rightInsertionMarker, tileScale);
        updateTitleAnimation();
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

    private void setGraphicIfChanged(Labeled control, Node graphic) {
        if (control.getGraphic() != graphic) {
            control.setGraphic(graphic);
        }
    }

    private Color resolveStatusColor(boolean missing, PlaybackStatus playbackStatus) {
        if (missing || playbackStatus == PlaybackStatus.ERROR) {
            return ERROR_COLOR;
        }

        return switch (playbackStatus) {
            case PLAYING -> PLAYING_COLOR;
            case PAUSED -> PAUSED_COLOR;
            case FINISHED -> FINISHED_COLOR;
            default -> READY_COLOR;
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
