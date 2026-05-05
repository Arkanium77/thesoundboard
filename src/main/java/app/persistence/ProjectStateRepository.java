package app.persistence;

import app.model.ProjectState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class ProjectStateRepository {
    private final ObjectMapper objectMapper;
    private final String stateFileName;

    public ProjectStateRepository(String stateFileName) {
        this.objectMapper = new ObjectMapper().findAndRegisterModules()
                .enable(SerializationFeature.INDENT_OUTPUT);
        this.stateFileName = stateFileName;
    }

    public Optional<ProjectState> load(Path rootPath) throws IOException {
        Path stateFile = resolveStateFile(rootPath);
        if (!Files.exists(stateFile)) {
            return Optional.empty();
        }

        try (InputStream inputStream = Files.newInputStream(stateFile)) {
            ProjectState projectState = objectMapper.readValue(inputStream, ProjectState.class);
            normalize(projectState);
            return Optional.of(projectState);
        }
    }

    public void save(Path rootPath, ProjectState projectState) throws IOException {
        Path stateFile = resolveStateFile(rootPath);
        normalize(projectState);

        try (OutputStream outputStream = Files.newOutputStream(stateFile)) {
            objectMapper.writeValue(outputStream, projectState);
        }
    }

    public Path resolveStateFile(Path rootPath) {
        return rootPath.resolve(stateFileName);
    }

    private void normalize(ProjectState projectState) {
        if (projectState.getAudioFiles() == null) {
            projectState.setAudioFiles(null);
        }
        if (projectState.getVirtualFolders() == null) {
            projectState.setVirtualFolders(null);
        }
        if (projectState.getWorkspaceTracks() == null) {
            projectState.setWorkspaceTracks(null);
        }
    }
}
