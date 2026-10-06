package app.project;

import app.model.AudioFile;
import app.model.LinkedAudioFolder;
import app.model.ProjectState;
import app.model.QueueTrack;
import app.model.VirtualTileLayout;
import app.model.VirtualTileTrack;
import app.model.WorkspaceQueue;
import app.model.WorkspaceTrack;
import app.model.WorkspaceVirtualTile;
import app.persistence.ProjectStateRepository;
import app.scan.AudioScanner;
import app.support.TestDirectorySupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class LinkedAudioFoldersTest {
    @Test
    void preservesNamespacesAndPortablePathsAcrossSaveRescanAndCopy() throws Exception {
        Path parent = TestDirectorySupport.createTempDirectory("linked-audio-");
        Path root = Files.createDirectory(parent.resolve("project"));
        Path library = Files.createDirectories(parent.resolve("library/nested"));
        Files.createDirectories(root.resolve("nested"));
        Files.writeString(root.resolve("nested/tone.wav"), "local");
        Files.writeString(library.resolve("tone.wav"), "external");
        Files.writeString(library.getParent().resolve(".soundboard-project.json"), "external state must stay untouched");
        ProjectStateRepository repository = new ProjectStateRepository(".soundboard-project.json");
        ProjectService service = service(repository);
        ProjectState state = service.loadProject(root).getProjectState();
        ProjectState added = service.linkFolder(root, state, library.getParent()).getProjectState();
        state.getLinkedAudioFolders().addAll(added.getLinkedAudioFolders());
        state.getAudioFiles().addAll(added.getAudioFiles());
        AudioFile linked = added.getAudioFiles().getFirst();
        UUID linkedId = linked.getId();
        LinkedAudioFolder folder = added.getLinkedAudioFolders().getFirst();
        Assertions.assertThat(folder.isRelative()).isTrue();
        Assertions.assertThat(new AudioFileIndex(state).resolve(root, linked)).isEqualTo(library.resolve("tone.wav").toAbsolutePath().normalize());
        ProjectState copy = ProjectStateCopySupport.copy(state);
        Assertions.assertThat(copy.getLinkedAudioFolders().getFirst()).isNotSameAs(folder);
        repository.save(root, copy);
        ProjectState loaded = service.loadProject(root).getProjectState();
        Assertions.assertThat(loaded.getAudioFiles()).hasSize(2);
        Assertions.assertThat(loaded.getAudioFiles().stream().filter(file -> file.getLinkedFolderId() != null)
                .findFirst().orElseThrow().getId()).isEqualTo(linkedId);
        Assertions.assertThat(library.getParent().resolve(".soundboard-project.json")).content().isEqualTo("external state must stay untouched");
        Assertions.assertThatThrownBy(() -> service.linkFolder(root, loaded, library.getParent())).isInstanceOf(IOException.class);
        Assertions.assertThatThrownBy(() -> service.linkFolder(root, loaded, root.resolve("nested"))).isInstanceOf(IOException.class);
        ProjectState rebuilt = service.rebuildProjectState(root).getProjectState();
        Assertions.assertThat(rebuilt.getLinkedAudioFolders().getFirst().getId()).isEqualTo(folder.getId());
        Assertions.assertThat(rebuilt.getAudioFiles()).hasSize(2);
    }

    @Test
    void missingSourceReturnsWithSameIdsAndDoesNotPreventLocalScan() throws Exception {
        Path parent = TestDirectorySupport.createTempDirectory("missing-linked-audio-");
        Path root = Files.createDirectory(parent.resolve("project"));
        Path library = Files.createDirectory(parent.resolve("library"));
        Files.writeString(root.resolve("local.wav"), "local");
        Files.writeString(library.resolve("tone.wav"), "external");
        ProjectStateRepository repository = new ProjectStateRepository("state.json");
        ProjectService service = service(repository);
        ProjectState state = service.loadProject(root).getProjectState();
        ProjectState added = service.linkFolder(root, state, library).getProjectState();
        state.getLinkedAudioFolders().addAll(added.getLinkedAudioFolders());
        state.getAudioFiles().addAll(added.getAudioFiles());
        UUID id = added.getAudioFiles().getFirst().getId();
        repository.save(root, state);
        Path moved = parent.resolve("temporarily-away");
        Files.move(library, moved);
        ProjectState missing = service.loadProject(root).getProjectState();
        Assertions.assertThat(missing.getLinkedAudioFolders().getFirst().isUnavailable()).isTrue();
        Assertions.assertThat(new AudioFileIndex(missing).get(id).isMissing()).isTrue();
        Assertions.assertThat(missing.getAudioFiles().stream().filter(file -> !file.isMissing()).count()).isEqualTo(1);
        Files.move(moved, library);
        ProjectState restored = service.loadProject(root).getProjectState();
        Assertions.assertThat(new AudioFileIndex(restored).get(id).isMissing()).isFalse();
        Assertions.assertThat(restored.getLinkedAudioFolders().getFirst().isUnavailable()).isFalse();
    }

    @Test
    void unlinkRemovesEveryReferenceButPreservesMixedAndEmptyContainersAndIgnoresStaleScan() throws Exception {
        Path parent = TestDirectorySupport.createTempDirectory("unlink-audio-");
        Path root = Files.createDirectory(parent.resolve("project"));
        Path library = Files.createDirectory(parent.resolve("library"));
        Files.writeString(root.resolve("tone.wav"), "local");
        Files.writeString(library.resolve("tone.wav"), "external");
        ProjectService service = service(new ProjectStateRepository("state.json"));
        ProjectState state = service.loadProject(root).getProjectState();
        UUID local = state.getAudioFiles().getFirst().getId();
        ProjectLoadResult scan = service.linkFolder(root, state, library);
        state.getLinkedAudioFolders().addAll(scan.getProjectState().getLinkedAudioFolders());
        state.getAudioFiles().addAll(scan.getProjectState().getAudioFiles());
        UUID linked = scan.getProjectState().getAudioFiles().getFirst().getId();
        ProjectStateEditor editor = new ProjectStateEditor();
        editor.addWorkspaceTrack(state, linked, 0.5d, false);
        editor.addWorkspaceTrack(state, local, 0.5d, false);
        WorkspaceQueue queue = new WorkspaceQueue(UUID.randomUUID(), "Mixed", 2, 0.5d, false);
        QueueTrack removed = new QueueTrack(UUID.randomUUID(), linked, 0, false);
        QueueTrack remaining = new QueueTrack(UUID.randomUUID(), local, 1, false);
        queue.setTracks(new ArrayList<>(List.of(removed, remaining)));
        queue.setSelectedTrackId(removed.getId());
        state.getWorkspaceQueues().add(queue);
        WorkspaceVirtualTile tile = new WorkspaceVirtualTile(UUID.randomUUID(), 3, VirtualTileLayout.TWO_BY_TWO);
        tile.setTracks(List.of(new VirtualTileTrack(UUID.randomUUID(), linked, 0, 1d)));
        state.getWorkspaceVirtualTiles().add(tile);
        editor.unlinkAudioFolder(state, scan.getProjectState().getLinkedAudioFolders().getFirst().getId());
        Assertions.assertThat(state.getAudioFiles()).extracting(AudioFile::getId).containsExactly(local);
        Assertions.assertThat(state.getWorkspaceTracks()).extracting(WorkspaceTrack::getAudioFileId).containsExactly(local);
        Assertions.assertThat(queue.getTracks()).containsExactly(remaining);
        Assertions.assertThat(queue.getSelectedTrackId()).isEqualTo(remaining.getId());
        Assertions.assertThat(remaining.getOrder()).isZero();
        Assertions.assertThat(state.getWorkspaceVirtualTiles()).containsExactly(tile);
        Assertions.assertThat(tile.getTracks()).isEmpty();
        ProjectState merged = service.synchronizeLoadedState(scan, ProjectStateCopySupport.copy(state)).getProjectState();
        Assertions.assertThat(merged.getLinkedAudioFolders()).isEmpty();
        Assertions.assertThat(merged.getAudioFiles()).extracting(AudioFile::getId).containsExactly(local);
        Assertions.assertThat(library.resolve("tone.wav")).content().isEqualTo("external");
    }

    private ProjectService service(ProjectStateRepository repository) {
        return new ProjectService(2, repository, new AudioScanner(List.of("wav")), new ProjectStateSynchronizer(2));
    }
}
