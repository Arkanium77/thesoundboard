package app.ui.workspace;

import app.audio.AudioEngine;
import app.audio.PlayingTrack;
import app.model.AudioFile;
import app.model.PlaybackStatus;
import app.model.WorkspaceTrack;
import app.support.TestDirectorySupport;
import javafx.util.Duration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class WorkspaceTrackItemTest {
    @Test
    void createsPlayingTrackLazilyOnFirstPlayback() throws IOException {
        Path rootPath = TestDirectorySupport.createTempDirectory("workspace-track-item-");
        Files.writeString(rootPath.resolve("theme.mp3"), "audio");

        AtomicInteger createTrackCalls = new AtomicInteger();
        FakePlayingTrack fakePlayingTrack = new FakePlayingTrack();
        AudioEngine audioEngine = audioPath -> {
            createTrackCalls.incrementAndGet();
            return fakePlayingTrack;
        };

        WorkspaceTrack workspaceTrack = new WorkspaceTrack(UUID.randomUUID(), UUID.randomUUID(), 0, 0.7d, true);
        AudioFile audioFile = new AudioFile(workspaceTrack.getAudioFileId(), "theme.mp3", "theme.mp3", false);

        WorkspaceTrackItem workspaceTrackItem = new WorkspaceTrackItem(
                rootPath,
                workspaceTrack,
                audioFile,
                audioEngine,
                0.5d,
                exception -> {
                }
        );

        Assertions.assertThat(createTrackCalls.get()).isZero();
        Assertions.assertThat(workspaceTrackItem.getStatus()).isEqualTo(PlaybackStatus.READY);
        Assertions.assertThat(workspaceTrackItem.getTotalDuration()).isEqualTo(Duration.ZERO);

        workspaceTrackItem.togglePlayPause();

        Assertions.assertThat(createTrackCalls.get()).isEqualTo(1);
        Assertions.assertThat(fakePlayingTrack.playCalls).isEqualTo(1);
        Assertions.assertThat(fakePlayingTrack.loop).isTrue();
        Assertions.assertThat(fakePlayingTrack.volume).isEqualTo(0.35d);
    }

    @Test
    void pausesOnlyPlayingTrackAndCanResumePausedTrack() throws IOException {
        Path rootPath = TestDirectorySupport.createTempDirectory("workspace-track-item-pause-");
        Files.writeString(rootPath.resolve("theme.mp3"), "audio");

        FakePlayingTrack fakePlayingTrack = new FakePlayingTrack();
        AudioEngine audioEngine = audioPath -> fakePlayingTrack;

        WorkspaceTrack workspaceTrack = new WorkspaceTrack(UUID.randomUUID(), UUID.randomUUID(), 0, 0.7d, false);
        AudioFile audioFile = new AudioFile(workspaceTrack.getAudioFileId(), "theme.mp3", "theme.mp3", false);

        WorkspaceTrackItem workspaceTrackItem = new WorkspaceTrackItem(
                rootPath,
                workspaceTrack,
                audioFile,
                audioEngine,
                1d,
                exception -> {
                }
        );

        Assertions.assertThat(workspaceTrackItem.pauseIfPlaying()).isFalse();
        workspaceTrackItem.togglePlayPause();

        Assertions.assertThat(workspaceTrackItem.pauseIfPlaying()).isTrue();
        Assertions.assertThat(fakePlayingTrack.playbackStatus).isEqualTo(PlaybackStatus.PAUSED);
        Assertions.assertThat(workspaceTrackItem.pauseIfPlaying()).isFalse();
        Assertions.assertThat(workspaceTrackItem.resumeIfPaused()).isTrue();
        Assertions.assertThat(fakePlayingTrack.playbackStatus).isEqualTo(PlaybackStatus.PLAYING);
    }

    @Test
    void restoresPlaybackPositionAndPausedStateAfterMovingBetweenContainers() throws IOException {
        Path rootPath = TestDirectorySupport.createTempDirectory("workspace-track-item-transfer-");
        Files.writeString(rootPath.resolve("theme.mp3"), "audio");
        FakePlayingTrack sourceTrack = new FakePlayingTrack();
        sourceTrack.currentTime = Duration.seconds(4);
        sourceTrack.playbackStatus = PlaybackStatus.PAUSED;
        WorkspaceTrack model = new WorkspaceTrack(UUID.randomUUID(), UUID.randomUUID(), 0, 0.7d, false);
        AudioFile audioFile = new AudioFile(model.getAudioFileId(), "theme.mp3", "theme.mp3", false);
        WorkspaceTrackItem source = new WorkspaceTrackItem(
                rootPath, model, audioFile, path -> sourceTrack, 1d, exception -> { });
        source.togglePlayPause();
        source.pauseIfPlaying();
        PlaybackSnapshot snapshot = source.snapshotPlayback();

        FakePlayingTrack targetTrack = new FakePlayingTrack();
        WorkspaceTrackItem target = new WorkspaceTrackItem(
                rootPath, model, audioFile, path -> targetTrack, 1d, exception -> { });
        target.restorePlayback(snapshot);

        Assertions.assertThat(targetTrack.seekPosition).isEqualTo(Duration.seconds(4));
        Assertions.assertThat(targetTrack.playbackStatus).isEqualTo(PlaybackStatus.PAUSED);
    }

    private static final class FakePlayingTrack implements PlayingTrack {
        private int playCalls;
        private double volume;
        private boolean loop;
        private PlaybackStatus playbackStatus = PlaybackStatus.READY;
        private Duration currentTime = Duration.ZERO;
        private Duration seekPosition = Duration.ZERO;

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
            seekPosition = position;
        }

        @Override
        public void setVolume(double volume) {
            this.volume = volume;
        }

        @Override
        public void setLoop(boolean loop) {
            this.loop = loop;
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
