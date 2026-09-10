package app.ui.tile;

import app.ui.InsertionMarkers;
import app.ui.TitleScrollAnimation;

import app.config.UiConfig;
import app.localization.TextKey;
import app.localization.Texts;
import app.model.PlaybackStatus;
import app.ui.UiIcons;
import app.ui.DurationText;
import app.ui.UiViewport;
import app.ui.VolumeSliderSupport;
import app.ui.waveform.WaveformSeekView;
import app.ui.waveform.WaveformSubscription;
import app.ui.workspace.WorkspaceInsertionMarker;
import app.ui.workspace.WorkspaceItemView;
import app.ui.workspace.WorkspaceTrackItem;
import app.waveform.WaveformData;
import app.waveform.WaveformService;
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

import java.util.UUID;

public class TrackTileView extends StackPane implements WorkspaceItemView {
    private static final Color ERROR_COLOR = Color.web("#a94442");
    private static final Color PLAYING_COLOR = Color.web("#2f9e44");
    private static final Color PAUSED_COLOR = Color.web("#f0b429");
    private static final Color FINISHED_COLOR = Color.web("#d64545");
    private static final Color READY_COLOR = Color.web("#97a3b6");
    private final WorkspaceTrackItem workspaceTrackItem;
    private final Runnable removeAction;
    private final Runnable persistenceChangeAction;
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
    private final WaveformSubscription waveformSubscription;
    private final TitleScrollAnimation titleAnimation = new TitleScrollAnimation(
            titleViewport, titleLabel, titleClip, TitleScrollAnimation.Style.TILE);
    private boolean titleHovered;

    public TrackTileView(
            UiConfig uiConfig,
            WorkspaceTrackItem workspaceTrackItem,
            WaveformService waveformService,
            Runnable removeAction,
            Runnable persistenceChangeAction
    ) {
        getStyleClass().add("track-tile");
        this.workspaceTrackItem = workspaceTrackItem;
        this.waveformSubscription = new WaveformSubscription(waveformService, waveformSeekView::setWaveformData);
        this.removeAction = removeAction;
        this.persistenceChangeAction = persistenceChangeAction;
        this.baseWaveformHeight = uiConfig.getWaveformHeight();
        configureLayout(uiConfig);
        configureActions();
        workspaceTrackItem.setOnPlaybackChanged(this::refresh);
        refresh();
    }

    public void dispose() {
        workspaceTrackItem.setOnPlaybackChanged(null);
        waveformSeekView.dispose();
        waveformSubscription.dispose();
        stopTitleAnimation();
    }

    /** The shared scheduler updates progress only; status and queue chips are refreshed by commands/events. */
    public void refreshPlayback() {
        if (workspaceTrackItem.getStatus() != PlaybackStatus.PLAYING) { waveformSeekView.setPlaying(false); return; }
        boolean visible = UiViewport.isVisible(this);
        waveformSeekView.setPlaying(visible && workspaceTrackItem.getStatus() == PlaybackStatus.PLAYING);
        if (!visible) { stopTitleAnimation(); return; }
        if (workspaceTrackItem.getStatus() == PlaybackStatus.PLAYING) {
            waveformSeekView.setPlaybackPosition(workspaceTrackItem.getCurrentTime(), workspaceTrackItem.getTotalDuration());
            DurationText.update(currentTimeLabel, waveformSeekView.getDisplayedPosition());
            DurationText.update(totalTimeLabel, workspaceTrackItem.getTotalDuration());
        }
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
        titleViewport.setOnMouseEntered(event -> {
            titleHovered = true;
            updateTitleAnimation();
        });
        titleViewport.setOnMouseExited(event -> {
            titleHovered = false;
            stopTitleAnimation();
            titleLabel.setTranslateX(0d);
        });
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

        VolumeSliderSupport.bind(volumeSlider, workspaceTrackItem::setVolume, persistenceChangeAction);
        waveformSeekView.setSeekHandler(position -> {
            workspaceTrackItem.seek(position);
            refresh();
        });
    }

    private void refresh() {
        boolean missing = workspaceTrackItem.isMissing();
        PlaybackStatus playbackStatus = workspaceTrackItem.getStatus();
        waveformSeekView.setPlaying(playbackStatus == PlaybackStatus.PLAYING);
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
        DurationText.update(currentTimeLabel, waveformSeekView.getDisplayedPosition());
        DurationText.update(totalTimeLabel, totalTime);
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
        waveformSubscription.load(workspaceTrackItem.getAudioPath().orElse(null));
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
        return InsertionMarkers.create(leftSide, false);
    }

    private HBox createMarkerHolder(AnchorPane marker) {
        HBox holder = new HBox(marker);
        holder.setAlignment(Pos.CENTER);
        holder.setFillHeight(true);
        HBox.setHgrow(holder, Priority.NEVER);
        VBox.setVgrow(holder, Priority.ALWAYS);
        return holder;
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
        InsertionMarkers.scale(marker, scale, false);
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

    private void updateTitleAnimation() { titleAnimation.update(titleHovered, 22d * currentScale); }

    private void stopTitleAnimation() { titleAnimation.stop(); }
}
