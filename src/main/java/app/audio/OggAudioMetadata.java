package app.audio;

import javafx.util.Duration;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads container metadata without decoding PCM or creating an audio device. Streaming Ogg audio can play even
 * when the native length API returns MA_NOT_IMPLEMENTED; treating that metadata limitation as a playback error
 * would reject valid sounds. Ogg granules provide duration independently of that API. Only page headers and a
 * bounded identification prefix are read; packet payloads are skipped and no converted recording is retained.
 * Opus granules use 48 kHz and exclude pre-skip; Vorbis and FLAC use their header sample rate. Unknown Ogg codecs
 * must be rejected during the library scan, because the extension alone does not imply a playable audio stream.
 */
public final class OggAudioMetadata {
    private OggAudioMetadata() { }

    public static boolean isSupported(Path path) {
        return codec(path) != Codec.UNSUPPORTED;
    }

    public static Codec codec(Path path) {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            Page page = readPage(file);
            StreamInfo info = page != null && page.beginning() ? identify(page.prefix()) : null;
            return info == null ? Codec.UNSUPPORTED : info.codec();
        } catch (IOException exception) {
            return Codec.UNSUPPORTED;
        }
    }

    public static Duration duration(Path path) throws IOException {
        Map<Integer, StreamInfo> streams = new LinkedHashMap<>();
        double seconds = 0d;
        boolean found = false;
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            Page page;
            while ((page = readPage(file)) != null) {
                if (page.beginning()) {
                    StreamInfo info = identify(page.prefix());
                    if (info != null) {
                        if (!streams.isEmpty()) throw new IOException("Overlapping Ogg audio streams");
                        streams.put(page.serial(), info);
                        found = true;
                    }
                }
                if (page.ending() && streams.containsKey(page.serial())) {
                    StreamInfo info = streams.remove(page.serial());
                    long frames = page.granule() - info.preSkip();
                    if (frames < 0L) throw new IOException("Invalid Ogg duration");
                    seconds += frames / (double) info.sampleRate();
                }
            }
        }
        if (!found || !streams.isEmpty()) throw new IOException("Incomplete Ogg audio stream");
        return Duration.seconds(seconds);
    }

    private static StreamInfo identify(byte[] prefix) {
        ByteBuffer data = ByteBuffer.wrap(prefix).order(ByteOrder.LITTLE_ENDIAN);
        if (prefix.length >= 30 && prefix[0] == 1 && signature(prefix, 1, 6).equals("vorbis")) {
            int sampleRate = data.getInt(12);
            return sampleRate > 0 && prefix[11] != 0 ? new StreamInfo(sampleRate, 0, Codec.VORBIS) : null;
        }
        if (prefix.length >= 19 && signature(prefix, 0, 8).equals("OpusHead")) {
            int channels = Byte.toUnsignedInt(prefix[9]);
            int mapping = Byte.toUnsignedInt(prefix[18]);
            if (channels < 1 || channels > 8 || mapping > 1 || mapping == 0 && channels > 2) return null;
            return new StreamInfo(48_000, Short.toUnsignedInt(data.getShort(10)), Codec.OPUS);
        }
        if (prefix.length >= 51 && prefix[0] == 0x7f && signature(prefix, 1, 4).equals("FLAC")
                && prefix[5] == 1 && prefix[6] == 0 && signature(prefix, 9, 4).equals("fLaC")) {
            long format = ByteBuffer.wrap(prefix).order(ByteOrder.BIG_ENDIAN).getLong(27);
            int sampleRate = (int) (format >>> 44);
            return sampleRate > 0 ? new StreamInfo(sampleRate, 0, Codec.FLAC) : null;
        }
        return null;
    }

    private static String signature(byte[] data, int offset, int length) {
        return new String(data, offset, length, StandardCharsets.US_ASCII);
    }

    private static Page readPage(RandomAccessFile file) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Ogg metadata cancelled");
        if (file.getFilePointer() == file.length()) return null;
        byte[] header = new byte[27];
        file.readFully(header);
        if (!signature(header, 0, 4).equals("OggS") || header[4] != 0) throw new IOException("Invalid Ogg page");
        byte[] segments = new byte[Byte.toUnsignedInt(header[26])];
        file.readFully(segments);
        int payloadLength = 0;
        for (byte segment : segments) payloadLength += Byte.toUnsignedInt(segment);
        long payloadStart = file.getFilePointer();
        long nextPage = payloadStart + payloadLength;
        if (nextPage > file.length()) throw new IOException("Truncated Ogg page");
        int flags = Byte.toUnsignedInt(header[5]);
        byte[] prefix = new byte[(flags & 2) != 0 ? Math.min(payloadLength, 64) : 0];
        file.readFully(prefix);
        file.seek(nextPage);
        ByteBuffer data = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        return new Page(data.getInt(14), data.getLong(6), (flags & 2) != 0, (flags & 4) != 0, prefix);
    }

    public enum Codec { VORBIS, OPUS, FLAC, UNSUPPORTED }

    private record StreamInfo(int sampleRate, int preSkip, Codec codec) { }
    private record Page(int serial, long granule, boolean beginning, boolean ending, byte[] prefix) { }
}
