package app.ui.tile;

import app.localization.TextKey;
import app.localization.Texts;
import app.model.PlaybackStatus;
import app.model.VirtualTileLayout;
import app.ui.UiIcons;
import app.ui.drag.DragPayload;
import app.ui.waveform.WaveformSeekView;
import app.ui.workspace.WorkspaceInsertionMarker;
import app.ui.workspace.WorkspaceItemView;
import app.ui.workspace.WorkspaceTrackItem;
import app.ui.workspace.WorkspaceVirtualTileItem;
import app.waveform.WaveformData;
import app.waveform.WaveformService;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VirtualTileView extends StackPane implements WorkspaceItemView {
    private static final PseudoClass INSERT_BEFORE = PseudoClass.getPseudoClass("insert-before");
    private static final PseudoClass INSERT_AFTER = PseudoClass.getPseudoClass("insert-after");
    private final WorkspaceVirtualTileItem tileItem;
    private final WaveformService waveformService;
    private final AudioDropHandler addTracksAction;
    private final WorkspaceTrackDropHandler addWorkspaceTrackAction;
    private final VirtualTrackMoveHandler moveTrackAction;
    private final QueueTrackDropHandler addQueueTrackAction;
    private final TrackRemovalHandler removeTrackAction;
    private final Runnable removeTileAction;
    private final Runnable persistenceChangeAction;
    private final GridPane grid = new GridPane();
    private final Region dragHandle = new Region();
    private final Rectangle gridClip = new Rectangle();
    private final Timeline refreshTimeline;
    private final Map<UUID, MiniTrackControls> controls = new LinkedHashMap<>();
    private final Region leftMarker = createMarker();
    private final Region rightMarker = createMarker();
    private ContextMenu activeContextMenu;
    private double currentScale = 1d;

    public VirtualTileView(int progressRefreshMillis, WorkspaceVirtualTileItem tileItem,
                           WaveformService waveformService, AudioDropHandler addTracksAction,
                           WorkspaceTrackDropHandler addWorkspaceTrackAction,
                           VirtualTrackMoveHandler moveTrackAction, TrackRemovalHandler removeTrackAction,
                           QueueTrackDropHandler addQueueTrackAction,
                           Runnable removeTileAction,
                           Runnable persistenceChangeAction) {
        this.tileItem = tileItem;
        this.waveformService = waveformService;
        this.addTracksAction = addTracksAction;
        this.addWorkspaceTrackAction = addWorkspaceTrackAction;
        this.moveTrackAction = moveTrackAction;
        this.addQueueTrackAction = addQueueTrackAction;
        this.removeTrackAction = removeTrackAction;
        this.removeTileAction = removeTileAction;
        this.persistenceChangeAction = persistenceChangeAction;
        getStyleClass().add("virtual-tile");
        setClip(gridClip);
        widthProperty().addListener((observable, oldValue, newValue) -> gridClip.setWidth(newValue.doubleValue()));
        heightProperty().addListener((observable, oldValue, newValue) -> gridClip.setHeight(newValue.doubleValue()));
        grid.setMinSize(0d, 0d);
        dragHandle.getStyleClass().add("virtual-tile-drag-handle");
        dragHandle.setMaxWidth(Double.MAX_VALUE);
        configureGrid();
        configureDragAndDrop();
        rebuild();
        getChildren().setAll(grid, dragHandle, leftMarker, rightMarker);
        StackPane.setAlignment(dragHandle, Pos.TOP_CENTER);
        StackPane.setAlignment(leftMarker, Pos.CENTER_LEFT);
        StackPane.setAlignment(rightMarker, Pos.CENTER_RIGHT);
        setOnMousePressed(event -> hideActiveContextMenu());
        refreshTimeline = new Timeline(new KeyFrame(Duration.millis(progressRefreshMillis), event -> refresh()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    public void rebuild() {
        controls.values().forEach(MiniTrackControls::dispose);
        controls.clear();
        grid.getChildren().clear();
        List<WorkspaceTrackItem> tracks = tileItem.getTrackItems();
        int capacity = tileItem.getTile().getLayout().getCapacity();
        for (int index = 0; index < capacity; index++) {
            Node node = index < tracks.size() ? createMiniTrack(tracks.get(index)) : createEmptySlot();
            grid.add(node, index % tileItem.getTile().getLayout().getColumns(),
                    index / tileItem.getTile().getLayout().getColumns());
        }
    }

    public void updateTileSize(double width, double height, double scale) {
        setMinSize(width, height);
        setPrefSize(width, height);
        setMaxSize(width, height);
        currentScale = scale;
        grid.setPadding(Insets.EMPTY);
        double dragHandleHeight = Math.max(6d, 8d * scale);
        dragHandle.setMinHeight(dragHandleHeight);
        dragHandle.setPrefHeight(dragHandleHeight);
        dragHandle.setMaxHeight(dragHandleHeight);
        StackPane.setMargin(grid, new Insets(dragHandleHeight, 0d, 0d, 0d));
        grid.setHgap(Math.max(1d, scale));
        grid.setVgap(Math.max(1d, scale));
        controls.values().forEach(control -> control.applyScale(scale));
        leftMarker.setPrefWidth(4d * scale);
        rightMarker.setPrefWidth(4d * scale);
    }

    public void refreshLocalization() { rebuild(); }
    public void dispose() {
        refreshTimeline.stop();
        hideActiveContextMenu();
        controls.values().forEach(MiniTrackControls::dispose);
    }

    @Override public UUID getWorkspaceItemId() { return tileItem.getTile().getId(); }
    @Override public void setInsertionMarker(WorkspaceInsertionMarker marker) {
        leftMarker.setVisible(marker == WorkspaceInsertionMarker.LEFT);
        rightMarker.setVisible(marker == WorkspaceInsertionMarker.RIGHT);
    }

    private void configureGrid() {
        grid.getStyleClass().add("virtual-tile-grid");
        VirtualTileLayout layout = tileItem.getTile().getLayout();
        for (int index = 0; index < layout.getColumns(); index++) grid.getColumnConstraints().add(createColumn(layout));
        for (int index = 0; index < layout.getRows(); index++) grid.getRowConstraints().add(createRow(layout));
        grid.setOnContextMenuRequested(event -> {
            if (isInsideMiniTile(event.getTarget())) return;
            showContextMenu(new ContextMenu(createRemoveTileItem()), grid, event);
        });
    }

    private ColumnConstraints createColumn(VirtualTileLayout layout) {
        ColumnConstraints column = new ColumnConstraints();
        column.setPercentWidth(100d / layout.getColumns());
        column.setMinWidth(0d);
        column.setHgrow(Priority.ALWAYS);
        return column;
    }

    private RowConstraints createRow(VirtualTileLayout layout) {
        RowConstraints row = new RowConstraints();
        row.setPercentHeight(100d / layout.getRows());
        row.setMinHeight(0d);
        row.setVgrow(Priority.ALWAYS);
        return row;
    }

    private Node createMiniTrack(WorkspaceTrackItem trackItem) {
        TitleMarquee title = new TitleMarquee(trackItem.getAudioFile().getDisplayName());
        Node playGraphic = UiIcons.play();
        Node pauseGraphic = UiIcons.pause();
        Node stopGraphic = UiIcons.stop();
        Button playPause = new Button(null, playGraphic);
        Button stop = new Button(null, stopGraphic);
        configureMiniButton(playPause);
        configureMiniButton(stop);
        playPause.setTooltip(new Tooltip(Texts.text(TextKey.TOOLTIP_PLAY_PAUSE)));
        stop.setTooltip(new Tooltip(Texts.text(TextKey.TOOLTIP_STOP)));
        HBox buttons = new HBox(4d, playPause, stop);
        Node mutedGraphic = null;
        Node unmutedGraphic = null;
        ToggleButton mute = null;
        Node loopGraphic = null;
        ToggleButton loop = null;
        WaveformSeekView waveform = null;
        boolean detailed = tileItem.getTile().getLayout() == VirtualTileLayout.TWO_BY_TWO;
        boolean compact = tileItem.getTile().getLayout() == VirtualTileLayout.TWO_BY_THREE;
        if (detailed || compact) {
            mutedGraphic = UiIcons.muted();
            unmutedGraphic = UiIcons.unmuted();
            mute = new ToggleButton(null, unmutedGraphic);
            configureMiniButton(mute);
            mute.setTooltip(new Tooltip(Texts.text(TextKey.TOOLTIP_MUTE)));
            buttons.getChildren().add(mute);
        }
        if (compact) {
            loopGraphic = UiIcons.loop();
            loop = new ToggleButton(null, loopGraphic);
            configureMiniButton(loop);
            loop.setTooltip(new Tooltip(Texts.text(TextKey.TOOLTIP_LOOP)));
            buttons.getChildren().add(loop);
        }
        if (detailed) {
            waveform = new WaveformSeekView();
            waveform.setMinHeight(20d);
            waveform.setPrefHeight(20d);
            waveform.setMaxHeight(20d);
        }
        Circle status = new Circle(4d, Color.web("#97a3b6"));
        VBox content = detailed ? new VBox(4d, title, buttons, waveform) : new VBox(4d, title, buttons);
        StackPane pane = new StackPane(content, status);
        Tooltip.install(pane, new Tooltip(trackItem.getAudioFile().getDisplayName()));
        pane.setMinSize(0d, 0d);
        pane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        pane.getStyleClass().add("virtual-mini-tile");
        StackPane.setAlignment(status, Pos.TOP_RIGHT);
        StackPane.setMargin(status, new Insets(4d));
        playPause.setOnAction(event -> trackItem.togglePlayPause());
        stop.setOnAction(event -> trackItem.stop());
        ToggleButton finalMute = mute;
        if (finalMute != null) finalMute.setOnAction(event -> {
            if (trackItem.isMuted() != finalMute.isSelected()) trackItem.toggleMuted();
        });
        ToggleButton finalLoop = loop;
        if (finalLoop != null) finalLoop.setOnAction(event -> {
            trackItem.setLoop(finalLoop.isSelected());
            persistenceChangeAction.run();
        });
        pane.setOnContextMenuRequested(event -> showContextMenu(createTrackContextMenu(trackItem), pane, event));
        configureMiniTrackDragAndDrop(pane, trackItem);
        MiniTrackControls miniControls = new MiniTrackControls(trackItem, title, playPause, playGraphic,
                pauseGraphic, stop, stopGraphic, finalMute, mutedGraphic, unmutedGraphic, waveform, status, content);
        miniControls.setLoopControls(finalLoop, loopGraphic);
        controls.put(trackItem.getWorkspaceTrack().getId(), miniControls);
        miniControls.loadWaveform();
        miniControls.applyScale(currentScale);
        return pane;
    }

    private Node createEmptySlot() {
        Label label = new Label(Texts.text(TextKey.VIRTUAL_TILE_EMPTY_SLOT));
        label.setWrapText(true);
        StackPane pane = new StackPane(label);
        pane.getStyleClass().addAll("virtual-mini-tile", "virtual-mini-tile-empty");
        return pane;
    }

    private void configureMiniButton(ButtonBase button) {
        button.getStyleClass().add("virtual-mini-control");
        button.setMinWidth(0d);
        button.setMinHeight(0d);
        button.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(button, Priority.ALWAYS);
    }

    private ContextMenu createTrackContextMenu(WorkspaceTrackItem trackItem) {
        Slider volume = new Slider(0d, 100d, trackItem.getWorkspaceTrack().getVolume() * 100d);
        volume.valueProperty().addListener((observable, oldValue, newValue) ->
                trackItem.setVolume(newValue.doubleValue() / 100d));
        volume.setOnMouseReleased(event -> persistenceChangeAction.run());
        VBox volumeBox = new VBox(4d, new Label(Texts.text(TextKey.VIRTUAL_TILE_VOLUME)), volume);
        volumeBox.setPadding(new Insets(6d));
        CustomMenuItem volumeItem = new CustomMenuItem(volumeBox, false);
        CheckMenuItem loopItem = new CheckMenuItem(Texts.text(TextKey.VIRTUAL_TILE_LOOP));
        loopItem.setSelected(trackItem.getWorkspaceTrack().isLoop());
        loopItem.setOnAction(event -> { trackItem.setLoop(loopItem.isSelected()); persistenceChangeAction.run(); });
        CheckMenuItem muteItem = new CheckMenuItem(Texts.text(TextKey.VIRTUAL_TILE_MUTE));
        muteItem.setSelected(trackItem.isMuted());
        muteItem.setOnAction(event -> {
            if (trackItem.isMuted() != muteItem.isSelected()) trackItem.toggleMuted();
            refresh();
        });
        MenuItem removeTrack = new MenuItem(Texts.text(TextKey.VIRTUAL_TILE_REMOVE_TRACK));
        removeTrack.setOnAction(event -> removeTrackAction.remove(trackItem.getWorkspaceTrack().getId()));
        return new ContextMenu(volumeItem, loopItem, muteItem, removeTrack,
                new SeparatorMenuItem(), createRemoveTileItem());
    }

    private MenuItem createRemoveTileItem() {
        MenuItem item = new MenuItem(Texts.text(TextKey.VIRTUAL_TILE_REMOVE));
        item.setOnAction(event -> removeTileAction.run());
        return item;
    }

    private void showContextMenu(ContextMenu menu, Node owner, ContextMenuEvent event) {
        hideActiveContextMenu();
        activeContextMenu = menu;
        menu.setOnHidden(hidden -> { if (activeContextMenu == menu) activeContextMenu = null; });
        menu.show(owner, event.getScreenX(), event.getScreenY());
        event.consume();
    }

    private void hideActiveContextMenu() {
        if (activeContextMenu != null) activeContextMenu.hide();
        activeContextMenu = null;
    }

    private boolean isInsideMiniTile(Object target) {
        if (!(target instanceof Node node)) return false;
        Node current = node;
        while (current != null && current != grid) {
            if (current.getStyleClass().contains("virtual-mini-tile")) return true;
            current = current.getParent();
        }
        return false;
    }

    private void configureDragAndDrop() {
        setOnDragOver(event -> {
            String payload = event.getDragboard().getString();
            if (canAcceptDrop(payload, null)) {
                event.acceptTransferModes(DragPayload.parseAudioFileIds(payload).isEmpty()
                        ? TransferMode.MOVE : TransferMode.COPY);
                event.consume();
            }
        });
        setOnDragDropped(event -> {
            boolean accepted = handleDrop(event.getDragboard().getString(), null, false);
            event.setDropCompleted(accepted);
            event.consume();
        });
    }

    private void configureMiniTrackDragAndDrop(StackPane pane, WorkspaceTrackItem trackItem) {
        UUID trackId = trackItem.getWorkspaceTrack().getId();
        pane.setOnDragDetected(event -> {
            var dragboard = pane.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(DragPayload.virtualTileTrack(getWorkspaceItemId(), trackId));
            dragboard.setContent(content);
            event.consume();
        });
        pane.setOnDragOver(event -> {
            String payload = event.getDragboard().getString();
            if (!canAcceptDrop(payload, trackId)) return;
            boolean after = event.getX() >= pane.getBoundsInLocal().getWidth() / 2d;
            pane.pseudoClassStateChanged(INSERT_BEFORE, !after);
            pane.pseudoClassStateChanged(INSERT_AFTER, after);
            event.acceptTransferModes(DragPayload.parseAudioFileIds(payload).isEmpty()
                    ? TransferMode.MOVE : TransferMode.COPY);
            event.consume();
        });
        pane.setOnDragExited(event -> clearMiniInsertion(pane));
        pane.setOnDragDropped(event -> {
            boolean after = event.getX() >= pane.getBoundsInLocal().getWidth() / 2d;
            boolean accepted = handleDrop(event.getDragboard().getString(), trackId, after);
            clearMiniInsertion(pane);
            event.setDropCompleted(accepted);
            event.consume();
        });
        pane.setOnDragDone(event -> clearMiniInsertion(pane));
    }

    private void clearMiniInsertion(StackPane pane) {
        pane.pseudoClassStateChanged(INSERT_BEFORE, false);
        pane.pseudoClassStateChanged(INSERT_AFTER, false);
    }

    private boolean canAcceptDrop(String payload, UUID targetTrackId) {
        if (tileItem.getTrackItems().size() >= tileItem.getTile().getLayout().getCapacity()) {
            DragPayload.VirtualTileTrackRef ref = DragPayload.parseVirtualTileTrack(payload);
            if (ref == null || !ref.tileId().equals(getWorkspaceItemId())) return false;
        }
        if (!DragPayload.parseAudioFileIds(payload).isEmpty()) return true;
        if (DragPayload.parseWorkspaceTrackId(payload) != null) return true;
        if (DragPayload.parseQueueTrack(payload) != null) return true;
        DragPayload.VirtualTileTrackRef ref = DragPayload.parseVirtualTileTrack(payload);
        return ref != null && (targetTrackId == null || !ref.trackId().equals(targetTrackId));
    }

    private boolean handleDrop(String payload, UUID targetTrackId, boolean placeAfter) {
        List<UUID> ids = DragPayload.parseAudioFileIds(payload);
        if (!ids.isEmpty()) {
            addTracksAction.add(ids, targetTrackId, placeAfter);
            return true;
        }
        UUID workspaceTrackId = DragPayload.parseWorkspaceTrackId(payload);
        if (workspaceTrackId != null) {
            addWorkspaceTrackAction.add(workspaceTrackId, targetTrackId, placeAfter);
            return true;
        }
        DragPayload.VirtualTileTrackRef ref = DragPayload.parseVirtualTileTrack(payload);
        if (ref != null) {
            moveTrackAction.move(ref.tileId(), ref.trackId(), targetTrackId, placeAfter);
            return true;
        }
        DragPayload.QueueTrackRef queueTrack = DragPayload.parseQueueTrack(payload);
        if (queueTrack != null) {
            addQueueTrackAction.add(queueTrack.queueId(), queueTrack.queueTrackId(), targetTrackId, placeAfter);
            return true;
        }
        return false;
    }

    private void refresh() { controls.values().forEach(MiniTrackControls::refresh); }

    private Region createMarker() {
        Region marker = new Region();
        marker.getStyleClass().add("insertion-marker");
        marker.setMinWidth(4d);
        marker.setMaxWidth(4d);
        marker.setVisible(false);
        marker.setMouseTransparent(true);
        return marker;
    }

    private final class MiniTrackControls {
        private final WorkspaceTrackItem item;
        private final TitleMarquee title;
        private final Button playPause;
        private final Node playGraphic;
        private final Node pauseGraphic;
        private final Button stop;
        private final Node stopGraphic;
        private final ToggleButton mute;
        private final Node mutedGraphic;
        private final Node unmutedGraphic;
        private final WaveformSeekView waveform;
        private ToggleButton loop;
        private Node loopGraphic;
        private final Circle status;
        private final VBox content;

        private MiniTrackControls(WorkspaceTrackItem item, TitleMarquee title, Button playPause,
                                  Node playGraphic, Node pauseGraphic, Button stop, Node stopGraphic,
                                  ToggleButton mute, Node mutedGraphic, Node unmutedGraphic,
                                  WaveformSeekView waveform, Circle status, VBox content) {
            this.item = item;
            this.title = title;
            this.playPause = playPause;
            this.playGraphic = playGraphic;
            this.pauseGraphic = pauseGraphic;
            this.stop = stop;
            this.stopGraphic = stopGraphic;
            this.mute = mute;
            this.mutedGraphic = mutedGraphic;
            this.unmutedGraphic = unmutedGraphic;
            this.waveform = waveform;
            this.status = status;
            this.content = content;
            if (waveform != null) waveform.setSeekHandler(item::seek);
        }

        private void setLoopControls(ToggleButton loop, Node loopGraphic) {
            this.loop = loop;
            this.loopGraphic = loopGraphic;
        }

        private void loadWaveform() {
            if (waveform == null) return;
            Path path = item.getAudioPath().orElse(null);
            if (path == null) return;
            waveformService.loadWaveform(path).thenAccept(data -> Platform.runLater(() -> waveform.setWaveformData(data)));
        }

        private void refresh() {
            PlaybackStatus playbackStatus = item.getStatus();
            playPause.setGraphic(playbackStatus == PlaybackStatus.PLAYING ? pauseGraphic : playGraphic);
            boolean disabled = item.isMissing() || playbackStatus == PlaybackStatus.ERROR;
            playPause.setDisable(disabled);
            stop.setDisable(disabled);
            if (waveform != null) {
                waveform.setDisable(disabled);
                waveform.setPlaybackPosition(item.getCurrentTime(), item.getTotalDuration());
            }
            if (mute != null) {
                mute.setSelected(item.isMuted());
                mute.setGraphic(item.isMuted() ? mutedGraphic : unmutedGraphic);
            }
            if (loop != null) loop.setSelected(item.getWorkspaceTrack().isLoop());
            status.setFill(switch (playbackStatus) {
                case PLAYING -> Color.web("#2f9e44");
                case PAUSED -> Color.web("#f0b429");
                case ERROR -> Color.web("#a94442");
                case FINISHED -> Color.web("#d64545");
                default -> Color.web("#97a3b6");
            });
        }

        private void applyScale(double scale) {
            boolean detailed = waveform != null;
            boolean dense = tileItem.getTile().getLayout() == VirtualTileLayout.FOUR_BY_FOUR;
            content.setPadding(new Insets((detailed ? 5d : 2d) * scale));
            title.applyScale(scale, detailed);
            UiIcons.resize(playGraphic, scale * 0.7d);
            UiIcons.resize(pauseGraphic, scale * 0.7d);
            UiIcons.resize(stopGraphic, scale * 0.7d);
            if (mutedGraphic != null) UiIcons.resize(mutedGraphic, scale * 0.7d);
            if (unmutedGraphic != null) UiIcons.resize(unmutedGraphic, scale * 0.7d);
            if (loopGraphic != null) UiIcons.resize(loopGraphic, scale * 0.7d);
            applyButtonSize(playPause, scale, detailed, dense);
            applyButtonSize(stop, scale, detailed, dense);
            if (mute != null) applyButtonSize(mute, scale, detailed, false);
            if (loop != null) applyButtonSize(loop, scale, detailed, false);
            if (waveform != null) {
                waveform.setMinHeight(20d * scale);
                waveform.setPrefHeight(20d * scale);
                waveform.setMaxHeight(20d * scale);
            }
            status.setRadius(4d * scale);
        }

        private void applyButtonSize(ButtonBase button, double scale, boolean detailed, boolean dense) {
            double height = (detailed ? 26d : dense ? 18d : 22d) * scale;
            button.setMinHeight(height);
            button.setPrefHeight(height);
            button.setMaxHeight(height);
            button.setStyle("-fx-padding: " + Math.max(2d, 4d * scale)
                    + "px; -fx-border-width: 1px;");
            button.setMinWidth(0d);
            button.setPrefWidth(Region.USE_COMPUTED_SIZE);
            button.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(button, Priority.ALWAYS);
        }

        private void dispose() {
            title.dispose();
            if (waveform != null) waveform.setWaveformData(WaveformData.empty());
        }
    }

    private static final class TitleMarquee extends Pane {
        private final Label label = new Label();
        private final Rectangle clip = new Rectangle();
        private SequentialTransition animation;

        private TitleMarquee(String text) {
            label.setText(text);
            label.setWrapText(false);
            setClip(clip);
            setMinWidth(0d);
            setMaxWidth(Double.MAX_VALUE);
            getChildren().add(label);
            widthProperty().addListener((observable, oldValue, newValue) -> updateAnimation());
            heightProperty().addListener((observable, oldValue, newValue) -> updateAnimation());
            label.layoutBoundsProperty().addListener((observable, oldValue, newValue) -> updateAnimation());
        }

        private void applyScale(double scale, boolean detailed) {
            double height = detailed ? 18d : 14d;
            setMinHeight(height * scale);
            setPrefHeight(height * scale);
            label.setStyle("-fx-font-size: " + ((detailed ? 10d : 9d) * scale) + "px; -fx-font-weight: bold;");
            Platform.runLater(this::updateAnimation);
        }

        private void updateAnimation() {
            clip.setWidth(Math.max(getWidth(), 0d));
            clip.setHeight(Math.max(getHeight(), 0d));
            if (animation != null) animation.stop();
            label.setTranslateX(0d);
            double overflow = label.getLayoutBounds().getWidth() - getWidth();
            if (overflow <= 2d || getWidth() <= 0d) return;
            PauseTransition before = new PauseTransition(Duration.seconds(1));
            TranslateTransition left = new TranslateTransition(Duration.millis(Math.max(1200d, overflow * 35d)), label);
            left.setToX(-overflow);
            PauseTransition after = new PauseTransition(Duration.seconds(1));
            TranslateTransition right = new TranslateTransition(Duration.millis(Math.max(600d, overflow * 18d)), label);
            right.setToX(0d);
            animation = new SequentialTransition(before, left, after, right);
            animation.setCycleCount(Timeline.INDEFINITE);
            animation.play();
        }

        private void dispose() { if (animation != null) animation.stop(); }
    }

    public interface AudioDropHandler {
        void add(List<UUID> audioFileIds, UUID targetTrackId, boolean placeAfter);
    }

    public interface WorkspaceTrackDropHandler {
        void add(UUID workspaceTrackId, UUID targetTrackId, boolean placeAfter);
    }

    public interface VirtualTrackMoveHandler {
        void move(UUID sourceTileId, UUID trackId, UUID targetTrackId, boolean placeAfter);
    }

    public interface TrackRemovalHandler {
        void remove(UUID trackId);
    }

    public interface QueueTrackDropHandler {
        void add(UUID queueId, UUID queueTrackId, UUID targetTrackId, boolean placeAfter);
    }
}
