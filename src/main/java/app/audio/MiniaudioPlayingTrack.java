package app.audio;

import app.model.PlaybackStatus;
import games.rednblack.miniaudio.MASound;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Adapts native streaming commands to the existing workspace contract. Pause retains position, Stop resets it,
 * and restoration of a paused owner never briefly starts audible playback. The binding clears looping on Stop,
 * so the application loop choice is reapplied before every Play. Commands, completion and disposal share the UI
 * thread; callbacks must check disposal before touching the native sound, including after listener reentrancy.
 * Ogg duration comes from container granules because a working streaming decoder can lack the native length API.
 */
public class MiniaudioPlayingTrack implements PlayingTrack {
    private final MiniaudioAudioEngine engine;
    private final MASound sound;
    private final Duration totalDuration;
    private PlaybackStatus status = PlaybackStatus.READY;
    private Runnable statusChanged = () -> { };
    private boolean loop;
    private boolean disposed;

    MiniaudioPlayingTrack(MiniaudioAudioEngine engine, MASound sound, Path audioPath) {
        this.engine = engine;
        this.sound = sound;
        totalDuration = resolveDuration(sound, audioPath);
    }

    /** Streaming Ogg length is not implemented by every native decoder; container granules must not prevent Play. */
    private Duration resolveDuration(MASound sound, Path audioPath) {
        String name = audioPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".ogg") || name.endsWith(".opus")) {
            try {
                return OggAudioMetadata.duration(audioPath);
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot read Ogg audio duration", exception);
            }
        }
        return Duration.seconds(Math.max(0d, sound.getLength()));
    }

    @Override
    public void play() {
        if (disposed) return;
        if (status == PlaybackStatus.FINISHED) sound.seekTo(0f);
        sound.setLooping(loop);
        sound.play();
        engine.setPlaying(this, true);
        updateStatus(PlaybackStatus.PLAYING);
    }

    @Override
    public void pause() {
        if (disposed) return;
        sound.pause();
        engine.setPlaying(this, false);
        updateStatus(PlaybackStatus.PAUSED);
    }

    @Override
    public void stop() {
        if (disposed) return;
        sound.stop();
        engine.setPlaying(this, false);
        updateStatus(PlaybackStatus.STOPPED);
    }

    @Override
    public void seek(Duration position) {
        if (disposed) return;
        double seconds = position == null || position.isUnknown() || position.isIndefinite()
                ? 0d : Math.max(0d, position.toSeconds());
        sound.seekTo((float) Math.min(seconds, totalDuration.toSeconds()));
        if (status == PlaybackStatus.FINISHED) updateStatus(PlaybackStatus.STOPPED);
    }

    @Override
    public void restorePlayback(Duration position, boolean paused) {
        if (disposed) return;
        seek(position);
        if (paused) pause(); else play();
    }

    @Override
    public void setVolume(double volume) {
        if (!disposed) sound.setVolume((float) Math.max(0d, Math.min(1d, volume)));
    }

    @Override
    public void setLoop(boolean loop) {
        this.loop = loop;
        if (!disposed) sound.setLooping(loop);
    }

    @Override
    public Duration getCurrentTime() {
        return disposed || status == PlaybackStatus.FINISHED ? Duration.ZERO
                : Duration.seconds(Math.max(0d, sound.getCursorPosition()));
    }

    @Override
    public Duration getTotalDuration() {
        return totalDuration;
    }

    @Override
    public PlaybackStatus getStatus() {
        return status;
    }

    void handleEnd() {
        if (disposed || loop || status != PlaybackStatus.PLAYING || !sound.isEnd()) return;
        engine.setPlaying(this, false);
        updateStatus(PlaybackStatus.FINISHED);
    }

    @Override
    public void setOnStatusChanged(Runnable listener) {
        statusChanged = listener == null ? () -> { } : listener;
    }

    private void updateStatus(PlaybackStatus nextStatus) {
        if (disposed || status == nextStatus) return;
        status = nextStatus;
        statusChanged.run();
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        statusChanged = () -> { };
        engine.release(this, sound);
    }
}
