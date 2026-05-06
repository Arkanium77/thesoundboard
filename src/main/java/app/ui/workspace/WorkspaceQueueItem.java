package app.ui.workspace;

import app.audio.AudioEngine;
import app.audio.PlayingTrack;
import app.model.AudioFile;
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
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class WorkspaceQueueItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceQueueItem.class);

    private final Path rootPath;
    private final WorkspaceQueue workspaceQueue;
    private final AudioEngine audioEngine;
    private final Consumer<Exception> errorHandler;
    private final Map<UUID, AudioFile> audioFilesById = new LinkedHashMap<>();

    private PlayingTrack playingTrack;
    private UUID playingQueueTrackId;
    private boolean trackCreationFailed;
    private boolean finishedHandled;
    private double masterVolume;

    public WorkspaceQueueItem(
            Path rootPath,
            WorkspaceQueue workspaceQueue,
            List<AudioFile> audioFiles,
            AudioEngine audioEngine,
            double masterVolume,
            Consumer<Exception> errorHandler
    ) {
        this.rootPath = rootPath;
        this.workspaceQueue = workspaceQueue;
        this.audioEngine = audioEngine;
        this.masterVolume = masterVolume;
        this.errorHandler = errorHandler;

        for (AudioFile audioFile : audioFiles) {
            audioFilesById.put(audioFile.getId(), audioFile);
        }

        refreshAfterMutation();
    }

    public WorkspaceQueue getWorkspaceQueue() {
        return workspaceQueue;
    }

    public List<QueueTrack> getTracks() {
        List<QueueTrack> queueTracks = new ArrayList<>(workspaceQueue.getTracks());
        if (workspaceQueue.isShuffleEnabled()) {
            ensureShuffleOrder();
            queueTracks.sort(Comparator
                    .comparingInt((QueueTrack queueTrack) -> queueTrack.getShuffledOrder() == null ? Integer.MAX_VALUE : queueTrack.getShuffledOrder())
                    .thenComparingInt(QueueTrack::getOrder));
        } else {
            queueTracks.sort(Comparator.comparingInt(QueueTrack::getOrder));
        }
        return queueTracks;
    }

    public Optional<QueueTrack> getSelectedTrack() {
        ensureSelectedTrack();
        UUID selectedTrackId = workspaceQueue.getSelectedTrackId();
        return getTracks().stream()
                .filter(track -> track.getId().equals(selectedTrackId))
                .findFirst();
    }

    public Optional<AudioFile> getAudioFile(UUID audioFileId) {
        return Optional.ofNullable(audioFilesById.get(audioFileId));
    }

    public Optional<Path> getSelectedAudioPath() {
        QueueTrack selectedTrack = getSelectedTrack().orElse(null);
        if (selectedTrack == null) {
            return Optional.empty();
        }

        AudioFile audioFile = audioFilesById.get(selectedTrack.getAudioFileId());
        if (audioFile == null || audioFile.isMissing()) {
            return Optional.empty();
        }

        Path audioPath = rootPath.resolve(audioFile.getRelativePath());
        return Files.exists(audioPath) ? Optional.of(audioPath) : Optional.empty();
    }

    public List<Path> getAudioPaths() {
        List<Path> audioPaths = new ArrayList<>();
        for (QueueTrack queueTrack : workspaceQueue.getTracks()) {
            AudioFile audioFile = audioFilesById.get(queueTrack.getAudioFileId());
            if (audioFile == null || audioFile.isMissing()) {
                continue;
            }

            Path audioPath = rootPath.resolve(audioFile.getRelativePath());
            if (Files.exists(audioPath)) {
                audioPaths.add(audioPath);
            }
        }
        return audioPaths;
    }

    public PlaybackStatus getStatus() {
        refreshPlaybackState();

        if (trackCreationFailed) {
            return PlaybackStatus.ERROR;
        }
        return playingTrack == null ? PlaybackStatus.READY : playingTrack.getStatus();
    }

    public Duration getCurrentTime() {
        refreshPlaybackState();
        return playingTrack == null ? Duration.ZERO : playingTrack.getCurrentTime();
    }

    public Duration getTotalDuration() {
        refreshPlaybackState();
        return playingTrack == null ? Duration.ZERO : playingTrack.getTotalDuration();
    }

    public void togglePlayPause() {
        QueueTrack selectedTrack = getSelectedTrack().orElse(null);
        if (selectedTrack == null) {
            return;
        }

        if (!ensurePlayingTrack(selectedTrack)) {
            return;
        }

        PlaybackStatus playbackStatus = playingTrack.getStatus();
        finishedHandled = false;
        if (playbackStatus == PlaybackStatus.PLAYING) {
            playingTrack.pause();
        } else {
            playingTrack.play();
        }
    }

    public void stop() {
        finishedHandled = false;
        if (playingTrack != null) {
            playingTrack.stop();
        }
    }

    public boolean pauseIfPlaying() {
        refreshPlaybackState();
        if (playingTrack == null || playingTrack.getStatus() != PlaybackStatus.PLAYING) {
            return false;
        }

        finishedHandled = false;
        playingTrack.pause();
        return true;
    }

    public boolean resumeIfPaused() {
        refreshPlaybackState();
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
        selectTrack(queueTrackId, false);
    }

    public void playSelectedTrack(UUID queueTrackId) {
        selectTrack(queueTrackId, true);
    }

    public boolean isLoopCurrentTrack() {
        return getSelectedTrack().map(QueueTrack::isLoop).orElse(false);
    }

    public void setLoopCurrentTrack(boolean loop) {
        getSelectedTrack().ifPresent(queueTrack -> {
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
    }

    public void seek(Duration position) {
        if (playingTrack != null) {
            playingTrack.seek(position);
        }
    }

    public void refreshAfterMutation() {
        normalizeAuthorOrder();
        if (workspaceQueue.isShuffleEnabled()) {
            ensureShuffleOrder();
        }
        List<QueueTrack> queueTracks = getTracks();
        workspaceQueue.setTracks(queueTracks);
        ensureSelectedTrack();

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

    private void refreshPlaybackState() {
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
        for (int index = 0; index < queueTracks.size(); index++) {
            if (queueTracks.get(index).getId().equals(queueTrackId)) {
                return index;
            }
        }
        return -1;
    }

    private void ensureSelectedTrack() {
        if (workspaceQueue.getSelectedTrackId() != null) {
            boolean exists = getTracks().stream()
                    .anyMatch(track -> track.getId().equals(workspaceQueue.getSelectedTrackId()));
            if (exists) {
                return;
            }
        }

        List<QueueTrack> queueTracks = getTracks();
        workspaceQueue.setSelectedTrackId(queueTracks.isEmpty() ? null : queueTracks.getFirst().getId());
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
            playingTrack.dispose();
            playingTrack = null;
        }
        playingQueueTrackId = null;
    }

    private void applyVolume() {
        if (playingTrack != null) {
            playingTrack.setVolume(workspaceQueue.getVolume() * masterVolume);
        }
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
