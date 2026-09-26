package app.ui.queue;

import app.ui.InsertionMarkers;
import app.ui.VolumeSliderSupport;
import app.ui.TitleScrollAnimation;

import app.model.AudioFile;
import app.model.PlaybackStatus;
import app.model.QueueTrack;
import app.localization.TextKey;
import app.localization.Texts;
import app.ui.workspace.WorkspaceInsertionMarker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.css.PseudoClass;
import javafx.scene.input.MouseButton;
import javafx.scene.shape.Rectangle;

import java.util.function.Consumer;

public class QueueTrackChipView extends StackPane {
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass PLAYING = PseudoClass.getPseudoClass("playing");
    private static final PseudoClass PAUSED = PseudoClass.getPseudoClass("paused");
    private static final PseudoClass FINISHED = PseudoClass.getPseudoClass("finished");
    private static final PseudoClass READY = PseudoClass.getPseudoClass("ready");

    private final QueueTrack queueTrack;
    private final Pane titleViewport = new Pane();
    private final Rectangle titleClip = new Rectangle();
    private final Label titleLabel = new Label();
    private final AnchorPane leftInsertionMarker = createInsertionMarker(true);
    private final AnchorPane rightInsertionMarker = createInsertionMarker(false);
    private double currentScale = 1d;
    private boolean lastSelected;
    private boolean lastActiveTrack;
    private PlaybackStatus lastPlaybackStatus;
    private final TitleScrollAnimation titleAnimation = new TitleScrollAnimation(
            titleViewport, titleLabel, titleClip, TitleScrollAnimation.Style.CHIP);
    private boolean titleHovered;

    public QueueTrackChipView(
            QueueTrack queueTrack,
            AudioFile audioFile,
            Runnable selectAction,
            Runnable playAction,
            Consumer<QueueTrack> removeAction,
            Consumer<Double> volumeAction,
            Runnable persistenceChangeAction
    ) {
        getStyleClass().add("queue-chip");
        this.queueTrack = queueTrack;
        titleLabel.setText(audioFile == null ? Texts.text(TextKey.QUEUE_MISSING) : audioFile.getDisplayName());
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

        setAlignment(Pos.CENTER_LEFT);
        HBox insertionMarkers = new HBox(createMarkerHolder(leftInsertionMarker), createSpacer(), createMarkerHolder(rightInsertionMarker));
        insertionMarkers.setMouseTransparent(true);
        insertionMarkers.setAlignment(Pos.CENTER);
        getChildren().setAll(titleViewport, insertionMarkers);

        setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;
            if (event.getClickCount() == 2) {
                playAction.run();
            } else {
                selectAction.run();
            }
        });

        MenuItem removeItem = new MenuItem(Texts.text(TextKey.QUEUE_REMOVE_TRACK));
        removeItem.setOnAction(event -> removeAction.accept(queueTrack));
        Slider volume = new Slider(0d, 100d, queueTrack.getVolume() * 100d);
        VolumeSliderSupport.bind(volume, volumeAction::accept, persistenceChangeAction);
        VBox volumeBox = new VBox(4d, new Label(Texts.text(TextKey.QUEUE_TRACK_VOLUME)), volume);
        volumeBox.setPadding(new Insets(6d));
        CustomMenuItem volumeItem = new CustomMenuItem(volumeBox, false);
        ContextMenu contextMenu = new ContextMenu(volumeItem, removeItem);
        setOnContextMenuRequested(event -> {
            contextMenu.show(this, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        updateScale(1d);
        updateTitleAnimation();
    }

    public QueueTrack getQueueTrack() {
        return queueTrack;
    }

    /**
     * Stops transitions before a queue rebuild detaches this chip. JavaFX animations retain their target nodes, so
     * merely replacing the children would otherwise keep obsolete chips and labels alive indefinitely.
     */
    public void dispose() {
        stopTitleAnimation();
    }

    public void setInsertionMarker(WorkspaceInsertionMarker insertionMarker) {
        leftInsertionMarker.setVisible(insertionMarker == WorkspaceInsertionMarker.LEFT);
        rightInsertionMarker.setVisible(insertionMarker == WorkspaceInsertionMarker.RIGHT);
    }

    public void updateScale(double scale) {
        currentScale = scale;
        lastPlaybackStatus = null;
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
        updateInsertionMarkerScale(leftInsertionMarker, scale);
        updateInsertionMarkerScale(rightInsertionMarker, scale);
    }

    public void refresh(boolean selected, PlaybackStatus playbackStatus, boolean activeTrack) {
        if (selected == lastSelected && activeTrack == lastActiveTrack && playbackStatus == lastPlaybackStatus) {
            return;
        }
        lastSelected = selected;
        lastActiveTrack = activeTrack;
        lastPlaybackStatus = playbackStatus;
        pseudoClassStateChanged(SELECTED, selected && !activeTrack);
        pseudoClassStateChanged(PLAYING, activeTrack && playbackStatus == PlaybackStatus.PLAYING);
        pseudoClassStateChanged(PAUSED, activeTrack && playbackStatus == PlaybackStatus.PAUSED);
        pseudoClassStateChanged(FINISHED, activeTrack
                && (playbackStatus == PlaybackStatus.FINISHED || playbackStatus == PlaybackStatus.ERROR));
        pseudoClassStateChanged(READY, activeTrack
                && playbackStatus != PlaybackStatus.PLAYING
                && playbackStatus != PlaybackStatus.PAUSED
                && playbackStatus != PlaybackStatus.FINISHED
                && playbackStatus != PlaybackStatus.ERROR);
        setStyle(
                "-fx-border-width: " + ((activeTrack ? 2d : 1d) * currentScale) + ";" +
                " -fx-border-radius: " + (5d * currentScale) + "; -fx-background-radius: " + (5d * currentScale) + ";"
        );
    }

    private void updateInsertionMarkerScale(AnchorPane marker, double scale) {
        InsertionMarkers.scale(marker, scale, true);
    }

    private AnchorPane createInsertionMarker(boolean leftSide) {
        return InsertionMarkers.create(leftSide, true);
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

    private void updateTitleAnimation() { titleAnimation.update(titleHovered, 14d * currentScale); }

    private void stopTitleAnimation() { titleAnimation.stop(); }
}
