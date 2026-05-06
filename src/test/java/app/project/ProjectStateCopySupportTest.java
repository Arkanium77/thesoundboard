package app.project;

import app.model.AudioFile;
import app.model.ProjectState;
import app.model.QueueTrack;
import app.model.WorkspaceQueue;
import app.model.WorkspaceTrack;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class ProjectStateCopySupportTest {
    @Test
    void createsDeepCopyOfProjectState() {
        UUID audioFileId = UUID.randomUUID();
        UUID queueTrackId = UUID.randomUUID();
        UUID selectedTrackId = UUID.randomUUID();

        ProjectState source = new ProjectState(2);
        source.setMasterVolume(0.65d);
        source.setAudioFiles(List.of(new AudioFile(audioFileId, "music/theme.mp3", "theme.mp3", false)));
        source.setWorkspaceTracks(List.of(new WorkspaceTrack(UUID.randomUUID(), audioFileId, 0, 0.8d, true)));

        WorkspaceQueue workspaceQueue = new WorkspaceQueue(UUID.randomUUID(), "Queue A", 1, 0.6d, true);
        workspaceQueue.setShuffleEnabled(true);
        workspaceQueue.setSelectedTrackId(selectedTrackId);
        QueueTrack queueTrack = new QueueTrack(queueTrackId, audioFileId, 0, false);
        queueTrack.setShuffledOrder(3);
        workspaceQueue.setTracks(List.of(queueTrack));
        source.setWorkspaceQueues(List.of(workspaceQueue));

        ProjectState copy = ProjectStateCopySupport.copy(source);

        source.getAudioFiles().getFirst().setDisplayName("mutated.mp3");
        source.getWorkspaceTracks().getFirst().setVolume(0.2d);
        source.getWorkspaceQueues().getFirst().setName("Changed");
        source.getWorkspaceQueues().getFirst().setShuffleEnabled(false);
        source.getWorkspaceQueues().getFirst().getTracks().getFirst().setLoop(true);
        source.getWorkspaceQueues().getFirst().getTracks().getFirst().setShuffledOrder(1);
        source.setMasterVolume(0.2d);

        Assertions.assertThat(copy).isNotSameAs(source);
        Assertions.assertThat(copy.getMasterVolume()).isEqualTo(0.65d);
        Assertions.assertThat(copy.getAudioFiles().getFirst().getDisplayName()).isEqualTo("theme.mp3");
        Assertions.assertThat(copy.getWorkspaceTracks().getFirst().getVolume()).isEqualTo(0.8d);
        Assertions.assertThat(copy.getWorkspaceQueues().getFirst().getName()).isEqualTo("Queue A");
        Assertions.assertThat(copy.getWorkspaceQueues().getFirst().isShuffleEnabled()).isTrue();
        Assertions.assertThat(copy.getWorkspaceQueues().getFirst().getTracks().getFirst().isLoop()).isFalse();
        Assertions.assertThat(copy.getWorkspaceQueues().getFirst().getTracks().getFirst().getShuffledOrder()).isEqualTo(3);
        Assertions.assertThat(copy.getWorkspaceQueues().getFirst().getSelectedTrackId()).isEqualTo(selectedTrackId);
    }
}
