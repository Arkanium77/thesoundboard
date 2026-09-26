package app.ui.settings;

import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Serializes package work without blocking the FX thread. The worker is non-daemon so normal exit cannot abandon
 * a filesystem replacement; the window uses the same running state for controls and close/restart guards.
 * Completion clears the guard before invoking callbacks, allowing a confirmed replacement or next batch item.
 * All entry points and callbacks are confined to the FX thread; only the supplied operation runs on the worker.
 */
final class SettingsOperationRunner {
    private final Consumer<Boolean> busyChanged;
    private boolean running;

    SettingsOperationRunner(Consumer<Boolean> busyChanged) {
        this.busyChanged = busyChanged;
    }

    boolean isRunning() { return running; }

    <T> void run(Callable<T> operation, Consumer<T> completed, Consumer<Throwable> failed) {
        if (running) return;
        running = true;
        busyChanged.accept(true);
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return operation.call(); }
        };
        task.setOnSucceeded(event -> { finish(); completed.accept(task.getValue()); });
        task.setOnFailed(event -> { finish(); failed.accept(task.getException()); });
        Thread worker = new Thread(task, "package-operation");
        worker.setDaemon(false);
        worker.start();
    }

    private void finish() {
        running = false;
        busyChanged.accept(false);
    }
}
