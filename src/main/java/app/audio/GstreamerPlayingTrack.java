package app.audio;

import app.model.PlaybackStatus;
import javafx.util.Duration;
import org.freedesktop.gstreamer.BusSyncReply;
import org.freedesktop.gstreamer.message.ErrorMessage;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Format;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.StateChangeReturn;
import org.freedesktop.gstreamer.event.SeekFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Prerolls silently and defers the entire position-before-play sequence until ASYNC_DONE. In particular a paused
 * ownership transfer must never briefly start the device. Native bus callbacks filter notifications before queuing
 * control work directly on the engine executor. The binding's default bus watch schedules every message
 * (including unobserved state/tag messages) on its global executor first;
 * bypassing that hop avoids task bursts when many tiles start or stop. No playback state changes run inside a native
 * callback. Error text is copied before returning DROP because the binding releases the message on return; native
 * messages must never escape into queued commands. Listeners run on JavaFX and are looked up at delivery time
 * so a queue transfer cannot notify the previous owner. Cached
 * timestamps keep native duration/position queries off the rendering thread. Between native samples the UI advances
 * from a monotonic timestamp, avoiding a staircase cursor or a 200 ms artificial first-play delay. NULL precedes disposal.
 * Windows uses the C WASAPI plugin: WASAPI2's newer C++ mutex implementation crashes with the older MSVC
 * runtime already loaded by supported JDK 21 installations. Changing a DLL directory cannot replace that runtime.
 * WASAPI keeps low-latency mode but uses ordinary shared IAudioClient by default: negotiating AudioClient3 again on
 * every cold trigger cost about 10 ms in both preroll and measured loopback onset, without a resume benefit on the
 * tested device. Other drivers may benefit from AudioClient3; the system property
 * thesoundboard.gstreamer.wasapi.audioclient3 retains that option without changing saved projects or other platforms.
 * Initial validation, Stop and end use NULL, retaining only the reusable decoder shell and known duration. The device
 * sink is detached while idle and reattached before preroll; only one spare is retained per engine, because leaving
 * every WASAPI sink in NULL still retains device-enumerator threads. PAUSED would
 * keep decoder/device threads for every tile ever created. Resume from Pause stays warm; replay after Stop prerolls
 * and applies any requested seek. ASYNC_DONE is accepted only when the pipeline reached PAUSED/PLAYING, not a stale stop.
 * A local audio-only chain avoids PlayBin's extra playback queue/task per tile. Decodebin retains the same format
 * discovery, while conversion/resampling and software gain stay explicit. Its dynamic pad must be linked on every
 * activation: parseLaunch's delayed link only survives the first preroll and breaks replay after NULL. NULL already
 * resets the source to zero, so replay must not add a redundant flushing seek unless the caller requested a position.
 * Add/link elements individually: the binding's native variadic addMany/linkMany signatures do not describe the fixed
 * arguments correctly for the Apple arm64 ABI and can pass invalid element pointers there. Ordinary calls work on all hosts.
 */
final class GstreamerPlayingTrack implements PlayingTrack {
    private static final Logger LOGGER = LoggerFactory.getLogger(GstreamerPlayingTrack.class);
    private final GstreamerAudioEngine engine;
    private final Path path;
    private final List<Element> elements = new ArrayList<>();
    private Pipeline player;
    private Element gain;
    private Element output;
    private volatile PlaybackStatus status = PlaybackStatus.READY;
    private volatile Duration currentTime = Duration.ZERO;
    private volatile Duration totalDuration = Duration.ZERO;
    private volatile long positionUpdatedAt;
    private volatile Runnable listener = () -> { };
    private volatile boolean disposed;
    private boolean ready;
    private boolean loop;
    private double volume = 1d;
    private Duration pendingSeek;
    private PlaybackStatus requested = PlaybackStatus.READY;
    private long initializedAt;

    GstreamerPlayingTrack(GstreamerAudioEngine engine, Path path) {
        this.engine = engine;
        this.path = path.toAbsolutePath();
    }

