package app.ui.settings;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Advances only after the current install, conflict dialog and optional replacement have finished. A per-item
 * continuation is idempotent so duplicate completion cannot skip a file. Synchronous cancellation is drained in
 * a loop instead of recursive callbacks; asynchronous completion resumes the same cursor on the UI thread.
 * The package-specific step owns error presentation and calls its continuation for recoverable failures.
 */
final class PackageInstallBatch<T> {
    private final List<T> items;
    private final BiConsumer<T, Runnable> install;
    private int cursor;
    private boolean waiting;
    private boolean advancing;

    PackageInstallBatch(List<T> items, BiConsumer<T, Runnable> install) {
        this.items = List.copyOf(items);
        this.install = install;
    }

    void start() { advance(); }

    private void advance() {
        if (advancing || waiting) return;
        advancing = true;
        try {
            while (!waiting && cursor < items.size()) {
                int item = cursor++;
                waiting = true;
                install.accept(items.get(item), () -> {
                    if (!waiting || cursor != item + 1) return;
                    waiting = false;
                    advance();
                });
            }
        } finally { advancing = false; }
    }
}
