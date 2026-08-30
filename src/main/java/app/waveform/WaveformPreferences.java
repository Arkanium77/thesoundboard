package app.waveform;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class WaveformPreferences {
    private static final String ADAPTIVE_AMPLITUDE = "adaptiveWaveformAmplitude";
    private static final String DISPLAY_MODE = "waveformDisplayMode";
    private Preferences preferences;

    /**
     * Loads the extensible display mode while migrating the former adaptive checkbox. The legacy false/default value
     * maps to RMS-linear and true maps to RMS-dB, preserving the exact behavior selected before the mode list existed.
     * Persisted enum names are part of the compatibility contract and invalid future values recover to RMS-linear.
     */
    public WaveformDisplayMode loadMode() {
        try {
            String value = preferences().get(DISPLAY_MODE, "");
            if (!value.isBlank()) return WaveformDisplayMode.valueOf(value);
            return preferences().getBoolean(ADAPTIVE_AMPLITUDE, false)
                    ? WaveformDisplayMode.RMS_DB
                    : WaveformDisplayMode.RMS_LINEAR;
        } catch (SecurityException | IllegalStateException | IllegalArgumentException exception) {
            return WaveformDisplayMode.RMS_LINEAR;
        }
    }

    public void saveMode(WaveformDisplayMode mode) {
        try {
            preferences().put(DISPLAY_MODE, (mode == null ? WaveformDisplayMode.RMS_LINEAR : mode).name());
            preferences().flush();
        } catch (SecurityException | IllegalStateException | BackingStoreException exception) {
            // RMS-linear remains active when operating-system preferences are unavailable.
        }
    }

    private Preferences preferences() {
        if (preferences == null) preferences = Preferences.userNodeForPackage(WaveformPreferences.class);
        return preferences;
    }
}
