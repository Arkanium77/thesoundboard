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

class ProjectStateEditorTest {
    @Test
    void addsSameAudioFileToMultipleQueues() {
        UUID audioFileId = UUID.randomUUID();
        ProjectState projectState = new ProjectState(2);
        projectState.setAudioFiles(List.of(new AudioFile(audioFileId, "battle/roar.mp3", "roar.mp3", false)));

        ProjectStateEditor editor = new ProjectStateEditor();
        WorkspaceQueue firstQueue = editor.createWorkspaceQueue(projectState, "Ambience", 0.8d);
        WorkspaceQueue secondQueue = editor.createWorkspaceQueue(projectState, "Action", 0.8d);

        editor.addQueueTracks(projectState, firstQueue.getId(), List.of(audioFileId));
        editor.addQueueTracks(projectState, secondQueue.getId(), List.of(audioFileId));

        Assertions.assertThat(projectState.getWorkspaceQueues()).hasSize(2);
        Assertions.assertThat(projectState.getWorkspaceQueues())
                .extracting(queue -> queue.getTracks().getFirst().getAudioFileId())
                .containsExactly(audioFileId, audioFileId);
    }

    @Test
    void movesWorkspaceItemBeforeAnotherWorkspaceItem() {
        UUID firstAudioFileId = UUID.randomUUID();
        UUID secondAudioFileId = UUID.randomUUID();
        UUID thirdAudioFileId = UUID.randomUUID();
        UUID firstTrackId = UUID.randomUUID();
        UUID secondTrackId = UUID.randomUUID();
        UUID thirdTrackId = UUID.randomUUID();

        ProjectState projectState = new ProjectState(2);
        projectState.setAudioFiles(List.of(
                new AudioFile(firstAudioFileId, "battle/one.mp3", "one.mp3", false),
                new AudioFile(secondAudioFileId, "battle/two.mp3", "two.mp3", false),
                new AudioFile(thirdAudioFileId, "battle/three.mp3", "three.mp3", false)
        ));
        projectState.setWorkspaceTracks(List.of(
                new WorkspaceTrack(firstTrackId, firstAudioFileId, 0, 0.8d, false),
                new WorkspaceTrack(secondTrackId, secondAudioFileId, 1, 0.8d, false),
                new WorkspaceTrack(thirdTrackId, thirdAudioFileId, 2, 0.8d, false)
        ));

        ProjectStateEditor editor = new ProjectStateEditor();

        boolean moved = editor.moveWorkspaceItem(projectState, thirdTrackId, firstTrackId, false);

        Assertions.assertThat(moved).isTrue();
        Assertions.assertThat(projectState.getWorkspaceTracks())
                .extracting(WorkspaceTrack::getId)
                .containsExactly(thirdTrackId, firstTrackId, secondTrackId);
        Assertions.assertThat(projectState.getWorkspaceTracks())
                .extracting(WorkspaceTrack::getOrder)
                .containsExactly(0, 1, 2);
    }

    @Test
    void insertsQueueTracksBeforeTargetTrack() {
        UUID firstAudioFileId = UUID.randomUUID();
        UUID secondAudioFileId = UUID.randomUUID();
        UUID thirdAudioFileId = UUID.randomUUID();
        UUID firstQueueTrackId = UUID.randomUUID();
        UUID secondQueueTrackId = UUID.randomUUID();

        ProjectState projectState = new ProjectState(2);
        projectState.setAudioFiles(List.of(
                new AudioFile(firstAudioFileId, "battle/one.mp3", "one.mp3", false),
                new AudioFile(secondAudioFileId, "battle/two.mp3", "two.mp3", false),
                new AudioFile(thirdAudioFileId, "battle/three.mp3", "three.mp3", false)
        ));

        WorkspaceQueue workspaceQueue = new WorkspaceQueue(UUID.randomUUID(), "Ambience", 0, 0.8d, false);
        workspaceQueue.setTracks(List.of(
                new QueueTrack(firstQueueTrackId, firstAudioFileId, 0, false),
                new QueueTrack(secondQueueTrackId, secondAudioFileId, 1, false)
        ));
        projectState.setWorkspaceQueues(List.of(workspaceQueue));

        ProjectStateEditor editor = new ProjectStateEditor();

        List<QueueTrack> createdTracks = editor.addQueueTracks(
                projectState,
                workspaceQueue.getId(),
                List.of(thirdAudioFileId),
                secondQueueTrackId,
                false
        );

        Assertions.assertThat(createdTracks).hasSize(1);
        Assertions.assertThat(projectState.getWorkspaceQueues().getFirst().getTracks())
                .extracting(QueueTrack::getAudioFileId)
                .containsExactly(firstAudioFileId, thirdAudioFileId, secondAudioFileId);
        Assertions.assertThat(projectState.getWorkspaceQueues().getFirst().getTracks())
                .extracting(QueueTrack::getOrder)
                .containsExactly(0, 1, 2);
    }

