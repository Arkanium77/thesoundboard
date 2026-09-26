package app.audio;

import app.model.PlaybackStatus;
import javafx.util.Duration;

public interface PlayingTrack {
    void play();

    void pause();

    void stop();

    void seek(Duration position);

    /**
     * Restores position before starting playback because media backends are allowed to reset a stopped player when
     * Play is invoked. Implementations that have an asynchronous ready state must defer this whole ordered sequence;
     * reversing it can make a transferred or explicitly positioned track audibly and permanently start at zero.
     */
    default void restorePlayback(Duration position, boolean paused) {
        seek(position);
        play();
        if (paused) pause();
    }

    void setVolume(double volume);

    void setLoop(boolean loop);

    Duration getCurrentTime();

    Duration getTotalDuration();

    PlaybackStatus getStatus();

    /**
     * Reports backend state changes on the UI thread, including FINISHED and ERROR. Ownership transfers replace this
     * single listener; dispose/detach clear it. Queue progression must not depend on a UI getter being polled.
     */
    default void setOnStatusChanged(Runnable listener) { }

    void dispose();
}
