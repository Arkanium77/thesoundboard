package app.project;

import app.model.ProjectState;
import app.model.WorkspaceVirtualTile;
import app.model.VirtualTileLayout;
import app.model.WorkspaceTrack;
import app.persistence.ProjectStateRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

class WorkspaceVirtualTileStateTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void limitsVirtualTileToFourIndependentTracksAndCopiesItDeeply() {
        ProjectState state = new ProjectState(2);
        ProjectStateEditor editor = new ProjectStateEditor();
        WorkspaceVirtualTile tile = editor.createWorkspaceVirtualTile(state, VirtualTileLayout.TWO_BY_TWO);
        List<UUID> audioFileIds = List.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
        );

        editor.addVirtualTileTracks(state, tile.getId(), audioFileIds, 0.8d);
        tile.getTracks().getFirst().setLoop(true);
        ProjectState copy = ProjectStateCopySupport.copy(state);
        copy.getWorkspaceVirtualTiles().getFirst().getTracks().getFirst().setVolume(0.2d);

        Assertions.assertThat(tile.getTracks()).hasSize(VirtualTileLayout.TWO_BY_TWO.getCapacity());
        Assertions.assertThat(tile.getTracks()).extracting(track -> track.getAudioFileId())
                .containsExactlyElementsOf(audioFileIds.subList(0, 4));
        Assertions.assertThat(tile.getTracks().getFirst().getVolume()).isEqualTo(0.8d);
        Assertions.assertThat(copy.getWorkspaceVirtualTiles().getFirst().getTracks().getFirst().isLoop()).isTrue();
    }

    @Test
    void persistsVirtualTilesAndLoadsOlderStateWithoutThem() throws Exception {
        ProjectStateRepository repository = new ProjectStateRepository(".soundboard-project.json");
        ProjectState state = new ProjectState(2);
        ProjectStateEditor editor = new ProjectStateEditor();
        WorkspaceVirtualTile tile = editor.createWorkspaceVirtualTile(state, VirtualTileLayout.TWO_BY_THREE);
        editor.addVirtualTileTracks(state, tile.getId(), List.of(UUID.randomUUID()), 0.65d);

        repository.save(temporaryDirectory, state);
        ProjectState loaded = repository.load(temporaryDirectory).orElseThrow();

        Assertions.assertThat(loaded.getWorkspaceVirtualTiles()).hasSize(1);
        Assertions.assertThat(loaded.getWorkspaceVirtualTiles().getFirst().getTracks()).hasSize(1);
        Assertions.assertThat(loaded.getWorkspaceVirtualTiles().getFirst().getLayout())
                .isEqualTo(VirtualTileLayout.TWO_BY_THREE);

        Files.writeString(repository.resolveStateFile(temporaryDirectory), """
                {
                  "schemaVersion": 2,
                  "audioFiles": [],
                  "workspaceTracks": [],
                  "workspaceQueues": []
                }
                """);

        Assertions.assertThat(repository.load(temporaryDirectory).orElseThrow().getWorkspaceVirtualTiles()).isEmpty();
    }

    @Test
    void supportsSixTrackLayoutAndDefaultsEarlierVirtualTilesToTwoByTwo() throws Exception {
        ProjectState state = new ProjectState(2);
        ProjectStateEditor editor = new ProjectStateEditor();
        WorkspaceVirtualTile tile = editor.createWorkspaceVirtualTile(state, VirtualTileLayout.TWO_BY_THREE);
        editor.addVirtualTileTracks(state, tile.getId(), List.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
        ), 0.8d);

        Assertions.assertThat(tile.getTracks()).hasSize(6);

        ProjectStateRepository repository = new ProjectStateRepository(".soundboard-project.json");
        Files.writeString(repository.resolveStateFile(temporaryDirectory), """
                {
                  "schemaVersion": 2,
                  "audioFiles": [],
                  "workspaceTracks": [],
                  "workspaceQueues": [],
                  "workspaceVirtualTiles": [{"id":"10000000-0000-0000-0000-000000000001","order":0,"tracks":[]}]
                }
                """);

        Assertions.assertThat(repository.load(temporaryDirectory).orElseThrow()
                .getWorkspaceVirtualTiles().getFirst().getLayout()).isEqualTo(VirtualTileLayout.TWO_BY_TWO);
    }

    @Test
    void supportsSixteenTrackDenseLayout() {
        ProjectState state = new ProjectState(2);
        ProjectStateEditor editor = new ProjectStateEditor();
        WorkspaceVirtualTile tile = editor.createWorkspaceVirtualTile(state, VirtualTileLayout.FOUR_BY_FOUR);
        List<UUID> audioFileIds = IntStream.range(0, 18)
                .mapToObj(index -> UUID.randomUUID())
                .toList();

        editor.addVirtualTileTracks(state, tile.getId(), audioFileIds, 0.8d);

        Assertions.assertThat(tile.getTracks()).hasSize(16);
        Assertions.assertThat(tile.getLayout()).isEqualTo(VirtualTileLayout.FOUR_BY_FOUR);
    }

    @Test
    void supportsNineTrackThreeByThreeLayout() {
        ProjectState state = new ProjectState(2);
        ProjectStateEditor editor = new ProjectStateEditor();
        WorkspaceVirtualTile tile = editor.createWorkspaceVirtualTile(state, VirtualTileLayout.THREE_BY_THREE);
        List<UUID> audioFileIds = IntStream.range(0, 11).mapToObj(index -> UUID.randomUUID()).toList();

        editor.addVirtualTileTracks(state, tile.getId(), audioFileIds, 0.8d);

        Assertions.assertThat(tile.getTracks()).hasSize(9);
        Assertions.assertThat(tile.getLayout()).isEqualTo(VirtualTileLayout.THREE_BY_THREE);
    }

    @Test
    void insertsReordersAndMovesTracksBackToWorkspace() {
        ProjectState state = new ProjectState(2);
        ProjectStateEditor editor = new ProjectStateEditor();
        WorkspaceVirtualTile tile = editor.createWorkspaceVirtualTile(state, VirtualTileLayout.TWO_BY_TWO);
        UUID firstAudio = UUID.randomUUID();
        UUID secondAudio = UUID.randomUUID();
        UUID insertedAudio = UUID.randomUUID();
        editor.addVirtualTileTracks(state, tile.getId(), List.of(firstAudio, secondAudio), 0.8d);

        UUID secondTrack = tile.getTracks().get(1).getId();
        editor.addVirtualTileTracks(state, tile.getId(), List.of(insertedAudio), 0.6d, secondTrack, false);
        UUID firstTrack = tile.getTracks().getFirst().getId();
        editor.moveVirtualTileTrack(state, tile.getId(), tile.getId(), firstTrack, secondTrack, true);

        Assertions.assertThat(tile.getTracks()).extracting(track -> track.getAudioFileId())
                .containsExactly(insertedAudio, secondAudio, firstAudio);

        WorkspaceTrack moved = editor.moveVirtualTileTrackToWorkspace(
                state, tile.getId(), secondTrack, null, false);

        Assertions.assertThat(moved.getAudioFileId()).isEqualTo(secondAudio);
        Assertions.assertThat(tile.getTracks()).extracting(track -> track.getOrder()).containsExactly(0, 1);
        Assertions.assertThat(state.getWorkspaceTracks()).containsExactly(moved);
    }
}
