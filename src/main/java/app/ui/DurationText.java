package app.ui;

import javafx.scene.control.Label;
import javafx.util.Duration;

public final class DurationText {
    private static final Object SECONDS_KEY = new Object();
    private DurationText() { }

    /** Only whole seconds affect the label; avoid Formatter allocation and CSS/layout work between ticks. */
    public static void update(Label label, Duration duration) {
        long seconds = seconds(duration);
        if (Long.valueOf(seconds).equals(label.getProperties().get(SECONDS_KEY))) return;
        label.getProperties().put(SECONDS_KEY, seconds);
        label.setText(format(duration));
    }

    public static String format(Duration duration) {
        long seconds = seconds(duration);
        long minutes = seconds / 60;
        return (minutes < 10 ? "0" : "") + minutes + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }

    private static long seconds(Duration duration) {
        return duration == null || duration.isUnknown() || duration.isIndefinite() ? 0L
                : (long) Math.max(duration.toSeconds(), 0d);
    }
}
