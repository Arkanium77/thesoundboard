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
import java.util.concurrent.atomic.AtomicInteger;

class WorkspaceQueueItemTest {
    @Test
    void refreshesIndexedSelectionAndOrderAfterMutationAndShuffle() {
        WorkspaceQueue queue = new WorkspaceQueue(UUID.randomUUID(), "Queue", 0, 1d, false);
        QueueTrack first = new QueueTrack(UUID.randomUUID(), UUID.randomUUID(), 0, false);
        QueueTrack second = new QueueTrack(UUID.randomUUID(), UUID.randomUUID(), 1, false);
        queue.setTracks(List.of(first, second));
        WorkspaceQueueItem item = new WorkspaceQueueItem(Path.of("."), queue, List.of(), path -> null, 1d, exception -> { });
        Assertions.assertThat(item.getSelectedTrack()).contains(first);
        first.setOrder(1);
        second.setOrder(0);
        queue.setSelectedTrackId(second.getId());
        item.refreshAfterMutation();
        Assertions.assertThat(item.getTracks()).containsExactly(second, first);
        Assertions.assertThat(item.getSelectedTrack()).contains(second);
        item.setShuffleEnabled(true);
        Assertions.assertThat(item.getTracks()).containsExactlyInAnyOrder(first, second);
        item.setShuffleEnabled(false);
        Assertions.assertThat(item.getTracks()).containsExactly(second, first);
        queue.setTracks(List.of(first));
        item.refreshAfterMutation();
        Assertions.assertThat(item.getSelectedTrack()).contains(first);
        Assertions.assertThat(item.getFocusedTrack()).contains(first);
    }

    @Test
    void completionAdvancesWithoutPollingAndOldOwnerCallbacksAreIgnored() throws IOException {
        Path root = TestDirectorySupport.createTempDirectory("queue-events-");
        Files.writeString(root.resolve("one.mp3"), "audio");
        UUID audioId = UUID.randomUUID();
        AudioFile audio = new AudioFile(audioId, "one.mp3", "one.mp3", false);
        WorkspaceQueue source = new WorkspaceQueue(UUID.randomUUID(), "Source", 0, 1d, false);
        QueueTrack sourceTrack = new QueueTrack(UUID.randomUUID(), audioId, 0, false);
        source.setTracks(List.of(sourceTrack));
        WorkspaceQueue target = new WorkspaceQueue(UUID.randomUUID(), "Target", 1, 1d, false);
        QueueTrack first = new QueueTrack(UUID.randomUUID(), audioId, 0, false);
        QueueTrack second = new QueueTrack(UUID.randomUUID(), audioId, 1, false);
        target.setTracks(List.of(first, second));
        List<FakePlayingTrack> players = new ArrayList<>();
        AudioEngine engine = path -> { FakePlayingTrack player = new FakePlayingTrack(); players.add(player); return player; };
        WorkspaceQueueItem oldOwner = new WorkspaceQueueItem(root, source, List.of(audio), engine, 1d, exception -> { });
        WorkspaceQueueItem newOwner = new WorkspaceQueueItem(root, target, List.of(audio), engine, 1d, exception -> { });
        AtomicInteger selections = new AtomicInteger();
        newOwner.setOnSelectionChanged(selections::incrementAndGet);
        oldOwner.togglePlayPause();
        FakePlayingTrack transferred = players.getFirst();
        Runnable oldCallback = transferred.statusChanged;
        newOwner.acceptPlayback(first.getId(), oldOwner.detachPlayback(sourceTrack.getId()));
        transferred.finish();
        Assertions.assertThat(target.getSelectedTrackId()).isEqualTo(second.getId());
        Assertions.assertThat(players).hasSize(2);
        Assertions.assertThat(selections.get()).isEqualTo(1);
        oldCallback.run();
        transferred.finish();
        Assertions.assertThat(players).hasSize(2);
        Assertions.assertThat(source.getSelectedTrackId()).isEqualTo(sourceTrack.getId());
        Assertions.assertThat(target.getSelectedTrackId()).isEqualTo(second.getId());
        newOwner.dispose();
        players.getLast().finish();
        Assertions.assertThat(players).hasSize(2);
    }

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
        createdTracks.getFirst().finish();

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

    @Test
    void acceptsTransferredPlayerAsSelectedTrackAndPausesTheSamePlayer() throws IOException {
        Path rootPath = TestDirectorySupport.createTempDirectory("workspace-queue-transfer-");
        UUID firstAudioId = UUID.randomUUID();
        UUID transferredAudioId = UUID.randomUUID();
        UUID firstTrackId = UUID.randomUUID();
        UUID transferredTrackId = UUID.randomUUID();
        WorkspaceQueue queue = new WorkspaceQueue(UUID.randomUUID(), "Queue A", 0, 0.8d, false);
        queue.setTracks(List.of(
                new QueueTrack(firstTrackId, firstAudioId, 0, false),
                new QueueTrack(transferredTrackId, transferredAudioId, 1, false)
        ));
        queue.setSelectedTrackId(firstTrackId);
        AtomicInteger createdPlayers = new AtomicInteger();
        WorkspaceQueueItem item = new WorkspaceQueueItem(rootPath, queue, List.of(
                new AudioFile(firstAudioId, "one.mp3", "one.mp3", false),
                new AudioFile(transferredAudioId, "two.mp3", "two.mp3", false)
        ), path -> {
            createdPlayers.incrementAndGet();
            return new FakePlayingTrack();
        }, 1d, exception -> { });
        FakePlayingTrack transferredPlayer = new FakePlayingTrack();
        transferredPlayer.play();

        item.acceptPlayback(transferredTrackId,
                new PlaybackTransfer(transferredPlayer, PlaybackStatus.PLAYING, false));
        item.selectTrack(firstTrackId);

        Assertions.assertThat(queue.getSelectedTrackId()).isEqualTo(transferredTrackId);
        Assertions.assertThat(item.getFocusedTrack().orElseThrow().getId()).isEqualTo(firstTrackId);
        Assertions.assertThat(transferredPlayer.playbackStatus).isEqualTo(PlaybackStatus.PLAYING);
        Assertions.assertThat(createdPlayers.get()).isZero();

        item.selectTrack(transferredTrackId);
        item.togglePlayPause();

        Assertions.assertThat(transferredPlayer.playbackStatus).isEqualTo(PlaybackStatus.PAUSED);
        Assertions.assertThat(createdPlayers.get()).isZero();
    }