    @Test
    void movesQueueTrackAfterAnotherQueueTrack() {
        UUID firstAudioFileId = UUID.randomUUID();
        UUID secondAudioFileId = UUID.randomUUID();
        UUID thirdAudioFileId = UUID.randomUUID();
        UUID firstQueueTrackId = UUID.randomUUID();
        UUID secondQueueTrackId = UUID.randomUUID();
        UUID thirdQueueTrackId = UUID.randomUUID();

        ProjectState projectState = new ProjectState(2);
        projectState.setAudioFiles(List.of(
                new AudioFile(firstAudioFileId, "battle/one.mp3", "one.mp3", false),
                new AudioFile(secondAudioFileId, "battle/two.mp3", "two.mp3", false),
                new AudioFile(thirdAudioFileId, "battle/three.mp3", "three.mp3", false)
        ));

        WorkspaceQueue workspaceQueue = new WorkspaceQueue(UUID.randomUUID(), "Ambience", 0, 0.8d, false);
        workspaceQueue.setTracks(List.of(
                new QueueTrack(firstQueueTrackId, firstAudioFileId, 0, false),
                new QueueTrack(secondQueueTrackId, secondAudioFileId, 1, false),
                new QueueTrack(thirdQueueTrackId, thirdAudioFileId, 2, false)
        ));
        projectState.setWorkspaceQueues(List.of(workspaceQueue));

        ProjectStateEditor editor = new ProjectStateEditor();

        boolean moved = editor.moveQueueTrack(projectState, workspaceQueue.getId(), firstQueueTrackId, secondQueueTrackId, true);

        Assertions.assertThat(moved).isTrue();
        Assertions.assertThat(projectState.getWorkspaceQueues().getFirst().getTracks())
                .extracting(QueueTrack::getId)
                .containsExactly(secondQueueTrackId, firstQueueTrackId, thirdQueueTrackId);
        Assertions.assertThat(projectState.getWorkspaceQueues().getFirst().getTracks())
                .extracting(QueueTrack::getOrder)
                .containsExactly(0, 1, 2);
    }

    @Test
    void movesWorkspaceTrackIntoQueueAndRemovesItFromWorkspace() {
        UUID workspaceAudioFileId = UUID.randomUUID();
        UUID queueAudioFileId = UUID.randomUUID();
        UUID workspaceTrackId = UUID.randomUUID();
        UUID queueTrackId = UUID.randomUUID();

        ProjectState projectState = new ProjectState(2);
        projectState.setAudioFiles(List.of(
                new AudioFile(workspaceAudioFileId, "battle/one.mp3", "one.mp3", false),
                new AudioFile(queueAudioFileId, "battle/two.mp3", "two.mp3", false)
        ));
        projectState.setWorkspaceTracks(List.of(
                new WorkspaceTrack(workspaceTrackId, workspaceAudioFileId, 0, 0.8d, false)
        ));

        WorkspaceQueue workspaceQueue = new WorkspaceQueue(UUID.randomUUID(), "Ambience", 1, 0.8d, false);
        workspaceQueue.setTracks(List.of(
                new QueueTrack(queueTrackId, queueAudioFileId, 0, false)
        ));
        projectState.setWorkspaceQueues(List.of(workspaceQueue));

        ProjectStateEditor editor = new ProjectStateEditor();

        boolean moved = editor.moveWorkspaceTrackToQueue(projectState, workspaceTrackId, workspaceQueue.getId(), queueTrackId, false);

        Assertions.assertThat(moved).isTrue();
        Assertions.assertThat(projectState.getWorkspaceTracks()).isEmpty();
        Assertions.assertThat(projectState.getWorkspaceQueues().getFirst().getTracks())
                .extracting(QueueTrack::getAudioFileId)
                .containsExactly(workspaceAudioFileId, queueAudioFileId);
        Assertions.assertThat(projectState.getWorkspaceQueues().getFirst().getTracks())
                .extracting(QueueTrack::getOrder)
                .containsExactly(0, 1);
    }

