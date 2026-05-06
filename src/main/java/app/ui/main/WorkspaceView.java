package app.ui.main;

import app.config.UiConfig;
import app.ui.drag.DragPayload;
import app.ui.queue.QueueView;
import app.ui.tile.TrackTileView;
import app.ui.workspace.WorkspaceInsertionMarker;
import app.ui.workspace.WorkspaceItemView;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class WorkspaceView extends BorderPane {
    private final FlowPane contentPane = new FlowPane();
    private final Label emptyStateLabel = new Label("Add tracks from the left tree or create a queue from the workspace context menu.");
    private final ScrollPane scrollPane = new ScrollPane(contentPane);
    private final WorkspaceDropHandler workspaceDropHandler;
    private final UiConfig uiConfig;
    private double currentTileWidth;
    private double currentTileHeight;
    private ContextMenu workspaceContextMenu;
    private WorkspaceItemView insertionTargetView;
    private boolean insertionAfter;

    public WorkspaceView(UiConfig uiConfig, WorkspaceDropHandler workspaceDropHandler) {
        this.uiConfig = uiConfig;
        this.workspaceDropHandler = workspaceDropHandler;
        this.currentTileWidth = uiConfig.getTrackTileWidth();
        this.currentTileHeight = uiConfig.getTrackTileHeight();

        contentPane.setPadding(new Insets(12));
        contentPane.setHgap(12);
        contentPane.setVgap(12);
        contentPane.setPrefWrapLength(uiConfig.getMinWidth());
        contentPane.setAlignment(Pos.TOP_LEFT);

        scrollPane.setFitToWidth(true);
        scrollPane.setContent(contentPane);
        scrollPane.viewportBoundsProperty().addListener((observable, oldValue, newValue) -> updateContainerSize(newValue));
        addEventFilter(ScrollEvent.SCROLL, event -> {
            if (!event.isControlDown()) {
                return;
            }
            workspaceDropHandler.adjustWorkspaceZoom(event.getDeltaY());
            event.consume();
        });

        setCenter(scrollPane);
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
            }
        }
        updateQueueWidths();
    }

    private void configureContextMenu() {
        MenuItem createQueueItem = new MenuItem("Create Queue");
        createQueueItem.setOnAction(event -> workspaceDropHandler.createQueue());
        workspaceContextMenu = new ContextMenu(createQueueItem);

        setOnContextMenuRequested(event -> toggleContextMenu(this, event.getScreenX(), event.getScreenY()));
        scrollPane.setOnContextMenuRequested(event -> toggleContextMenu(scrollPane, event.getScreenX(), event.getScreenY()));
        contentPane.setOnContextMenuRequested(event -> toggleContextMenu(contentPane, event.getScreenX(), event.getScreenY()));
        emptyStateLabel.setOnContextMenuRequested(event -> toggleContextMenu(emptyStateLabel, event.getScreenX(), event.getScreenY()));

        setOnMousePressed(event -> hideContextMenu());
        scrollPane.setOnMousePressed(event -> hideContextMenu());
        contentPane.setOnMousePressed(event -> hideContextMenu());
        emptyStateLabel.setOnMousePressed(event -> hideContextMenu());
    }

    private void configureContainerDragAndDrop() {
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
            if (!isSupportedPayload(payload)) {
                event.setDropCompleted(false);
                return;
            }

            boolean completed = false;
            List<UUID> audioFileIds = parseAudioFileIds(payload);
            if (!audioFileIds.isEmpty()) {
                workspaceDropHandler.addAudioFilesAsSolo(audioFileIds, null, false);
                completed = true;
            }

            UUID workspaceItemId = parseWorkspaceTrackId(payload);
            if (workspaceItemId == null) {
                workspaceItemId = parseWorkspaceQueueId(payload);
            }
            if (workspaceItemId != null) {
                workspaceDropHandler.moveWorkspaceItemToEnd(workspaceItemId);
                completed = true;
            }

            QueueTrackPayload queueTrackPayload = parseQueueTrackPayload(payload);
            if (queueTrackPayload != null) {
                workspaceDropHandler.moveQueueTrackToWorkspace(queueTrackPayload.queueId(), queueTrackPayload.queueTrackId(), null, false);
                completed = true;
            }

            clearInsertionMarker();
            event.setDropCompleted(completed);
            event.consume();
        });
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
            } else if (node instanceof TrackTileView) {
                content.putString(DragPayload.workspaceTrack(workspaceItemView.getWorkspaceItemId()));
            } else {
                return;
            }
            dragboard.setContent(content);
            event.consume();
        });

        if (node instanceof QueueView) {
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
            boolean completed = false;
            boolean placeAfter = event.getX() >= node.getBoundsInLocal().getWidth() / 2d;

            UUID workspaceItemId = parseWorkspaceTrackId(payload);
            if (workspaceItemId == null) {
                workspaceItemId = parseWorkspaceQueueId(payload);
            }
            if (workspaceItemId != null && !workspaceItemId.equals(workspaceItemView.getWorkspaceItemId())) {
                workspaceDropHandler.moveWorkspaceItem(workspaceItemId, workspaceItemView.getWorkspaceItemId(), placeAfter);
                completed = true;
            }

            List<UUID> audioFileIds = parseAudioFileIds(payload);
            if (!audioFileIds.isEmpty()) {
                workspaceDropHandler.addAudioFilesAsSolo(audioFileIds, workspaceItemView.getWorkspaceItemId(), placeAfter);
                completed = true;
            }

            QueueTrackPayload queueTrackPayload = parseQueueTrackPayload(payload);
            if (queueTrackPayload != null) {
                workspaceDropHandler.moveQueueTrackToWorkspace(
                        queueTrackPayload.queueId(),
                        queueTrackPayload.queueTrackId(),
                        workspaceItemView.getWorkspaceItemId(),
                        placeAfter
                );
                completed = true;
            }

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

    private List<UUID> parseAudioFileIds(String payload) {
        return DragPayload.parseAudioFileIds(payload);
    }

    private boolean isSupportedPayload(String payload) {
        return parseWorkspaceTrackId(payload) != null
                || parseWorkspaceQueueId(payload) != null
                || parseQueueTrackPayload(payload) != null
                || !parseAudioFileIds(payload).isEmpty();
    }

    private boolean isPointerInside(Node node, double sceneX, double sceneY) {
        Bounds bounds = node.localToScene(node.getBoundsInLocal());
        return bounds != null && bounds.contains(sceneX, sceneY);
    }

    private void updateEmptyState() {
        if (contentPane.getChildren().isEmpty()) {
            setTop(emptyStateLabel);
            BorderPane.setMargin(emptyStateLabel, new Insets(12, 12, 0, 12));
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

        void adjustWorkspaceZoom(double deltaY);

        void createQueue();
    }

    private record QueueTrackPayload(UUID queueId, UUID queueTrackId) {
    }
}
