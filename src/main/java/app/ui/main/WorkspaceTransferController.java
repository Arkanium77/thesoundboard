package app.ui.main;

import app.model.ProjectState;
import app.model.WorkspaceTrack;
import app.project.ProjectStateEditor;
import app.project.TrackMoveResult;
import app.ui.workspace.PlaybackTransfer;
import app.ui.workspace.WorkspaceTrackItem;
import app.ui.workspace.WorkspaceQueueItem;
import app.ui.workspace.WorkspaceVirtualTileItem;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Coordinates model edits and live playback independently of scene construction. A rejected edit leaves playback
 * attached to its owner. Successful moves detach before refreshing/disposal and attach only to the ID returned by
 * the editor. An already active destination queue retains priority; same-queue reorders never replace its player.
 * All calls belong to the FX thread, and saving is requested only after model and views agree. */
final class WorkspaceTransferController {
    private final ProjectStateEditor projectStateEditor;
    private final Supplier<ProjectState> state;
    private final Map<UUID, WorkspaceTrackItem> workspaceTrackItems;
    private final Map<UUID, WorkspaceQueueItem> workspaceQueueItems;
    private final Map<UUID, WorkspaceVirtualTileItem> workspaceVirtualTileItems;
    private final Views views;

    WorkspaceTransferController(ProjectStateEditor editor, Supplier<ProjectState> state,
                                Map<UUID, WorkspaceTrackItem> tracks, Map<UUID, WorkspaceQueueItem> queues,
                                Map<UUID, WorkspaceVirtualTileItem> virtualTiles, Views views) {
        this.projectStateEditor = editor;
        this.state = state;
        this.workspaceTrackItems = tracks;
        this.workspaceQueueItems = queues;
        this.workspaceVirtualTileItems = virtualTiles;
        this.views = views;
    }

    void moveWorkspaceTrackToVirtualTile(UUID workspaceTrackId, UUID tileId,
                                      UUID targetTrackId, boolean placeAfter) {
        WorkspaceTrackItem sourceItem = workspaceTrackItems.get(workspaceTrackId);
        TrackMoveResult result = projectStateEditor.moveWorkspaceTrackToVirtualTile(state.get(), workspaceTrackId,
                tileId, targetTrackId, placeAfter);
        if (!result.moved()) return;
        PlaybackTransfer transfer = sourceItem == null ? null : sourceItem.detachPlayback();
        views.removeWorkspaceTrackTile(workspaceTrackId);
        views.refreshVirtualTile(tileId);
        acceptVirtualPlayback(tileId, result.trackId(), transfer);
        views.refreshWorkspaceOrder();
        views.requestProjectSave();
    }

    void moveVirtualTileTrack(UUID sourceTileId, UUID targetTileId, UUID trackId,
                                      UUID targetTrackId, boolean placeAfter) {
        WorkspaceVirtualTileItem sourceItem = workspaceVirtualTileItems.get(sourceTileId);
        WorkspaceTrackItem sourceTrack = sourceItem == null ? null : sourceItem.getTrackItem(trackId);
        TrackMoveResult result = projectStateEditor.moveVirtualTileTrack(state.get(), sourceTileId, targetTileId,
                trackId, targetTrackId, placeAfter);
        if (!result.moved()) return;
        PlaybackTransfer transfer = sourceTrack == null ? null : sourceTrack.detachPlayback();
        views.refreshVirtualTile(sourceTileId);
        if (!sourceTileId.equals(targetTileId)) views.refreshVirtualTile(targetTileId);
        acceptVirtualPlayback(targetTileId, result.trackId(), transfer);
        views.requestProjectSave();
    }

    void moveVirtualTileTrackToWorkspace(UUID tileId, UUID trackId,
                                      UUID targetWorkspaceItemId, boolean placeAfter) {
        WorkspaceVirtualTileItem sourceItem = workspaceVirtualTileItems.get(tileId);
        WorkspaceTrackItem sourceTrack = sourceItem == null ? null : sourceItem.getTrackItem(trackId);
        WorkspaceTrack track = projectStateEditor.moveVirtualTileTrackToWorkspace(state.get(), tileId,
                trackId, targetWorkspaceItemId, placeAfter);
        if (track == null) return;
        PlaybackTransfer transfer = sourceTrack == null ? null : sourceTrack.detachPlayback();
        views.createWorkspaceTrackTile(track);
        WorkspaceTrackItem createdItem = workspaceTrackItems.get(track.getId());
        if (createdItem != null) createdItem.acceptPlayback(transfer);
        views.refreshVirtualTile(tileId);
        views.refreshWorkspaceOrder();
        views.requestProjectSave();
    }

    void moveQueueTrackToVirtualTile(UUID queueId, UUID queueTrackId, UUID tileId,
                                      UUID targetTrackId, boolean placeAfter) {
        WorkspaceQueueItem queueItem = workspaceQueueItems.get(queueId);
        TrackMoveResult result = projectStateEditor.moveQueueTrackToVirtualTile(state.get(), queueId, queueTrackId,
                tileId, targetTrackId, placeAfter);
        if (!result.moved()) return;
        PlaybackTransfer transfer = queueItem == null ? null : queueItem.detachPlayback(queueTrackId);
        views.refreshQueueView(queueId);
        views.refreshVirtualTile(tileId);
        acceptVirtualPlayback(tileId, result.trackId(), transfer);
        views.requestProjectSave();
    }

