package app.localization;

import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class LocalizationPreferences {
    private static final String SELECTED_UID = "selectedLocalizationUid";
    private Preferences preferences;

    public UUID load() {
        try {
            return UUID.fromString(preferences().get(SELECTED_UID, LocalizationRepository.ENGLISH_UID.toString()));
        } catch (SecurityException | IllegalStateException | IllegalArgumentException exception) {
            return LocalizationRepository.ENGLISH_UID;
        }
    }

    public void save(UUID uid) {
        try {
            preferences().put(SELECTED_UID, uid.toString());
            preferences().flush();
        } catch (SecurityException | IllegalStateException | BackingStoreException exception) {
            // Built-in English remains available when preferences cannot be written.
        }
    }

    private Preferences preferences() {
        if (preferences == null) {
            preferences = Preferences.userNodeForPackage(LocalizationPreferences.class);
        }
        return preferences;
    }
}
