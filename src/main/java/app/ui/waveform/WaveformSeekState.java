package app.ui.waveform;

import java.util.function.LongSupplier;

/** Keeps a requested seek visible while native media callbacks still report the old position. Drag preview wins
 * over the pending request, which wins over player time until it settles within 750 ms or times out after 1.5 s.
 * The clock is supplied so delayed callbacks and consecutive seeks can be verified without a toolkit or sleeps.
 * Pixel-based change detection is retained to avoid repainting static canvases on every playback poll. */
final class WaveformSeekState {
    private static final double SETTLE_THRESHOLD_MILLIS = 750d;
    private static final long SETTLE_TIMEOUT_NANOS = 1_500_000_000L;
    private final LongSupplier clock;
    private double totalMillis = 1d;
    private double playerTotalMillis;
    private double waveformTotalMillis;
    private double currentMillis;
    private boolean dragging;
    private double dragMillis;
    private Double pendingSeekMillis;
    private long pendingSeekDeadline;

    WaveformSeekState(LongSupplier clock) { this.clock = clock; }

    void setWaveformDuration(double millis) {
        waveformTotalMillis = Math.max(millis, 0d);
        totalMillis = Math.max(Math.max(playerTotalMillis, waveformTotalMillis), 1d);
    }

    boolean updatePlayback(double current, double total, double width) {
        playerTotalMillis = Math.max(total, 0d);
        double nextTotal = Math.max(Math.max(playerTotalMillis, waveformTotalMillis), 1d);
        double nextCurrent = Math.max(current, 0d);
        boolean changed = Double.compare(totalMillis, nextTotal) != 0
                || (long) (currentMillis / totalMillis * width) != (long) (nextCurrent / nextTotal * width);
        totalMillis = nextTotal;
        currentMillis = nextCurrent;
        if (pendingSeekMillis != null && (Math.abs(currentMillis - pendingSeekMillis) <= SETTLE_THRESHOLD_MILLIS
                || clock.getAsLong() >= pendingSeekDeadline)) {
            pendingSeekMillis = null;
            changed = true;
        }
        return changed;
    }

    boolean canSeek() { return playerTotalMillis > 0d || waveformTotalMillis > 0d; }
    boolean isDragging() { return dragging; }
    double totalMillis() { return totalMillis; }

    void beginDrag(double x, double width) { dragging = true; dragTo(x, width); }
    void dragTo(double x, double width) { dragMillis = toMillis(x, width); }

    double release(double x, double width) {
        dragTo(x, width);
        dragging = false;
        pendingSeekMillis = dragMillis;
        pendingSeekDeadline = clock.getAsLong() + SETTLE_TIMEOUT_NANOS;
        return dragMillis;
    }

    double displayedMillis() {
        if (dragging) return dragMillis;
        return pendingSeekMillis == null ? currentMillis : pendingSeekMillis;
    }

    private double toMillis(double x, double width) {
        double safeWidth = Math.max(width, 1d);
        return Math.max(0d, Math.min(safeWidth, x)) / safeWidth * totalMillis;
    }
}
