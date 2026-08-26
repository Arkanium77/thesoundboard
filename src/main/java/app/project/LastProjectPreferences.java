package app.project;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class LastProjectPreferences {
    private static final String ROOT_PATH = "lastProjectRootPath";
    private static final String RESTORE_ON_START = "restoreLastProjectOnStart";
    private Preferences preferences;

    public Optional<Path> load() {
        try {
            String value = preferences().get(ROOT_PATH, "");
            if (value.isBlank()) return Optional.empty();
            Path path = Path.of(value);
            return Files.isDirectory(path) ? Optional.of(path) : Optional.empty();
        } catch (SecurityException | IllegalStateException | InvalidPathException exception) {
            return Optional.empty();
        }
    }

    public void save(Path rootPath) {
        try {
            preferences().put(ROOT_PATH, rootPath.toAbsolutePath().normalize().toString());
            preferences().flush();
        } catch (SecurityException | IllegalStateException | BackingStoreException exception) {
            // The project can still be opened when operating-system preferences are unavailable.
        }
    }

    public boolean isRestoreOnStart() {
        try {
            return preferences().getBoolean(RESTORE_ON_START, false);
        } catch (SecurityException | IllegalStateException exception) {
            return false;
        }
    }

    public void setRestoreOnStart(boolean restoreOnStart) {
        try {
            preferences().putBoolean(RESTORE_ON_START, restoreOnStart);
            preferences().flush();
        } catch (SecurityException | IllegalStateException | BackingStoreException exception) {
            // The default remains disabled when operating-system preferences are unavailable.
        }
    }

    private Preferences preferences() {
        if (preferences == null) preferences = Preferences.userNodeForPackage(LastProjectPreferences.class);
        return preferences;
    }
}
