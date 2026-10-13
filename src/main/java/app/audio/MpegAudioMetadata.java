package app.audio;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

/**
 * Rejects encrypted/unknown MP4 audio and malformed ADTS during background scans without starting native decoders.
 * MP4 media payloads and index tables are skipped by their atom lengths, including files with moov after mdat.
 * The first audio track is checked because it is the player's default stream; a playable secondary stream must not
 * disguise an unsupported default. A bounded atom budget prevents pathological metadata from delaying large libraries.
 * This is identification, not a full integrity check: corrupt media payloads still report a playback error.
 */
public final class MpegAudioMetadata {
    private static final Set<String> CODECS = Set.of("mp4a", "alac", "fLaC", "Opus", ".mp3");

    private MpegAudioMetadata() { }

    public static boolean supportsM4a(Path path) {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            Reader reader = new Reader(file);
            Atom movie = reader.find(0, file.length(), "moov");
            if (movie == null) return false;
            long position = movie.body();
            while (position < movie.end()) {
                Atom track = reader.atom(position, movie.end());
                position = track.end();
                if (!track.type().equals("trak")) continue;
                Atom media = reader.find(track.body(), track.end(), "mdia");
                if (media == null) continue;
                Atom handler = reader.find(media.body(), media.end(), "hdlr");
                if (handler == null || handler.end() - handler.body() < 12) continue;
                file.seek(handler.body() + 8);
                if (!reader.type().equals("soun")) continue;
                Atom information = reader.find(media.body(), media.end(), "minf");
                if (information == null) return false;
                Atom table = reader.find(information.body(), information.end(), "stbl");
                if (table == null) return false;
                Atom descriptions = reader.find(table.body(), table.end(), "stsd");
                if (descriptions == null || descriptions.end() - descriptions.body() < 8) return false;
                file.seek(descriptions.body() + 4);
                long count = Integer.toUnsignedLong(file.readInt());
                if (count == 0 || count > 32) return false;
                long entry = descriptions.body() + 8;
                for (long index = 0; index < count; index++) {
                    Atom format = reader.atom(entry, descriptions.end());
                    if (!CODECS.contains(format.type()) || format.end() - format.body() < 28) return false;
                    entry = format.end();
                }
                return true;
            }
            return false;
        } catch (IOException exception) {
            return false;
        }
    }

    public static boolean supportsAdts(Path path) {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            byte[] header = new byte[10];
            file.readFully(header, 0, 3);
            long start = 0;
            if (new String(header, 0, 3, StandardCharsets.US_ASCII).equals("ID3")) {
                file.readFully(header, 3, 7);
                long size = 0;
                for (int index = 6; index < 10; index++) {
                    if (header[index] < 0) return false;
                    size = size << 7 | header[index];
                }
                start = size + 10 + ((header[5] & 0x10) != 0 ? 10 : 0);
            }
            file.seek(start);
            file.readFully(header, 0, 7);
            int frameSize = (header[3] & 3) << 11 | Byte.toUnsignedInt(header[4]) << 3
                    | Byte.toUnsignedInt(header[5]) >>> 5;
            int minimum = (header[1] & 1) == 0 ? 9 : 7;
            return Byte.toUnsignedInt(header[0]) == 0xff && (header[1] & 0xf6) == 0xf0
                    && (Byte.toUnsignedInt(header[2]) >>> 2 & 15) < 13
                    && frameSize >= minimum && frameSize <= file.length() - start;
        } catch (IOException exception) {
            return false;
        }
    }

    private record Atom(String type, long body, long end) { }

    private static final class Reader {
        private final RandomAccessFile file;
        private int remaining = 4096;

        private Reader(RandomAccessFile file) { this.file = file; }

        private String type() throws IOException {
            byte[] value = new byte[4];
            file.readFully(value);
            return new String(value, StandardCharsets.US_ASCII);
        }

        private Atom atom(long start, long end) throws IOException {
            if (--remaining < 0 || end - start < 8 || Thread.currentThread().isInterrupted()) {
                throw new IOException("Invalid or cancelled MP4 metadata");
            }
            file.seek(start);
            long length = Integer.toUnsignedLong(file.readInt());
            String type = type();
            int header = 8;
            if (length == 1) {
                if (end - start < 16) throw new IOException("Truncated MP4 atom");
                length = file.readLong();
                header = 16;
            } else if (length == 0) {
                length = end - start;
            }
            if (length < header || length > end - start) throw new IOException("Invalid MP4 atom length");
            return new Atom(type, start + header, start + length);
        }

        private Atom find(long start, long end, String type) throws IOException {
            while (start < end) {
                Atom atom = atom(start, end);
                if (atom.type().equals(type)) return atom;
                start = atom.end();
            }
            return null;
        }
    }
}
