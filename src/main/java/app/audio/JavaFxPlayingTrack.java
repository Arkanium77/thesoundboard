package app.audio;

import app.model.PlaybackStatus;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

public class JavaFxPlayingTrack implements PlayingTrack {
    private final MediaPlayer mediaPlayer;
    private boolean finished;
    private boolean loop;
    private PlaybackStatus playbackStatus = PlaybackStatus.READY;

    public JavaFxPlayingTrack(MediaPlayer mediaPlayer) {
        this.mediaPlayer = mediaPlayer;
        this.mediaPlayer.setOnReady(() -> playbackStatus = PlaybackStatus.READY);
        this.mediaPlayer.setOnPlaying(() -> {
            finished = false;
            playbackStatus = PlaybackStatus.PLAYING;
        });
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
        mediaPlayer.play();
    }

    @Override
    public void pause() {
        mediaPlayer.pause();
    }

    @Override
    public void stop() {
        finished = false;
        mediaPlayer.stop();
    }

    @Override
    public void seek(Duration position) {
        mediaPlayer.seek(position == null ? Duration.ZERO : position);
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
        mediaPlayer.stop();
        mediaPlayer.dispose();
    }

    private boolean isInvalidDuration(Duration duration) {
        return duration == null || duration.isUnknown() || duration.isIndefinite() || duration.lessThan(Duration.ZERO);
    }
}
