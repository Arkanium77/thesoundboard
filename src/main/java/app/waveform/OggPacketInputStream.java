package app.waveform;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;

/**
 * The Ogg reader assembles continued packets internally. Bound bytes consumed by a single next-packet call
 * (including page overhead) to 1 MiB; normal audio packets are much smaller. Large comment blocks are rejected
 * rather than trading the waveform worker's fixed memory budget for unbounded tag storage.
 */
final class OggPacketInputStream extends FilterInputStream {
    private int remaining;

    OggPacketInputStream(InputStream stream) {
        super(stream);
    }

    void resetBudget() throws InterruptedIOException {
        checkInterrupted();
        remaining = 1_048_576;
    }

    @Override
    public int read() throws IOException {
        checkInterrupted();
        if (remaining-- <= 0) throw new IOException("Ogg packet exceeds waveform input budget");
        return in.read();
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        checkInterrupted();
        if (length == 0) return 0;
        if (remaining <= 0) throw new IOException("Ogg packet exceeds waveform input budget");
        int read = in.read(bytes, offset, Math.min(length, remaining));
        if (read > 0) remaining -= read;
        return read;
    }

    private void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Waveform extraction cancelled");
    }
}
