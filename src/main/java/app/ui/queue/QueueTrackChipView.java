package app.ui.queue;

import app.model.AudioFile;
import app.model.PlaybackStatus;
import app.model.QueueTrack;
import app.ui.workspace.WorkspaceInsertionMarker;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.function.Consumer;

public class QueueTrackChipView extends StackPane {
    private static final String INSERT_SEGMENT_STYLE = "-fx-background-color: #4a83d8;";

    private final QueueTrack queueTrack;
    private final Pane titleViewport = new Pane();
    private final Rectangle titleClip = new Rectangle();
    private final Label titleLabel = new Label();
    private final AnchorPane leftInsertionMarker = createInsertionMarker(true);
    private final AnchorPane rightInsertionMarker = createInsertionMarker(false);
    private double currentScale = 1d;
    private SequentialTransition titleAnimation;

    public QueueTrackChipView(
            QueueTrack queueTrack,
            AudioFile audioFile,
            Runnable selectAction,
            Runnable playAction,
            Consumer<QueueTrack> removeAction
    ) {
        this.queueTrack = queueTrack;
        titleLabel.setText(audioFile == null ? "Missing" : audioFile.getDisplayName());
        titleLabel.setWrapText(false);
        titleViewport.setClip(titleClip);
        titleViewport.getChildren().add(titleLabel);
        titleViewport.widthProperty().addListener((observable, oldValue, newValue) -> updateTitleAnimation());
        titleViewport.heightProperty().addListener((observable, oldValue, newValue) -> updateTitleAnimation());
        titleLabel.layoutBoundsProperty().addListener((observable, oldValue, newValue) -> updateTitleAnimation());

        setAlignment(Pos.CENTER_LEFT);
        HBox insertionMarkers = new HBox(createMarkerHolder(leftInsertionMarker), createSpacer(), createMarkerHolder(rightInsertionMarker));
        insertionMarkers.setMouseTransparent(true);
        insertionMarkers.setAlignment(Pos.CENTER);
        getChildren().setAll(titleViewport, insertionMarkers);

        setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                playAction.run();
            } else {
                selectAction.run();
            }
        });

        MenuItem removeItem = new MenuItem("Remove From Queue");
        removeItem.setOnAction(event -> removeAction.accept(queueTrack));
        ContextMenu contextMenu = new ContextMenu(removeItem);
        setOnContextMenuRequested(event -> contextMenu.show(this, event.getScreenX(), event.getScreenY()));
        updateScale(1d);
        updateTitleAnimation();
    }

    public QueueTrack getQueueTrack() {
        return queueTrack;
    }

    public void setInsertionMarker(WorkspaceInsertionMarker insertionMarker) {
        leftInsertionMarker.setVisible(insertionMarker == WorkspaceInsertionMarker.LEFT);
        rightInsertionMarker.setVisible(insertionMarker == WorkspaceInsertionMarker.RIGHT);
    }

    public void updateScale(double scale) {
        currentScale = scale;
        titleLabel.setStyle("-fx-font-size: " + (10d * scale) + "px; -fx-font-weight: bold;");
        titleViewport.setMinHeight(14d * scale);
        titleViewport.setPrefHeight(14d * scale);
        setPadding(new Insets(3d * scale, 5d * scale, 3d * scale, 5d * scale));
        setMinWidth(96d * scale);
        setPrefWidth(96d * scale);
        setMaxWidth(96d * scale);
        setMinHeight(26d * scale);
        setPrefHeight(26d * scale);
        setMaxHeight(26d * scale);
    }

    public void refresh(boolean selected, PlaybackStatus playbackStatus, boolean activeTrack) {
        String borderColor = selected ? "#4a83d8" : "#c9d1e3";
        String backgroundColor = "#f5f7fb";
        if (activeTrack) {
            backgroundColor = switch (playbackStatus) {
                case PLAYING -> "#e8f7ec";
                case PAUSED -> "#fff5d9";
                case FINISHED, ERROR -> "#fdeaea";
                default -> "#eef6ff";
            };
            borderColor = switch (playbackStatus) {
                case PLAYING -> "#2f9e44";
                case PAUSED -> "#f0b429";
                case FINISHED, ERROR -> "#d64545";
                default -> "#4a83d8";
            };
        }
        setStyle(
                "-fx-background-color: " + backgroundColor + ";" +
                " -fx-border-color: " + borderColor + ";" +
                " -fx-border-width: " + (activeTrack ? "2" : "1") + ";" +
                " -fx-border-radius: 5; -fx-background-radius: 5;"
        );
    }

    private AnchorPane createInsertionMarker(boolean leftSide) {
        AnchorPane marker = new AnchorPane();
        marker.setPrefWidth(8d);
        marker.setMinWidth(8d);
        marker.setMaxWidth(8d);
        marker.setVisible(false);
        marker.setMouseTransparent(true);

        Region vertical = createMarkerSegment(3d, -1d);
        Region top = createMarkerSegment(7d, 3d);
        Region bottom = createMarkerSegment(7d, 3d);

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

    private HBox createMarkerHolder(AnchorPane marker) {
        HBox holder = new HBox(marker);
        holder.setAlignment(Pos.CENTER);
        holder.setFillHeight(true);
        HBox.setHgrow(holder, Priority.NEVER);
        return holder;
    }

    private Region createSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private void updateTitleAnimation() {
        titleClip.setWidth(Math.max(titleViewport.getWidth(), 0d));
        titleClip.setHeight(Math.max(titleViewport.getHeight(), 14d * currentScale));

        double viewportWidth = titleViewport.getWidth();
        double labelWidth = titleLabel.getLayoutBounds().getWidth();
        if (viewportWidth <= 0d || labelWidth <= viewportWidth) {
            stopTitleAnimation();
            titleLabel.setTranslateX(0d);
            return;
        }

        double travelDistance = labelWidth - viewportWidth + 12d;
        if (titleAnimation != null && titleAnimation.getStatus() == Animation.Status.RUNNING) {
            titleAnimation.stop();
        }

        TranslateTransition forward = new TranslateTransition(Duration.seconds(Math.max(travelDistance / 28d, 2.4d)), titleLabel);
        forward.setFromX(0d);
        forward.setToX(-travelDistance);

        PauseTransition pauseAtEnd = new PauseTransition(Duration.seconds(0.7d));
        TranslateTransition backward = new TranslateTransition(Duration.seconds(0.01d), titleLabel);
        backward.setFromX(-travelDistance);
        backward.setToX(0d);
        PauseTransition pauseAtStart = new PauseTransition(Duration.seconds(1.0d));

        titleAnimation = new SequentialTransition(pauseAtStart, forward, pauseAtEnd, backward);
        titleAnimation.setCycleCount(Animation.INDEFINITE);
        titleAnimation.playFromStart();
    }

    private void stopTitleAnimation() {
        if (titleAnimation != null) {
            titleAnimation.stop();
            titleAnimation = null;
        }
    }
}
