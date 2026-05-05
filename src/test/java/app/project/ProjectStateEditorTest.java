package app.project;

import app.model.AudioFile;
import app.model.ProjectState;
import app.model.VirtualFolder;
import app.model.WorkspaceTrack;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class ProjectStateEditorTest {
    @Test
    void addsSameAudioFileToMultipleVirtualFolders() {
        UUID audioFileId = UUID.randomUUID();
        ProjectState projectState = new ProjectState(1);
        projectState.setAudioFiles(List.of(new AudioFile(audioFileId, "battle/roar.mp3", "roar.mp3", false)));

        ProjectStateEditor editor = new ProjectStateEditor();
        VirtualFolder bossFightFolder = editor.createVirtualFolder(projectState, null, "Boss Fight");
        VirtualFolder dragonsFolder = editor.createVirtualFolder(projectState, null, "Dragons");

        editor.addAudioFileToVirtualFolder(projectState, bossFightFolder.getId(), audioFileId);
        editor.addAudioFileToVirtualFolder(projectState, dragonsFolder.getId(), audioFileId);

        Assertions.assertThat(projectState.getVirtualFolders()).hasSize(2);
        Assertions.assertThat(projectState.getVirtualFolders())
                .extracting(VirtualFolder::getAudioFileIds)
                .containsExactly(List.of(audioFileId), List.of(audioFileId));
    }

    @Test
    void movesWorkspaceTrackBeforeAnotherTrack() {
        UUID firstAudioFileId = UUID.randomUUID();
        UUID secondAudioFileId = UUID.randomUUID();
        UUID thirdAudioFileId = UUID.randomUUID();
        UUID firstTrackId = UUID.randomUUID();
        UUID secondTrackId = UUID.randomUUID();
        UUID thirdTrackId = UUID.randomUUID();

        ProjectState projectState = new ProjectState(1);
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

        boolean moved = editor.moveWorkspaceTrack(projectState, thirdTrackId, firstTrackId, false);

        Assertions.assertThat(moved).isTrue();
        Assertions.assertThat(projectState.getWorkspaceTracks())
                .extracting(WorkspaceTrack::getId)
                .containsExactly(thirdTrackId, firstTrackId, secondTrackId);
        Assertions.assertThat(projectState.getWorkspaceTracks())
                .extracting(WorkspaceTrack::getOrder)
                .containsExactly(0, 1, 2);
    }
}
