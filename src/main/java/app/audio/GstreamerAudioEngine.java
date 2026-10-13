package app.audio;

import javafx.application.Platform;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Serializes native commands away from JavaFX and native notification threads. Each track owns a decoder pipeline
 * so seeking or pausing one tile cannot affect another. A shared source bin remains able to push preroll PCM in PAUSED;
 * changing its state alone therefore does not pause a tile. Shared playback requires proven branch gating, time offsets
 * and end-of-stream draining before it can replace this model, rather than forwarding pipeline-wide controls.
 * Sharing one spare stopped device sink does not share playback controls or clock domains.
 * Independent pipelines do not imply lower multi-track CPU. Shutdown drains track disposal before
 * stopping the executor; process-wide GStreamer teardown is owned by the application after waveform workers stop.
 */
public class GstreamerAudioEngine implements AudioEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(GstreamerAudioEngine.class);
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "gstreamer-controls");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<GstreamerPlayingTrack> tracks = new HashSet<>();
    private final Consumer<Runnable> dispatcher;
    private final String outputFactory;
    private Element idleOutput;
    private volatile boolean closed;

    public GstreamerAudioEngine() {
        this(Platform::runLater);
    }

    GstreamerAudioEngine(Consumer<Runnable> dispatcher) {
        this(dispatcher, null);
    }

    GstreamerAudioEngine(Consumer<Runnable> dispatcher, String outputFactory) {
        GstreamerRuntime.initialize();
        this.dispatcher = dispatcher;
        this.outputFactory = outputFactory;
        executor.scheduleWithFixedDelay(() -> tracks.forEach(GstreamerPlayingTrack::refreshPosition),
                200, 200, TimeUnit.MILLISECONDS);
    }

    @Override
    public PlayingTrack createTrack(Path audioPath) {
        if (closed) throw new IllegalStateException("Audio engine is closed");
        GstreamerPlayingTrack track = new GstreamerPlayingTrack(this, audioPath);
        execute(() -> {
            tracks.add(track);
            track.initialize();
        });
        return track;
    }

    void execute(Runnable command) {
        if (closed) return;
        try {
            executor.execute(() -> {
                try {
                    command.run();
                } catch (RuntimeException | Error exception) {
                    LOGGER.error("GStreamer control command failed", exception);
                    throw exception;
                }
            });
        } catch (RejectedExecutionException exception) {
            if (!closed) throw exception;
        }
    }

    void dispatch(Runnable notification) {
        if (!closed) dispatcher.accept(notification);
    }

    void remove(GstreamerPlayingTrack track) {
        tracks.remove(track);
    }

    /**
     * One unparented NULL sink is enough for sequential soundboard triggers without retaining a device enumerator
     * for every visited tile. Active and paused tracks keep exclusive ownership; the control executor transfers
     * only stopped, unlinked sinks. This bound must not grow with project size. Shutdown disposes the spare as well.
     */
    Element acquireOutput() {
        if (idleOutput != null) {
            Element output = idleOutput;
            idleOutput = null;
            return output;
        }
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String factory = outputFactory;
        if (factory == null) factory = os.contains("windows") ? "wasapisink"
                : os.contains("mac") ? "osxaudiosink" : "pulsesink";
        Element output = ElementFactory.make(factory, "soundboard-output");
        output.set("sync", true);
        output.set("enable-last-sample", false);
        if (factory.equals("wasapisink")) {
            output.set("low-latency", true);
            output.set("use-audioclient3", Boolean.getBoolean("thesoundboard.gstreamer.wasapi.audioclient3"));
        }
        return output;
    }

    void recycleOutput(Element output) {
        if (!closed && idleOutput == null) idleOutput = output;
        else output.dispose();
    }

    @Override
    public void shutdown() {
        if (closed) return;
        closed = true;
        executor.execute(() -> {
            for (GstreamerPlayingTrack track : tracks.toArray(GstreamerPlayingTrack[]::new)) track.release();
            tracks.clear();
            if (idleOutput != null) {
                idleOutput.dispose();
                idleOutput = null;
            }
        });
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("GStreamer playback shutdown timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GStreamer shutdown was interrupted", exception);
        }
    }
}
