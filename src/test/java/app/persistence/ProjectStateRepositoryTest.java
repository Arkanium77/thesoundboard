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
        workspaceQueue.setTracks(List.of(new QueueTrack(queueTrackId, audioFileId, 0, false)));
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
