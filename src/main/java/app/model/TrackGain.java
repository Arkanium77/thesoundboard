package app.model;

/**
 * Track trim is stored separately from normalized faders, so changing master/queue volume never overwrites it.
 * Amplitude uses 20 log10, not the power conversion. Missing persisted gain is zero (unity); invalid imported
 * values recover to zero and finite values stay within the UI range. Applying trim last preserves mute at zero.
 */
public final class TrackGain {
    public static final double MIN_DB = -30d;
    public static final double MAX_DB = 30d;

    public static final double MAX_MULTIPLIER = Math.pow(10d, MAX_DB / 20d);

    private TrackGain() { }

    public static double normalize(double gainDb) {
        return Double.isFinite(gainDb) ? Math.max(MIN_DB, Math.min(MAX_DB, gainDb)) : 0d;
    }

    public static double multiplier(double gainDb) {
        return Math.pow(10d, normalize(gainDb) / 20d);
    }
}
