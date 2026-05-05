package app.ui.main;

import app.config.UiConfig;
import app.ui.tile.TrackTileView;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.input.TransferMode;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.TilePane;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class WorkspaceView extends BorderPane {
    private static final String WORKSPACE_TRACK_PREFIX = "workspace-track:";
    private static final String AUDIO_FILE_PREFIX = "audio-files:";

    private final TilePane trackContainer = new TilePane();
    private final Label emptyStateLabel = new Label("Add tracks from the left tree to build the workspace.");
    private final ScrollPane scrollPane = new ScrollPane(trackContainer);
    private final WorkspaceDropHandler workspaceDropHandler;
    private TrackTileView insertionTargetTile;
    private boolean insertionAfter;

    public WorkspaceView(UiConfig uiConfig, WorkspaceDropHandler workspaceDropHandler) {
        this.workspaceDropHandler = workspaceDropHandler;
        trackContainer.setPadding(new Insets(12));
        trackContainer.setHgap(12);
        trackContainer.setVgap(12);
        trackContainer.setPrefTileWidth(uiConfig.getTrackTileWidth());
        trackContainer.setPrefTileHeight(uiConfig.getTrackTileHeight());
        trackContainer.setAlignment(Pos.TOP_LEFT);
        configureContainerDragAndDrop();

        scrollPane.setFitToWidth(true);
        scrollPane.setContent(trackContainer);
        scrollPane.viewportBoundsProperty().addListener((observable, oldValue, newValue) -> updateContainerWidth(newValue));

        setCenter(scrollPane);
        configureRootDragAndDrop();
        updateEmptyState();
    }

    public void setTrackTiles(List<TrackTileView> trackTileViews) {
        trackTileViews.forEach(this::configureDragAndDrop);
        trackContainer.getChildren().setAll(trackTileViews);
        updateEmptyState();
    }

    public List<TrackTileView> clearAndReturnCurrentTiles() {
        List<TrackTileView> trackTileViews = new ArrayList<>();
        trackContainer.getChildren().forEach(node -> {
            if (node instanceof TrackTileView trackTileView) {
                trackTileView.setInsertionMarker(TrackTileView.InsertionMarker.NONE);
                trackTileViews.add(trackTileView);
            }
        });

        trackContainer.getChildren().clear();
        updateEmptyState();
        return trackTileViews;
    }

    private void updateContainerWidth(Bounds viewportBounds) {
        trackContainer.setPrefWidth(Math.max(viewportBounds.getWidth(), trackContainer.getPrefTileWidth()));
    }

    private void configureDragAndDrop(TrackTileView trackTileView) {
        trackTileView.setOnDragDetected(event -> {
            if (trackContainer.getChildren().size() < 2) {
                return;
            }

            var dragboard = trackTileView.startDragAndDrop(TransferMode.MOVE);
            var content = new javafx.scene.input.ClipboardContent();
            content.putString(WORKSPACE_TRACK_PREFIX + trackTileView.getWorkspaceTrackId());
            dragboard.setContent(content);
            event.consume();
        });

        trackTileView.setOnDragOver(event -> {
            if (!isSupportedDragPayload(event.getDragboard().getString())) {
                return;
            }

            UUID draggedTrackId = parseWorkspaceTrackId(event.getDragboard().getString());
            if (draggedTrackId != null && draggedTrackId.equals(trackTileView.getWorkspaceTrackId())) {
                return;
            }

            updateInsertionMarker(trackTileView, event.getX() >= trackTileView.getWidth() / 2d);
            event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            event.consume();
        });

        trackTileView.setOnDragExited(event -> {
            if (!isPointerInside(trackTileView, event.getSceneX(), event.getSceneY())) {
                clearInsertionMarker();
            }
        });

        trackTileView.setOnDragDropped(event -> {
            boolean completed = false;
            String payload = event.getDragboard().getString();
            boolean placeAfter = event.getX() >= trackTileView.getWidth() / 2d;

            UUID draggedTrackId = parseWorkspaceTrackId(payload);
            if (draggedTrackId != null && !draggedTrackId.equals(trackTileView.getWorkspaceTrackId())) {
                workspaceDropHandler.moveTrack(draggedTrackId, trackTileView.getWorkspaceTrackId(), placeAfter);
                completed = true;
            }

            List<UUID> audioFileIds = parseAudioFileIds(payload);
            if (!audioFileIds.isEmpty()) {
                workspaceDropHandler.addAudioFiles(audioFileIds, trackTileView.getWorkspaceTrackId(), placeAfter);
                completed = true;
            }

            clearInsertionMarker();
            event.setDropCompleted(completed);
            event.consume();
        });

        trackTileView.setOnDragDone(event -> clearInsertionMarker());
    }

    private void configureContainerDragAndDrop() {
        trackContainer.setOnDragOver(event -> {
            if (!isSupportedDragPayload(event.getDragboard().getString())) {
                return;
            }

            Node targetNode = event.getPickResult().getIntersectedNode();
            if (findTrackTile(targetNode) == null) {
                showAppendMarker();
            }

            event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            event.consume();
        });

        trackContainer.setOnDragExited(event -> {
            if (!isPointerInside(trackContainer, event.getSceneX(), event.getSceneY())) {
                clearInsertionMarker();
            }
        });

        trackContainer.setOnDragDropped(event -> {
            String payload = event.getDragboard().getString();
            if (!isSupportedDragPayload(payload)) {
                event.setDropCompleted(false);
                return;
            }

            boolean completed = false;
            UUID draggedTrackId = parseWorkspaceTrackId(payload);
            if (draggedTrackId != null && insertionTargetTile != null) {
                workspaceDropHandler.moveTrack(draggedTrackId, insertionTargetTile.getWorkspaceTrackId(), insertionAfter);
                completed = true;
            } else if (draggedTrackId != null) {
                workspaceDropHandler.moveTrackToEnd(draggedTrackId);
                completed = true;
            }

            List<UUID> audioFileIds = parseAudioFileIds(payload);
            if (!audioFileIds.isEmpty()) {
                if (insertionTargetTile != null) {
                    workspaceDropHandler.addAudioFiles(audioFileIds, insertionTargetTile.getWorkspaceTrackId(), insertionAfter);
                } else {
                    workspaceDropHandler.addAudioFiles(audioFileIds, null, false);
                }
                completed = true;
            }

            clearInsertionMarker();
            event.setDropCompleted(completed);
            event.consume();
        });
    }

    private void configureRootDragAndDrop() {
        setOnDragOver(event -> {
            if (!isSupportedDragPayload(event.getDragboard().getString())) {
                return;
            }

            if (trackContainer.getChildren().isEmpty()) {
                clearInsertionMarker();
            }

            event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            event.consume();
        });

        setOnDragDropped(event -> {
            String payload = event.getDragboard().getString();
            if (!isSupportedDragPayload(payload)) {
                event.setDropCompleted(false);
                return;
            }

            boolean completed = false;
            List<UUID> audioFileIds = parseAudioFileIds(payload);
            if (!audioFileIds.isEmpty()) {
                workspaceDropHandler.addAudioFiles(audioFileIds, null, false);
                completed = true;
            }

            UUID draggedTrackId = parseWorkspaceTrackId(payload);
            if (draggedTrackId != null) {
                workspaceDropHandler.moveTrackToEnd(draggedTrackId);
                completed = true;
            }

            clearInsertionMarker();
            event.setDropCompleted(completed);
            event.consume();
        });
    }

    private void updateInsertionMarker(TrackTileView trackTileView, boolean placeAfter) {
        if (insertionTargetTile == trackTileView && insertionAfter == placeAfter) {
            return;
        }

        clearInsertionMarker();
        insertionTargetTile = trackTileView;
        insertionAfter = placeAfter;
        trackTileView.setInsertionMarker(placeAfter ? TrackTileView.InsertionMarker.RIGHT : TrackTileView.InsertionMarker.LEFT);
    }

    private void showAppendMarker() {
        clearInsertionMarker();
        TrackTileView lastTile = getLastTile();
        if (lastTile != null) {
            insertionTargetTile = lastTile;
            insertionAfter = true;
            lastTile.setInsertionMarker(TrackTileView.InsertionMarker.RIGHT);
        }
    }

    private void clearInsertionMarker() {
        if (insertionTargetTile != null) {
            insertionTargetTile.setInsertionMarker(TrackTileView.InsertionMarker.NONE);
            insertionTargetTile = null;
        }
        insertionAfter = false;
    }

    private boolean isSupportedDragPayload(String payload) {
        return parseWorkspaceTrackId(payload) != null || !parseAudioFileIds(payload).isEmpty();
    }

    private UUID parseWorkspaceTrackId(String payload) {
        if (payload == null || !payload.startsWith(WORKSPACE_TRACK_PREFIX)) {
            return null;
        }

        return parseTrackId(payload.substring(WORKSPACE_TRACK_PREFIX.length()));
    }

    private List<UUID> parseAudioFileIds(String payload) {
        if (payload == null || !payload.startsWith(AUDIO_FILE_PREFIX)) {
            return List.of();
        }

        return List.of(payload.substring(AUDIO_FILE_PREFIX.length()).split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(this::parseTrackId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private UUID parseTrackId(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private TrackTileView getLastTile() {
        for (int index = trackContainer.getChildren().size() - 1; index >= 0; index--) {
            Node node = trackContainer.getChildren().get(index);
            if (node instanceof TrackTileView trackTileView) {
                return trackTileView;
            }
        }
        return null;
    }

    private TrackTileView findTrackTile(Node node) {
        Node current = node;
        while (current != null && current != trackContainer) {
            if (current instanceof TrackTileView trackTileView) {
                return trackTileView;
            }
            current = current.getParent();
        }
        return null;
    }

    private boolean isPointerInside(Node node, double sceneX, double sceneY) {
        Bounds bounds = node.localToScene(node.getBoundsInLocal());
        return bounds != null && bounds.contains(sceneX, sceneY);
    }

    private void updateEmptyState() {
        if (trackContainer.getChildren().isEmpty()) {
            setTop(emptyStateLabel);
            BorderPane.setMargin(emptyStateLabel, new Insets(12, 12, 0, 12));
        } else {
            setTop(null);
        }
    }

    public interface WorkspaceDropHandler {
        void moveTrack(UUID workspaceTrackId, UUID targetWorkspaceTrackId, boolean placeAfter);

        void moveTrackToEnd(UUID workspaceTrackId);

        void addAudioFiles(List<UUID> audioFileIds, UUID targetWorkspaceTrackId, boolean placeAfter);
    }
}
