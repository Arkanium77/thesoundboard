package app.audio;

import app.model.PlaybackStatus;
import javafx.application.Platform;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

public class JavaFxPlayingTrack implements PlayingTrack {
    private final MediaPlayer mediaPlayer;
    private boolean finished;
    private boolean loop;
    private Duration pendingReadySeek;
    private Duration pendingStartSeek;
    private boolean playWhenReady;
    private boolean pauseWhenReady;
    private long commandGeneration;
    private long pendingStartGeneration;
    private boolean disposed;
    private PlaybackStatus playbackStatus = PlaybackStatus.READY;

    public JavaFxPlayingTrack(MediaPlayer mediaPlayer) {
        this.mediaPlayer = mediaPlayer;
        this.mediaPlayer.setOnReady(this::handleReady);
        this.mediaPlayer.setOnPlaying(this::handlePlaying);
        this.mediaPlayer.setOnPaused(() -> playbackStatus = PlaybackStatus.PAUSED);
        this.mediaPlayer.setOnStopped(() -> {
            if (!finished) {
                playbackStatus = PlaybackStatus.STOPPED;
            }
        });
        this.mediaPlayer.setOnEndOfMedia(() -> {
            if (loop) {
                playbackStatus = PlaybackStatus.PLAYING;
                return;
            }

            finished = true;
            playbackStatus = PlaybackStatus.FINISHED;
            mediaPlayer.stop();
            mediaPlayer.seek(Duration.ZERO);
        });
        this.mediaPlayer.setOnError(() -> playbackStatus = PlaybackStatus.ERROR);
    }

    @Override
    public void play() {
        if (finished) {
            finished = false;
            mediaPlayer.seek(Duration.ZERO);
        }
        if (mediaPlayer.getStatus() == MediaPlayer.Status.UNKNOWN) {
            playWhenReady = true;
            pauseWhenReady = false;
            return;
        }
        mediaPlayer.play();
    }

    @Override
    public void pause() {
        mediaPlayer.pause();
    }

    @Override
    public void stop() {
        commandGeneration++;
        finished = false;
        pendingReadySeek = null;
        pendingStartSeek = null;
        playWhenReady = false;
        pauseWhenReady = false;
        mediaPlayer.setStartTime(Duration.ZERO);
        mediaPlayer.stop();
    }

    @Override
    public void seek(Duration position) {
        Duration seekPosition = normalizePosition(position);
        long generation = ++commandGeneration;
        if (mediaPlayer.getStatus() == MediaPlayer.Status.UNKNOWN) {
            pendingReadySeek = seekPosition;
            return;
        }
        applySeekAndVerify(seekPosition, generation);
    }

    @Override
    public void restorePlayback(Duration position, boolean paused) {
        finished = false;
        pendingStartSeek = normalizePosition(position);
        pendingStartGeneration = ++commandGeneration;
        pauseWhenReady = paused;
        if (mediaPlayer.getStatus() == MediaPlayer.Status.UNKNOWN) {
            playWhenReady = true;
            return;
        }
        mediaPlayer.setStartTime(pendingStartSeek);
        mediaPlayer.play();
    }

    @Override
    public void setVolume(double volume) {
        mediaPlayer.setVolume(Math.max(0d, Math.min(1d, volume)));
    }

    @Override
    public void setLoop(boolean loop) {
        this.loop = loop;
        mediaPlayer.setCycleCount(loop ? MediaPlayer.INDEFINITE : 1);
    }

    @Override
    public Duration getCurrentTime() {
        Duration currentTime = mediaPlayer.getCurrentTime();
        return currentTime == null || currentTime.isUnknown() ? Duration.ZERO : currentTime;
    }

    @Override
    public Duration getTotalDuration() {
        Duration totalDuration = mediaPlayer.getTotalDuration();
        if (isInvalidDuration(totalDuration)) {
            totalDuration = mediaPlayer.getMedia().getDuration();
        }
        return isInvalidDuration(totalDuration) ? Duration.ZERO : totalDuration;
    }

    @Override
    public PlaybackStatus getStatus() {
        if (finished) {
            return PlaybackStatus.FINISHED;
        }
        return playbackStatus;
    }

    @Override
    public void dispose() {
        disposed = true;
        commandGeneration++;
        pendingReadySeek = null;
        pendingStartSeek = null;
        playWhenReady = false;
        pauseWhenReady = false;
        mediaPlayer.stop();
        mediaPlayer.dispose();
    }

    /**
     * Applies commands issued while JavaFX still reports {@link MediaPlayer.Status#UNKNOWN}. Calling seek directly in
     * that state is backend-dependent and was intermittently discarded, making a click on either a solo or queue
     * waveform start at zero. One permanent ready callback preserves deferred commands without replacing JavaFX
     * status callbacks. A start-position seek is intentionally not applied here: some media backends reset their
     * clock again while transitioning from STOPPED to PLAYING, so {@link #handlePlaying()} owns that final seek.
     */
    private void handleReady() {
        playbackStatus = PlaybackStatus.READY;
        if (pendingReadySeek != null) {
            Duration seekPosition = pendingReadySeek;
            pendingReadySeek = null;
            applySeekAndVerify(seekPosition, commandGeneration);
        }
        if (playWhenReady) {
            playWhenReady = false;
            if (pendingStartSeek != null) mediaPlayer.setStartTime(pendingStartSeek);
            mediaPlayer.play();
        }
    }

    /**
     * Applies a restored start position after the backend has actually entered PLAYING and again on the next JavaFX
     * pulse. JavaFX media backends can acknowledge seek during READY/STOPPED, emit PLAYING, and still finish their
     * native transition by resetting the clock to zero. startTime prevents audible playback from zero, while the
     * deferred seek makes the chosen position authoritative after that native transition. Generation checks prevent
     * an older deferred command from overriding a newer seek or Stop.
     */
    private void handlePlaying() {
        finished = false;
        playbackStatus = PlaybackStatus.PLAYING;
        if (pendingStartSeek == null) return;

        Duration seekPosition = pendingStartSeek;
        boolean pauseAfterSeek = pauseWhenReady;
        long generation = pendingStartGeneration;
        pendingStartSeek = null;
        pauseWhenReady = false;
        mediaPlayer.seek(seekPosition);
        Platform.runLater(() -> {
            if (disposed || generation != commandGeneration) return;
            mediaPlayer.seek(seekPosition);
            mediaPlayer.setStartTime(Duration.ZERO);
            if (pauseAfterSeek) mediaPlayer.pause();
        });
    }

    /**
     * Verifies ordinary seeks one JavaFX pulse later because the native media layer may discard a command issued in
     * the same pulse as a status transition. Repeating only the newest command is cheap, preserves user ordering and
     * covers active playback as well as the stopped-start path handled by {@link #handlePlaying()}.
     */
    private void applySeekAndVerify(Duration position, long generation) {
        mediaPlayer.seek(position);
        Platform.runLater(() -> {
            if (disposed || generation != commandGeneration) return;
            mediaPlayer.seek(position);
        });
    }

    private Duration normalizePosition(Duration position) {
        return position == null || position.isUnknown() || position.isIndefinite() || position.lessThan(Duration.ZERO)
                ? Duration.ZERO
                : position;
    }

    private boolean isInvalidDuration(Duration duration) {
        return duration == null || duration.isUnknown() || duration.isIndefinite() || duration.lessThan(Duration.ZERO);
    }
}
