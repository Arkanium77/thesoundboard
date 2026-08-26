package app.skin;

import java.util.prefs.Preferences;
import java.util.prefs.BackingStoreException;
import java.util.UUID;

public class SkinPreferences {
    private static final String SELECTED_SKIN_KEY = "selectedSkinUid";
    private Preferences preferences;

    public UUID loadSelectedSkinUid() {
        try {
            String value = preferences().get(SELECTED_SKIN_KEY, SkinRepository.DEFAULT_SKIN_UID.toString());
            return UUID.fromString(value);
        } catch (SecurityException | IllegalStateException | IllegalArgumentException exception) {
            return SkinRepository.DEFAULT_SKIN_UID;
        }
    }

    public void saveSelectedSkinUid(UUID skinUid) {
        try {
            preferences().put(SELECTED_SKIN_KEY, skinUid.toString());
            preferences().flush();
        } catch (SecurityException | IllegalStateException | BackingStoreException exception) {
            // The default skin remains usable when preferences are unavailable.
        }
    }

    private Preferences preferences() {
        if (preferences == null) {
            preferences = Preferences.userNodeForPackage(SkinPreferences.class);
        }
        return preferences;
    }
}
