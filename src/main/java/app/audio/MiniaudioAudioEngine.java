package app.audio;

import games.rednblack.miniaudio.MASound;
import games.rednblack.miniaudio.MASound.Flags;
import games.rednblack.miniaudio.MiniAudio;
import games.rednblack.miniaudio.config.MAContextConfiguration;
import games.rednblack.miniaudio.config.MAEngineConfiguration;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns one native mixer for the process: the released JNI binding uses global native state and cannot host
 * independent MiniAudio instances. libGDX is used only for its utility classes and native loader, never its
 * application/backend or render loop. Files stay on disk and STREAM bounds PCM buffering; no decoded file or
 * whole-file byte array is created. Device processing stops when the last playing owner pauses or finishes.
 * All commands and disposal run on the UI thread; native notifications only enqueue work on that thread.
 * Failed native initialization is not retried in this process: the binding has no rollback API for partially
 * initialized global resources. A restart is required rather than overwriting live native context state.
 */
public class MiniaudioAudioEngine implements AudioEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(MiniaudioAudioEngine.class);
    private static final AtomicBoolean NATIVE_IN_USE = new AtomicBoolean();
    private static final short STREAM_FLAGS = (short) (Flags.MA_SOUND_FLAG_STREAM
            | Flags.MA_SOUND_FLAG_NO_SPATIALIZATION | Flags.MA_SOUND_FLAG_NO_PITCH);
    private final Consumer<Runnable> dispatcher;
    private final ConcurrentMap<Long, MiniaudioPlayingTrack> tracks = new ConcurrentHashMap<>();
    private final Set<MiniaudioPlayingTrack> playing = new HashSet<>();
    private NativeAudio nativeAudio;
    private boolean closed;
    private IllegalStateException initializationFailure;

    public MiniaudioAudioEngine() {
        this(Platform::runLater);
    }

    MiniaudioAudioEngine(Consumer<Runnable> dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public PlayingTrack createTrack(Path audioPath) {
        if (closed) throw new IllegalStateException("Audio engine is closed");
        if (initializationFailure != null) throw initializationFailure;
        if (nativeAudio == null) {
            if (!NATIVE_IN_USE.compareAndSet(false, true)) {
                throw new IllegalStateException("Only one miniaudio engine can be initialized in a process");
            }
            try {
                nativeAudio = new NativeAudio(contextConfiguration());
                nativeAudio.stopEngine();
                LOGGER.info("Streaming audio engine: gdx-miniaudio 0.8, {} / {}",
                        System.getProperty("os.name"), System.getProperty("os.arch"));
            } catch (LinkageError error) {
                NATIVE_IN_USE.set(false);
                initializationFailure = new IllegalStateException("Cannot load the bundled miniaudio native library", error);
                throw initializationFailure;
            } catch (RuntimeException exception) {
                initializationFailure = new IllegalStateException("Cannot initialize miniaudio; restart before retrying", exception);
                throw initializationFailure;
            }
        }
        // The external=true overload dereferences Gdx.app even on desktop. Absolute desktop paths work with false.
        MASound sound = nativeAudio.createSound(audioPath.toAbsolutePath().toString(), STREAM_FLAGS, null);
        try {
            MiniaudioPlayingTrack track = new MiniaudioPlayingTrack(this, sound, audioPath);
            tracks.put(sound.getAddress(), track);
            return track;
        } catch (RuntimeException exception) {
            sound.dispose();
            throw exception;
        }
    }

    private MAContextConfiguration contextConfiguration() {
        MAContextConfiguration configuration = new MAContextConfiguration();
        configuration.logCallback = (level, message) -> {
            switch (level) {
                case ERROR -> LOGGER.error("miniaudio: {}", message);
                case WARNING -> LOGGER.warn("miniaudio: {}", message);
                default -> LOGGER.debug("miniaudio {}: {}", level, message);
            }
        };
        return configuration;
    }

    void setPlaying(MiniaudioPlayingTrack track, boolean active) {
        if (active) {
            if (playing.add(track) && playing.size() == 1) {
                try { nativeAudio.startEngine(); } catch (RuntimeException exception) {
                    playing.remove(track);
                    throw exception;
                }
            }
        } else if (playing.remove(track) && playing.isEmpty()) {
            nativeAudio.stopEngine();
        }
    }

    void release(MiniaudioPlayingTrack track, MASound sound) {
        tracks.remove(sound.getAddress(), track);
        setPlaying(track, false);
        sound.dispose();
    }

    boolean isProcessingAudio() {
        return !playing.isEmpty();
    }

    @Override
    public void shutdown() {
        if (closed) return;
        closed = true;
        for (MiniaudioPlayingTrack track : tracks.values().toArray(MiniaudioPlayingTrack[]::new)) track.dispose();
        if (nativeAudio != null) {
            nativeAudio.dispose();
            nativeAudio = null;
            NATIVE_IN_USE.set(false);
        }
    }

    /**
     * The binding's default end callback dereferences a sound pointer on its notification thread. A queued
     * notification can outlive sound disposal, so override it before that dereference and capture only the Java
     * owner. Disposal and the final isEnd check then happen on the command thread; stale events cannot access freed
     * native memory or finish a restarted track. Never block the notification thread while native disposal joins it.
     */
    private final class NativeAudio extends MiniAudio {
        private NativeAudio(MAContextConfiguration contextConfiguration) {
            super(contextConfiguration, new MAEngineConfiguration());
        }

        @Override
        public void on_native_sound_end(long soundAddress) {
            MiniaudioPlayingTrack track = tracks.get(soundAddress);
            if (track != null) dispatcher.accept(track::handleEnd);
        }
    }
}
