package app.project;

import app.model.AudioFile;
import app.model.ProjectState;
import app.model.QueueTrack;
import app.model.WorkspaceQueue;
import app.model.WorkspaceTrack;
import app.scan.ScannedAudioFile;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class ProjectStateSynchronizerTest {
    @Test
    void marksMissingFilesWithoutRemovingThem() {
        UUID missingAudioFileId = UUID.randomUUID();
        ProjectState projectState = new ProjectState(2);
        projectState.setAudioFiles(List.of(
                new AudioFile(missingAudioFileId, "battle/roar.mp3", "roar.mp3", false)
        ));
        projectState.setWorkspaceTracks(List.of(
                new WorkspaceTrack(UUID.randomUUID(), missingAudioFileId, 3, 0.8d, false)
        ));
        WorkspaceQueue workspaceQueue = new WorkspaceQueue(UUID.randomUUID(), "Ambience", 5, 0.8d, false);
        workspaceQueue.setTracks(List.of(new QueueTrack(UUID.randomUUID(), missingAudioFileId, 7, false)));
        projectState.setWorkspaceQueues(List.of(workspaceQueue));

        ProjectStateSynchronizer synchronizer = new ProjectStateSynchronizer(2);

        ProjectState synchronizedState = synchronizer.synchronize(projectState, List.of());

        Assertions.assertThat(synchronizedState.getAudioFiles()).hasSize(1);
        Assertions.assertThat(synchronizedState.getAudioFiles().getFirst().isMissing()).isTrue();
        Assertions.assertThat(synchronizedState.getWorkspaceTracks().getFirst().getOrder()).isEqualTo(0);
        Assertions.assertThat(synchronizedState.getWorkspaceQueues().getFirst().getOrder()).isEqualTo(1);
        Assertions.assertThat(synchronizedState.getWorkspaceQueues().getFirst().getTracks().getFirst().getOrder()).isEqualTo(0);
    }

    @Test
    void matchesExistingFilesByNormalizedRelativePath() {
        UUID existingAudioFileId = UUID.randomUUID();
        ProjectState projectState = new ProjectState(2);
        projectState.setAudioFiles(List.of(
                new AudioFile(existingAudioFileId, "battle\\dragons\\roar.mp3", "roar.mp3", true)
        ));

        ProjectStateSynchronizer synchronizer = new ProjectStateSynchronizer(2);

        ProjectState synchronizedState = synchronizer.synchronize(
                projectState,
                List.of(new ScannedAudioFile("battle/dragons/roar.mp3", "roar.mp3"))
        );

        Assertions.assertThat(synchronizedState.getAudioFiles()).hasSize(1);
        Assertions.assertThat(synchronizedState.getAudioFiles().getFirst().getId()).isEqualTo(existingAudioFileId);
        Assertions.assertThat(synchronizedState.getAudioFiles().getFirst().isMissing()).isFalse();
    }

    @Test
    void preservesInterleavedWorkspaceOrderAcrossTracksAndQueues() {
        UUID firstAudioFileId = UUID.randomUUID();
        UUID secondAudioFileId = UUID.randomUUID();
        UUID thirdAudioFileId = UUID.randomUUID();

        ProjectState projectState = new ProjectState(2);
        projectState.setAudioFiles(List.of(
                new AudioFile(firstAudioFileId, "battle/one.mp3", "one.mp3", false),
                new AudioFile(secondAudioFileId, "battle/two.mp3", "two.mp3", false),
                new AudioFile(thirdAudioFileId, "battle/three.mp3", "three.mp3", false)
        ));
        projectState.setWorkspaceTracks(List.of(
                new WorkspaceTrack(UUID.randomUUID(), firstAudioFileId, 1, 0.8d, false),
                new WorkspaceTrack(UUID.randomUUID(), secondAudioFileId, 3, 0.8d, false)
        ));

        WorkspaceQueue firstQueue = new WorkspaceQueue(UUID.randomUUID(), "Queue 1", 0, 0.8d, false);
        firstQueue.setTracks(List.of(new QueueTrack(UUID.randomUUID(), thirdAudioFileId, 0, false)));
        WorkspaceQueue secondQueue = new WorkspaceQueue(UUID.randomUUID(), "Queue 2", 2, 0.8d, false);
        secondQueue.setTracks(List.of(new QueueTrack(UUID.randomUUID(), firstAudioFileId, 0, false)));
        projectState.setWorkspaceQueues(List.of(firstQueue, secondQueue));

        ProjectStateSynchronizer synchronizer = new ProjectStateSynchronizer(2);

        ProjectState synchronizedState = synchronizer.synchronize(projectState, List.of());

        Assertions.assertThat(synchronizedState.getWorkspaceQueues())
                .extracting(WorkspaceQueue::getOrder)
                .containsExactly(0, 2);
        Assertions.assertThat(synchronizedState.getWorkspaceTracks())
                .extracting(WorkspaceTrack::getOrder)
                .containsExactly(1, 3);
    }
}