    void initialize() {
        if (disposed) return;
        try {
            player = new Pipeline("soundboard-track");
            Element fileSource = element("filesrc", "soundboard-source");
            Element decoder = element("decodebin", "soundboard-decoder");
            Element convert = element("audioconvert", "soundboard-convert");
            Element resample = element("audioresample", "soundboard-resample");
            gain = element("volume", "soundboard-gain");
            for (Element element : elements) {
                if (!player.add(element)) throw new IllegalStateException("Cannot add an audio element");
            }
            fileSource.set("location", path.toString());
            if (!fileSource.link(decoder) || !convert.link(resample) || !resample.link(gain)) {
                throw new IllegalStateException("Cannot connect the audio pipeline");
            }
            decoder.connect((Element.PAD_ADDED) (element, pad) -> {
                Pad target = convert.getStaticPad("sink");
                Caps caps = pad.getCurrentCaps();
                try {
                    if (!target.isLinked() && caps != null && caps.toString().startsWith("audio/")) pad.link(target);
                } finally {
                    if (caps != null) caps.dispose();
                    target.dispose();
                }
            });
            player.getBus().setSyncHandler(message -> {
                switch (message.getType()) {
                    case ASYNC_DONE -> engine.execute(this::prerolled);
                    case EOS -> engine.execute(this::ended);
                    case ERROR -> {
                        String detail = ((ErrorMessage) message).getMessage();
                        engine.execute(() -> fail(detail));
                    }
                    case DURATION_CHANGED -> engine.execute(this::refreshDuration);
                    default -> { }
                }
                return BusSyncReply.DROP;
            });
            attachOutput();
            gain.set("volume", volume);
            initializedAt = System.nanoTime();
            if (player.setState(State.PAUSED) == StateChangeReturn.FAILURE) fail("Cannot open the audio stream");
        } catch (RuntimeException | Error exception) {
            LOGGER.warn("Cannot initialize the GStreamer player for {}", path, exception);
            fail(exception.getMessage());
        }
    }

    private void prerolled() {
        if (disposed || ready) return;
        State state = player.getState(0, TimeUnit.NANOSECONDS);
        if (state != State.PAUSED && state != State.PLAYING) return;
        ready = true;
        refreshPosition();
        applyRequested();
    }

    private void ended() {
        if (disposed || requested != PlaybackStatus.PLAYING) return;
        if (loop) {
            pendingSeek = Duration.ZERO;
            applyRequested();
        } else {
            requested = PlaybackStatus.FINISHED;
            idle(PlaybackStatus.FINISHED);
        }
    }

    /**
     * Keep the decoder shell reusable, but return the idle device object to the engine's bounded spare slot.
     * NULL alone stops streaming without releasing WASAPI's device-enumerator resources; retaining one sink per visited tile keeps idle native
     * threads alive. Pause deliberately retains its sink for warm resume. The old sink is removed only after NULL,
     * and an exclusively acquired sink is linked before preroll, preserving decoder replay and independent seeking.
     */
    private void attachOutput() {
        if (output != null) return;
        output = engine.acquireOutput();
        elements.add(output);
        if (!player.add(output) || !gain.link(output)) {
            throw new IllegalStateException("Cannot connect the audio output");
        }
    }

    private void detachOutput() {
        if (output == null) return;
        gain.unlink(output);
        player.remove(output);
        elements.remove(output);
        engine.recycleOutput(output);
        output = null;
    }

    private Element element(String factory, String name) {
        Element element = ElementFactory.make(factory, name);
        elements.add(element);
        return element;
    }

    @Override
    public void play() {
        engine.execute(() -> {
            if (disposed) return;
            requested = PlaybackStatus.PLAYING;
            applyRequested();
        });
    }

    @Override
    public void pause() {
        engine.execute(() -> { requested = PlaybackStatus.PAUSED; applyRequested(); });
    }

    @Override
    public void stop() {
        engine.execute(() -> {
            if (disposed || player == null) return;
            requested = PlaybackStatus.STOPPED;
            idle(PlaybackStatus.STOPPED);
        });
    }

    @Override
    public void seek(Duration position) {
        engine.execute(() -> {
            pendingSeek = sanitize(position);
            if (requested == PlaybackStatus.FINISHED) requested = PlaybackStatus.STOPPED;
            applyRequested();
        });
    }

    @Override
    public void restorePlayback(Duration position, boolean paused) {
        engine.execute(() -> {
            pendingSeek = sanitize(position);
            requested = paused ? PlaybackStatus.PAUSED : PlaybackStatus.PLAYING;
            applyRequested();
        });
    }

    private Duration sanitize(Duration position) {
        if (position == null || position.isUnknown() || position.isIndefinite()) return Duration.ZERO;
        double millis = Math.max(0d, position.toMillis());
        if (totalDuration.greaterThan(Duration.ZERO)) millis = Math.min(millis, totalDuration.toMillis());
        return Duration.millis(millis);
    }

