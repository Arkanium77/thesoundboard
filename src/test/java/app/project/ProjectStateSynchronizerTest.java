package app.project;

import app.model.AudioFile;
import app.model.ProjectState;
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
        ProjectState projectState = new ProjectState(1);
        projectState.setAudioFiles(List.of(
                new AudioFile(missingAudioFileId, "battle/roar.mp3", "roar.mp3", false)
        ));
        projectState.setWorkspaceTracks(List.of(
                new WorkspaceTrack(UUID.randomUUID(), missingAudioFileId, 3, 0.8d, false)
        ));

        ProjectStateSynchronizer synchronizer = new ProjectStateSynchronizer(1);

        ProjectState synchronizedState = synchronizer.synchronize(projectState, List.of());

        Assertions.assertThat(synchronizedState.getAudioFiles()).hasSize(1);
        Assertions.assertThat(synchronizedState.getAudioFiles().getFirst().isMissing()).isTrue();
        Assertions.assertThat(synchronizedState.getWorkspaceTracks().getFirst().getOrder()).isEqualTo(0);
    }

    @Test
    void matchesExistingFilesByNormalizedRelativePath() {
        UUID existingAudioFileId = UUID.randomUUID();
        ProjectState projectState = new ProjectState(1);
        projectState.setAudioFiles(List.of(
                new AudioFile(existingAudioFileId, "battle\\dragons\\roar.mp3", "roar.mp3", true)
        ));

        ProjectStateSynchronizer synchronizer = new ProjectStateSynchronizer(1);

        ProjectState synchronizedState = synchronizer.synchronize(
                projectState,
                List.of(new ScannedAudioFile("battle/dragons/roar.mp3", "roar.mp3"))
        );

        Assertions.assertThat(synchronizedState.getAudioFiles()).hasSize(1);
        Assertions.assertThat(synchronizedState.getAudioFiles().getFirst().getId()).isEqualTo(existingAudioFileId);
        Assertions.assertThat(synchronizedState.getAudioFiles().getFirst().isMissing()).isFalse();
    }
}
