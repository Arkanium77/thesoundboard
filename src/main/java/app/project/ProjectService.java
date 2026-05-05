package app.project;

import app.model.ProjectState;
import app.persistence.ProjectStateRepository;
import app.scan.AudioScanner;
import app.scan.ScannedAudioFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class ProjectService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectService.class);

    private final int schemaVersion;
    private final ProjectStateRepository projectStateRepository;
    private final AudioScanner audioScanner;
    private final ProjectStateSynchronizer projectStateSynchronizer;

    public ProjectService(
            int schemaVersion,
            ProjectStateRepository projectStateRepository,
            AudioScanner audioScanner,
            ProjectStateSynchronizer projectStateSynchronizer
    ) {
        this.schemaVersion = schemaVersion;
        this.projectStateRepository = projectStateRepository;
        this.audioScanner = audioScanner;
        this.projectStateSynchronizer = projectStateSynchronizer;
    }

    public ProjectLoadResult loadProject(Path rootPath) throws IOException {
        ProjectState projectState = ProjectState.empty(schemaVersion);
        Exception loadException = null;

        try {
            Optional<ProjectState> persistedState = projectStateRepository.load(rootPath);
            if (persistedState.isPresent()) {
                projectState = persistedState.get();
            }
        } catch (IOException exception) {
            loadException = exception;
            LOGGER.error("Failed to read project state for root folder {}", rootPath, exception);
        }

        List<ScannedAudioFile> scannedAudioFiles = audioScanner.scan(rootPath);
        ProjectState synchronizedState = projectStateSynchronizer.synchronize(projectState, scannedAudioFiles);
        return new ProjectLoadResult(rootPath, synchronizedState, scannedAudioFiles, loadException);
    }

    public ProjectLoadResult rebuildProjectState(Path rootPath) throws IOException {
        List<ScannedAudioFile> scannedAudioFiles = audioScanner.scan(rootPath);
        ProjectState synchronizedState = projectStateSynchronizer.synchronize(ProjectState.empty(schemaVersion), scannedAudioFiles);
        return new ProjectLoadResult(rootPath, synchronizedState, scannedAudioFiles, null);
    }

    public void saveProject(Path rootPath, ProjectState projectState) throws IOException {
        projectStateRepository.save(rootPath, projectState);
    }
}
