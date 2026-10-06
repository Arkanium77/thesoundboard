package app.waveform;

import app.support.TestDirectorySupport;
import org.assertj.core.api.Assertions;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

class OpusWaveformExtractorTest {
    @ParameterizedTest
    @ValueSource(strings = {"levels.stereo.opus", "tone.surround.opus"})
    void trimsPaddingAndSupportsChannelMappings(String name) throws Exception {
        WaveformData data = new AudioInputStreamWaveformExtractor(64).extract(fixture(name));
        Assertions.assertThat(data.isEmpty()).isFalse();
        Assertions.assertThat(data.getDuration().toSeconds()).isCloseTo(1.013d, Offset.offset(0.0001d));
        Assertions.assertThat(data.size()).isEqualTo(64);
        for (double amplitude : data.copyAmplitudes()) Assertions.assertThat(amplitude).isBetween(0d, 1d);
    }

    @Test
    void preservesSilenceAndRelativeLevels() throws Exception {
        WaveformData data = new AudioInputStreamWaveformExtractor(96).extract(fixture("levels.opus"));
        Assertions.assertThat(data.amplitudeAt(10)).isZero();
        Assertions.assertThat(data.amplitudeAt(46)).isBetween(0.08d, 0.2d);
        Assertions.assertThat(data.amplitudeAt(83)).isGreaterThan(0.9d);
        Assertions.assertThat(data.getDuration().toSeconds()).isCloseTo(3d, Offset.offset(0.0001d));
    }

    @Test
    void handlesChainedStreamsWithIndependentPreSkip() throws Exception {
        Path original = fixture("tone.opus");
        Path chained = original.resolveSibling("chained.opus");
        try (var output = Files.newOutputStream(chained)) {
            Files.copy(original, output);
            Files.copy(original, output);
        }
        WaveformData data = new AudioInputStreamWaveformExtractor(64).extract(chained);
        Assertions.assertThat(data.getDuration().toSeconds()).isCloseTo(2d, Offset.offset(0.0001d));
        Assertions.assertThat(data.isEmpty()).isFalse();
    }

    @Test
    void rejectsTruncatedAudioRatherThanReportingPaddingAsDuration() throws Exception {
        Path original = fixture("tone.opus");
        byte[] bytes = Files.readAllBytes(original);
        Path truncated = original.resolveSibling("truncated.opus");
        Files.write(truncated, Arrays.copyOf(bytes, bytes.length - 16));
        Assertions.assertThatThrownBy(() -> new AudioInputStreamWaveformExtractor(64).extract(truncated))
                .isInstanceOf(IOException.class);
    }

    @Test
    void cancelsDuringDecodingAndReleasesInput() throws Exception {
        Path original = fixture("tone.opus");
        Path longFile = original.resolveSibling("long.opus");
        try (var output = Files.newOutputStream(longFile)) {
            for (int index = 0; index < 1000; index++) Files.copy(original, output);
        }
        FutureTask<Boolean> result = new FutureTask<>(() -> {
            try {
                new AudioInputStreamWaveformExtractor(64).extract(longFile);
                return false;
            } catch (InterruptedIOException exception) {
                return true;
            }
        });
        Thread worker = new Thread(result, "opus-cancellation-test");
        worker.start();
        try {
            Thread.sleep(30);
            worker.interrupt();
            Assertions.assertThat(result.get(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            worker.interrupt();
            worker.join(5000);
        }
        Files.delete(longFile);
        Assertions.assertThat(Files.exists(longFile)).isFalse();
    }

    private Path fixture(String name) throws IOException {
        Path path = TestDirectorySupport.createTempDirectory("opus-waveform-").resolve(name);
        try (InputStream input = getClass().getResourceAsStream("/audio/" + name)) {
            Files.copy(input, path);
        }
        return path;
    }
}
