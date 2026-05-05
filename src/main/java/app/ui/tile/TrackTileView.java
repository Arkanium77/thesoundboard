package app.ui.tile;

import app.config.UiConfig;
import app.model.PlaybackStatus;
import app.ui.workspace.WorkspaceTrackItem;
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

import java.util.UUID;

public class TrackTileView extends StackPane {
    private static final String BASE_STYLE = "-fx-background-color: #f5f7fb; -fx-border-color: #c9d1e3; -fx-border-radius: 8; -fx-background-radius: 8;";
    private static final String LOOP_BUTTON_STYLE = "-fx-font-size: 14px; -fx-padding: 2 8 2 8;";
    private static final String MUTE_BUTTON_STYLE = "-fx-font-size: 13px; -fx-padding: 2 8 2 8;";
    private static final String INSERT_SEGMENT_STYLE = "-fx-background-color: #4a83d8;";

    private final WorkspaceTrackItem workspaceTrackItem;
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

    private final Button playPauseButton = new Button("Play");
    private final Button stopButton = new Button("Stop");
    private final Button removeButton = new Button("Remove");
    private final ToggleButton loopButton = new ToggleButton("↻");
    private final ToggleButton muteButton = new ToggleButton("🔊");
    private final Slider volumeSlider = new Slider(0d, 100d, 80d);
    private final Slider progressSlider = new Slider(0d, 1d, 0d);
    private final Label currentTimeLabel = new Label("00:00");
    private final Label totalTimeLabel = new Label("00:00");

    private boolean seeking;
    private SequentialTransition titleAnimation;

    public TrackTileView(
            UiConfig uiConfig,
            int progressRefreshMillis,
            WorkspaceTrackItem workspaceTrackItem,
            Runnable removeAction,
            Runnable persistenceChangeAction
    ) {
        this.workspaceTrackItem = workspaceTrackItem;
        this.removeAction = removeAction;
        this.persistenceChangeAction = persistenceChangeAction;
        this.refreshTimeline = new Timeline(new KeyFrame(Duration.millis(progressRefreshMillis), event -> refresh()));

        configureLayout(uiConfig);
        configureActions();
        refresh();

        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    public void dispose() {
        refreshTimeline.stop();
        stopTitleAnimation();
    }

    private void configureLayout(UiConfig uiConfig) {
        setPadding(new Insets(12));
        setMinWidth(uiConfig.getTrackTileWidth());
        setPrefWidth(uiConfig.getTrackTileWidth());
        setMaxWidth(uiConfig.getTrackTileWidth());
        setMinHeight(uiConfig.getTrackTileHeight());
        setPrefHeight(uiConfig.getTrackTileHeight());
        setMaxHeight(uiConfig.getTrackTileHeight());
        setStyle(BASE_STYLE);
        contentPane.setPadding(new Insets(12));

        titleLabel.setText(workspaceTrackItem.getAudioFile().getDisplayName());
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        titleLabel.setWrapText(false);
        titleViewport.setMinHeight(22d);
        titleViewport.setPrefHeight(22d);
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

        HBox progressRow = new HBox(progressSlider);
        progressRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(progressSlider, Priority.ALWAYS);

        VBox content = new VBox(8, titleRow, controlsRow, detailsRow, progressTimeRow, progressRow);
        content.setFillWidth(true);
        contentPane.setCenter(content);

        HBox insertionMarkers = new HBox(createMarkerHolder(leftInsertionMarker), createSpacer(), createMarkerHolder(rightInsertionMarker));
        insertionMarkers.setMouseTransparent(true);
        insertionMarkers.setAlignment(Pos.CENTER);

        StackPane.setAlignment(statusIndicator, Pos.TOP_RIGHT);
        StackPane.setMargin(statusIndicator, new Insets(8, 8, 0, 0));
        getChildren().setAll(contentPane, insertionMarkers, statusIndicator);
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

        progressSlider.valueChangingProperty().addListener((observable, oldValue, newValue) -> {
            seeking = newValue;
            if (!newValue) {
                seekToSliderValue();
            }
        });
        progressSlider.setOnMousePressed(event -> seeking = true);
        progressSlider.setOnMouseReleased(event -> {
            seeking = false;
            seekToSliderValue();
        });
    }

    private void seekToSliderValue() {
        workspaceTrackItem.seek(Duration.millis(progressSlider.getValue()));
        refresh();
    }

    private void refresh() {
        boolean missing = workspaceTrackItem.isMissing();
        PlaybackStatus playbackStatus = workspaceTrackItem.getStatus();
        Duration currentTime = workspaceTrackItem.getCurrentTime();
        Duration totalTime = workspaceTrackItem.getTotalDuration();

        playPauseButton.setDisable(missing || playbackStatus == PlaybackStatus.ERROR);
        stopButton.setDisable(missing || playbackStatus == PlaybackStatus.ERROR);
        loopButton.setDisable(missing || playbackStatus == PlaybackStatus.ERROR);
        volumeSlider.setDisable(missing || playbackStatus == PlaybackStatus.ERROR);
        progressSlider.setDisable(missing || playbackStatus == PlaybackStatus.ERROR);
        muteButton.setDisable(missing || playbackStatus == PlaybackStatus.ERROR);

        playPauseButton.setText(playbackStatus == PlaybackStatus.PLAYING ? "Pause" : "Play");
        refreshMuteButton();
        statusIndicator.setFill(resolveStatusColor(missing, playbackStatus));

        double totalMillis = Math.max(totalTime.toMillis(), 1d);
        progressSlider.setMax(totalMillis);
        if (!seeking) {
            progressSlider.setValue(Math.min(currentTime.toMillis(), totalMillis));
        }

        currentTimeLabel.setText(formatDuration(currentTime));
        totalTimeLabel.setText(formatDuration(totalTime));
    }

    public UUID getWorkspaceTrackId() {
        return workspaceTrackItem.getWorkspaceTrack().getId();
    }

    public void setInsertionMarker(InsertionMarker insertionMarker) {
        leftInsertionMarker.setVisible(insertionMarker == InsertionMarker.LEFT);
        rightInsertionMarker.setVisible(insertionMarker == InsertionMarker.RIGHT);
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
        muteButton.setText(muted ? "🔇" : "🔊");
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
        titleClip.setHeight(Math.max(titleViewport.getHeight(), 22d));

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

    public enum InsertionMarker {
        NONE,
        LEFT,
        RIGHT
    }
}
