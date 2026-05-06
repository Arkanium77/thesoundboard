package app.ui.workspace;

import app.audio.AudioEngine;
import app.audio.PlayingTrack;
import app.model.AudioFile;
import app.model.PlaybackStatus;
import app.model.QueueTrack;
import app.model.WorkspaceQueue;
import app.support.TestDirectorySupport;
import javafx.util.Duration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class WorkspaceQueueItemTest {
    @Test
    void loopsBackToFirstTrackWhenQueueLoopIsEnabled() throws IOException {
        Path rootPath = TestDirectorySupport.createTempDirectory("workspace-queue-item-");
        Files.writeString(rootPath.resolve("one.mp3"), "audio");
        Files.writeString(rootPath.resolve("two.mp3"), "audio");

        UUID firstAudioFileId = UUID.randomUUID();
        UUID secondAudioFileId = UUID.randomUUID();
        UUID firstQueueTrackId = UUID.randomUUID();
        UUID secondQueueTrackId = UUID.randomUUID();

        WorkspaceQueue workspaceQueue = new WorkspaceQueue(UUID.randomUUID(), "Queue A", 0, 0.8d, true);
        workspaceQueue.setTracks(List.of(
                new QueueTrack(firstQueueTrackId, firstAudioFileId, 0, false),
                new QueueTrack(secondQueueTrackId, secondAudioFileId, 1, false)
        ));
        workspaceQueue.setSelectedTrackId(secondQueueTrackId);

        List<AudioFile> audioFiles = List.of(
                new AudioFile(firstAudioFileId, "one.mp3", "one.mp3", false),
                new AudioFile(secondAudioFileId, "two.mp3", "two.mp3", false)
        );

        List<FakePlayingTrack> createdTracks = new ArrayList<>();
        AudioEngine audioEngine = audioPath -> {
            FakePlayingTrack fakePlayingTrack = new FakePlayingTrack();
            createdTracks.add(fakePlayingTrack);
            return fakePlayingTrack;
        };

        WorkspaceQueueItem workspaceQueueItem = new WorkspaceQueueItem(
                rootPath,
                workspaceQueue,
                audioFiles,
                audioEngine,
                1d,
                exception -> {
                }
        );

        workspaceQueueItem.playSelectedTrack(secondQueueTrackId);
        createdTracks.getFirst().playbackStatus = PlaybackStatus.FINISHED;

        PlaybackStatus playbackStatus = workspaceQueueItem.getStatus();

        Assertions.assertThat(playbackStatus).isEqualTo(PlaybackStatus.PLAYING);
        Assertions.assertThat(workspaceQueue.getSelectedTrackId()).isEqualTo(firstQueueTrackId);
        Assertions.assertThat(createdTracks).hasSize(2);
        Assertions.assertThat(createdTracks.get(1).playCalls).isEqualTo(1);
    }

    @Test
    void restoresAuthorOrderWhenShuffleIsDisabled() throws IOException {
        Path rootPath = TestDirectorySupport.createTempDirectory("workspace-queue-shuffle-");
        UUID firstAudioFileId = UUID.randomUUID();
        UUID secondAudioFileId = UUID.randomUUID();
        UUID thirdAudioFileId = UUID.randomUUID();
        UUID firstQueueTrackId = UUID.randomUUID();
        UUID secondQueueTrackId = UUID.randomUUID();
        UUID thirdQueueTrackId = UUID.randomUUID();

        WorkspaceQueue workspaceQueue = new WorkspaceQueue(UUID.randomUUID(), "Queue A", 0, 0.8d, false);
        workspaceQueue.setTracks(List.of(
                new QueueTrack(firstQueueTrackId, firstAudioFileId, 0, false),
                new QueueTrack(secondQueueTrackId, secondAudioFileId, 1, false),
                new QueueTrack(thirdQueueTrackId, thirdAudioFileId, 2, false)
        ));
        workspaceQueue.setSelectedTrackId(secondQueueTrackId);

        List<AudioFile> audioFiles = List.of(
                new AudioFile(firstAudioFileId, "one.mp3", "one.mp3", false),
                new AudioFile(secondAudioFileId, "two.mp3", "two.mp3", false),
                new AudioFile(thirdAudioFileId, "three.mp3", "three.mp3", false)
        );

        WorkspaceQueueItem workspaceQueueItem = new WorkspaceQueueItem(
                rootPath,
                workspaceQueue,
                audioFiles,
                audioPath -> new FakePlayingTrack(),
                1d,
                exception -> {
                }
        );

        List<UUID> authorOrder = workspaceQueueItem.getTracks().stream()
                .map(QueueTrack::getId)
                .toList();

        workspaceQueueItem.setShuffleEnabled(true);
        List<UUID> firstShuffleOrder = workspaceQueueItem.getTracks().stream()
                .map(QueueTrack::getId)
                .toList();

        workspaceQueueItem.setShuffleEnabled(false);
        List<UUID> restoredOrder = workspaceQueueItem.getTracks().stream()
                .map(QueueTrack::getId)
                .toList();

        workspaceQueueItem.setShuffleEnabled(true);
        List<UUID> secondShuffleOrder = workspaceQueueItem.getTracks().stream()
                .map(QueueTrack::getId)
                .toList();

        Assertions.assertThat(firstShuffleOrder).isNotEqualTo(authorOrder);
        Assertions.assertThat(restoredOrder).isEqualTo(authorOrder);
        Assertions.assertThat(secondShuffleOrder).isNotEqualTo(authorOrder);
        Assertions.assertThat(secondShuffleOrder).isNotEqualTo(firstShuffleOrder);
        Assertions.assertThat(workspaceQueue.getSelectedTrackId()).isEqualTo(secondQueueTrackId);
    }

    @Test
    void wrapsPreviousAndNextWhenQueueLoopIsEnabled() throws IOException {
        Path rootPath = TestDirectorySupport.createTempDirectory("workspace-queue-wrap-");
        Files.writeString(rootPath.resolve("one.mp3"), "audio");
        Files.writeString(rootPath.resolve("two.mp3"), "audio");
        Files.writeString(rootPath.resolve("three.mp3"), "audio");

        UUID firstAudioFileId = UUID.randomUUID();
        UUID secondAudioFileId = UUID.randomUUID();
        UUID thirdAudioFileId = UUID.randomUUID();
        UUID firstQueueTrackId = UUID.randomUUID();
        UUID secondQueueTrackId = UUID.randomUUID();
        UUID thirdQueueTrackId = UUID.randomUUID();

        WorkspaceQueue workspaceQueue = new WorkspaceQueue(UUID.randomUUID(), "Queue A", 0, 0.8d, true);
        workspaceQueue.setTracks(List.of(
                new QueueTrack(firstQueueTrackId, firstAudioFileId, 0, false),
                new QueueTrack(secondQueueTrackId, secondAudioFileId, 1, false),
                new QueueTrack(thirdQueueTrackId, thirdAudioFileId, 2, false)
        ));
        workspaceQueue.setSelectedTrackId(thirdQueueTrackId);

        List<AudioFile> audioFiles = List.of(
                new AudioFile(firstAudioFileId, "one.mp3", "one.mp3", false),
                new AudioFile(secondAudioFileId, "two.mp3", "two.mp3", false),
                new AudioFile(thirdAudioFileId, "three.mp3", "three.mp3", false)
        );

        WorkspaceQueueItem workspaceQueueItem = new WorkspaceQueueItem(
                rootPath,
                workspaceQueue,
                audioFiles,
                audioPath -> new FakePlayingTrack(),
                1d,
                exception -> {
                }
        );

        workspaceQueueItem.next();
        Assertions.assertThat(workspaceQueue.getSelectedTrackId()).isEqualTo(firstQueueTrackId);

        workspaceQueueItem.previous();
        Assertions.assertThat(workspaceQueue.getSelectedTrackId()).isEqualTo(thirdQueueTrackId);
    }

    private static final class FakePlayingTrack implements PlayingTrack {
        private int playCalls;
        private PlaybackStatus playbackStatus = PlaybackStatus.READY;

        @Override
        public void play() {
            playCalls++;
            playbackStatus = PlaybackStatus.PLAYING;
        }

        @Override
        public void pause() {
            playbackStatus = PlaybackStatus.PAUSED;
        }

        @Override
        public void stop() {
            playbackStatus = PlaybackStatus.STOPPED;
        }

        @Override
        public void seek(Duration position) {
        }

        @Override
        public void setVolume(double volume) {
        }

        @Override
        public void setLoop(boolean loop) {
        }

        @Override
        public Duration getCurrentTime() {
            return Duration.ZERO;
        }

        @Override
        public Duration getTotalDuration() {
            return Duration.seconds(10);
        }

        @Override
        public PlaybackStatus getStatus() {
            return playbackStatus;
        }

        @Override
        public void dispose() {
        }
    }
}
