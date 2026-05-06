package app.waveform;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AudioInputStreamWaveformExtractor implements WaveformExtractor {
    private static final int PCM_SAMPLE_SIZE_BITS = 16;
    private static final int CHUNK_FRAME_COUNT = 1024;

    private final int resolution;

    public AudioInputStreamWaveformExtractor(int resolution) {
        this.resolution = Math.max(resolution, 32);
    }

    @Override
    public WaveformData extract(Path audioPath) throws IOException {
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

    private WaveformData extractFixedResolution(AudioInputStream pcmStream, AudioFormat pcmFormat, long totalFrames) throws IOException {
        double[] peaks = new double[resolution];
        int frameSize = pcmFormat.getFrameSize();
        byte[] buffer = new byte[Math.max(frameSize * 1024, 4096)];
        long frameIndex = 0L;

        int bytesRead;
        while ((bytesRead = pcmStream.read(buffer)) >= 0) {
            int usableBytes = bytesRead - bytesRead % frameSize;
            for (int offset = 0; offset < usableBytes; offset += frameSize) {
                double amplitude = decodeFrameAmplitude(buffer, offset, pcmFormat.getChannels());
                int bucketIndex = (int) Math.min(resolution - 1L, frameIndex * resolution / Math.max(totalFrames, 1L));
                peaks[bucketIndex] = Math.max(peaks[bucketIndex], amplitude);
                frameIndex++;
            }
        }

        return new WaveformData(normalize(peaks));
    }

    private WaveformData extractChunkedResolution(AudioInputStream pcmStream, AudioFormat pcmFormat) throws IOException {
        List<Double> chunkPeaks = new ArrayList<>();
        int frameSize = pcmFormat.getFrameSize();
        byte[] buffer = new byte[Math.max(frameSize * 1024, 4096)];
        int chunkFrameIndex = 0;
        double chunkPeak = 0d;

        int bytesRead;
        while ((bytesRead = pcmStream.read(buffer)) >= 0) {
            int usableBytes = bytesRead - bytesRead % frameSize;
            for (int offset = 0; offset < usableBytes; offset += frameSize) {
                chunkPeak = Math.max(chunkPeak, decodeFrameAmplitude(buffer, offset, pcmFormat.getChannels()));
                chunkFrameIndex++;
                if (chunkFrameIndex >= CHUNK_FRAME_COUNT) {
                    chunkPeaks.add(chunkPeak);
                    chunkPeak = 0d;
                    chunkFrameIndex = 0;
                }
            }
        }

        if (chunkFrameIndex > 0 || chunkPeaks.isEmpty()) {
            chunkPeaks.add(chunkPeak);
        }

        double[] peaks = new double[resolution];
        for (int index = 0; index < resolution; index++) {
            int startIndex = (int) Math.floor((double) index * chunkPeaks.size() / resolution);
            int endIndex = (int) Math.floor((double) (index + 1) * chunkPeaks.size() / resolution);
            if (endIndex <= startIndex) {
                endIndex = Math.min(startIndex + 1, chunkPeaks.size());
            }

            double peak = 0d;
            for (int chunkIndex = startIndex; chunkIndex < endIndex; chunkIndex++) {
                peak = Math.max(peak, chunkPeaks.get(chunkIndex));
            }
            peaks[index] = peak;
        }

        return new WaveformData(normalize(peaks));
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
}
