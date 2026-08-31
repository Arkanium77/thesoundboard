package app.waveform;

import app.support.TestDirectorySupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;

class AudioInputStreamWaveformExtractorTest {
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
