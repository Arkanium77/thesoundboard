package app.skin;

import app.ui.UiFonts;
import app.ui.UiIcons;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class SkinService {
    private final SkinRepository repository;
    private final SkinPreferences preferences;
    private final List<SkinBinding> bindings = new ArrayList<>();
    private final Set<UUID> loadedSkinUids = new LinkedHashSet<>();
    private SkinDescriptor activeSkin;

    public SkinService() {
        this(new SkinRepository(), new SkinPreferences());
    }

    SkinService(SkinRepository repository, SkinPreferences preferences) {
        this.repository = repository;
        this.preferences = preferences;
        this.activeSkin = repository.findAll().stream()
                .filter(descriptor -> descriptor.manifest().getUid().equals(SkinBootstrap.getStartupSkinUid()))
                .findFirst()
                .orElseGet(() -> repository.findSelected(SkinRepository.DEFAULT_SKIN_UID));
    }

    public void prepareUiResources() {
        loadedSkinUids.add(activeSkin.manifest().getUid());
        UiFonts.configure(activeSkin);
        UiIcons.configure(activeSkin);
    }

    public void apply(Scene scene, Parent root) {
        if (bindings.stream().noneMatch(binding -> binding.scene() == scene)) {
            bindings.add(new SkinBinding(scene, root));
        }
        applySkin(scene, root);
    }

    public void apply(Parent root) {
        UiFonts.applyApplicationFont(root);
        URL stylesheet = activeSkin.resolveResource(activeSkin.manifest().getStylesheet());
        if (stylesheet != null) {
            root.getStylesheets().setAll(stylesheet.toExternalForm());
        }
        if (!root.getStyleClass().contains("soundboard-root")) {
            root.getStyleClass().add("soundboard-root");
        }
    }

    private void applySkin(Scene scene, Parent root) {
        UiFonts.applyApplicationFont(root);
        URL stylesheet = activeSkin.resolveResource(activeSkin.manifest().getStylesheet());
        if (stylesheet != null) {
            scene.getStylesheets().setAll(stylesheet.toExternalForm());
        }
        if (!root.getStyleClass().contains("soundboard-root")) {
            root.getStyleClass().add("soundboard-root");
        }
    }

    public List<SkinDescriptor> getAvailableSkins() {
        return repository.findAll();
    }

    public SkinDescriptor getActiveSkin() {
        return activeSkin;
    }

    public boolean selectSkin(UUID skinUid) {
        SkinDescriptor selectedSkin = repository.findAll().stream()
                .filter(descriptor -> descriptor.manifest().getUid().equals(skinUid))
                .findFirst()
                .orElse(null);
        if (selectedSkin == null) {
            return false;
        }
        preferences.saveSelectedSkinUid(selectedSkin.manifest().getUid());
        if (selectedSkin.manifest().getUid().equals(activeSkin.manifest().getUid())) {
            return false;
        }
        activeSkin = selectedSkin;
        applyActiveSkin();
        return true;
    }

    public List<String> getRestartRequiredChanges(UUID skinUid) {
        SkinRestartSettings currentSettings = new SkinRestartSettings(SkinBootstrap.getStartupRenderingMode());
        return repository.findAll().stream()
                .filter(descriptor -> descriptor.manifest().getUid().equals(skinUid))
                .map(SkinDescriptor::manifest)
                .map(SkinRestartSettings::from)
                .findFirst()
                .map(settings -> settings.differencesFrom(currentSettings))
                .orElseGet(List::of);
    }

    public SkinRepository getRepository() {
        return repository;
    }

    public UUID getSelectedSkinUid() {
        return preferences.loadSelectedSkinUid();
    }

    public void refreshSelectedSkin() {
        activeSkin = repository.findSelected(preferences.loadSelectedSkinUid());
        applyActiveSkin();
    }

    public boolean wasLoadedThisSession(UUID skinUid) {
        return loadedSkinUids.contains(skinUid);
    }

    private void applyActiveSkin() {
        loadedSkinUids.add(activeSkin.manifest().getUid());
        UiFonts.configure(activeSkin);
        UiIcons.configure(activeSkin);
        bindings.forEach(binding -> applySkin(binding.scene(), binding.root()));
    }

    private record SkinBinding(Scene scene, Parent root) {
    }
}
