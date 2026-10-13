package app.audio;

import app.model.PlaybackStatus;
import app.waveform.GstreamerWaveformExtractor;
import app.waveform.WaveformData;
import javafx.util.Duration;
import org.assertj.core.api.Assertions;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Format;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.event.SeekFlags;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.elements.AppSink;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.function.BooleanSupplier;
import java.util.concurrent.atomic.AtomicReference;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.Locale;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;

/** Exercises the real Java binding without opening speakers; opt in with a private runtime path. */
class GstreamerIntegrationTest {
    @Test
    void platformAudioPluginCanBeConstructedWithoutOpeningADevice() {
        prepareRuntime();
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        Element output = ElementFactory.make(os.contains("windows") ? "wasapisink"
                : os.contains("mac") ? "osxaudiosink" : "pulsesink", "native-output-smoke");
        try {
            Assertions.assertThat(output.getState(0, TimeUnit.NANOSECONDS)).isEqualTo(State.NULL);
        } finally {
            output.dispose();
        }
    }

    @Test
    void privateManifestDoesNotExposeUnselectedVideoFactories() {
        String runtime = System.getenv("THESOUNDBOARD_GSTREAMER_TEST_RUNTIME");
        Assumptions.assumeTrue(runtime != null);
        Assumptions.assumeTrue(Files.isRegularFile(Path.of(runtime, "runtime-manifest.json")));
        prepareRuntime();
        Assertions.assertThatThrownBy(() -> ElementFactory.find("videotestsrc"))
                .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> ElementFactory.find("wasapi2sink"))
                .isInstanceOf(IllegalArgumentException.class);
        ElementFactory playback = ElementFactory.find("playbin");
        Assertions.assertThat(playback).isNotNull();
        playback.dispose();
    }

    @AfterAll
    static void shutdownTestRuntime() {
        if (System.getenv("THESOUNDBOARD_GSTREAMER_TEST_RUNTIME") != null) GstreamerRuntime.shutdown();
    }

    @Test
    @Timeout(30)
    void restoresPausedPositionAndCompletesWithoutPollingListeners() throws Exception {
        prepareRuntime();
        GstreamerAudioEngine engine = new GstreamerAudioEngine(Runnable::run, "fakesink");
        try {
            PlayingTrack track = engine.createTrack(Path.of(getClass().getResource("/audio/tone.mp3").toURI()));
            CountDownLatch finished = new CountDownLatch(1);
            track.setOnStatusChanged(() -> {
                if (track.getStatus() == PlaybackStatus.FINISHED) finished.countDown();
            });
            track.restorePlayback(Duration.millis(200), true);
            await(() -> track.getStatus() == PlaybackStatus.PAUSED);
            Assertions.assertThat(track.getCurrentTime().toMillis()).isBetween(150d, 300d);
            track.play();
            await(() -> track.getStatus() == PlaybackStatus.PLAYING);
            track.pause();
            await(() -> track.getStatus() == PlaybackStatus.PAUSED);
            track.stop();
            await(() -> track.getStatus() == PlaybackStatus.STOPPED);
            Assertions.assertThat(track.getCurrentTime()).isEqualTo(Duration.ZERO);
            track.seek(Duration.millis(300));
            await(() -> track.getCurrentTime().toMillis() == 300d);
            Assertions.assertThat(track.getStatus()).isEqualTo(PlaybackStatus.STOPPED);
            track.play();
            Assertions.assertThat(finished.await(10, TimeUnit.SECONDS)).isTrue();
            Assertions.assertThat(track.getStatus()).isEqualTo(PlaybackStatus.FINISHED);
            track.dispose();
        } finally {
            engine.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void replaysAfterStopAndEndWithRecreatedDecoderPads() throws Exception {
        prepareRuntime();
        GstreamerAudioEngine engine = new GstreamerAudioEngine(Runnable::run, "fakesink");
        try {
            for (String extension : List.of("mp3", "wav", "ogg", "flac", "opus")) {
                PlayingTrack track = engine.createTrack(Path.of(getClass().getResource("/audio/tone." + extension).toURI()));
                for (int replay = 0; replay < 3; replay++) {
                    track.play();
                    await(() -> track.getStatus() == PlaybackStatus.PLAYING || track.getStatus() == PlaybackStatus.ERROR);
                    Assertions.assertThat(track.getStatus()).as(extension).isEqualTo(PlaybackStatus.PLAYING);
                    await(() -> track.getCurrentTime().toMillis() > 50d);
                    track.stop();
                    await(() -> track.getStatus() == PlaybackStatus.STOPPED);
                    Assertions.assertThat(track.getCurrentTime()).isEqualTo(Duration.ZERO);
                }
                track.play();
                await(() -> track.getStatus() == PlaybackStatus.FINISHED || track.getStatus() == PlaybackStatus.ERROR);
                Assertions.assertThat(track.getStatus()).as(extension).isEqualTo(PlaybackStatus.FINISHED);
                track.play();
                await(() -> track.getStatus() == PlaybackStatus.PLAYING || track.getStatus() == PlaybackStatus.ERROR);
                Assertions.assertThat(track.getStatus()).as(extension).isEqualTo(PlaybackStatus.PLAYING);
                track.dispose();
            }
        } finally {
            engine.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void buildsBoundedWaveformsUsingThePlaybackDecoderFamily() throws Exception {
        prepareRuntime();
        GstreamerWaveformExtractor extractor = new GstreamerWaveformExtractor(128);
        for (String fixture : new String[] {"tone.mp3", "tone.wav", "tone.ogg", "tone.flac", "tone.opus"}) {
            WaveformData waveform = extractor.extract(Path.of(getClass().getResource("/audio/" + fixture).toURI()));
            Assertions.assertThat(waveform.size()).as(fixture).isEqualTo(128);
            Assertions.assertThat(waveform.getDuration().toMillis()).as(fixture).isPositive();
            Assertions.assertThat(waveform.copyPeakAmplitudes()).as(fixture).contains(1d);
            Assertions.assertThat(waveform.copyRmsAmplitudes()).as(fixture).contains(1d);
        }
    }

    @Test
    @Timeout(30)
    void loopsWithoutFinishingAndKeepsAnotherPlayersPausedSeek() throws Exception {
        prepareRuntime();
        GstreamerAudioEngine engine = new GstreamerAudioEngine(Runnable::run, "fakesink");
        try {
            Path path = AudioTestSupport.fixture("mp3");
            PlayingTrack looping = engine.createTrack(path);
            PlayingTrack paused = engine.createTrack(path);
            CountDownLatch finished = new CountDownLatch(1);
            looping.setOnStatusChanged(() -> {
                if (looping.getStatus() == PlaybackStatus.FINISHED) finished.countDown();
            });
            paused.restorePlayback(Duration.millis(600), true);
            await(() -> paused.getStatus() == PlaybackStatus.PAUSED);
            looping.setLoop(true);
            looping.play();
            await(() -> looping.getTotalDuration().greaterThan(Duration.ZERO));
            looping.seek(looping.getTotalDuration().subtract(Duration.millis(100)));
            Assertions.assertThat(finished.await(1500, TimeUnit.MILLISECONDS)).isFalse();
            Assertions.assertThat(looping.getStatus()).isEqualTo(PlaybackStatus.PLAYING);
            Assertions.assertThat(paused.getStatus()).isEqualTo(PlaybackStatus.PAUSED);
            Assertions.assertThat(paused.getCurrentTime().toMillis()).isBetween(550d, 650d);
            looping.setLoop(false);
            looping.seek(looping.getTotalDuration().subtract(Duration.millis(100)));
            Assertions.assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
            looping.dispose();
            paused.dispose();
        } finally {
            engine.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void reportsInvalidAudioWithoutPollingAndHonoursWaveformCancellation() throws Exception {
        prepareRuntime();
        Path invalid = AudioTestSupport.fixture("wav");
        Files.writeString(invalid, "not an audio stream");
        GstreamerAudioEngine engine = new GstreamerAudioEngine(Runnable::run, "fakesink");
        try {
            PlayingTrack track = engine.createTrack(invalid);
            CountDownLatch failed = new CountDownLatch(1);
            track.setOnStatusChanged(() -> {
                if (track.getStatus() == PlaybackStatus.ERROR) failed.countDown();
            });
            track.play();
            Assertions.assertThat(failed.await(5, TimeUnit.SECONDS)).isTrue();
            Assertions.assertThat(track.getStatus()).isEqualTo(PlaybackStatus.ERROR);
            GstreamerWaveformExtractor extractor = new GstreamerWaveformExtractor(128);
            Assertions.assertThatThrownBy(() -> extractor.extract(invalid)).isInstanceOf(IOException.class);
            Thread.currentThread().interrupt();
            try {
                Assertions.assertThatThrownBy(() -> extractor.extract(invalid)).isInstanceOf(IOException.class)
                        .hasMessageContaining("cancelled");
                Assertions.assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                Thread.interrupted();
            }
            track.dispose();
        } finally {
            engine.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void decodesAndSeeksGeneratedAacAndM4aWithoutExternalFixtures() throws Exception {
        prepareRuntime();
        Path wave = AudioTestSupport.fixture("wav");
        GstreamerAudioEngine engine = new GstreamerAudioEngine(Runnable::run, "fakesink");
        try {
            for (String extension : List.of("aac", "m4a")) {
                Path encoded = wave.resolveSibling("Тестовый звук." + extension);
                encodeAac(wave, encoded, extension.equals("m4a"));
                Assertions.assertThat(AudioEngineMode.GSTREAMER.supportsFile(encoded)).as(extension).isTrue();
                WaveformData waveform = new GstreamerWaveformExtractor(128).extract(encoded);
                Assertions.assertThat(waveform.getDuration().toMillis()).isBetween(900d, 1200d);
                PlayingTrack track = engine.createTrack(encoded);
                track.restorePlayback(Duration.millis(500), true);
                await(() -> track.getStatus() == PlaybackStatus.PAUSED || track.getStatus() == PlaybackStatus.ERROR);
                Assertions.assertThat(track.getStatus()).as(extension).isEqualTo(PlaybackStatus.PAUSED);
                Assertions.assertThat(track.getCurrentTime().toMillis()).as(extension).isBetween(400d, 600d);
                track.play();
                await(() -> track.getStatus() == PlaybackStatus.FINISHED || track.getStatus() == PlaybackStatus.ERROR);
                Assertions.assertThat(track.getStatus()).as(extension).isEqualTo(PlaybackStatus.FINISHED);
                track.restorePlayback(Duration.millis(300), true);
                await(() -> track.getStatus() == PlaybackStatus.PAUSED || track.getStatus() == PlaybackStatus.ERROR);
                Assertions.assertThat(track.getStatus()).as(extension).isEqualTo(PlaybackStatus.PAUSED);
                Assertions.assertThat(track.getCurrentTime().toMillis()).as(extension).isBetween(250d, 350d);
                track.dispose();
            }
        } finally {
            engine.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void preservesAShortTransientAndDecodesAiff() throws Exception {
        prepareRuntime();
        Path file = AudioTestSupport.fixture("wav");
        byte[] pcm = new byte[480 * Short.BYTES];
        ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).putShort(240 * Short.BYTES, (short) 20000);
        AudioFormat format = new AudioFormat(48000f, 16, 1, true, false);
        for (AudioFileFormat.Type type : List.of(AudioFileFormat.Type.WAVE, AudioFileFormat.Type.AIFF)) {
            Path sound = file.resolveSibling("Короткий импульс." + type.getExtension());
            try (AudioInputStream input = new AudioInputStream(new ByteArrayInputStream(pcm), format, 480)) {
                AudioSystem.write(input, type, sound.toFile());
            }
            WaveformData waveform = new GstreamerWaveformExtractor(128).extract(sound);
            Assertions.assertThat(waveform.getDuration().toMillis()).isBetween(9d, 11d);
            double[] peaks = waveform.copyPeakAmplitudes();
            Assertions.assertThat(peaks).contains(0d, 1d);
            Assertions.assertThat(peaks[0]).isZero();
            Assertions.assertThat(peaks[peaks.length - 1]).isZero();
            Assertions.assertThat(peaks[64]).isEqualTo(1d);
        }
    }

    private void encodeAac(Path wave, Path output, boolean m4a) throws Exception {
        String muxer = m4a ? "aacparse ! mp4mux" : "aacparse ! audio/mpeg,mpegversion=4,stream-format=adts";
        Pipeline pipeline = (Pipeline) Gst.parseLaunch("filesrc name=source ! wavparse ! audioconvert ! "
                + "avenc_aac ! " + muxer + " ! filesink name=output");
        Element source = pipeline.getElementByName("source");
        Element destination = pipeline.getElementByName("output");
        Bus bus = pipeline.getBus();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<String> failure = new AtomicReference<>();
        bus.connect((Bus.EOS) ignored -> completed.countDown());
        bus.connect((Bus.ERROR) (ignored, code, message) -> { failure.set(message); completed.countDown(); });
        try {
            source.set("location", wave.toString());
            destination.set("location", output.toString());
            pipeline.setState(State.PLAYING);
            Assertions.assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
            Assertions.assertThat(failure.get()).isNull();
        } finally {
            pipeline.setState(State.NULL);
            source.dispose();
            destination.dispose();
            bus.dispose();
            pipeline.dispose();
        }
    }

    @Test
    @Timeout(60)
    void handlesM4aCodecsWithTheSamePlayerAndWaveformApi() throws Exception {
        String fixtures = System.getenv("THESOUNDBOARD_GSTREAMER_TEST_M4A");
        Assumptions.assumeTrue(fixtures != null);
        prepareRuntime();
        List<Path> files;
        try (var paths = Files.list(Path.of(fixtures))) {
            files = paths.filter(path -> path.toString().endsWith(".m4a")).toList();
        }
        Assertions.assertThat(files).isNotEmpty();
        GstreamerWaveformExtractor extractor = new GstreamerWaveformExtractor(128);
        GstreamerAudioEngine engine = new GstreamerAudioEngine(Runnable::run, "fakesink");
        try {
            for (Path file : files) {
                Assertions.assertThat(AudioEngineMode.GSTREAMER.supportsFile(file)).as(file.toString()).isTrue();
                WaveformData waveform = extractor.extract(file);
                Assertions.assertThat(waveform.size()).as(file.toString()).isEqualTo(128);
                PlayingTrack track = engine.createTrack(file);
                Duration halfway = waveform.getDuration().multiply(0.5);
                track.restorePlayback(halfway, true);
                await(() -> track.getStatus() == PlaybackStatus.PAUSED || track.getStatus() == PlaybackStatus.ERROR);
                Assertions.assertThat(track.getStatus()).as(file.toString()).isEqualTo(PlaybackStatus.PAUSED);
                Assertions.assertThat(track.getCurrentTime().toMillis()).as(file.toString())
                        .isBetween(halfway.toMillis() - 200d, halfway.toMillis() + 200d);
                track.seek(track.getTotalDuration().subtract(Duration.millis(100)));
                track.play();
                await(() -> track.getStatus() == PlaybackStatus.FINISHED || track.getStatus() == PlaybackStatus.ERROR);
                Assertions.assertThat(track.getStatus()).as(file.toString()).isEqualTo(PlaybackStatus.FINISHED);
                track.dispose();
            }
        } finally {
            engine.shutdown();
        }
    }

    private void prepareRuntime() {
        String runtime = System.getenv("THESOUNDBOARD_GSTREAMER_TEST_RUNTIME");
        Assumptions.assumeTrue(runtime != null);
        System.setProperty("thesoundboard.gstreamer.path", runtime);
        GstreamerRuntime.initialize();
    }

    private void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        Assertions.assertThat(condition.getAsBoolean()).isTrue();
    }

    @Test
    @Timeout(60)
    void decodesAndSeeksThroughThePrivateRuntime() throws Exception {
        String runtime = System.getenv("THESOUNDBOARD_GSTREAMER_TEST_RUNTIME");
        Assumptions.assumeTrue(runtime != null);
        System.setProperty("thesoundboard.gstreamer.path", runtime);
        GstreamerRuntime.initialize();
        for (String fixture : new String[] {"tone.mp3", "tone.wav", "tone.ogg", "tone.flac",
                "tone.flac.ogg", "tone.float.wav", "tone.opus", "tone.surround.opus"}) {
            Path file = Path.of(getClass().getResource("/audio/" + fixture).toURI());
            Pipeline pipeline = (Pipeline) Gst.parseLaunch("filesrc name=source ! decodebin ! audioconvert ! "
                    + "audioresample ! audio/x-raw,format=F32LE,channels=2,rate=48000 ! "
                    + "appsink name=wave sync=false max-buffers=2 drop=false wait-on-eos=false");
            pipeline.getElementByName("source").set("location", file.toString());
            AppSink sink = (AppSink) pipeline.getElementByName("wave");
            try {
                pipeline.setState(State.PAUSED);
                Assertions.assertThat(pipeline.getState(5, TimeUnit.SECONDS)).as(fixture).isEqualTo(State.PAUSED);
                long duration = pipeline.queryDuration(Format.TIME);
                Assertions.assertThat(duration).as(fixture).isPositive();
                Assertions.assertThat(pipeline.seekSimple(Format.TIME,
                        EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE), duration / 2)).as(fixture).isTrue();
                pipeline.setState(State.PLAYING);
                long frames = 0;
                double peak = 0;
                Sample sample;
                while ((sample = sink.pullSample()) != null) {
                    try {
                        Buffer buffer = sample.getBuffer();
                        ByteBuffer bytes = buffer.map(false).order(ByteOrder.LITTLE_ENDIAN);
                        try {
                            frames += bytes.remaining() / 8;
                            while (bytes.remaining() >= 4) peak = Math.max(peak, Math.abs(bytes.getFloat()));
                        } finally {
                            buffer.unmap();
                        }
                    } finally {
                        sample.dispose();
                    }
                }
                // A successful seek call alone does not prove that decoding resumed at the target.
                // Count remaining PCM against half the duration, allowing codec padding/resampling.
                long expectedFrames = duration * 48000 / TimeUnit.SECONDS.toNanos(1) / 2;
                Assertions.assertThat(frames).as(fixture).isBetween(expectedFrames - 4800, expectedFrames + 4800);
                Assertions.assertThat(peak).as(fixture).isGreaterThan(0.01);
                Assertions.assertThat(sink.isEOS()).as(fixture).isTrue();
            } finally {
                pipeline.setState(State.NULL);
                pipeline.getState(5, TimeUnit.SECONDS);
                pipeline.dispose();
            }
        }
    }
}
