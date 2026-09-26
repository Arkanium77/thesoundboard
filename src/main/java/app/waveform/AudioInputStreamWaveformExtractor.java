package app.waveform;

import javafx.util.Duration;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Path;

public class AudioInputStreamWaveformExtractor implements WaveformExtractor {
    private static final int PCM_SAMPLE_SIZE_BITS = 16;
    private static final int CHUNK_FRAME_COUNT = 1024;

    private final int resolution;

    public AudioInputStreamWaveformExtractor(int resolution) {
        this.resolution = Math.max(resolution, 32);
    }

    @Override
    public WaveformData extract(Path audioPath) throws IOException {
        checkInterrupted();
        try (AudioInputStream encodedStream = AudioSystem.getAudioInputStream(audioPath.toFile())) {
            AudioFormat pcmFormat = toPcmFormat(encodedStream.getFormat());
            long totalFrames = resolveTotalFrames(audioPath, encodedStream.getFormat(), encodedStream.getFrameLength());

            try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(pcmFormat, encodedStream)) {
                if (totalFrames > 0L) {
                    return extractFixedResolution(pcmStream, pcmFormat, totalFrames);
                }
                return extractChunkedResolution(pcmStream, pcmFormat);
            }
        } catch (UnsupportedAudioFileException exception) {
            throw new IOException("Unsupported audio file: " + audioPath, exception);
        }
    }

    /**
     * Reduces a known-length stream directly into paired peak and RMS envelopes. Keeping both is intentional:
     * peak mode preserves the original transient-oriented display, while RMS avoids making mastered music look
     * uniformly loud; a simple signed average would cancel positive and negative PCM samples. Exact zero remains
     * zero in both envelopes. Keep this rationale synchronized with the unknown-length path and mode definitions.
     */
    private WaveformData extractFixedResolution(AudioInputStream pcmStream, AudioFormat pcmFormat, long totalFrames) throws IOException {
        double[] squaredAmplitudeSums = new double[resolution];
        double[] peaks = new double[resolution];
        long[] frameCounts = new long[resolution];
        int frameSize = pcmFormat.getFrameSize();
        byte[] buffer = new byte[Math.max(frameSize * 1024, 4096)];
        long frameIndex = 0L;

        int bytesRead;
        while ((bytesRead = pcmStream.read(buffer)) >= 0) {
            checkInterrupted();
            int usableBytes = bytesRead - bytesRead % frameSize;
            for (int offset = 0; offset < usableBytes; offset += frameSize) {
                double amplitude = decodeFrameAmplitude(buffer, offset, pcmFormat.getChannels());
                int bucketIndex = (int) Math.min(resolution - 1L, frameIndex * resolution / Math.max(totalFrames, 1L));
                squaredAmplitudeSums[bucketIndex] += amplitude * amplitude;
                peaks[bucketIndex] = Math.max(peaks[bucketIndex], amplitude);
                frameCounts[bucketIndex]++;
                frameIndex++;
            }
        }

        return new WaveformData(
                normalize(peaks),
                normalize(toRootMeanSquare(squaredAmplitudeSums, frameCounts)),
                durationOf(totalFrames, pcmFormat)
        );
    }

    /**
     * Reduces unknown-length audio with bounded primitive buckets. When storage fills, adjacent buckets merge and
     * their frame span doubles; weighted energy sums preserve RMS and maxima preserve peaks, including a short final
     * bucket. This intentionally trades temporal detail for a fixed memory budget on arbitrarily long recordings.
     * Cancellation is checked between PCM blocks in both extraction paths so project switches release decoder workers.
     */
    private WaveformData extractChunkedResolution(AudioInputStream pcmStream, AudioFormat pcmFormat) throws IOException {
        WaveformAccumulator accumulator = new WaveformAccumulator(resolution, CHUNK_FRAME_COUNT);
        int frameSize = pcmFormat.getFrameSize();
        byte[] buffer = new byte[Math.max(frameSize * 1024, 4096)];
        long totalFrames = 0L;
        int bytesRead;
        while ((bytesRead = pcmStream.read(buffer)) >= 0) {
            checkInterrupted();
            int usableBytes = bytesRead - bytesRead % frameSize;
            totalFrames += usableBytes / frameSize;
            for (int offset = 0; offset < usableBytes; offset += frameSize) {
                accumulator.add(decodeFrameAmplitude(buffer, offset, pcmFormat.getChannels()));
            }
        }
        return accumulator.finish(durationOf(totalFrames, pcmFormat));
    }

    private void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Waveform extraction cancelled");
    }

    private AudioFormat toPcmFormat(AudioFormat sourceFormat) {
        int channels = Math.max(sourceFormat.getChannels(), 1);
        float sampleRate = sourceFormat.getSampleRate() > 0f ? sourceFormat.getSampleRate() : 44100f;
        return new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate,
                PCM_SAMPLE_SIZE_BITS,
                channels,
                channels * 2,
                sampleRate,
                false
        );
    }

    private long resolveTotalFrames(Path audioPath, AudioFormat sourceFormat, long streamFrameLength) {
        if (streamFrameLength > 0L) {
            return streamFrameLength;
        }

        try {
            AudioFileFormat audioFileFormat = AudioSystem.getAudioFileFormat(audioPath.toFile());
            Object durationValue = audioFileFormat.properties().get("duration");
            if (durationValue instanceof Long durationMicros && sourceFormat.getSampleRate() > 0f) {
                return Math.round(durationMicros * sourceFormat.getSampleRate() / 1_000_000d);
            }
        } catch (Exception ignored) {
        }

        return -1L;
    }

    private double decodeFrameAmplitude(byte[] buffer, int offset, int channels) {
        double peak = 0d;
        for (int channel = 0; channel < channels; channel++) {
            int sampleOffset = offset + channel * 2;
            if (sampleOffset + 1 >= buffer.length) {
                break;
            }

            int low = buffer[sampleOffset] & 0xFF;
            int high = buffer[sampleOffset + 1];
            short sample = (short) ((high << 8) | low);
            peak = Math.max(peak, Math.abs(sample) / 32768d);
        }
        return peak;
    }

    private double[] normalize(double[] peaks) {
        double maxAmplitude = 0d;
        for (double peak : peaks) {
            maxAmplitude = Math.max(maxAmplitude, peak);
        }

        if (maxAmplitude <= 0d) {
            return peaks;
        }

        double[] normalized = new double[peaks.length];
        for (int index = 0; index < peaks.length; index++) {
            normalized[index] = Math.max(0d, Math.min(1d, peaks[index] / maxAmplitude));
        }
        return normalized;
    }

    private double[] toRootMeanSquare(double[] squaredAmplitudeSums, long[] frameCounts) {
        double[] levels = new double[squaredAmplitudeSums.length];
        for (int index = 0; index < squaredAmplitudeSums.length; index++) {
            if (frameCounts[index] > 0L) {
                levels[index] = Math.sqrt(squaredAmplitudeSums[index] / frameCounts[index]);
            }
        }
        return levels;
    }

    private Duration durationOf(long frameCount, AudioFormat audioFormat) {
        if (frameCount <= 0L || audioFormat.getFrameRate() <= 0f) return Duration.ZERO;
        return Duration.seconds(frameCount / audioFormat.getFrameRate());
    }
}
