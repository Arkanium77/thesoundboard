package app.ui.main;

import app.config.UiConfig;
import app.localization.TextKey;
import app.localization.Texts;
import app.model.VirtualTileLayout;
import app.ui.drag.DragPayload;
import app.ui.queue.QueueView;
import app.ui.tile.TrackTileView;
import app.ui.tile.VirtualTileView;
import app.ui.workspace.WorkspaceInsertionMarker;
import app.ui.workspace.WorkspaceItemView;
import javafx.animation.AnimationTimer;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class WorkspaceView extends BorderPane {
    private static final double AUTO_SCROLL_EDGE = 64d;
    private static final double AUTO_SCROLL_MAX_SPEED = 640d;
    private final FlowPane contentPane = new FlowPane();
    private final Label emptyStateLabel = new Label(Texts.text(TextKey.WORKSPACE_EMPTY));
    private final ScrollPane scrollPane = new ScrollPane(contentPane);
    private final Region background = new Region();
    private final WorkspaceDropHandler workspaceDropHandler;
    private final UiConfig uiConfig;
    private double currentTileWidth;
    private double currentTileHeight;
    private double interfaceScale = 1d;
    private ContextMenu workspaceContextMenu;
    private WorkspaceItemView insertionTargetView;
    private boolean insertionAfter;
    private double dragPointerSceneY = Double.NaN;
    private long lastAutoScrollFrame;
    private final AnimationTimer dragAutoScroll = new AnimationTimer() {
        @Override
        public void handle(long now) {
            autoScrollDuringDrag(now);
        }
    };

    public WorkspaceView(UiConfig uiConfig, WorkspaceDropHandler workspaceDropHandler) {
        this.uiConfig = uiConfig;
        this.workspaceDropHandler = workspaceDropHandler;
        this.currentTileWidth = uiConfig.getTrackTileWidth();
        this.currentTileHeight = uiConfig.getTrackTileHeight();

        getStyleClass().add("workspace-view");
        background.getStyleClass().add("workspace-background");
        background.setMouseTransparent(true);
        scrollPane.getStyleClass().add("workspace-scroll");
        contentPane.getStyleClass().add("workspace-content");

        contentPane.setPadding(new Insets(12));
        contentPane.setHgap(12);
        contentPane.setVgap(12);
        contentPane.setPrefWrapLength(uiConfig.getMinWidth());
        contentPane.setAlignment(Pos.TOP_LEFT);

        scrollPane.setFitToWidth(true);
        scrollPane.setContent(contentPane);
        scrollPane.viewportBoundsProperty().addListener((observable, oldValue, newValue) -> updateContainerSize(newValue));
        addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                workspaceDropHandler.adjustWorkspaceZoom(event.getDeltaY());
                event.consume();
                return;
            }
            if (!Double.isNaN(dragPointerSceneY)) {
                scrollByPixels(-event.getDeltaY());
                event.consume();
            }
        });

        setCenter(new StackPane(background, scrollPane));
        addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> hideContextMenu());
        configureContainerDragAndDrop();
        configureContextMenu();
        updateEmptyState();
    }

    public void setWorkspaceNodes(List<Node> workspaceNodes) {
        workspaceNodes.forEach(this::configureNodeDragAndDrop);
        contentPane.getChildren().setAll(workspaceNodes);
        updateQueueWidths();
        updateEmptyState();
    }

    public List<Node> clearAndReturnCurrentNodes() {
        List<Node> workspaceNodes = new ArrayList<>(contentPane.getChildren());
        contentPane.getChildren().clear();
        clearInsertionMarker();
        updateEmptyState();
        return workspaceNodes;
    }

    public void refreshTileMetrics(double tileWidth, double tileHeight, double tileScale) {
        currentTileWidth = tileWidth;
        currentTileHeight = tileHeight;
        for (Node node : contentPane.getChildren()) {
            if (node instanceof TrackTileView trackTileView) {
                trackTileView.updateTileSize(tileWidth, tileHeight, tileScale);
            } else if (node instanceof QueueView queueView) {
                queueView.updateTileMetrics(tileWidth, tileHeight, tileScale);
            } else if (node instanceof VirtualTileView virtualTileView) {
                virtualTileView.updateTileSize(tileWidth, tileHeight, tileScale);
            }
        }
        updateQueueWidths();
    }

    public void updateInterfaceScale(double interfaceScale) {
        this.interfaceScale = interfaceScale;
        contentPane.setPadding(new Insets(12d * interfaceScale));
        contentPane.setHgap(12d * interfaceScale);
        contentPane.setVgap(12d * interfaceScale);
        updateEmptyState();
        updateQueueWidths();
    }

    public void refreshLocalization() {
        emptyStateLabel.setText(Texts.text(TextKey.WORKSPACE_EMPTY));
        configureContextMenu();
    }

    private void configureContextMenu() {
        MenuItem createQueueItem = new MenuItem(Texts.text(TextKey.WORKSPACE_CREATE_QUEUE));
        createQueueItem.setOnAction(event -> workspaceDropHandler.createQueue());
        Menu createVirtualTileMenu = new Menu(Texts.text(TextKey.WORKSPACE_CREATE_VIRTUAL_TILE));
        MenuItem createTwoByTwo = new MenuItem(Texts.text(TextKey.VIRTUAL_TILE_LAYOUT_TWO_BY_TWO));
        createTwoByTwo.setOnAction(event -> workspaceDropHandler.createVirtualTile(VirtualTileLayout.TWO_BY_TWO));
        MenuItem createTwoByThree = new MenuItem(Texts.text(TextKey.VIRTUAL_TILE_LAYOUT_TWO_BY_THREE));
        createTwoByThree.setOnAction(event -> workspaceDropHandler.createVirtualTile(VirtualTileLayout.TWO_BY_THREE));
        MenuItem createThreeByThree = new MenuItem(Texts.text(TextKey.VIRTUAL_TILE_LAYOUT_THREE_BY_THREE));
        createThreeByThree.setOnAction(event -> workspaceDropHandler.createVirtualTile(VirtualTileLayout.THREE_BY_THREE));
        MenuItem createFourByFour = new MenuItem(Texts.text(TextKey.VIRTUAL_TILE_LAYOUT_FOUR_BY_FOUR));
        createFourByFour.setOnAction(event -> workspaceDropHandler.createVirtualTile(VirtualTileLayout.FOUR_BY_FOUR));
        createVirtualTileMenu.getItems().addAll(createTwoByTwo, createTwoByThree, createThreeByThree, createFourByFour);
        workspaceContextMenu = new ContextMenu(createQueueItem, createVirtualTileMenu);

        setOnContextMenuRequested(event -> showWorkspaceContextMenu(event, this));
        scrollPane.setOnContextMenuRequested(event -> showWorkspaceContextMenu(event, scrollPane));
        contentPane.setOnContextMenuRequested(event -> showWorkspaceContextMenu(event, contentPane));
        emptyStateLabel.setOnContextMenuRequested(event -> showWorkspaceContextMenu(event, emptyStateLabel));

        setOnMousePressed(event -> hideContextMenu());
        scrollPane.setOnMousePressed(event -> hideContextMenu());
        contentPane.setOnMousePressed(event -> hideContextMenu());
        emptyStateLabel.setOnMousePressed(event -> hideContextMenu());
    }

    private void configureContainerDragAndDrop() {
        addEventFilter(DragEvent.DRAG_OVER, event -> {
            if (!isSupportedPayload(event.getDragboard().getString())) return;
            dragPointerSceneY = event.getSceneY();
            dragAutoScroll.start();
        });
        addEventFilter(DragEvent.DRAG_DROPPED, event -> stopDragAutoScroll());
        addEventFilter(DragEvent.DRAG_DONE, event -> stopDragAutoScroll());
        addEventFilter(DragEvent.DRAG_EXITED, event -> {
            Bounds bounds = localToScene(getBoundsInLocal());
            if (bounds == null || !bounds.contains(event.getSceneX(), event.getSceneY())) {
                stopDragAutoScroll();
            } else {
                dragPointerSceneY = event.getSceneY();
            }
        });

        contentPane.setOnDragOver(event -> {
            String payload = event.getDragboard().getString();
            if (!isSupportedPayload(payload)) {
                return;
            }

            if (contentPane.getChildren().isEmpty()) {
                clearInsertionMarker();
            } else {
                updateInsertionMarkerForContent(event.getX(), event.getY());
            }

            event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            event.consume();
        });

        contentPane.setOnDragExited(event -> {
            if (!isPointerInside(contentPane, event.getSceneX(), event.getSceneY())) {
                clearInsertionMarker();
            }
        });

        contentPane.setOnDragDropped(event -> {
            String payload = event.getDragboard().getString();
            boolean completed = handleWorkspaceDrop(
                    payload,
                    insertionTargetView == null ? null : insertionTargetView.getWorkspaceItemId(),
                    insertionAfter
            );
            clearInsertionMarker();
            event.setDropCompleted(completed);
            event.consume();
        });

        setOnDragOver(event -> {
            if (!isSupportedPayload(event.getDragboard().getString())) {
                return;
            }

            if (contentPane.getChildren().isEmpty()) {
                clearInsertionMarker();
            }

            event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            event.consume();
        });

        setOnDragDropped(event -> {
            String payload = event.getDragboard().getString();
            boolean completed = handleWorkspaceDrop(payload, null, false);
            clearInsertionMarker();
            event.setDropCompleted(completed);
            event.consume();
        });
    }

    private void autoScrollDuringDrag(long now) {
        Node viewport = scrollPane.lookup(".viewport");
        if (viewport == null || Double.isNaN(dragPointerSceneY)) return;
        Bounds bounds = viewport.localToScene(viewport.getBoundsInLocal());
        if (bounds == null) return;
        double speed = calculateAutoScrollVelocity(dragPointerSceneY, bounds.getMinY(), bounds.getMaxY(),
                AUTO_SCROLL_EDGE * interfaceScale, AUTO_SCROLL_MAX_SPEED * interfaceScale);
        if (lastAutoScrollFrame != 0L && speed != 0d) {
            double elapsedSeconds = Math.min((now - lastAutoScrollFrame) / 1_000_000_000d, 0.05d);
            scrollByPixels(speed * elapsedSeconds);
        }
        lastAutoScrollFrame = now;
    }

    private void scrollByPixels(double pixels) {
        double viewportHeight = scrollPane.getViewportBounds().getHeight();
        double contentHeight = contentPane.getLayoutBounds().getHeight();
        double scrollableHeight = contentHeight - viewportHeight;
        if (scrollableHeight <= 0d) return;
        double nextValue = scrollPane.getVvalue() + pixels / scrollableHeight;
        scrollPane.setVvalue(Math.max(scrollPane.getVmin(), Math.min(scrollPane.getVmax(), nextValue)));
    }

    private void stopDragAutoScroll() {
        dragAutoScroll.stop();
        dragPointerSceneY = Double.NaN;
        lastAutoScrollFrame = 0L;
    }

    static double calculateAutoScrollVelocity(double pointerY, double top, double bottom,
                                              double edgeSize, double maxSpeed) {
        if (edgeSize <= 0d || bottom <= top) return 0d;
        if (pointerY < top || pointerY > bottom) return 0d;
        if (pointerY < top + edgeSize) {
            return -maxSpeed * (top + edgeSize - pointerY) / edgeSize;
        }
        if (pointerY > bottom - edgeSize) {
            return maxSpeed * (pointerY - (bottom - edgeSize)) / edgeSize;
        }
        return 0d;
    }

    private void configureNodeDragAndDrop(Node node) {
        if (!(node instanceof WorkspaceItemView workspaceItemView)) {
            return;
        }

        node.setOnDragDetected(event -> {
            var dragboard = node.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            if (node instanceof QueueView) {
                content.putString(DragPayload.workspaceQueue(workspaceItemView.getWorkspaceItemId()));
            } else if (node instanceof VirtualTileView) {
                content.putString(DragPayload.workspaceVirtualTile(workspaceItemView.getWorkspaceItemId()));
            } else if (node instanceof TrackTileView) {
                content.putString(DragPayload.workspaceTrack(workspaceItemView.getWorkspaceItemId()));
            } else {
                return;
            }
            dragboard.setContent(content);
            event.consume();
        });

        if (node instanceof QueueView || node instanceof VirtualTileView) {
            return;
        }

        node.setOnDragOver(event -> {
            String payload = event.getDragboard().getString();
            if (!isSupportedPayload(payload)) {
                return;
            }

            UUID workspaceItemId = parseWorkspaceTrackId(payload);
            if (workspaceItemId == null) {
                workspaceItemId = parseWorkspaceQueueId(payload);
            }
            if (workspaceItemId == null) workspaceItemId = parseWorkspaceVirtualTileId(payload);
            if (workspaceItemId != null && workspaceItemId.equals(workspaceItemView.getWorkspaceItemId())) {
                return;
            }

            updateInsertionMarker(workspaceItemView, event.getX() >= node.getBoundsInLocal().getWidth() / 2d);
            event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            event.consume();
        });

        node.setOnDragExited(event -> {
            if (!isPointerInside(node, event.getSceneX(), event.getSceneY())) {
                clearInsertionMarker();
            }
        });

        node.setOnDragDropped(event -> {
            String payload = event.getDragboard().getString();
            boolean placeAfter = event.getX() >= node.getBoundsInLocal().getWidth() / 2d;
            boolean completed = handleWorkspaceDrop(payload, workspaceItemView.getWorkspaceItemId(), placeAfter);
            clearInsertionMarker();
            event.setDropCompleted(completed);
            event.consume();
        });

        node.setOnDragDone(event -> clearInsertionMarker());
    }

    private void updateContainerSize(Bounds viewportBounds) {
        contentPane.setPrefWrapLength(Math.max(viewportBounds.getWidth(), 320d));
        contentPane.setMinHeight(Math.max(viewportBounds.getHeight(), 0d));
        contentPane.setPrefHeight(Region.USE_COMPUTED_SIZE);
        updateQueueWidths();
    }

    private void updateQueueWidths() {
        double queueWidth = contentPane.getHgap() <= 0d
                ? uiConfig.getQueueWidthInTiles() * currentTileWidth
                : uiConfig.getQueueWidthInTiles() * currentTileWidth + (uiConfig.getQueueWidthInTiles() - 1d) * contentPane.getHgap();
        for (Node node : contentPane.getChildren()) {
            if (node instanceof QueueView queueView) {
                queueView.setMinWidth(queueWidth);
                queueView.setPrefWidth(queueWidth);
                queueView.setMaxWidth(queueWidth);
            }
        }
    }

    private void updateInsertionMarker(WorkspaceItemView workspaceItemView, boolean placeAfter) {
        if (insertionTargetView == workspaceItemView && insertionAfter == placeAfter) {
            return;
        }

        clearInsertionMarker();
        insertionTargetView = workspaceItemView;
        insertionAfter = placeAfter;
        workspaceItemView.setInsertionMarker(placeAfter ? WorkspaceInsertionMarker.RIGHT : WorkspaceInsertionMarker.LEFT);
    }

    private void clearInsertionMarker() {
        if (insertionTargetView != null) {
            insertionTargetView.setInsertionMarker(WorkspaceInsertionMarker.NONE);
            insertionTargetView = null;
        }
        insertionAfter = false;
    }

    private void updateInsertionMarkerForContent(double x, double y) {
        InsertionSlot nearestSlot = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Node node : contentPane.getChildren()) {
            if (!(node instanceof WorkspaceItemView workspaceItemView)) {
                continue;
            }

            Bounds bounds = node.getBoundsInParent();
            double centerY = bounds.getMinY() + bounds.getHeight() / 2d;
            double leftX = bounds.getMinX();
            double rightX = bounds.getMaxX();

            double leftDistance = squaredDistance(x, y, leftX, centerY);
            if (leftDistance < nearestDistance) {
                nearestDistance = leftDistance;
                nearestSlot = new InsertionSlot(workspaceItemView, false);
            }

            double rightDistance = squaredDistance(x, y, rightX, centerY);
            if (rightDistance < nearestDistance) {
                nearestDistance = rightDistance;
                nearestSlot = new InsertionSlot(workspaceItemView, true);
            }
        }

        if (nearestSlot == null) {
            clearInsertionMarker();
            return;
        }

        updateInsertionMarker(nearestSlot.workspaceItemView(), nearestSlot.placeAfter());
    }

    private double squaredDistance(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    private boolean handleWorkspaceDrop(String payload, UUID targetWorkspaceItemId, boolean placeAfter) {
        if (!isSupportedPayload(payload)) {
            return false;
        }

        boolean completed = false;
        List<UUID> audioFileIds = parseAudioFileIds(payload);
        if (!audioFileIds.isEmpty()) {
            workspaceDropHandler.addAudioFilesAsSolo(audioFileIds, targetWorkspaceItemId, placeAfter);
            completed = true;
        }

        UUID workspaceItemId = parseWorkspaceTrackId(payload);
        if (workspaceItemId == null) {
            workspaceItemId = parseWorkspaceQueueId(payload);
        }
        if (workspaceItemId == null) workspaceItemId = parseWorkspaceVirtualTileId(payload);
        if (workspaceItemId != null) {
            if (targetWorkspaceItemId == null) {
                workspaceDropHandler.moveWorkspaceItemToEnd(workspaceItemId);
                completed = true;
            } else if (!workspaceItemId.equals(targetWorkspaceItemId)) {
                workspaceDropHandler.moveWorkspaceItem(workspaceItemId, targetWorkspaceItemId, placeAfter);
                completed = true;
            }
        }

        QueueTrackPayload queueTrackPayload = parseQueueTrackPayload(payload);
        if (queueTrackPayload != null) {
            workspaceDropHandler.moveQueueTrackToWorkspace(
                    queueTrackPayload.queueId(),
                    queueTrackPayload.queueTrackId(),
                    targetWorkspaceItemId,
                    placeAfter
            );
            completed = true;
        }

        DragPayload.VirtualTileTrackRef virtualTrack = DragPayload.parseVirtualTileTrack(payload);
        if (virtualTrack != null) {
            workspaceDropHandler.moveVirtualTileTrackToWorkspace(virtualTrack.tileId(), virtualTrack.trackId(),
                    targetWorkspaceItemId, placeAfter);
            completed = true;
        }

        return completed;
    }

    private UUID parseWorkspaceTrackId(String payload) {
        return DragPayload.parseWorkspaceTrackId(payload);
    }

    private UUID parseWorkspaceQueueId(String payload) {
        return DragPayload.parseWorkspaceQueueId(payload);
    }

    private UUID parseWorkspaceVirtualTileId(String payload) { return DragPayload.parseWorkspaceVirtualTileId(payload); }

    private QueueTrackPayload parseQueueTrackPayload(String payload) {
        DragPayload.QueueTrackRef queueTrackRef = DragPayload.parseQueueTrack(payload);
        return queueTrackRef == null ? null : new QueueTrackPayload(queueTrackRef.queueId(), queueTrackRef.queueTrackId());
    }

    private List<UUID> parseAudioFileIds(String payload) {
        return DragPayload.parseAudioFileIds(payload);
    }

    private boolean isSupportedPayload(String payload) {
        return parseWorkspaceTrackId(payload) != null
                || parseWorkspaceQueueId(payload) != null
                || parseWorkspaceVirtualTileId(payload) != null
                || parseQueueTrackPayload(payload) != null
                || DragPayload.parseVirtualTileTrack(payload) != null
                || !parseAudioFileIds(payload).isEmpty();
    }

    private boolean isPointerInside(Node node, double sceneX, double sceneY) {
        Bounds bounds = node.localToScene(node.getBoundsInLocal());
        return bounds != null && bounds.contains(sceneX, sceneY);
    }

    private void updateEmptyState() {
        if (contentPane.getChildren().isEmpty()) {
            setTop(emptyStateLabel);
            BorderPane.setMargin(emptyStateLabel, new Insets(12d * interfaceScale, 12d * interfaceScale, 0, 12d * interfaceScale));
        } else {
            setTop(null);
        }
    }

    private void toggleContextMenu(Node owner, double screenX, double screenY) {
        if (workspaceContextMenu == null) {
            return;
        }
        if (workspaceContextMenu.isShowing()) {
            workspaceContextMenu.hide();
            return;
        }
        workspaceContextMenu.show(owner, screenX, screenY);
    }

    private void showWorkspaceContextMenu(ContextMenuEvent event, Node owner) {
        if (isInsideWorkspaceItem(event.getTarget())) return;
        toggleContextMenu(owner, event.getScreenX(), event.getScreenY());
        event.consume();
    }

    private boolean isInsideWorkspaceItem(Object target) {
        if (!(target instanceof Node node)) return false;
        Node current = node;
        while (current != null && current != this) {
            if (current instanceof WorkspaceItemView) return true;
            current = current.getParent();
        }
        return false;
    }

    private void hideContextMenu() {
        if (workspaceContextMenu != null && workspaceContextMenu.isShowing()) {
            workspaceContextMenu.hide();
        }
    }

    public interface WorkspaceDropHandler {
        void moveWorkspaceItem(UUID workspaceItemId, UUID targetWorkspaceItemId, boolean placeAfter);

        void moveWorkspaceItemToEnd(UUID workspaceItemId);

        void addAudioFilesAsSolo(List<UUID> audioFileIds, UUID targetWorkspaceItemId, boolean placeAfter);

        void moveQueueTrackToWorkspace(UUID sourceQueueId, UUID queueTrackId, UUID targetWorkspaceItemId, boolean placeAfter);

        void moveVirtualTileTrackToWorkspace(UUID tileId, UUID trackId, UUID targetWorkspaceItemId, boolean placeAfter);

        void adjustWorkspaceZoom(double deltaY);

        void createQueue();

        void createVirtualTile(VirtualTileLayout layout);
    }

    private record QueueTrackPayload(UUID queueId, UUID queueTrackId) {
    }

    private record InsertionSlot(WorkspaceItemView workspaceItemView, boolean placeAfter) {
    }
}
