package app.project;

import app.model.ProjectState;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Owns the root/state pair and revision on the UI thread. Loading never replaces a live session until its matching
 * request succeeds; close invalidates outstanding loads. Writers receive detached snapshots, and acknowledgements
 * from another session or an older revision cannot mark newer edits saved. Unreadable state starts write-protected.
 */
public class ProjectSessionController {
    private Path rootPath;
    private ProjectState state;
    private UUID id = UUID.randomUUID();
    private long revision;
    private long savedRevision;
    private long loadGeneration;
    private boolean loading;
    private boolean closing;
    private boolean writable = true;

    public ProjectSessionController(int schemaVersion) {
        state = ProjectState.empty(schemaVersion);
    }

    public Path getRootPath() { return rootPath; }
    public ProjectState getState() { return state; }
    public boolean isLoading() { return loading; }
    public boolean isClosing() { return closing; }
    public boolean isWritable() { return writable; }
    public boolean isDirty() { return revision != savedRevision; }

    public long beginLoad() {
        loading = true;
        return ++loadGeneration;
    }

    public boolean acceptsLoad(long generation) {
        return loading && !closing && generation == loadGeneration;
    }

    public boolean completeLoad(long generation, ProjectLoadResult result) {
        if (!acceptsLoad(generation)) return false;
        rootPath = result.getRootPath();
        state = result.getProjectState();
        id = UUID.randomUUID();
        revision = savedRevision = 0L;
        writable = result.getPersistenceLoadException() == null;
        loading = false;
        return true;
    }

    public void failLoad(long generation) {
        if (acceptsLoad(generation)) loading = false;
    }

    public void changed() {
        revision++;
    }

    public Snapshot snapshot() {
        return new Snapshot(id, rootPath, ProjectStateCopySupport.copy(state), revision);
    }

    public boolean matches(Snapshot snapshot) { return id.equals(snapshot.sessionId()); }

    public boolean saved(Snapshot snapshot) {
        if (!id.equals(snapshot.sessionId())) return false;
        savedRevision = Math.max(savedRevision, snapshot.revision());
        writable = true;
        return savedRevision == revision;
    }

    public void beginClose() {
        closing = true;
        loading = false;
        loadGeneration++;
    }

    public void cancelLoad() { loading = false; loadGeneration++; }

    public void cancelClose() { closing = false; }

    public record Snapshot(UUID sessionId, Path rootPath, ProjectState state, long revision) { }
}
