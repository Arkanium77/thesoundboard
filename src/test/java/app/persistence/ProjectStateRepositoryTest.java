package app.persistence;

import app.model.AudioFile;
import app.model.ProjectState;
import app.model.QueueTrack;
import app.model.WorkspaceQueue;
import app.model.WorkspaceTrack;
import app.support.TestDirectorySupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

class ProjectStateRepositoryTest {
    @Test
    void retainsPreviousSaveAndPreservesUnreadableOriginal() throws IOException {
        Path root = TestDirectorySupport.createTempDirectory("project-backup-");
        ProjectStateRepository repository = new ProjectStateRepository("state.json");
        ProjectState state = new ProjectState(2);
        state.setMasterVolume(0.2d);
        repository.save(root, state);
        String previous = Files.readString(root.resolve("state.json"));
        state.setMasterVolume(0.8d);
        repository.save(root, state);
        Assertions.assertThat(root.resolve("state.json.bak")).content().isEqualTo(previous);

        Files.writeString(root.resolve("state.json"), "broken JSON");
        repository.save(root, state);
        try (var files = Files.list(root)) {
            List<Path> recovery = files.filter(path -> path.getFileName().toString().startsWith("state.json.recovery-")).toList();
            Assertions.assertThat(recovery).hasSize(1);
            Assertions.assertThat(recovery.getFirst()).content().isEqualTo("broken JSON");
        }
        Assertions.assertThat(root.resolve("state.json.bak")).content().isEqualTo(previous);
        Assertions.assertThat(repository.load(root).orElseThrow().getMasterVolume()).isEqualTo(0.8d);
    }

    @Test
    void serializationFailureCannotTruncateExistingState() throws IOException {
        Path root = TestDirectorySupport.createTempDirectory("project-write-failure-");
        ProjectStateRepository repository = new ProjectStateRepository("state.json");
        repository.save(root, new ProjectState(2));
        String original = Files.readString(root.resolve("state.json"));
        ProjectState invalid = new ProjectState(2) {
            @Override
            public int getSchemaVersion() {
                throw new IllegalStateException("Simulated serialization failure");
            }
        };
        Assertions.assertThatThrownBy(() -> repository.save(root, invalid)).isInstanceOf(IOException.class);
        Assertions.assertThat(root.resolve("state.json")).content().isEqualTo(original);
    }

    @Test
    void rejectsJsonNullAsUnreadableState() throws IOException {
        Path root = TestDirectorySupport.createTempDirectory("project-null-");
        Files.writeString(root.resolve("state.json"), "null");
        Assertions.assertThatThrownBy(() -> new ProjectStateRepository("state.json").load(root))
                .isInstanceOf(IOException.class);
    }

    @Test
    void savesAndLoadsProjectState() throws IOException {
        ProjectStateRepository repository = new ProjectStateRepository(".soundboard-project.json");
        Path tempDir = TestDirectorySupport.createTempDirectory("project-state-repository-");
        UUID audioFileId = UUID.randomUUID();
        UUID queueTrackId = UUID.randomUUID();
        UUID workspaceTrackId = UUID.randomUUID();

        ProjectState projectState = new ProjectState(2);
        projectState.setMasterVolume(0.55d);
        projectState.setAudioFiles(List.of(new AudioFile(audioFileId, "battle/roar.mp3", "roar.mp3", false)));
        projectState.setWorkspaceTracks(List.of(new WorkspaceTrack(workspaceTrackId, audioFileId, 0, 0.8d, true)));

        WorkspaceQueue workspaceQueue = new WorkspaceQueue(UUID.randomUUID(), "Ambience", 1, 0.6d, true);
        QueueTrack queueTrack = new QueueTrack(queueTrackId, audioFileId, 0, false);
        queueTrack.setVolume(0.35d);
        workspaceQueue.setTracks(List.of(queueTrack));
        workspaceQueue.setSelectedTrackId(queueTrackId);
        projectState.setWorkspaceQueues(List.of(workspaceQueue));

        repository.save(tempDir, projectState);

        ProjectState loadedProjectState = repository.load(tempDir).orElseThrow();

        Assertions.assertThat(loadedProjectState.getSchemaVersion()).isEqualTo(2);
        Assertions.assertThat(loadedProjectState.getMasterVolume()).isEqualTo(0.55d);
        Assertions.assertThat(loadedProjectState.getAudioFiles()).hasSize(1);
        Assertions.assertThat(loadedProjectState.getWorkspaceTracks()).hasSize(1);
        Assertions.assertThat(loadedProjectState.getWorkspaceTracks().getFirst().isLoop()).isTrue();
        Assertions.assertThat(loadedProjectState.getWorkspaceQueues()).hasSize(1);
        Assertions.assertThat(loadedProjectState.getWorkspaceQueues().getFirst().getName()).isEqualTo("Ambience");
        Assertions.assertThat(loadedProjectState.getWorkspaceQueues().getFirst().getTracks()).hasSize(1);
        Assertions.assertThat(loadedProjectState.getWorkspaceQueues().getFirst().getSelectedTrackId()).isEqualTo(queueTrackId);
        Assertions.assertThat(loadedProjectState.getWorkspaceQueues().getFirst().getTracks().getFirst().getVolume())
                .isEqualTo(0.35d);
    }

    @Test
    void keepsQueueAndWorkspaceTrackOrderInSavedJson() throws IOException {
        ProjectStateRepository repository = new ProjectStateRepository(".soundboard-project.json");
        Path tempDir = TestDirectorySupport.createTempDirectory("project-state-repository-");
        UUID firstAudioFileId = UUID.randomUUID();
        UUID secondAudioFileId = UUID.randomUUID();

        ProjectState projectState = new ProjectState(2);
        projectState.setAudioFiles(List.of(
                new AudioFile(firstAudioFileId, "battle/roar.mp3", "roar.mp3", false),
                new AudioFile(secondAudioFileId, "battle/hiss.mp3", "hiss.mp3", false)
        ));
        projectState.setWorkspaceTracks(List.of(
                new WorkspaceTrack(UUID.randomUUID(), secondAudioFileId, 0, 0.5d, false)
        ));

        WorkspaceQueue workspaceQueue = new WorkspaceQueue(UUID.randomUUID(), "Action", 1, 0.9d, false);
        workspaceQueue.setTracks(List.of(
                new QueueTrack(UUID.randomUUID(), firstAudioFileId, 0, true),
                new QueueTrack(UUID.randomUUID(), secondAudioFileId, 1, false)
        ));
        projectState.setWorkspaceQueues(List.of(workspaceQueue));

        repository.save(tempDir, projectState);

        Path stateFile = tempDir.resolve(".soundboard-project.json");
        String json = Files.readString(stateFile);
        ProjectState loadedProjectState = repository.load(tempDir).orElseThrow();

        Assertions.assertThat(json).contains("\"workspaceQueues\"");
        Assertions.assertThat(json).contains("\"order\" : 0");
        Assertions.assertThat(json).contains("\"order\" : 1");
        Assertions.assertThat(loadedProjectState.getWorkspaceTracks())
                .extracting(WorkspaceTrack::getOrder)
                .containsExactly(0);
        Assertions.assertThat(loadedProjectState.getWorkspaceQueues())
                .extracting(WorkspaceQueue::getOrder)
                .containsExactly(1);
        Assertions.assertThat(loadedProjectState.getWorkspaceQueues().getFirst().getTracks())
                .extracting(QueueTrack::getOrder)
                .containsExactly(0, 1);
    }
}
