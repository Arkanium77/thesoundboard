package app.project;

import app.model.ProjectState;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Orders background and foreground saves through the same lock. Invalidation alone cannot stop a writer already
 * inside the repository: foreground saves must wait for it before committing the newest snapshot. Generations are
 * advanced on the UI thread before snapshot creation, allowing debounced edits to supersede queued work cheaply.
 */
public class ProjectSaveCoordinator {
    private final ProjectService projectService;
    private final AtomicInteger generation = new AtomicInteger();
    private final Object queueLock = new Object();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "project-writer"));
    private SaveRequest pending;
    private boolean running;
    private boolean closed;

    public ProjectSaveCoordinator(ProjectService projectService) {
        this.projectService = projectService;
    }

    public int invalidate() {
        return generation.incrementAndGet();
    }

    public boolean isCurrent(int expectedGeneration) {
        return generation.get() == expectedGeneration;
    }

    public synchronized boolean save(Path rootPath, ProjectState state, int expectedGeneration) throws IOException {
        if (generation.get() != expectedGeneration) return false;
        projectService.saveProject(rootPath, state);
        return true;
    }

    public void saveNow(Path rootPath, ProjectState state) throws IOException {
        save(rootPath, state, invalidate());
    }

    /**
     * Keeps only the latest waiting snapshot without interrupting a write already inside the repository. The queue
     * lock is separate from the I/O lock so submitting on the FX thread never waits for the disk. Replaced requests
     * complete as superseded; closing/switching disables edits before submitting its final snapshot.
     */
    public CompletableFuture<Boolean> submit(ProjectSessionController.Snapshot snapshot) {
        CompletableFuture<Boolean> completion = new CompletableFuture<>();
        synchronized (queueLock) {
            if (closed) return CompletableFuture.failedFuture(new IOException("Project writer is closed"));
            if (pending != null) pending.completion().complete(false);
            pending = new SaveRequest(snapshot, invalidate(), completion);
            if (!running) {
                running = true;
                writer.execute(this::drain);
            }
        }
        return completion;
    }

    private void drain() {
        while (true) {
            SaveRequest request;
            synchronized (queueLock) {
                request = pending;
                pending = null;
                if (request == null) { running = false; return; }
            }
            try {
                request.completion().complete(save(request.snapshot().rootPath(), request.snapshot().state(), request.generation()));
            } catch (Exception exception) {
                request.completion().completeExceptionally(exception);
            }
        }
    }

    public void shutdown() {
        synchronized (queueLock) {
            closed = true;
            writer.shutdown();
        }
        try {
            writer.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private record SaveRequest(ProjectSessionController.Snapshot snapshot, int generation, CompletableFuture<Boolean> completion) { }
}
