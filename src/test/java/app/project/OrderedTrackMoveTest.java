package app.project;

import app.model.ProjectState;
import app.model.QueueTrack;
import app.model.VirtualTileLayout;
import app.model.VirtualTileTrack;
import app.model.WorkspaceQueue;
import app.model.WorkspaceVirtualTile;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class OrderedTrackMoveTest {
    @Test
    void queueRejectsMissingReorderTargetAndRetainsIdentityOnSuccessfulReorder() {
        ProjectState state = new ProjectState(2);
        ProjectStateEditor editor = new ProjectStateEditor();
        WorkspaceQueue queue = editor.createWorkspaceQueue(state, "Queue", 0.8d);
        List<QueueTrack> tracks = editor.addQueueTracks(state, queue.getId(), List.of(UUID.randomUUID(), UUID.randomUUID()));
        List<UUID> ids = tracks.stream().map(QueueTrack::getId).toList();
        TrackMoveResult rejected = editor.moveQueueTrackToQueue(state, queue.getId(), queue.getId(), ids.getFirst(), UUID.randomUUID(), false);
        Assertions.assertThat(rejected.moved()).isFalse();
        Assertions.assertThat(queue.getTracks()).extracting(QueueTrack::getId).containsExactlyElementsOf(ids);
        TrackMoveResult moved = editor.moveQueueTrackToQueue(state, queue.getId(), queue.getId(), ids.getFirst(), ids.getLast(), true);
        Assertions.assertThat(moved.trackId()).isEqualTo(ids.getFirst());
        Assertions.assertThat(queue.getTracks()).extracting(QueueTrack::getId).containsExactly(ids.getLast(), ids.getFirst());
        Assertions.assertThat(queue.getTracks()).extracting(QueueTrack::getOrder).containsExactly(0, 1);
    }

    @Test
    void virtualTileAppendsOnMissingTargetAndKeepsTrackIdentityWhenReordering() {
        ProjectState state = new ProjectState(2);
        ProjectStateEditor editor = new ProjectStateEditor();
        WorkspaceVirtualTile tile = editor.createWorkspaceVirtualTile(state, VirtualTileLayout.TWO_BY_TWO);
        List<VirtualTileTrack> tracks = editor.addVirtualTileTracks(state, tile.getId(), List.of(UUID.randomUUID(), UUID.randomUUID()), 0.8d);
        UUID movedId = tracks.getFirst().getId();
        UUID otherId = tracks.getLast().getId();
        TrackMoveResult moved = editor.moveVirtualTileTrack(state, tile.getId(), tile.getId(), movedId, UUID.randomUUID(), false);
        Assertions.assertThat(moved.trackId()).isEqualTo(movedId);
        Assertions.assertThat(tile.getTracks()).extracting(VirtualTileTrack::getId).containsExactly(otherId, movedId);
        Assertions.assertThat(tile.getTracks()).extracting(VirtualTileTrack::getOrder).containsExactly(0, 1);
    }
}
