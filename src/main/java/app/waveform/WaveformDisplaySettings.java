package app.waveform;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

public final class WaveformDisplaySettings {
    private static final ObjectProperty<WaveformDisplayMode> MODE =
            new SimpleObjectProperty<>(WaveformDisplayMode.RMS_LINEAR);

    private WaveformDisplaySettings() {
    }

    public static ObjectProperty<WaveformDisplayMode> modeProperty() {
        return MODE;
    }

    public static WaveformDisplayMode getMode() {
        return MODE.get();
    }

    public static void setMode(WaveformDisplayMode mode) {
        MODE.set(mode == null ? WaveformDisplayMode.RMS_LINEAR : mode);
    }
}
