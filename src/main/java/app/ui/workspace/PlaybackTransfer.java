package app.ui.workspace;

import app.audio.PlayingTrack;
import app.model.PlaybackStatus;

public record PlaybackTransfer(PlayingTrack playingTrack, PlaybackStatus status, boolean muted) {
    public boolean isActive() {
        return playingTrack != null && (status == PlaybackStatus.PLAYING || status == PlaybackStatus.PAUSED);
    }
}
