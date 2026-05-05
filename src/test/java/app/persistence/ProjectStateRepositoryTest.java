package app.persistence;

import app.model.AudioFile;
import app.model.ProjectState;
import app.model.VirtualFolder;
import app.model.WorkspaceTrack;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

class ProjectStateRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsProjectState() throws IOException {
        ProjectStateRepository repository = new ProjectStateRepository(".soundboard-project.json");
        UUID audioFileId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID workspaceTrackId = UUID.randomUUID();

        ProjectState projectState = new ProjectState(1);
        projectState.setAudioFiles(List.of(new AudioFile(audioFileId, "battle/roar.mp3", "roar.mp3", false)));

        VirtualFolder virtualFolder = new VirtualFolder(folderId, "Boss Fight");
        virtualFolder.setAudioFileIds(List.of(audioFileId));
        projectState.setVirtualFolders(List.of(virtualFolder));
        projectState.setWorkspaceTracks(List.of(new WorkspaceTrack(workspaceTrackId, audioFileId, 0, 0.8d, true)));

        repository.save(tempDir, projectState);

        ProjectState loadedProjectState = repository.load(tempDir).orElseThrow();

        Assertions.assertThat(loadedProjectState.getSchemaVersion()).isEqualTo(1);
        Assertions.assertThat(loadedProjectState.getAudioFiles()).hasSize(1);
        Assertions.assertThat(loadedProjectState.getAudioFiles().getFirst().getId()).isEqualTo(audioFileId);
        Assertions.assertThat(loadedProjectState.getVirtualFolders()).hasSize(1);
        Assertions.assertThat(loadedProjectState.getVirtualFolders().getFirst().getAudioFileIds()).containsExactly(audioFileId);
        Assertions.assertThat(loadedProjectState.getWorkspaceTracks()).hasSize(1);
        Assertions.assertThat(loadedProjectState.getWorkspaceTracks().getFirst().isLoop()).isTrue();
    }

    @Test
    void keepsWorkspaceTrackOrderInSavedJson() throws IOException {
        ProjectStateRepository repository = new ProjectStateRepository(".soundboard-project.json");
        UUID firstAudioFileId = UUID.randomUUID();
        UUID secondAudioFileId = UUID.randomUUID();

        ProjectState projectState = new ProjectState(1);
        projectState.setAudioFiles(List.of(
                new AudioFile(firstAudioFileId, "battle/roar.mp3", "roar.mp3", false),
                new AudioFile(secondAudioFileId, "battle/hiss.mp3", "hiss.mp3", false)
        ));
        projectState.setWorkspaceTracks(List.of(
                new WorkspaceTrack(UUID.randomUUID(), secondAudioFileId, 0, 0.5d, false),
                new WorkspaceTrack(UUID.randomUUID(), firstAudioFileId, 1, 0.9d, true)
        ));

        repository.save(tempDir, projectState);

        Path stateFile = tempDir.resolve(".soundboard-project.json");
        String json = Files.readString(stateFile);
        ProjectState loadedProjectState = repository.load(tempDir).orElseThrow();

        Assertions.assertThat(json).contains("\"order\" : 0");
        Assertions.assertThat(json).contains("\"order\" : 1");
        Assertions.assertThat(loadedProjectState.getWorkspaceTracks())
                .extracting(WorkspaceTrack::getOrder)
                .containsExactly(0, 1);
    }
}
