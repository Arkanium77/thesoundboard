package app.project;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class LastProjectPreferences {
    private static final String ROOT_PATH = "lastProjectRootPath";
    private static final String RESTORE_ON_START = "restoreLastProjectOnStart";
    private Preferences preferences;

    public LastProjectPreferences() { }

    LastProjectPreferences(Preferences preferences) {
        this.preferences = preferences;
    }

    /** Indexed optional preferences preserve the old last-project key for existing installations. Only successful
     * opens enter this bounded list; duplicates move to the front and unavailable folders remain visible so a
     * temporarily disconnected library does not erase the user's history. */
    public List<Path> loadRecent() {
        List<Path> paths = new ArrayList<>();
        try {
            for (int index = 0; index < 10; index++) {
                String value = preferences().get("recentProject" + index, "");
                if (value.isBlank()) continue;
                try {
                    Path path = Path.of(value).toAbsolutePath().normalize();
                    if (!paths.contains(path)) paths.add(path);
                } catch (InvalidPathException exception) {
                    // Ignore one damaged entry without losing the remaining history.
                }
            }
            if (paths.isEmpty()) {
                String legacy = preferences().get(ROOT_PATH, "");
                if (!legacy.isBlank()) paths.add(Path.of(legacy).toAbsolutePath().normalize());
            }
        } catch (SecurityException | IllegalStateException | InvalidPathException exception) {
            // History is optional when preferences are unavailable.
        }
        return List.copyOf(paths);
    }

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
            Path path = rootPath.toAbsolutePath().normalize();
            List<Path> recent = new ArrayList<>(loadRecent());
            recent.remove(path);
            recent.addFirst(path);
            for (int index = 0; index < 10; index++) {
                if (index < recent.size()) preferences().put("recentProject" + index, recent.get(index).toString());
                else preferences().remove("recentProject" + index);
            }
            preferences().put(ROOT_PATH, path.toString());
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
