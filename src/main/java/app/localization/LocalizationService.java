package app.localization;

import java.text.MessageFormat;

import java.util.List;
import java.util.UUID;

public class LocalizationService {
    private final LocalizationRepository repository;
    private final LocalizationPreferences preferences;
    private LocalizationDescriptor active;

    public LocalizationService() {
        this(new LocalizationRepository(), new LocalizationPreferences());
    }

    LocalizationService(LocalizationRepository repository, LocalizationPreferences preferences) {
        this.repository = repository;
        this.preferences = preferences;
        this.active = repository.findSelected(preferences.load());
    }

    public String text(TextKey key) {
        return active.strings().getOrDefault(key.id(), key.english());
    }

    public String format(TextKey key, Object... arguments) {
        return MessageFormat.format(text(key), arguments);
    }

    public List<LocalizationDescriptor> getAvailable() { return repository.findAll(); }
    public UUID getSelectedUid() { return preferences.load(); }
    public LocalizationDescriptor getActive() { return active; }
    public LocalizationRepository getRepository() { return repository; }
    public boolean select(UUID uid) {
        LocalizationDescriptor selected = repository.findSelected(uid);
        if (!selected.manifest().getUid().equals(uid)) return false;
        boolean changed = !active.manifest().getUid().equals(uid);
        preferences.save(uid);
        active = selected;
        return changed;
    }

    public void reloadSelected() {
        active = repository.findSelected(preferences.load());
    }
}
