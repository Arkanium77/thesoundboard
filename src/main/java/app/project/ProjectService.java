package app.project;

import app.model.ProjectState;
import app.model.LinkedAudioFolder;
import app.persistence.ProjectStateRepository;
import app.scan.AudioScanner;
import app.scan.ScannedAudioFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.UUID;
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

        List<ScannedAudioFile> scannedAudioFiles = scanSources(rootPath, projectState);
        ProjectState synchronizedState = projectStateSynchronizer.synchronize(projectState, scannedAudioFiles);
        return new ProjectLoadResult(rootPath, synchronizedState, scannedAudioFiles, loadException);
    }

    public ProjectLoadResult rebuildProjectState(Path rootPath) throws IOException {
        ProjectState source;
        try { source = projectStateRepository.load(rootPath).orElseGet(() -> ProjectState.empty(schemaVersion)); }
        catch (IOException exception) {
            LOGGER.warn("Rebuilding unreadable project state for {}", rootPath, exception);
            source = ProjectState.empty(schemaVersion);
        }
        return rebuildProjectState(rootPath, source);
    }

    /** An explicit rebuild resets the arrangement, never its library definitions. Use the detached live state when
     * available so unreadable JSON or in-session source edits cannot silently discard known external libraries. */
    public ProjectLoadResult rebuildProjectState(Path rootPath, ProjectState source) throws IOException {
        ProjectState state = ProjectState.empty(schemaVersion);
        state.setLinkedAudioFolders(ProjectStateCopySupport.copy(source).getLinkedAudioFolders());
        List<ScannedAudioFile> scannedAudioFiles = scanSources(rootPath, state);
        ProjectState synchronizedState = projectStateSynchronizer.synchronize(state, scannedAudioFiles);
        return new ProjectLoadResult(rootPath, synchronizedState, scannedAudioFiles, null);
    }

    /** Reuses a finished scan with a detached live revision. It performs no I/O and preserves edits made while the
     * asynchronous scanner was reading. Source definitions come from the latest revision: stale results cannot
     * restore an unlinked source. Availability comes from this scan, while rebuilding preserves source definitions. */
    public ProjectLoadResult synchronizeLoadedState(ProjectLoadResult scanned, ProjectState latest) {
        latest.getLinkedAudioFolders().forEach(folder -> scanned.getProjectState().getLinkedAudioFolders().stream()
                .filter(candidate -> candidate.getId().equals(folder.getId())).findFirst()
                .ifPresent(candidate -> folder.setUnavailable(candidate.isUnavailable())));
        return new ProjectLoadResult(scanned.getRootPath(), projectStateSynchronizer.synchronize(latest,
                scanned.getScannedAudioFiles()), scanned.getScannedAudioFiles(), null);
    }

    /** Only project metadata is created. Validate real paths off the FX thread to reject obvious self/repeated
     * sources without reading audio hashes. Each source owns its namespace even when libraries overlap. */
    public ProjectLoadResult linkFolder(Path root, ProjectState state, Path selected) throws IOException {
        Path realRoot = root.toRealPath();
        Path realSelected = selected.toRealPath();
        if (!Files.isDirectory(realSelected) || realSelected.startsWith(realRoot)) throw new LinkedAudioFolderException(LinkedAudioFolderException.Reason.INSIDE_PROJECT);
        for (LinkedAudioFolder folder : state.getLinkedAudioFolders()) {
            try {
                Path existing = folder.resolve(root);
                if (existing.equals(realSelected) || (Files.exists(existing) && existing.toRealPath().equals(realSelected))) {
                    throw new LinkedAudioFolderException(LinkedAudioFolderException.Reason.ALREADY_LINKED);
                }
            } catch (IllegalArgumentException ignored) { }
        }
        String location;
        boolean relative;
        try {
            location = realRoot.relativize(realSelected).toString().replace('\\', '/');
            relative = true;
        } catch (IllegalArgumentException exception) {
            location = realSelected.toString();
            relative = false;
        }
        LinkedAudioFolder folder = new LinkedAudioFolder(UUID.randomUUID(), location, relative);
        folder.setName(realSelected.getFileName() == null ? realSelected.toString() : realSelected.getFileName().toString());
        List<ScannedAudioFile> files = audioScanner.scan(realSelected).stream()
                .map(file -> new ScannedAudioFile(file.getRelativePath(), file.getDisplayName(), folder.getId())).toList();
        ProjectState linked = ProjectState.empty(schemaVersion);
        linked.setLinkedAudioFolders(List.of(folder));
        return new ProjectLoadResult(root, projectStateSynchronizer.synchronize(linked, files), files, null);
    }

    /** Failure of an external source preserves its references as missing, rather than deleting user arrangements
     * or preventing other sources from opening. No external project JSON is read or written. */
    private List<ScannedAudioFile> scanSources(Path root, ProjectState state) throws IOException {
        List<ScannedAudioFile> files = new ArrayList<>(audioScanner.scan(root));
        for (LinkedAudioFolder folder : state.getLinkedAudioFolders()) {
            try {
                Path location = folder.resolve(root);
                if (!Files.isDirectory(location)) throw new IOException("Audio folder is unavailable");
                List<ScannedAudioFile> scanned = audioScanner.scan(location);
                scanned.forEach(file -> files.add(new ScannedAudioFile(file.getRelativePath(), file.getDisplayName(), folder.getId())));
                folder.setUnavailable(false);
            } catch (IOException | UncheckedIOException | IllegalArgumentException exception) {
                folder.setUnavailable(true);
                LOGGER.warn("Linked audio folder unavailable: {}", folder.getLocation(), exception);
            }
        }
        return files;
    }

    public void saveProject(Path rootPath, ProjectState projectState) throws IOException {
        projectStateRepository.save(rootPath, projectState);
    }
}
