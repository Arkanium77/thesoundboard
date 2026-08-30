package app.ui.workspace;

import app.model.PlaybackStatus;
import javafx.util.Duration;

public record PlaybackSnapshot(PlaybackStatus status, Duration position, boolean muted) {
    public static PlaybackSnapshot stopped() {
        return new PlaybackSnapshot(PlaybackStatus.STOPPED, Duration.ZERO, false);
    }

    public boolean isActive() {
        return status == PlaybackStatus.PLAYING || status == PlaybackStatus.PAUSED;
    }
}
