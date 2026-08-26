package app.ui;

import app.config.UiConfig;

import java.util.prefs.Preferences;

public class UiScalePreferences {
    private static final String SCALE_KEY = "interfaceScale";
    private Preferences preferences;

    public double load(UiConfig uiConfig) {
        try {
            return clamp(
                    preferences().getDouble(SCALE_KEY, uiConfig.getDefaultScale()),
                    uiConfig.getMinScale(),
                    uiConfig.getMaxScale()
            );
        } catch (SecurityException | IllegalStateException exception) {
            return clamp(uiConfig.getDefaultScale(), uiConfig.getMinScale(), uiConfig.getMaxScale());
        }
    }

    public void save(double scale, UiConfig uiConfig) {
        try {
            preferences().putDouble(SCALE_KEY, clamp(scale, uiConfig.getMinScale(), uiConfig.getMaxScale()));
        } catch (SecurityException | IllegalStateException exception) {
            // The application remains usable when the operating system denies access to user preferences.
        }
    }

    private Preferences preferences() {
        if (preferences == null) {
            preferences = Preferences.userNodeForPackage(UiScalePreferences.class);
        }
        return preferences;
    }

    static double clamp(double scale, double minScale, double maxScale) {
        if (!Double.isFinite(scale)) {
            return minScale;
        }
        return Math.max(minScale, Math.min(scale, maxScale));
    }
}
