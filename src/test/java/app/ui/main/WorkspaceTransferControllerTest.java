package app.ui.main;

import app.audio.PlayingTrack;
import app.model.AudioFile;
import app.model.PlaybackStatus;
import app.model.ProjectState;
import app.model.VirtualTileLayout;
import app.model.WorkspaceQueue;
import app.model.WorkspaceTrack;
import app.model.WorkspaceVirtualTile;
import app.project.AudioFileIndex;
import app.project.ProjectStateEditor;
import app.ui.workspace.PlaybackTransfer;
import app.ui.workspace.WorkspaceQueueItem;
import app.ui.workspace.WorkspaceTrackItem;
import app.ui.workspace.WorkspaceVirtualTileItem;
import javafx.util.Duration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class WorkspaceTransferControllerTest implements WorkspaceTransferController.Views {
    @TempDir private Path root;
    private final ProjectState state = new ProjectState(2);
    private final ProjectStateEditor editor = new ProjectStateEditor();
    private final Map<UUID, WorkspaceTrackItem> tracks = new LinkedHashMap<>();
    private final Map<UUID, WorkspaceQueueItem> queues = new LinkedHashMap<>();
    private final Map<UUID, WorkspaceVirtualTileItem> tiles = new LinkedHashMap<>();
    private final UUID audioId = UUID.randomUUID();
    private AudioFileIndex index;
    private WorkspaceTransferController controller;
    private int saves;

    @BeforeEach
    void configure() {
        state.setAudioFiles(List.of(new AudioFile(audioId, "one.mp3", "One", false)));
        index = new AudioFileIndex(state.getAudioFiles());
        controller = new WorkspaceTransferController(editor, () -> state, tracks, queues, tiles, this);
    }

    @Test
    void routesSamePlayerThroughEveryContainerDirectionUsingActualDestinationIds() {
        WorkspaceTrack source = editor.addWorkspaceTrack(state, audioId, 0.65d, true);
        createWorkspaceTrackTile(source);
        TestPlayer player = new TestPlayer();
        tracks.get(source.getId()).acceptPlayback(new PlaybackTransfer(player, PlaybackStatus.PLAYING, false));
        WorkspaceQueue first = queue();
        WorkspaceQueue second = queue();
        WorkspaceVirtualTile left = tile();
        WorkspaceVirtualTile right = tile();

        controller.addWorkspaceTrackToQueue(first.getId(), source.getId(), null, false);
        controller.moveQueueTrack(first.getId(), second.getId(), first.getTracks().getFirst().getId(), null, false);
        controller.moveQueueTrackToVirtualTile(second.getId(), second.getTracks().getFirst().getId(), left.getId(), null, false);
        controller.moveVirtualTileTrack(left.getId(), right.getId(), left.getTracks().getFirst().getId(), null, false);
        controller.moveVirtualTileTrackToWorkspace(right.getId(), right.getTracks().getFirst().getId(), null, false);
        UUID solo = state.getWorkspaceTracks().getFirst().getId();
        controller.moveWorkspaceTrackToVirtualTile(solo, left.getId(), null, false);
        controller.moveVirtualTileTrackToQueue(left.getId(), left.getTracks().getFirst().getId(), first.getId(), null, false);
        controller.moveQueueTrackToWorkspace(first.getId(), first.getTracks().getFirst().getId(), null, false);

        WorkspaceTrackItem destination = tracks.values().iterator().next();
        Assertions.assertThat(destination.detachPlayback().playingTrack()).isSameAs(player);
        Assertions.assertThat(player.disposed).isFalse();
        Assertions.assertThat(player.getCurrentTime()).isEqualTo(Duration.seconds(7));
        Assertions.assertThat(saves).isEqualTo(8);
    }

    @Test
    void fullTileRejectsMoveWithoutDetachingPlaybackOrSaving() {
        WorkspaceVirtualTile tile = tile();
        editor.addVirtualTileTracks(state, tile.getId(), List.of(audioId, audioId, audioId, audioId), 0.8d);
        refreshVirtualTile(tile.getId());
        WorkspaceTrack source = editor.addWorkspaceTrack(state, audioId, 0.65d, true);
        createWorkspaceTrackTile(source);
        TestPlayer player = new TestPlayer();
        tracks.get(source.getId()).acceptPlayback(new PlaybackTransfer(player, PlaybackStatus.PLAYING, false));

        controller.moveWorkspaceTrackToVirtualTile(source.getId(), tile.getId(), null, false);

        Assertions.assertThat(state.getWorkspaceTracks()).containsExactly(source);
        Assertions.assertThat(tracks.get(source.getId()).detachPlayback().playingTrack()).isSameAs(player);
        Assertions.assertThat(saves).isZero();
    }

    @Test
    void activeQueueKeepsPriorityAndSameQueueReorderKeepsItsPlayer() {
        WorkspaceQueue source = queue();
        WorkspaceQueue target = queue();
        UUID sourceId = editor.addQueueTracks(state, source.getId(), List.of(audioId)).getFirst().getId();
        UUID targetId = editor.addQueueTracks(state, target.getId(), List.of(audioId)).getFirst().getId();
        refreshQueueView(source.getId());
        refreshQueueView(target.getId());
        TestPlayer sourcePlayer = new TestPlayer();
        TestPlayer targetPlayer = new TestPlayer();
        queues.get(source.getId()).acceptPlayback(sourceId, new PlaybackTransfer(sourcePlayer, PlaybackStatus.PLAYING, false));
        queues.get(target.getId()).acceptPlayback(targetId, new PlaybackTransfer(targetPlayer, PlaybackStatus.PLAYING, false));

        controller.moveQueueTrack(source.getId(), target.getId(), sourceId, targetId, false);
        UUID movedId = target.getTracks().getFirst().getId();
        controller.moveQueueTrack(target.getId(), target.getId(), targetId, movedId, false);

        Assertions.assertThat(target.getSelectedTrackId()).isEqualTo(targetId);
        Assertions.assertThat(queues.get(target.getId()).detachPlayback(targetId).playingTrack()).isSameAs(targetPlayer);
        Assertions.assertThat(targetPlayer.disposed).isFalse();
        Assertions.assertThat(sourcePlayer.disposed).isTrue();
        Assertions.assertThat(target.getTracks()).extracting(track -> track.getId()).containsExactly(targetId, movedId);
    }

    private WorkspaceQueue queue() {
        WorkspaceQueue queue = editor.createWorkspaceQueue(state, "Queue", 0.8d);
        queues.put(queue.getId(), new WorkspaceQueueItem(root, queue, index, path -> new TestPlayer(), 1d, exception -> {
            throw new AssertionError(exception);
        }));
        return queue;
    }

    private WorkspaceVirtualTile tile() {
        WorkspaceVirtualTile tile = editor.createWorkspaceVirtualTile(state, VirtualTileLayout.TWO_BY_TWO);
        tiles.put(tile.getId(), new WorkspaceVirtualTileItem(root, tile, index, path -> new TestPlayer(), 1d, exception -> {
            throw new AssertionError(exception);
        }));
        return tile;
    }

    @Override public void createWorkspaceTrackTile(WorkspaceTrack track) {
        tracks.put(track.getId(), new WorkspaceTrackItem(root, track, index.findOrMissing(track.getAudioFileId()),
                path -> new TestPlayer(), 1d, exception -> { throw new AssertionError(exception); }));
    }
    @Override public void removeWorkspaceTrackTile(UUID id) { tracks.remove(id).dispose(); }
    @Override public void refreshQueueView(UUID id) { queues.get(id).refreshAfterMutation(); }
    @Override public void refreshVirtualTile(UUID id) { tiles.get(id).refreshAfterMutation(); }
    @Override public void refreshWorkspaceOrder() { }
    @Override public void requestProjectSave() { saves++; }

    private static final class TestPlayer implements PlayingTrack {
        private boolean disposed;
        private PlaybackStatus status = PlaybackStatus.PLAYING;
        private Duration currentTime = Duration.seconds(7);
        @Override public void play() { status = PlaybackStatus.PLAYING; }
        @Override public void pause() { status = PlaybackStatus.PAUSED; }
        @Override public void stop() { status = PlaybackStatus.STOPPED; }
        @Override public void seek(Duration position) { currentTime = position; }
        @Override public void setVolume(double volume) { }
        @Override public void setLoop(boolean loop) { }
        @Override public Duration getCurrentTime() { return currentTime; }
        @Override public Duration getTotalDuration() { return Duration.seconds(10); }
        @Override public PlaybackStatus getStatus() { return status; }
        @Override public void dispose() { disposed = true; }
    }
}
