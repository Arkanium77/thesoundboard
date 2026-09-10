package app.ui.workspace;

import app.audio.AudioEngine;
import app.audio.PlayingTrack;
import app.model.AudioFile;
import app.model.PlaybackStatus;
import app.model.WorkspaceTrack;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

public class WorkspaceTrackItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceTrackItem.class);

    private final Path rootPath;
    private final WorkspaceTrack workspaceTrack;
    private final AudioFile audioFile;
    private final AudioEngine audioEngine;
    private final Consumer<Exception> errorHandler;

    private PlayingTrack playingTrack;
    private boolean trackCreationFailed;
    private boolean muted;
    private double masterVolume;
    private Duration pendingSeekPosition;
    private Runnable playbackChanged = () -> { };

    public WorkspaceTrackItem(
            Path rootPath,
            WorkspaceTrack workspaceTrack,
            AudioFile audioFile,
            AudioEngine audioEngine,
            double masterVolume,
            Consumer<Exception> errorHandler
    ) {
        this.rootPath = rootPath;
        this.workspaceTrack = workspaceTrack;
        this.audioFile = audioFile;
        this.audioEngine = audioEngine;
        this.masterVolume = masterVolume;
        this.errorHandler = errorHandler;
    }

    public WorkspaceTrack getWorkspaceTrack() {
        return workspaceTrack;
    }

    public AudioFile getAudioFile() {
        return audioFile;
    }

    public Optional<Path> getAudioPath() {
        if (audioFile.isMissing()) {
            return Optional.empty();
        }

        Path audioPath = rootPath.resolve(audioFile.getRelativePath());
        return Optional.of(audioPath);
    }

    public boolean isMissing() {
        return audioFile.isMissing();
    }

    public PlaybackStatus getStatus() {
        if (audioFile.isMissing()) {
            return PlaybackStatus.STOPPED;
        }
        if (trackCreationFailed) {
            return PlaybackStatus.ERROR;
        }
        return playingTrack == null ? PlaybackStatus.READY : playingTrack.getStatus();
    }

    public Duration getCurrentTime() {
        if (pendingSeekPosition != null) return pendingSeekPosition;
        return playingTrack == null ? Duration.ZERO : playingTrack.getCurrentTime();
    }

    public Duration getTotalDuration() {
        return playingTrack == null ? Duration.ZERO : playingTrack.getTotalDuration();
    }

    public void togglePlayPause() {
        if (audioFile.isMissing() || !ensureTrackInitialized() || playingTrack == null) {
            return;
        }

        PlaybackStatus playbackStatus = playingTrack.getStatus();
        if (playbackStatus == PlaybackStatus.PLAYING) {
            playingTrack.pause();
        } else if (pendingSeekPosition != null) {
            Duration startPosition = pendingSeekPosition;
            pendingSeekPosition = null;
            playingTrack.restorePlayback(startPosition, false);
        } else {
            playingTrack.play();
        }
    }

    public void stop() {
        pendingSeekPosition = null;
        if (playingTrack != null) {
            playingTrack.stop();
        }
    }

    public boolean pauseIfPlaying() {
        if (playingTrack == null || playingTrack.getStatus() != PlaybackStatus.PLAYING) {
            return false;
        }

        playingTrack.pause();
        return true;
    }

    public boolean resumeIfPaused() {
        if (playingTrack == null || playingTrack.getStatus() != PlaybackStatus.PAUSED) {
            return false;
        }

        playingTrack.play();
        return true;
    }

    /**
     * Retains a seek made before playback instead of trusting a stopped media backend to preserve it. JavaFX can
     * report the requested position and still reset it to zero on the next Play. Active and paused tracks seek
     * immediately; READY, STOPPED and FINISHED tracks reapply the position through restorePlayback when started.
     */
    public void seek(Duration position) {
        if (!ensureTrackInitialized() || playingTrack == null) return;

        Duration seekPosition = position == null || position.isUnknown() || position.isIndefinite()
                || position.lessThan(Duration.ZERO) ? Duration.ZERO : position;
        PlaybackStatus status = playingTrack.getStatus();
        if (status == PlaybackStatus.PLAYING || status == PlaybackStatus.PAUSED) {
            pendingSeekPosition = null;
            playingTrack.seek(seekPosition);
            return;
        }
        pendingSeekPosition = seekPosition;
    }

    public void setVolume(double volume) {
        workspaceTrack.setVolume(volume);
        applyVolume();
    }

    public void setMasterVolume(double masterVolume) {
        this.masterVolume = masterVolume;
        applyVolume();
    }

    public void setLoop(boolean loop) {
        workspaceTrack.setLoop(loop);
        if (playingTrack != null) {
            playingTrack.setLoop(loop);
        }
    }

    public boolean isMuted() {
        return muted;
    }

    public void toggleMuted() {
        muted = !muted;
        applyVolume();
    }

    public PlaybackSnapshot snapshotPlayback() {
        return new PlaybackSnapshot(getStatus(), getCurrentTime(), muted);
    }

    public PlaybackTransfer detachPlayback() {
        if (playingTrack == null) return null;
        pendingSeekPosition = null;
        PlaybackTransfer transfer = new PlaybackTransfer(playingTrack, playingTrack.getStatus(), muted);
        playingTrack.setOnStatusChanged(null);
        playingTrack = null;
        playbackChanged.run();
        return transfer;
    }

    public void acceptPlayback(PlaybackTransfer transfer) {
        if (transfer == null || !transfer.isActive()) return;
        if (playingTrack != null) playingTrack.dispose();
        playingTrack = transfer.playingTrack();
        bindPlayback();
        pendingSeekPosition = null;
        muted = transfer.muted();
        applyVolume();
        playingTrack.setLoop(workspaceTrack.isLoop());
        playbackChanged.run();
    }

    public void restorePlayback(PlaybackSnapshot snapshot) {
        if (snapshot == null || !snapshot.isActive() || !ensureTrackInitialized() || playingTrack == null) return;
        pendingSeekPosition = null;
        muted = snapshot.muted();
        applyVolume();
        playingTrack.restorePlayback(snapshot.position(), snapshot.status() == PlaybackStatus.PAUSED);
    }

    public void dispose() {
        pendingSeekPosition = null;
        if (playingTrack != null) {
            playingTrack.setOnStatusChanged(null);
            playingTrack.dispose();
            playingTrack = null;
        }
    }

    public void setOnPlaybackChanged(Runnable listener) { playbackChanged = listener == null ? () -> { } : listener; }

    private void bindPlayback() {
        PlayingTrack expected = playingTrack;
        expected.setOnStatusChanged(() -> { if (playingTrack == expected) playbackChanged.run(); });
    }

    private void applyVolume() {
        if (playingTrack != null) {
            double effectiveVolume = muted ? 0d : workspaceTrack.getVolume() * masterVolume;
            playingTrack.setVolume(effectiveVolume);
        }
    }

    private boolean ensureTrackInitialized() {
        if (playingTrack != null) {
            return true;
        }
        if (audioFile.isMissing() || trackCreationFailed) {
            return false;
        }

        Path audioPath = rootPath.resolve(audioFile.getRelativePath());
        if (!Files.exists(audioPath)) {
            audioFile.setMissing(true);
            return false;
        }

        try {
            playingTrack = audioEngine.createTrack(audioPath);
            bindPlayback();
            applyVolume();
            playingTrack.setLoop(workspaceTrack.isLoop());
            return true;
        } catch (Exception exception) {
            trackCreationFailed = true;
            LOGGER.error("Failed to initialize track for {}", audioPath, exception);
            errorHandler.accept(exception);
            return false;
        }
    }
}