    void addWorkspaceTrackToQueue(UUID queueId, UUID workspaceTrackId, UUID targetQueueTrackId, boolean placeAfter) {
        if (workspaceTrackId == null) {
            return;
        }

        WorkspaceTrackItem sourceItem = workspaceTrackItems.get(workspaceTrackId);
        WorkspaceQueueItem targetItem = workspaceQueueItems.get(queueId);
        boolean targetHasPriority = targetItem != null && targetItem.hasActivePlayback();
        TrackMoveResult result = projectStateEditor.moveWorkspaceTrackToQueue(
                state.get(),
                workspaceTrackId,
                queueId,
                targetQueueTrackId,
                placeAfter
        );
        if (!result.moved()) {
            return;
        }
        PlaybackTransfer transfer = targetHasPriority || sourceItem == null ? null : sourceItem.detachPlayback();

        views.removeWorkspaceTrackTile(workspaceTrackId);

        views.refreshQueueView(queueId);
        acceptQueuePlayback(queueId, result.trackId(), transfer);
        views.refreshWorkspaceOrder();
        views.requestProjectSave();
    }

    void moveQueueTrack(UUID sourceQueueId, UUID targetQueueId, UUID queueTrackId, UUID targetQueueTrackId, boolean placeAfter) {
        WorkspaceQueueItem sourceItem = workspaceQueueItems.get(sourceQueueId);
        WorkspaceQueueItem targetItem = workspaceQueueItems.get(targetQueueId);
        boolean sameQueue = Objects.equals(sourceQueueId, targetQueueId);
        boolean targetHasPriority = !sameQueue && targetItem != null && targetItem.hasActivePlayback();
        TrackMoveResult result = projectStateEditor.moveQueueTrackToQueue(state.get(), sourceQueueId, targetQueueId, queueTrackId, targetQueueTrackId, placeAfter);
        if (!result.moved()) {
            return;
        }
        PlaybackTransfer transfer = sameQueue || targetHasPriority || sourceItem == null
                ? null : sourceItem.detachPlayback(queueTrackId);

        views.refreshQueueView(sourceQueueId);
        if (!Objects.equals(sourceQueueId, targetQueueId)) {
            views.refreshQueueView(targetQueueId);
        }
        acceptQueuePlayback(targetQueueId, result.trackId(), transfer);
        views.requestProjectSave();
    }

    void moveVirtualTileTrackToQueue(UUID tileId, UUID trackId, UUID queueId,
                                      UUID targetQueueTrackId, boolean placeAfter) {
        WorkspaceVirtualTileItem sourceItem = workspaceVirtualTileItems.get(tileId);
        WorkspaceTrackItem sourceTrack = sourceItem == null ? null : sourceItem.getTrackItem(trackId);
        WorkspaceQueueItem targetItem = workspaceQueueItems.get(queueId);
        boolean targetHasPriority = targetItem != null && targetItem.hasActivePlayback();
        TrackMoveResult result = projectStateEditor.moveVirtualTileTrackToQueue(state.get(), tileId, trackId, queueId,
                targetQueueTrackId, placeAfter);
        if (!result.moved()) return;
        PlaybackTransfer transfer = targetHasPriority || sourceTrack == null ? null : sourceTrack.detachPlayback();
        views.refreshVirtualTile(tileId);
        views.refreshQueueView(queueId);
        acceptQueuePlayback(queueId, result.trackId(), transfer);
        views.requestProjectSave();
    }

    void moveQueueTrackToWorkspace(UUID sourceQueueId, UUID queueTrackId, UUID targetWorkspaceItemId, boolean placeAfter) {
        WorkspaceQueueItem sourceItem = workspaceQueueItems.get(sourceQueueId);
        WorkspaceTrack workspaceTrack = projectStateEditor.moveQueueTrackToWorkspace(
                state.get(),
                sourceQueueId,
                queueTrackId,
                targetWorkspaceItemId,
                placeAfter
        );
        if (workspaceTrack == null) {
            return;
        }
        PlaybackTransfer transfer = sourceItem == null ? null : sourceItem.detachPlayback(queueTrackId);

        views.createWorkspaceTrackTile(workspaceTrack);
        WorkspaceTrackItem createdItem = workspaceTrackItems.get(workspaceTrack.getId());
        if (createdItem != null) createdItem.acceptPlayback(transfer);
        views.refreshQueueView(sourceQueueId);
        views.refreshWorkspaceOrder();
        views.requestProjectSave();
    }

    private void acceptVirtualPlayback(UUID tileId, UUID trackId, PlaybackTransfer playback) {
        if (trackId == null) return;
        WorkspaceVirtualTileItem item = workspaceVirtualTileItems.get(tileId);
        WorkspaceTrackItem track = item == null ? null : item.getTrackItem(trackId);
        if (track != null) track.acceptPlayback(playback);
    }

    private void acceptQueuePlayback(UUID queueId, UUID trackId, PlaybackTransfer playback) {
        if (trackId == null) return;
        WorkspaceQueueItem item = workspaceQueueItems.get(queueId);
        if (item != null) item.acceptPlayback(trackId, playback);
    }
    interface Views {
        void createWorkspaceTrackTile(WorkspaceTrack track);
        void removeWorkspaceTrackTile(UUID id);
        void refreshQueueView(UUID id);
        void refreshVirtualTile(UUID id);
        void refreshWorkspaceOrder();
        void requestProjectSave();
    }
}
