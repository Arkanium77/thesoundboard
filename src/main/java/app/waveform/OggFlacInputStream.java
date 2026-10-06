package app.waveform;

import org.gagravarr.ogg.OggPacket;
import org.gagravarr.ogg.OggPacketReader;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Presents Ogg FLAC packets as the original FLAC bitstream to the existing streaming Java Sound decoder.
 * jFLAC's file reader accepts native FLAC, while the playback decoder also accepts its Ogg mapping; rejecting
 * the latter would leave playable tracks without a waveform. Removing the nine-byte mapping prefix and page
 * framing is container demultiplexing, not audio conversion. Keep only one bounded packet, preserve metadata
 * and encoded frames verbatim, and propagate cancellation into the shared Ogg reader.
 */
final class OggFlacInputStream extends InputStream {
    private final OggPacketInputStream input;
    private final OggPacketReader reader;
    private final int serial;
    private byte[] packet;
    private int position;
    private boolean ended;

    static InputStream open(Path path) throws IOException {
        OggPacketInputStream input = new OggPacketInputStream(new BufferedInputStream(Files.newInputStream(path)));
        try {
            return new OggFlacInputStream(input);
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    private OggFlacInputStream(OggPacketInputStream input) throws IOException {
        this.input = input;
        reader = new OggPacketReader(input);
        input.resetBudget();
        OggPacket first = reader.getNextPacket();
        byte[] header = first == null ? new byte[0] : first.getData();
        if (header.length < 51 || !first.isBeginningOfStream() || header[0] != 0x7f
                || !new String(header, 1, 4, StandardCharsets.US_ASCII).equals("FLAC")
                || header[5] != 1 || header[6] != 0) throw new IOException("Invalid Ogg FLAC mapping header");
        serial = first.getSid();
        packet = Arrays.copyOfRange(header, 9, header.length);
        ended = first.isEndOfStream();
    }

    @Override
    public int read() throws IOException {
        if (!availablePacket()) return -1;
        return Byte.toUnsignedInt(packet[position++]);
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, bytes.length);
        if (length == 0) return 0;
        if (!availablePacket()) return -1;
        int count = Math.min(length, packet.length - position);
        System.arraycopy(packet, position, bytes, offset, count);
        position += count;
        return count;
    }

    private boolean availablePacket() throws IOException {
        while (position == packet.length) {
            if (ended) return false;
            input.resetBudget();
            OggPacket next = reader.getNextPacket();
            if (next == null) throw new IOException("Incomplete Ogg FLAC stream");
            if (next.getSid() != serial) continue;
            packet = next.getData();
            position = 0;
            ended = next.isEndOfStream();
        }
        return true;
    }

    @Override
    public void close() throws IOException {
        input.close();
    }
}
