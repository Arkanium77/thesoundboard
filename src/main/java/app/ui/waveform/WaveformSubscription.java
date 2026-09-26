package app.ui.waveform;

import app.waveform.WaveformData;
import app.waveform.WaveformService;
import javafx.application.Platform;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/** A shared decoder must not retain removed views or deliver a previous selection's envelope to a new selection.
 * Cancellation belongs to the service, because multiple views may await the same file; disposal only detaches this
 * consumer. The weak completion reference prevents a queued decode from keeping the entire workspace alive. */
public final class WaveformSubscription {
    private final WaveformService service;
    private Consumer<WaveformData> consumer;
    private Path path;
    private long generation;

    public WaveformSubscription(WaveformService service, Consumer<WaveformData> consumer) {
        this.service = service;
        this.consumer = consumer;
    }

    public void load(Path nextPath) {
        if (consumer == null || Objects.equals(path, nextPath)) return;
        path = nextPath;
        long request = ++generation;
        consumer.accept(WaveformData.empty());
        if (path == null) return;
        WeakReference<WaveformSubscription> reference = new WeakReference<>(this);
        service.loadWaveform(path).thenAccept(data -> Platform.runLater(() -> {
            WaveformSubscription subscription = reference.get();
            if (subscription != null && subscription.consumer != null && subscription.generation == request) {
                subscription.consumer.accept(data);
            }
        }));
    }

    public void dispose() {
        generation++;
        path = null;
        consumer = null;
    }
}
