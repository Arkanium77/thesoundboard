package app.audio;

import app.model.PlaybackStatus;
import javafx.util.Duration;

public interface PlayingTrack {
    void play();

    void pause();

    void stop();

    void seek(Duration position);

    void setVolume(double volume);

    void setLoop(boolean loop);

    Duration getCurrentTime();

    Duration getTotalDuration();

    PlaybackStatus getStatus();

    void dispose();
}
