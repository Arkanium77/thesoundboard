package app.waveform;

import io.github.jaredmdobson.concentus.OpusException;
import io.github.jaredmdobson.concentus.OpusMSDecoder;
import javafx.util.Duration;
import org.gagravarr.ogg.OggPacket;
import org.gagravarr.ogg.OggPacketReader;
import org.gagravarr.opus.OpusInfo;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

/**
 * Analyses Opus independently of the playback mixer. The released miniaudio binding only exposes whole-file
 * PCM decoding; a portable Java decoder instead feeds the existing bounded envelope accumulator one packet at
 * a time. No audio is retained or written. Opus granules always use 48 kHz, regardless of the original sample
 * rate in its header. Pre-skip and the final page granule remove codec padding so waveform seeks match playback.
 * Chained streams reset the decoder but share the envelope. A per-packet input budget rejects oversized metadata
 * or unterminated packets before the Ogg reader can accumulate arbitrary memory; cancellation also reaches reads.
 */
final class OpusWaveformExtractor implements WaveformExtractor {
    private static final int SAMPLE_RATE = 48_000;
    private static final int MAX_PACKET_FRAMES = 5_760;
    private final int resolution;

    OpusWaveformExtractor(int resolution) {
        this.resolution = resolution;
    }

    @Override
    public WaveformData extract(Path audioPath) throws IOException {
        WaveformAccumulator accumulator = new WaveformAccumulator(resolution, 1024);
        long totalFrames = 0L;
        try (OggPacketInputStream stream = new OggPacketInputStream(new BufferedInputStream(Files.newInputStream(audioPath)))) {
            OggPacketReader reader = new OggPacketReader(stream);
            OpusMSDecoder decoder = null;
            int streamId = 0;
            int channels = 0;
            int preSkip = 0;
            long decodedFrames = 0L;
            short[] pcm = null;
            boolean needsTags = false;
            boolean ended = true;
            OggPacket packet;
            while (true) {
                stream.resetBudget();
                packet = reader.getNextPacket();
                if (packet == null) break;
                byte[] data = packet.getData();
                if (packet.isBeginningOfStream() && startsWith(data, "OpusHead")) {
                    if (!ended) throw new IOException("Overlapping Opus streams are unsupported");
                    OpusInfo info = new OpusInfo(packet);
                    channels = info.getNumChannels();
                    if (channels < 1 || channels > 8) throw new IOException("Unsupported Opus channel count: " + channels);
                    int family = Byte.toUnsignedInt(info.getChannelMappingFamily());
                    if (family != 0 && family != 1) throw new IOException("Unsupported Opus channel mapping: " + family);
                    if (family == 0 && channels > 2) throw new IOException("Invalid unmapped Opus channel count");
                    short[] mapping = new short[channels];
                    for (int index = 0; index < channels; index++) {
                        mapping[index] = (short) (family == 0 ? index : Byte.toUnsignedInt(info.getChannelMapping()[index]));
                    }
                    decoder = OpusMSDecoder.create(SAMPLE_RATE, channels,
                            family == 0 ? 1 : Byte.toUnsignedInt(info.getStreamCount()),
                            family == 0 ? channels - 1 : Byte.toUnsignedInt(info.getTwoChannelStreamCount()), mapping);
                    decoder.setGain((short) info.getOutputGain());
                    pcm = new short[MAX_PACKET_FRAMES * channels];
                    streamId = packet.getSid();
                    preSkip = info.getPreSkip();
                    decodedFrames = 0L;
                    needsTags = true;
                    ended = false;
                    continue;
                }
                if (decoder == null || packet.getSid() != streamId) continue;
                if (needsTags) {
                    if (!startsWith(data, "OpusTags")) throw new IOException("Missing OpusTags packet");
                    needsTags = false;
                    continue;
                }
                if (ended) throw new IOException("Audio packet after end of Opus stream");
                int frames = decoder.decodeMultistream(data, 0, data.length, pcm, 0, MAX_PACKET_FRAMES, 0);
                if (frames < 0) throw new IOException("Invalid Opus packet: " + frames);
                long end = decodedFrames + frames;
                if (packet.isEndOfStream()) {
                    long granule = packet.getGranulePosition();
                    if (granule < preSkip || granule > end) throw new IOException("Invalid final Opus granule");
                    end = Math.min(end, granule);
                    ended = true;
                }
                for (int frame = (int) Math.max(0L, preSkip - decodedFrames); frame < end - decodedFrames; frame++) {
                    double amplitude = 0d;
                    for (int channel = 0; channel < channels; channel++) {
                        amplitude = Math.max(amplitude, Math.abs(pcm[frame * channels + channel] / 32768d));
                    }
                    accumulator.add(amplitude);
                    totalFrames++;
                }
                decodedFrames += frames;
            }
            if (decoder == null || !ended || needsTags) throw new IOException("Incomplete Opus stream");
            return accumulator.finish(Duration.seconds(totalFrames / (double) SAMPLE_RATE));
        } catch (OpusException | RuntimeException exception) {
            throw new IOException("Cannot analyse Opus file: " + audioPath, exception);
        }
    }

    private boolean startsWith(byte[] data, String signature) {
        return data.length >= signature.length()
                && new String(data, 0, signature.length(), StandardCharsets.US_ASCII).equals(signature);
    }

}