    private void applyRequested() {
        if (disposed || player == null || status == PlaybackStatus.ERROR) return;
        if (requested == PlaybackStatus.STOPPED) {
            if (pendingSeek != null) currentTime = pendingSeek;
            updateStatus(PlaybackStatus.STOPPED);
            return;
        }
        if (!ready) {
            if (initializedAt == 0) {
                attachOutput();
                initializedAt = System.nanoTime();
                if (player.setState(State.PAUSED) == StateChangeReturn.FAILURE) fail("Cannot open the audio stream");
            }
            return;
        }
        if (requested == PlaybackStatus.READY) {
            idle(PlaybackStatus.READY);
            return;
        }
        if (pendingSeek != null) {
            Duration position = pendingSeek;
            pendingSeek = null;
            if (!player.seekSimple(Format.TIME, EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE),
                    (long) (position.toMillis() * 1_000_000d))) {
                fail("The audio stream cannot seek");
                return;
            }
            currentTime = position;
            positionUpdatedAt = System.nanoTime();
        }
        if (player.setState(requested == PlaybackStatus.PLAYING ? State.PLAYING : State.PAUSED)
                == StateChangeReturn.FAILURE) {
            fail("Cannot change audio playback state");
            return;
        }
        if (requested == PlaybackStatus.PAUSED) refreshPosition();
        if (requested == PlaybackStatus.PLAYING && status != PlaybackStatus.PLAYING) positionUpdatedAt = System.nanoTime();
        updateStatus(requested);
    }

    private void idle(PlaybackStatus value) {
        player.setState(State.NULL);
        detachOutput();
        ready = false;
        initializedAt = 0;
        pendingSeek = null;
        currentTime = Duration.ZERO;
        positionUpdatedAt = 0;
        updateStatus(value);
    }

    @Override
    public void setVolume(double volume) {
        engine.execute(() -> {
            this.volume = Math.max(0d, Math.min(1d, volume));
            if (!disposed && gain != null) gain.set("volume", this.volume);
        });
    }

    @Override
    public void setLoop(boolean loop) {
        engine.execute(() -> this.loop = loop);
    }

    void refreshPosition() {
        if (disposed || player == null || status == PlaybackStatus.ERROR) return;
        if (!ready) {
            if (initializedAt != 0 && System.nanoTime() - initializedAt > 15_000_000_000L) {
                fail("Audio initialization timed out");
            }
            return;
        }
        if (totalDuration.equals(Duration.ZERO)) refreshDuration();
        if (status == PlaybackStatus.PLAYING) {
            long position = player.queryPosition(Format.TIME);
            if (position >= 0) {
                currentTime = Duration.millis(position / 1_000_000d);
                positionUpdatedAt = System.nanoTime();
            }
        }
    }

    private void refreshDuration() {
        if (disposed || player == null || status == PlaybackStatus.ERROR) return;
        long duration = player.queryDuration(Format.TIME);
        if (duration > 0) totalDuration = Duration.millis(duration / 1_000_000d);
    }

    private void fail(String message) {
        if (disposed) return;
        LOGGER.warn("GStreamer cannot play {}: {}", path, message);
        if (player != null) {
            player.setState(State.NULL);
            detachOutput();
        }
        updateStatus(PlaybackStatus.ERROR);
    }

    private void updateStatus(PlaybackStatus value) {
        if (disposed || status == value) return;
        status = value;
        engine.dispatch(() -> { if (!disposed) listener.run(); });
    }

    @Override
    public Duration getCurrentTime() {
        Duration position = currentTime;
        long anchor = positionUpdatedAt;
        if (status != PlaybackStatus.PLAYING || anchor == 0) return position;
        double millis = position.toMillis() + (System.nanoTime() - anchor) / 1_000_000d;
        if (totalDuration.greaterThan(Duration.ZERO)) millis = Math.min(millis, totalDuration.toMillis());
        return Duration.millis(millis);
    }

    @Override
    public Duration getTotalDuration() { return totalDuration; }

    @Override
    public PlaybackStatus getStatus() { return status; }

    @Override
    public void setOnStatusChanged(Runnable listener) { this.listener = listener == null ? () -> { } : listener; }

    @Override
    public void dispose() {
        disposed = true;
        listener = () -> { };
        engine.execute(this::release);
    }

    void release() {
        disposed = true;
        listener = () -> { };
        if (player != null) {
            player.setState(State.NULL);
            detachOutput();
            elements.forEach(Element::dispose);
            elements.clear();
            player.dispose();
            player = null;
            gain = null;
            output = null;
        }
        engine.remove(this);
    }
}