    @Test
    void combinesMasterQueueAndTrackVolumesAndCanResetTrackVolumes() throws IOException {
        Path rootPath = TestDirectorySupport.createTempDirectory("workspace-queue-volume-");
        Files.writeString(rootPath.resolve("one.mp3"), "audio");
        UUID audioId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        QueueTrack queueTrack = new QueueTrack(trackId, audioId, 0, false);
        queueTrack.setVolume(0.4d);
        WorkspaceQueue queue = new WorkspaceQueue(UUID.randomUUID(), "Queue A", 0, 0.5d, false);
        queue.setTracks(List.of(queueTrack));
        queue.setSelectedTrackId(trackId);
        FakePlayingTrack player = new FakePlayingTrack();
        WorkspaceQueueItem item = new WorkspaceQueueItem(rootPath, queue,
                List.of(new AudioFile(audioId, "one.mp3", "one.mp3", false)), path -> player,
                0.5d, exception -> { });

        item.togglePlayPause();
        Assertions.assertThat(player.volume).isEqualTo(0.1d);

        item.setSelectedTrackVolume(0.8d);
        Assertions.assertThat(player.volume).isEqualTo(0.2d);

        item.resetTrackVolumes();
        Assertions.assertThat(queueTrack.getVolume()).isEqualTo(QueueTrack.DEFAULT_VOLUME);
        Assertions.assertThat(player.volume).isEqualTo(0.2d);
    }

    @Test
    void restoresSeekPositionWhenPlaybackStartsAfterStop() throws IOException {
        Path rootPath = TestDirectorySupport.createTempDirectory("workspace-queue-seek-");
        Files.writeString(rootPath.resolve("one.mp3"), "audio");
        UUID audioId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        WorkspaceQueue queue = new WorkspaceQueue(UUID.randomUUID(), "Queue A", 0, 0.8d, false);
        queue.setTracks(List.of(new QueueTrack(trackId, audioId, 0, false)));
        queue.setSelectedTrackId(trackId);
        FakePlayingTrack player = new FakePlayingTrack();
        WorkspaceQueueItem item = new WorkspaceQueueItem(rootPath, queue,
                List.of(new AudioFile(audioId, "one.mp3", "one.mp3", false)), path -> player,
                1d, exception -> { });

        item.togglePlayPause();
        item.stop();
        item.seek(Duration.seconds(7));

        Assertions.assertThat(item.getFocusedCurrentTime()).isEqualTo(Duration.seconds(7));

        player.events.clear();
        item.togglePlayPause();

        Assertions.assertThat(player.playbackStatus).isEqualTo(PlaybackStatus.PLAYING);
        Assertions.assertThat(player.currentTime).isEqualTo(Duration.seconds(7));
        Assertions.assertThat(item.getFocusedCurrentTime()).isEqualTo(Duration.seconds(7));
        Assertions.assertThat(player.events).containsExactly("seek", "play");
    }

    @Test
    void restoresSeekPositionWhenPlaybackHasNotBeenCreatedYet() throws IOException {
        Path rootPath = TestDirectorySupport.createTempDirectory("workspace-queue-initial-seek-");
        Files.writeString(rootPath.resolve("one.mp3"), "audio");
        UUID audioId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        WorkspaceQueue queue = new WorkspaceQueue(UUID.randomUUID(), "Queue A", 0, 0.8d, false);
        queue.setTracks(List.of(new QueueTrack(trackId, audioId, 0, false)));
        queue.setSelectedTrackId(trackId);
        FakePlayingTrack player = new FakePlayingTrack();
        WorkspaceQueueItem item = new WorkspaceQueueItem(rootPath, queue,
                List.of(new AudioFile(audioId, "one.mp3", "one.mp3", false)), path -> player,
                1d, exception -> { });

        item.seek(Duration.seconds(4));

        Assertions.assertThat(item.getFocusedCurrentTime()).isEqualTo(Duration.seconds(4));

        item.togglePlayPause();

        Assertions.assertThat(player.playbackStatus).isEqualTo(PlaybackStatus.PLAYING);
        Assertions.assertThat(player.currentTime).isEqualTo(Duration.seconds(4));
    }

    private static final class FakePlayingTrack implements PlayingTrack {
        private Runnable statusChanged = () -> { };
        private int playCalls;

        @Override
        public void setOnStatusChanged(Runnable listener) { statusChanged = listener == null ? () -> { } : listener; }

        private void finish() { playbackStatus = PlaybackStatus.FINISHED; statusChanged.run(); }

        private PlaybackStatus playbackStatus = PlaybackStatus.READY;
        private double volume;
        private Duration currentTime = Duration.ZERO;
        private final List<String> events = new ArrayList<>();

        @Override
        public void play() {
            events.add("play");
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
            events.add("seek");
            currentTime = position;
        }

        @Override
        public void setVolume(double volume) {
            this.volume = volume;
        }

        @Override
        public void setLoop(boolean loop) {
        }

        @Override
        public Duration getCurrentTime() {
            return currentTime;
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
