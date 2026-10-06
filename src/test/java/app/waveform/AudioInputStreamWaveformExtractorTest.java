package app.waveform;

import app.support.TestDirectorySupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.assertj.core.data.Offset;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.InputStream;

class AudioInputStreamWaveformExtractorTest {
    @ParameterizedTest
    @ValueSource(strings = {"wav", "mp3", "ogg", "flac", "24.flac", "float.wav", "opus", "flac.ogg"})
    void analysesSupportedFormatsThroughStreamingProviders(String extension) throws Exception {
        Path directory = TestDirectorySupport.createTempDirectory("waveform-formats-");
        Path path = directory.resolve("tone." + extension);
        try (InputStream stream = getClass().getResourceAsStream("/audio/tone." + extension)) {
            Files.copy(stream, path);
        }
        WaveformData data = new AudioInputStreamWaveformExtractor(64).extract(path);
        Assertions.assertThat(data.isEmpty()).isFalse();
        Assertions.assertThat(data.size()).isEqualTo(64);
        Assertions.assertThat(data.getDuration().toSeconds()).isCloseTo(1d, Offset.offset(0.12d));
        for (double value : data.copyAmplitudes()) Assertions.assertThat(value).isBetween(0d, 1d);
    }

    @Test
    void analysesOpusEvenWhenContainerUsesOggExtension() throws Exception {
        Path directory = TestDirectorySupport.createTempDirectory("waveform-opus-");
        Path path = directory.resolve("opus-in-ogg.ogg");
        try (InputStream stream = getClass().getResourceAsStream("/audio/tone.opus")) {
            Files.copy(stream, path);
        }
        WaveformData waveform = new AudioInputStreamWaveformExtractor(64).extract(path);
        Assertions.assertThat(waveform.isEmpty()).isFalse();
        Assertions.assertThat(waveform.getDuration().toSeconds()).isCloseTo(1d, Offset.offset(0.001d));
    }

    @Test
    void honoursCancellationBeforeOpeningAudio() {
        try {
            Thread.currentThread().interrupt();
            Assertions.assertThatThrownBy(() -> new AudioInputStreamWaveformExtractor(32).extract(Path.of("missing.wav")))
                    .isInstanceOf(InterruptedIOException.class);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void extractsNormalizedWaveformFromWavFile() throws IOException {
        Path tempDirectory = TestDirectorySupport.createTempDirectory("waveform-extractor-");
        Path audioPath = tempDirectory.resolve("tone.wav");
        writeSineWave(audioPath);

        AudioInputStreamWaveformExtractor extractor = new AudioInputStreamWaveformExtractor(64);

        WaveformData waveformData = extractor.extract(audioPath);
        double[] amplitudes = waveformData.copyAmplitudes();

        Assertions.assertThat(waveformData.isEmpty()).isFalse();
        Assertions.assertThat(waveformData.size()).isEqualTo(64);
        boolean hasStrongAmplitude = false;
        for (double amplitude : amplitudes) {
            Assertions.assertThat(amplitude).isBetween(0d, 1d);
            if (amplitude > 0.5d) {
                hasStrongAmplitude = true;
            }
        }
        Assertions.assertThat(hasStrongAmplitude).isTrue();
    }

    @Test
    void preservesSilenceAndRelativeRootMeanSquareIntensity() throws IOException {
        Path tempDirectory = TestDirectorySupport.createTempDirectory("waveform-rms-");
        Path audioPath = tempDirectory.resolve("levels.wav");
        writeSteppedSineWave(audioPath);

        WaveformData waveformData = new AudioInputStreamWaveformExtractor(32).extract(audioPath);

        Assertions.assertThat(waveformData.amplitudeAt(2)).isZero();
        Assertions.assertThat(waveformData.amplitudeAt(13)).isBetween(0.08d, 0.2d);
        Assertions.assertThat(waveformData.amplitudeAt(26)).isGreaterThan(0.9d);
    }

    private void writeSineWave(Path audioPath) throws IOException {
        AudioFormat audioFormat = new AudioFormat(8_000f, 16, 1, true, false);
        int frameCount = 8_000;
        byte[] pcm = new byte[frameCount * 2];
        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            double radians = frameIndex / 8_000d * Math.PI * 2d * 220d;
            short sample = (short) (Math.sin(radians) * Short.MAX_VALUE * 0.8d);
            pcm[frameIndex * 2] = (byte) (sample & 0xFF);
            pcm[frameIndex * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(pcm);
             AudioInputStream audioInputStream = new AudioInputStream(byteArrayInputStream, audioFormat, frameCount)) {
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, audioPath.toFile());
        }
    }

    private void writeSteppedSineWave(Path audioPath) throws IOException {
        AudioFormat audioFormat = new AudioFormat(8_000f, 16, 1, true, false);
        int frameCount = 32_000;
        byte[] pcm = new byte[frameCount * 2];
        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            double level = frameIndex < 8_000 ? 0d : frameIndex < 16_000 ? 0.1d : 0.8d;
            double radians = frameIndex / 8_000d * Math.PI * 2d * 220d;
            short sample = (short) (Math.sin(radians) * Short.MAX_VALUE * level);
            pcm[frameIndex * 2] = (byte) (sample & 0xFF);
            pcm[frameIndex * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(pcm);
             AudioInputStream audioInputStream = new AudioInputStream(byteArrayInputStream, audioFormat, frameCount)) {
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, audioPath.toFile());
        }
    }
}
