package app.project;

import app.model.ProjectState;
import app.scan.ScannedAudioFile;

import java.nio.file.Path;
import java.util.List;

public class ProjectLoadResult {
    private final Path rootPath;
    private final ProjectState projectState;
    private final List<ScannedAudioFile> scannedAudioFiles;
    private final Exception persistenceLoadException;

    public ProjectLoadResult(Path rootPath, ProjectState projectState, List<ScannedAudioFile> scannedAudioFiles, Exception persistenceLoadException) {
        this.rootPath = rootPath;
        this.projectState = projectState;
        this.scannedAudioFiles = scannedAudioFiles;
        this.persistenceLoadException = persistenceLoadException;
    }

    public Path getRootPath() {
        return rootPath;
    }

    public ProjectState getProjectState() {
        return projectState;
    }

    public List<ScannedAudioFile> getScannedAudioFiles() {
        return scannedAudioFiles;
    }

    public Exception getPersistenceLoadException() {
        return persistenceLoadException;
    }
}
