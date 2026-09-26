package app.ui.workspace;

import app.audio.AudioEngine;
import app.audio.PlayingTrack;
import app.model.AudioFile;
import app.project.AudioFileIndex;
import app.model.PlaybackStatus;
import app.model.QueueTrack;
import app.model.WorkspaceQueue;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class WorkspaceQueueItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceQueueItem.class);

    private final Path rootPath;
    private final WorkspaceQueue workspaceQueue;
    private final AudioEngine audioEngine;
    private final Consumer<Exception> errorHandler;
    private final AudioFileIndex audioFilesById;

    private List<QueueTrack> orderedTracks;
    private final Map<UUID, QueueTrack> tracksById = new LinkedHashMap<>();
    private final Map<UUID, Integer> positionsById = new LinkedHashMap<>();
    private PlayingTrack playingTrack;
    private UUID playingQueueTrackId;
    private UUID focusedQueueTrackId;
    private UUID pendingSeekQueueTrackId;
    private Duration pendingSeekPosition;
    private boolean trackCreationFailed;
    private boolean finishedHandled;
    private double masterVolume;
    private Runnable playbackChanged = () -> { };
    private Runnable selectionChanged = () -> { };

    public WorkspaceQueueItem(
            Path rootPath,
            WorkspaceQueue workspaceQueue,
            List<AudioFile> audioFiles,
            AudioEngine audioEngine,
            double masterVolume,
            Consumer<Exception> errorHandler
    ) {
        this(rootPath, workspaceQueue, new AudioFileIndex(audioFiles), audioEngine, masterVolume, errorHandler);
    }

    public WorkspaceQueueItem(
            Path rootPath,
            WorkspaceQueue workspaceQueue,
            AudioFileIndex audioFiles,
            AudioEngine audioEngine,
            double masterVolume,
            Consumer<Exception> errorHandler
    ) {
        this.rootPath = rootPath;
        this.workspaceQueue = workspaceQueue;
        this.audioEngine = audioEngine;
        this.masterVolume = masterVolume;
        this.errorHandler = errorHandler;

        this.audioFilesById = audioFiles;

        refreshAfterMutation();
    }

    public WorkspaceQueue getWorkspaceQueue() {
        return workspaceQueue;
    }

    /**
     * Reuses a sorted snapshot and UUID index until a structural mutation. Playback polling must not allocate and
     * sort the queue on every status/selection lookup. External model edits must call refreshAfterMutation; shuffle
     * changes invalidate here as well, preserving independent author and playback ordering.
     */
    public List<QueueTrack> getTracks() { return orderedTracks; }

    private void rebuildTrackIndex() {
        List<QueueTrack> queueTracks = new ArrayList<>(workspaceQueue.getTracks());
        if (workspaceQueue.isShuffleEnabled()) {
            ensureShuffleOrder();
            queueTracks.sort(Comparator
                    .comparingInt((QueueTrack queueTrack) -> queueTrack.getShuffledOrder() == null ? Integer.MAX_VALUE : queueTrack.getShuffledOrder())
                    .thenComparingInt(QueueTrack::getOrder));
        } else {
            queueTracks.sort(Comparator.comparingInt(QueueTrack::getOrder));
        }
        orderedTracks = List.copyOf(queueTracks);
        tracksById.clear();
        positionsById.clear();
        for (int index = 0; index < queueTracks.size(); index++) {
            QueueTrack track = queueTracks.get(index);
            tracksById.put(track.getId(), track);
            positionsById.put(track.getId(), index);
        }
    }

    public Optional<QueueTrack> getSelectedTrack() {
        UUID selectedTrackId = workspaceQueue.getSelectedTrackId();
        return Optional.ofNullable(tracksById.get(selectedTrackId));
    }

    public Optional<QueueTrack> getFocusedTrack() {
        return Optional.ofNullable(tracksById.get(focusedQueueTrackId));
    }

    public UUID getActiveTrackId() {
        return playingQueueTrackId;
    }

    public Optional<AudioFile> getAudioFile(UUID audioFileId) {
        return Optional.ofNullable(audioFilesById.get(audioFileId));
    }

    public Optional<Path> getSelectedAudioPath() {
        QueueTrack selectedTrack = getFocusedTrack().orElse(null);
        if (selectedTrack == null) {
            return Optional.empty();
        }

        AudioFile audioFile = audioFilesById.get(selectedTrack.getAudioFileId());
        if (audioFile == null || audioFile.isMissing()) {
            return Optional.empty();
        }

        Path audioPath = rootPath.resolve(audioFile.getRelativePath());
        return Optional.of(audioPath);
    }

    public List<Path> getAudioPaths() {
        List<Path> audioPaths = new ArrayList<>();
        for (QueueTrack queueTrack : workspaceQueue.getTracks()) {
            AudioFile audioFile = audioFilesById.get(queueTrack.getAudioFileId());
            if (audioFile == null || audioFile.isMissing()) {
                continue;
            }

            Path audioPath = rootPath.resolve(audioFile.getRelativePath());
            audioPaths.add(audioPath);
        }
        return audioPaths;
    }

    public PlaybackStatus getStatus() {

        if (trackCreationFailed) {
            return PlaybackStatus.ERROR;
        }
        return playingTrack == null ? PlaybackStatus.READY : playingTrack.getStatus();
    }

    public PlaybackStatus getFocusedStatus() {
        return focusedQueueTrackId != null && focusedQueueTrackId.equals(playingQueueTrackId) && playingTrack != null
                ? playingTrack.getStatus() : PlaybackStatus.READY;
    }

    public Duration getFocusedCurrentTime() {
        if (focusedQueueTrackId != null && focusedQueueTrackId.equals(pendingSeekQueueTrackId)
                && pendingSeekPosition != null) {
            if (focusedQueueTrackId.equals(playingQueueTrackId) && playingTrack != null) {
                Duration currentTime = playingTrack.getCurrentTime();
                PlaybackStatus status = playingTrack.getStatus();
                if ((status == PlaybackStatus.PLAYING || status == PlaybackStatus.PAUSED)
                        && Math.abs(currentTime.toMillis() - pendingSeekPosition.toMillis()) <= 250d) {
                    clearPendingSeek();
                    return currentTime;
                }
            }
            return pendingSeekPosition;
        }
        return focusedQueueTrackId != null && focusedQueueTrackId.equals(playingQueueTrackId) && playingTrack != null
                ? playingTrack.getCurrentTime() : Duration.ZERO;
    }

    public Duration getFocusedTotalDuration() {
        return focusedQueueTrackId != null && focusedQueueTrackId.equals(playingQueueTrackId) && playingTrack != null
                ? playingTrack.getTotalDuration() : Duration.ZERO;
    }

    public Duration getCurrentTime() {
        return playingTrack == null ? Duration.ZERO : playingTrack.getCurrentTime();
    }

    public Duration getTotalDuration() {
        return playingTrack == null ? Duration.ZERO : playingTrack.getTotalDuration();
    }

    public void togglePlayPause() {
        QueueTrack selectedTrack = getFocusedTrack().orElse(null);
        if (selectedTrack == null) {
            return;
        }

        workspaceQueue.setSelectedTrackId(selectedTrack.getId());
        selectionChanged.run();

        if (!ensurePlayingTrack(selectedTrack)) {
            return;
        }

        PlaybackStatus playbackStatus = playingTrack.getStatus();
        finishedHandled = false;
        if (playbackStatus == PlaybackStatus.PLAYING) {
            playingTrack.pause();
        } else if (selectedTrack.getId().equals(pendingSeekQueueTrackId) && pendingSeekPosition != null) {
            playingTrack.restorePlayback(pendingSeekPosition, false);
        } else {
            playingTrack.play();
        }
    }

    public void stop() {
        finishedHandled = false;
        if (playingTrack != null && Objects.equals(focusedQueueTrackId, playingQueueTrackId)) {
            playingTrack.stop();
        }
    }

    public boolean pauseIfPlaying() {
        if (playingTrack == null || playingTrack.getStatus() != PlaybackStatus.PLAYING) {
            return false;
        }

        finishedHandled = false;
        playingTrack.pause();
        return true;
    }

    public boolean resumeIfPaused() {
        if (playingTrack == null || playingTrack.getStatus() != PlaybackStatus.PAUSED) {
            return false;
        }

        finishedHandled = false;
        playingTrack.play();
        return true;
    }

    public void previous() {
        moveSelection(-1, true);
    }

    public void next() {
        moveSelection(1, true);
    }

    public void selectTrack(UUID queueTrackId) {
        if (queueTrackId != null && tracksById.containsKey(queueTrackId)) {
            focusedQueueTrackId = queueTrackId;
        }
    }

    public void playSelectedTrack(UUID queueTrackId) {
        selectTrack(queueTrackId, true);
    }

    public boolean isLoopCurrentTrack() {
        return getFocusedTrack().map(QueueTrack::isLoop).orElse(false);
    }

    public void setLoopCurrentTrack(boolean loop) {
        getFocusedTrack().ifPresent(queueTrack -> {
            queueTrack.setLoop(loop);
            if (playingTrack != null && queueTrack.getId().equals(playingQueueTrackId)) {
                playingTrack.setLoop(loop);
            }
        });
    }

    public boolean isLoopQueue() {
        return workspaceQueue.isLoopQueue();
    }

    public void setLoopQueue(boolean loopQueue) {
        workspaceQueue.setLoopQueue(loopQueue);
    }

    public double getVolume() {
        return workspaceQueue.getVolume();
    }

    public void setVolume(double volume) {
        workspaceQueue.setVolume(volume);
        applyVolume();
    }

    public double getSelectedTrackVolume() {
        return getFocusedTrack().map(QueueTrack::getVolume).orElse(QueueTrack.DEFAULT_VOLUME);
    }

    public void setSelectedTrackVolume(double volume) {
        getFocusedTrack().ifPresent(track -> setTrackVolume(track.getId(), volume));
    }

    public void setTrackVolume(UUID queueTrackId, double volume) {
        getTracks().stream().filter(track -> track.getId().equals(queueTrackId)).findFirst().ifPresent(track -> {
            track.setVolume(clampVolume(volume));
            if (queueTrackId.equals(playingQueueTrackId)) applyVolume();
        });
    }

    public void resetTrackVolumes() {
        workspaceQueue.getTracks().forEach(track -> track.setVolume(QueueTrack.DEFAULT_VOLUME));
        applyVolume();
    }

    public void setMasterVolume(double masterVolume) {
        this.masterVolume = masterVolume;
        applyVolume();
    }

    public boolean isShuffleEnabled() {
        return workspaceQueue.isShuffleEnabled();
    }

    public void setShuffleEnabled(boolean shuffleEnabled) {
        workspaceQueue.setShuffleEnabled(shuffleEnabled);
        if (shuffleEnabled) {
            reshuffleTracks();
        }
        rebuildTrackIndex();
        ensureSelectedTrack();
        ensureFocusedTrack();
        playbackChanged.run();
    }

    /**
     * Retains a seek made before playback exists or after Stop. JavaFX may ignore a seek issued before MediaPlayer is
     * ready and may reset a stopped player to its start when Play is invoked, so the requested position is associated
     * with the focused queue entry and reapplied through restorePlayback on the next Play. A seek during active or
     * paused playback is applied immediately because no subsequent state transition can discard it.
     */
    public void seek(Duration position) {
        QueueTrack focusedTrack = getFocusedTrack().orElse(null);
        if (focusedTrack == null) return;

        Duration seekPosition = position == null ? Duration.ZERO : position;
        if (playingTrack != null && focusedTrack.getId().equals(playingQueueTrackId)) {
            playingTrack.seek(seekPosition);
            PlaybackStatus status = playingTrack.getStatus();
            if (status == PlaybackStatus.PLAYING || status == PlaybackStatus.PAUSED) {
                clearPendingSeek();
                return;
            }
        }
        pendingSeekQueueTrackId = focusedTrack.getId();
        pendingSeekPosition = seekPosition;
    }

    public PlaybackSnapshot snapshotPlayback(UUID queueTrackId) {
        if (playingTrack == null || !queueTrackId.equals(playingQueueTrackId)) return PlaybackSnapshot.stopped();
        return new PlaybackSnapshot(playingTrack.getStatus(), playingTrack.getCurrentTime(), false);
    }

    public boolean hasActivePlayback() {
        if (playingTrack == null) return false;
        PlaybackStatus status = playingTrack.getStatus();
        return status == PlaybackStatus.PLAYING || status == PlaybackStatus.PAUSED;
    }

    public PlaybackTransfer detachPlayback(UUID queueTrackId) {
        if (playingTrack == null || !queueTrackId.equals(playingQueueTrackId)) return null;
        PlaybackTransfer transfer = new PlaybackTransfer(playingTrack, playingTrack.getStatus(), false);
        playingTrack.setOnStatusChanged(null);
        playingTrack = null;
        playingQueueTrackId = null;
        playbackChanged.run();
        return transfer;
    }

    public void acceptPlayback(UUID queueTrackId, PlaybackTransfer transfer) {
        if (transfer == null || !transfer.isActive()) return;
        QueueTrack track = getTracks().stream().filter(candidate -> candidate.getId().equals(queueTrackId))
                .findFirst().orElse(null);
        if (track == null) return;
        disposeCurrentTrack();
        playingTrack = transfer.playingTrack();
        playingQueueTrackId = queueTrackId;
        bindPlayback();
        workspaceQueue.setSelectedTrackId(queueTrackId);
        focusedQueueTrackId = queueTrackId;
        finishedHandled = false;
        applyVolume();
        playingTrack.setLoop(track.isLoop());
        playbackChanged.run();
    }

    public void restorePlayback(UUID queueTrackId, PlaybackSnapshot snapshot) {
        if (snapshot == null || !snapshot.isActive()) return;
        QueueTrack track = getTracks().stream().filter(candidate -> candidate.getId().equals(queueTrackId))
                .findFirst().orElse(null);
        if (track == null || !ensurePlayingTrack(track)) return;
        playingTrack.restorePlayback(snapshot.position(), snapshot.status() == PlaybackStatus.PAUSED);
    }

    public void refreshAfterMutation() {
        orderedTracks = null;
        normalizeAuthorOrder();
        if (workspaceQueue.isShuffleEnabled()) {
            ensureShuffleOrder();
        }
        rebuildTrackIndex();
        List<QueueTrack> queueTracks = getTracks();
        workspaceQueue.setTracks(queueTracks);
        ensureSelectedTrack();
        ensureFocusedTrack();

        UUID selectedTrackId = workspaceQueue.getSelectedTrackId();
        if (selectedTrackId == null) {
            disposeCurrentTrack();
            return;
        }

        if (!selectedTrackId.equals(playingQueueTrackId)) {
            disposeCurrentTrack();
        }
    }

    public void dispose() {
        disposeCurrentTrack();
    }

    public void setOnPlaybackChanged(Runnable listener) { playbackChanged = listener == null ? () -> { } : listener; }
    public void setOnSelectionChanged(Runnable listener) { selectionChanged = listener == null ? () -> { } : listener; }

    /**
     * Captures the player identity so queued callbacks from a removed/transferred player cannot advance this queue.
     * The backend event drives progression even when no view is visible. finishedHandled guards duplicate completion;
     * ordinary getters only read the resulting state and never create players or generate shuffle order.
     */
    private void bindPlayback() {
        PlayingTrack expected = playingTrack;
        expected.setOnStatusChanged(() -> {
            if (playingTrack != expected) return;
            handlePlaybackState();
            playbackChanged.run();
        });
    }

    private void handlePlaybackState() {
        ensureSelectedTrack();
        if (finishedHandled || playingTrack == null) {
            return;
        }

        if (playingTrack.getStatus() != PlaybackStatus.FINISHED) {
            return;
        }

        finishedHandled = true;
        QueueTrack currentTrack = getSelectedTrack().orElse(null);
        if (currentTrack == null) {
            return;
        }

        if (currentTrack.isLoop()) {
            return;
        }

        List<QueueTrack> queueTracks = getTracks();
        int currentIndex = indexOf(queueTracks, currentTrack.getId());
        if (currentIndex < 0) {
            return;
        }

        if (currentIndex + 1 < queueTracks.size()) {
            selectTrack(queueTracks.get(currentIndex + 1).getId(), true);
            return;
        }

        if (workspaceQueue.isLoopQueue() && !queueTracks.isEmpty()) {
            QueueTrack firstTrack = queueTracks.getFirst();
            if (currentTrack.getId().equals(firstTrack.getId())) {
                finishedHandled = false;
                disposeCurrentTrack();
                workspaceQueue.setSelectedTrackId(firstTrack.getId());
                if (ensurePlayingTrack(firstTrack) && playingTrack != null) {
                    playingTrack.play();
                }
                return;
            }

            selectTrack(firstTrack.getId(), true);
        }
    }

    private void selectTrack(UUID queueTrackId, boolean autoPlay) {
        if (queueTrackId == null) {
            return;
        }

        workspaceQueue.setSelectedTrackId(queueTrackId);
        focusedQueueTrackId = queueTrackId;
        selectionChanged.run();
        QueueTrack queueTrack = getSelectedTrack().orElse(null);
        if (queueTrack == null) {
            return;
        }

        if (!ensurePlayingTrack(queueTrack)) {
            return;
        }

        if (autoPlay && playingTrack != null) {
            finishedHandled = false;
            playingTrack.play();
        }
    }

    private void moveSelection(int direction, boolean autoPlay) {
        List<QueueTrack> queueTracks = getTracks();
        QueueTrack currentTrack = getSelectedTrack().orElse(null);
        if (queueTracks.isEmpty() || currentTrack == null) {
            return;
        }

        int currentIndex = indexOf(queueTracks, currentTrack.getId());
        if (currentIndex < 0) {
            return;
        }

        int targetIndex = currentIndex + direction;
        if (targetIndex < 0 || targetIndex >= queueTracks.size()) {
            if (!workspaceQueue.isLoopQueue()) {
                return;
            }
            targetIndex = targetIndex < 0 ? queueTracks.size() - 1 : 0;
        }

        selectTrack(queueTracks.get(targetIndex).getId(), autoPlay);
    }

    private int indexOf(List<QueueTrack> queueTracks, UUID queueTrackId) {
        return positionsById.getOrDefault(queueTrackId, -1);
    }

    private void ensureSelectedTrack() {
        if (workspaceQueue.getSelectedTrackId() != null) {
            boolean exists = tracksById.containsKey(workspaceQueue.getSelectedTrackId());
            if (exists) {
                return;
            }
        }

        List<QueueTrack> queueTracks = getTracks();
        workspaceQueue.setSelectedTrackId(queueTracks.isEmpty() ? null : queueTracks.getFirst().getId());
    }

    private void ensureFocusedTrack() {
        if (focusedQueueTrackId != null && tracksById.containsKey(focusedQueueTrackId)) return;
        focusedQueueTrackId = workspaceQueue.getSelectedTrackId();
        if (focusedQueueTrackId == null && !getTracks().isEmpty()) focusedQueueTrackId = getTracks().getFirst().getId();
    }

    private boolean ensurePlayingTrack(QueueTrack queueTrack) {
        if (playingTrack != null && queueTrack.getId().equals(playingQueueTrackId)) {
            return true;
        }

        disposeCurrentTrack();
        trackCreationFailed = false;
        finishedHandled = false;

        AudioFile audioFile = audioFilesById.get(queueTrack.getAudioFileId());
        if (audioFile == null || audioFile.isMissing()) {
            return false;
        }

        Path audioPath = rootPath.resolve(audioFile.getRelativePath());
        if (!Files.exists(audioPath)) {
            audioFile.setMissing(true);
            return false;
        }

        try {
            playingTrack = audioEngine.createTrack(audioPath);
            playingQueueTrackId = queueTrack.getId();
            bindPlayback();
            applyVolume();
            playingTrack.setLoop(queueTrack.isLoop());
            return true;
        } catch (Exception exception) {
            trackCreationFailed = true;
            LOGGER.error("Failed to initialize queue track for {}", audioPath, exception);
            errorHandler.accept(exception);
            return false;
        }
    }

    private void disposeCurrentTrack() {
        if (playingTrack != null) {
            playingTrack.setOnStatusChanged(null);
            playingTrack.dispose();
            playingTrack = null;
        }
        playingQueueTrackId = null;
    }

    private void clearPendingSeek() {
        pendingSeekQueueTrackId = null;
        pendingSeekPosition = null;
    }

    private void applyVolume() {
        if (playingTrack != null) {
            double trackVolume = getTracks().stream()
                    .filter(track -> track.getId().equals(playingQueueTrackId))
                    .map(QueueTrack::getVolume)
                    .findFirst()
                    .orElse(1d);
            playingTrack.setVolume(workspaceQueue.getVolume() * trackVolume * masterVolume);
        }
    }

    private double clampVolume(double volume) {
        return Math.max(0d, Math.min(1d, volume));
    }

    private void reshuffleTracks() {
        List<QueueTrack> authorOrderedTracks = new ArrayList<>(workspaceQueue.getTracks());
        authorOrderedTracks.sort(Comparator.comparingInt(QueueTrack::getOrder));
        if (authorOrderedTracks.isEmpty()) {
            return;
        }

        List<QueueTrack> previousShuffledTracks = new ArrayList<>(workspaceQueue.getTracks());
        previousShuffledTracks.sort(Comparator
                .comparingInt((QueueTrack queueTrack) -> queueTrack.getShuffledOrder() == null ? Integer.MAX_VALUE : queueTrack.getShuffledOrder())
                .thenComparingInt(QueueTrack::getOrder));
        List<QueueTrack> shuffledTracks = new ArrayList<>(authorOrderedTracks);
        if (shuffledTracks.size() > 1) {
            for (int attempt = 0; attempt < 12; attempt++) {
                Collections.shuffle(shuffledTracks);
                if (!sameTrackOrder(authorOrderedTracks, shuffledTracks) && !sameTrackOrder(previousShuffledTracks, shuffledTracks)) {
                    break;
                }
            }
            if (sameTrackOrder(authorOrderedTracks, shuffledTracks) || sameTrackOrder(previousShuffledTracks, shuffledTracks)) {
                Collections.rotate(shuffledTracks, 1);
                if (sameTrackOrder(previousShuffledTracks, shuffledTracks) && shuffledTracks.size() > 2) {
                    Collections.rotate(shuffledTracks, 1);
                }
            }
        }

        for (int index = 0; index < shuffledTracks.size(); index++) {
            shuffledTracks.get(index).setShuffledOrder(index);
        }
    }

    private void normalizeAuthorOrder() {
        List<QueueTrack> authorOrderedTracks = new ArrayList<>(workspaceQueue.getTracks());
        authorOrderedTracks.sort(Comparator.comparingInt(QueueTrack::getOrder));
        for (int index = 0; index < authorOrderedTracks.size(); index++) {
            authorOrderedTracks.get(index).setOrder(index);
        }
        workspaceQueue.setTracks(authorOrderedTracks);
    }

    private void ensureShuffleOrder() {
        List<QueueTrack> queueTracks = new ArrayList<>(workspaceQueue.getTracks());
        if (queueTracks.isEmpty()) {
            return;
        }

        Set<Integer> seenOrders = new HashSet<>();
        boolean invalid = false;
        for (QueueTrack queueTrack : queueTracks) {
            Integer shuffledOrder = queueTrack.getShuffledOrder();
            if (shuffledOrder == null || shuffledOrder < 0 || shuffledOrder >= queueTracks.size() || !seenOrders.add(shuffledOrder)) {
                invalid = true;
                break;
            }
        }

        if (invalid) {
            reshuffleTracks();
        }
    }

    private boolean sameTrackOrder(List<QueueTrack> first, List<QueueTrack> second) {
        if (first.size() != second.size()) {
            return false;
        }

        for (int index = 0; index < first.size(); index++) {
            if (!first.get(index).getId().equals(second.get(index).getId())) {
                return false;
            }
        }
        return true;
    }
}
