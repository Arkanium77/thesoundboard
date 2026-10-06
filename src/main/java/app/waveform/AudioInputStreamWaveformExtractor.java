package app.waveform;

import app.audio.OggAudioMetadata;
import app.audio.OggAudioMetadata.Codec;
import javazoom.spi.vorbis.sampled.file.VorbisEncoding;
import javafx.util.Duration;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.InputStream;
import java.io.BufferedInputStream;

/**
 * Uses Java Sound providers for background MP3, PCM WAV, Vorbis and FLAC analysis independently of playback.
 * The native binding exposes no streaming PCM reader, so using its whole-file decode API would violate the
 * bounded-memory design. Opus uses a separate portable packet decoder; detect it by container contents,
 * including .ogg files, rather than sending it to an unsupported Java Sound provider. Ogg FLAC packets are
 * demultiplexed into their original encoded bitstream for jFLAC; both Ogg paths share bounded packet input.
 * No source file is converted or cached as audio; only the existing bounded envelopes are retained.
 * The encoded input is buffered before provider probing: some providers otherwise issue small reads directly
 * against a FileInputStream, making container analysis unnecessarily dependent on filesystem call overhead.
 */
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
        Codec codec = OggAudioMetadata.codec(audioPath);
        if (codec == Codec.OPUS) return new OpusWaveformExtractor(resolution).extract(audioPath);
        try (InputStream input = new BufferedInputStream(codec == Codec.FLAC
                ? OggFlacInputStream.open(audioPath) : Files.newInputStream(audioPath));
                AudioInputStream encodedStream = AudioSystem.getAudioInputStream(input)) {
            AudioFormat pcmFormat = toPcmFormat(encodedStream.getFormat());
            long totalFrames = codec == Codec.FLAC
                    ? Math.round(OggAudioMetadata.duration(audioPath).toSeconds() * encodedStream.getFormat().getSampleRate())
                    : resolveTotalFrames(audioPath, encodedStream.getFormat(), encodedStream.getFrameLength());

            try (AudioInputStream pcmStream = openPcmStream(encodedStream, pcmFormat)) {
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
     * Compressed providers can decode only to their native bit depth (notably 24-bit FLAC). Use the standard PCM
     * converter as a second streaming stage rather than rejecting those files or allocating a decoded recording.
     * The returned stream owns both stages so cancellation and ordinary closure release the decoder as before.
     */
    private AudioInputStream openPcmStream(AudioInputStream encodedStream, AudioFormat pcmFormat) throws IOException,
            UnsupportedAudioFileException {
        if (AudioSystem.isConversionSupported(pcmFormat, encodedStream.getFormat())) {
            return AudioSystem.getAudioInputStream(pcmFormat, encodedStream);
        }
        for (AudioFormat decodedFormat : AudioSystem.getTargetFormats(AudioFormat.Encoding.PCM_SIGNED, encodedStream.getFormat())) {
            if (!AudioSystem.isConversionSupported(pcmFormat, decodedFormat)) continue;
            AudioInputStream decodedStream = AudioSystem.getAudioInputStream(decodedFormat, encodedStream);
            try {
                return AudioSystem.getAudioInputStream(pcmFormat, decodedStream);
            } catch (RuntimeException exception) {
                decodedStream.close();
                throw exception;
            }
        }
        throw new UnsupportedAudioFileException("No streaming PCM conversion for " + encodedStream.getFormat());
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

    /**
     * Only PCM frame lengths describe sample counts. Compressed providers may expose byte or packet counts
     * (jFLAC returns the compressed file length), which would squeeze the waveform and report a false duration.
     * Prefer explicit duration metadata for compressed input; otherwise count decoded frames in the bounded path.
     * Vorbis uses the shared Ogg granule reader to keep its waveform timeline consistent with streaming playback
     * and avoid reopening a separate provider decoder just to obtain duration.
     */
    private long resolveTotalFrames(Path audioPath, AudioFormat sourceFormat, long streamFrameLength) {
        if (streamFrameLength > 0L && (sourceFormat.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)
                || sourceFormat.getEncoding().equals(AudioFormat.Encoding.PCM_UNSIGNED)
                || sourceFormat.getEncoding().equals(AudioFormat.Encoding.PCM_FLOAT))) {
            return streamFrameLength;
        }

        try {
            if (sourceFormat.getEncoding().equals(VorbisEncoding.VORBISENC)) {
                return Math.round(OggAudioMetadata.duration(audioPath).toSeconds() * sourceFormat.getSampleRate());
            }
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
