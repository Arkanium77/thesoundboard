package app.localization;

import app.packages.PackageSourceDirectories;
import app.packages.PackageSourceDirectoryRegistry;

import java.io.IOException;
import java.text.MessageFormat;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class LocalizationService {
    private final LocalizationRepository repository;
    private final LocalizationPreferences preferences;
    private final PackageSourceDirectoryRegistry packageSourceDirectories;
    private LocalizationDescriptor active;

    public LocalizationService() {
        this(
                new LocalizationRepository(),
                new LocalizationPreferences(),
                PackageSourceDirectories.discover().localizationDirectories()
        );
    }

    LocalizationService(LocalizationRepository repository, LocalizationPreferences preferences) {
        this(repository, preferences, new PackageSourceDirectoryRegistry(
                repository.getDirectory().resolve("_source-directories"),
                List.of()
        ));
    }

    LocalizationService(LocalizationRepository repository, LocalizationPreferences preferences,
                        List<Path> packageSourceDirectories) {
        this(repository, preferences, new PackageSourceDirectoryRegistry(
                repository.getDirectory().resolve("_source-directories"),
                packageSourceDirectories
        ));
    }

    LocalizationService(LocalizationRepository repository, LocalizationPreferences preferences,
                        PackageSourceDirectoryRegistry packageSourceDirectories) {
        this.repository = repository;
        this.preferences = preferences;
        this.packageSourceDirectories = packageSourceDirectories;
        refreshPackageSources();
        this.active = repository.findSelected(preferences.load(), preferences.loadVersion());
    }

    public String text(TextKey key) {
        return active.strings().getOrDefault(key.id(), key.english());
    }

    public String format(TextKey key, Object... arguments) {
        return MessageFormat.format(text(key), arguments);
    }

    public List<LocalizationDescriptor> getAvailable() { return repository.findAll(); }
    public void refreshPackageSources() {
        new LocalizationPackageInstaller(repository).installAvailablePackages(packageSourceDirectories.getDirectories());
        repository.invalidate();
    }
    public List<Path> getPackageSourceDirectories() { return packageSourceDirectories.getDirectories(); }
    public List<Path> getBundledPackageSourceDirectories() { return packageSourceDirectories.getBundledDirectories(); }
    public void addPackageSourceDirectory(Path directory) throws IOException {
        packageSourceDirectories.add(directory);
        new LocalizationPackageInstaller(repository).installAvailablePackages(directory);
    }
    public void removePackageSourceDirectory(Path directory) throws IOException { packageSourceDirectories.remove(directory); }
    public UUID getSelectedUid() { return preferences.load(); }
    public int getSelectedVersion() { return preferences.loadVersion(); }
    public LocalizationDescriptor getActive() { return active; }
    public LocalizationRepository getRepository() { return repository; }
    public boolean select(UUID uid) {
        return select(uid, 1);
    }

    public boolean select(UUID uid, int version) {
        LocalizationDescriptor selected = repository.findSelected(uid, version);
        if (!selected.manifest().getUid().equals(uid) || selected.manifest().getVersion() != version) return false;
        boolean changed = !active.manifest().getUid().equals(uid) || active.manifest().getVersion() != version;
        preferences.save(uid, version);
        active = selected;
        return changed;
    }

    public void reloadSelected() {
        active = repository.findSelected(preferences.load(), preferences.loadVersion());
    }
}