    @Test
    void movesQueueTrackIntoWorkspaceAndRemovesItFromQueue() {
        UUID queueAudioFileId = UUID.randomUUID();
        UUID workspaceAudioFileId = UUID.randomUUID();
        UUID queueTrackId = UUID.randomUUID();
        UUID workspaceTrackId = UUID.randomUUID();

        ProjectState projectState = new ProjectState(2);
        projectState.setAudioFiles(List.of(
                new AudioFile(queueAudioFileId, "battle/one.mp3", "one.mp3", false),
                new AudioFile(workspaceAudioFileId, "battle/two.mp3", "two.mp3", false)
        ));
        projectState.setWorkspaceTracks(List.of(
                new WorkspaceTrack(workspaceTrackId, workspaceAudioFileId, 0, 0.8d, false)
        ));

        WorkspaceQueue workspaceQueue = new WorkspaceQueue(UUID.randomUUID(), "Ambience", 1, 0.8d, false);
        QueueTrack queueTrack = new QueueTrack(queueTrackId, queueAudioFileId, 0, true);
        queueTrack.setVolume(0.65d);
        workspaceQueue.setTracks(List.of(queueTrack));
        projectState.setWorkspaceQueues(List.of(workspaceQueue));

        ProjectStateEditor editor = new ProjectStateEditor();

        WorkspaceTrack movedTrack = editor.moveQueueTrackToWorkspace(
                projectState,
                workspaceQueue.getId(),
                queueTrackId,
                0.65d,
                workspaceTrackId,
                false
        );

        Assertions.assertThat(movedTrack).isNotNull();
        Assertions.assertThat(projectState.getWorkspaceQueues().getFirst().getTracks()).isEmpty();
        Assertions.assertThat(projectState.getWorkspaceTracks())
                .extracting(WorkspaceTrack::getAudioFileId)
                .containsExactly(queueAudioFileId, workspaceAudioFileId);
        Assertions.assertThat(projectState.getWorkspaceTracks().getFirst().getVolume()).isEqualTo(0.65d);
        Assertions.assertThat(projectState.getWorkspaceTracks().getFirst().isLoop()).isTrue();
    }

    @Test
    void movesQueueTrackIntoAnotherQueueAndRemovesItFromSourceQueue() {
        UUID sourceAudioFileId = UUID.randomUUID();
        UUID targetAudioFileId = UUID.randomUUID();
        UUID sourceQueueTrackId = UUID.randomUUID();
        UUID targetQueueTrackId = UUID.randomUUID();

        ProjectState projectState = new ProjectState(2);
        projectState.setAudioFiles(List.of(
                new AudioFile(sourceAudioFileId, "battle/one.mp3", "one.mp3", false),
                new AudioFile(targetAudioFileId, "battle/two.mp3", "two.mp3", false)
        ));

        WorkspaceQueue sourceQueue = new WorkspaceQueue(UUID.randomUUID(), "Source", 0, 0.8d, false);
        sourceQueue.setTracks(List.of(
                new QueueTrack(sourceQueueTrackId, sourceAudioFileId, 0, true)
        ));

        WorkspaceQueue targetQueue = new WorkspaceQueue(UUID.randomUUID(), "Target", 1, 0.8d, false);
        targetQueue.setTracks(List.of(
                new QueueTrack(targetQueueTrackId, targetAudioFileId, 0, false)
        ));
        projectState.setWorkspaceQueues(List.of(sourceQueue, targetQueue));

        ProjectStateEditor editor = new ProjectStateEditor();

        boolean moved = editor.moveQueueTrackToQueue(
                projectState,
                sourceQueue.getId(),
                targetQueue.getId(),
                sourceQueueTrackId,
                targetQueueTrackId,
                false
        );

        Assertions.assertThat(moved).isTrue();
        Assertions.assertThat(projectState.getWorkspaceQueues().get(0).getTracks()).isEmpty();
        Assertions.assertThat(projectState.getWorkspaceQueues().get(1).getTracks())
                .extracting(QueueTrack::getAudioFileId)
                .containsExactly(sourceAudioFileId, targetAudioFileId);
        Assertions.assertThat(projectState.getWorkspaceQueues().get(1).getTracks())
                .extracting(QueueTrack::isLoop)
                .containsExactly(true, false);
    }
}
