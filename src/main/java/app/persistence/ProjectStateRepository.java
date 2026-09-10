package app.persistence;

import app.model.ProjectState;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public class ProjectStateRepository {
    private final ObjectMapper objectMapper;
    private final String stateFileName;

    public ProjectStateRepository(String stateFileName) {
        this.objectMapper = new ObjectMapper().findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
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
            if (projectState == null) throw new IOException("Project state must be a JSON object");
            normalize(projectState);
            return Optional.of(projectState);
        }
    }

    /**
     * Stages JSON beside the project before replacing it, so serialization errors cannot truncate the last save.
     * The previous readable state is retained as .bak; an unreadable state is preserved in a unique recovery file
     * instead of destroying evidence needed for recovery. All files remain beside the user-selected library and the
     * JSON schema is unchanged. Callers must serialize competing saves so an older snapshot cannot win last.
     */
    public synchronized void save(Path rootPath, ProjectState projectState) throws IOException {
        Path stateFile = resolveStateFile(rootPath);
        normalize(projectState);
        Path temporary = Files.createTempFile(rootPath, stateFileName + ".", ".tmp");
        try {
            try (OutputStream outputStream = Files.newOutputStream(temporary)) {
                objectMapper.writeValue(outputStream, projectState);
            }
            if (Files.exists(stateFile)) {
                boolean readable;
                try {
                    load(rootPath);
                    readable = true;
                } catch (IOException | RuntimeException exception) {
                    readable = false;
                }
                Path backup = readable ? stateFile.resolveSibling(stateFileName + ".bak")
                        : Files.createTempFile(rootPath, stateFileName + ".recovery-", ".json");
                Files.copy(stateFile, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            AtomicFiles.replace(temporary, stateFile);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public Path resolveStateFile(Path rootPath) {
        return rootPath.resolve(stateFileName);
    }

    private void normalize(ProjectState projectState) {
        projectState.setMasterVolume(projectState.getMasterVolume());
        if (projectState.getAudioFiles() == null) {
            projectState.setAudioFiles(null);
        }
        if (projectState.getWorkspaceTracks() == null) {
            projectState.setWorkspaceTracks(null);
        }
        if (projectState.getWorkspaceQueues() == null) {
            projectState.setWorkspaceQueues(null);
        }
        if (projectState.getWorkspaceVirtualTiles() == null) {
            projectState.setWorkspaceVirtualTiles(null);
        }
    }
}
