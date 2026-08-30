package app.skin;

import app.packages.PackageSourceDirectories;
import app.packages.PackageSourceDirectoryRegistry;
import app.ui.UiFonts;
import app.ui.UiIcons;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class SkinService {
    private static final String CORE_STYLESHEET = "/skins/core.css";
    private final SkinRepository repository;
    private final SkinPreferences preferences;
    private final PackageSourceDirectoryRegistry packageSourceDirectories;
    private final List<SkinBinding> bindings = new ArrayList<>();
    private final Set<String> loadedSkins = new LinkedHashSet<>();
    private SkinDescriptor activeSkin;

    public SkinService() {
        this(new SkinRepository(), new SkinPreferences(), PackageSourceDirectories.discover().skinDirectories());
    }

    SkinService(SkinRepository repository, SkinPreferences preferences) {
        this(repository, preferences, new PackageSourceDirectoryRegistry(
                repository.getExternalSkinsDirectory().resolve("_source-directories"),
                List.of()
        ));
    }

    SkinService(SkinRepository repository, SkinPreferences preferences, List<Path> packageSourceDirectories) {
        this(repository, preferences, new PackageSourceDirectoryRegistry(
                repository.getExternalSkinsDirectory().resolve("_source-directories"),
                packageSourceDirectories
        ));
    }

    SkinService(SkinRepository repository, SkinPreferences preferences,
                PackageSourceDirectoryRegistry packageSourceDirectories) {
        this.repository = repository;
        this.preferences = preferences;
        this.packageSourceDirectories = packageSourceDirectories;
        refreshPackageSources();
        this.activeSkin = repository.findAll().stream()
                .filter(descriptor -> descriptor.manifest().getUid().equals(SkinBootstrap.getStartupSkinUid()))
                .filter(descriptor -> descriptor.manifest().getVersion() == SkinBootstrap.getStartupSkinVersion())
                .findFirst()
                .orElseGet(() -> repository.findSelected(SkinRepository.DEFAULT_SKIN_UID));
    }

    public void prepareUiResources() {
        loadedSkins.add(identity(activeSkin));
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
        URL coreStylesheet = SkinService.class.getResource(CORE_STYLESHEET);
        URL stylesheet = activeSkin.resolveResource(activeSkin.manifest().getStylesheet());
        root.getStylesheets().setAll(stylesheets(coreStylesheet, stylesheet));
        if (!root.getStyleClass().contains("soundboard-root")) {
            root.getStyleClass().add("soundboard-root");
        }
    }

    private void applySkin(Scene scene, Parent root) {
        UiFonts.applyApplicationFont(root);
        URL coreStylesheet = SkinService.class.getResource(CORE_STYLESHEET);
        URL stylesheet = activeSkin.resolveResource(activeSkin.manifest().getStylesheet());
        scene.getStylesheets().setAll(stylesheets(coreStylesheet, stylesheet));
        if (!root.getStyleClass().contains("soundboard-root")) {
            root.getStyleClass().add("soundboard-root");
        }
    }

    public List<SkinDescriptor> getAvailableSkins() {
        return repository.findAll();
    }

    /**
     * Synchronizes package sources and reapplies the selected skin when the UI is already running. Startup invokes
     * this before {@code activeSkin} is assigned, while an in-app rescan must discard the old descriptor and CSS/font
     * bindings so a same-version content update becomes visible without a manual Update action or another restart.
     */
    public void refreshPackageSources() {
        new SkinPackageInstaller(repository).installAvailablePackages(packageSourceDirectories.getDirectories());
        if (activeSkin != null) {
            refreshSelectedSkin();
        }
    }

    public List<Path> getPackageSourceDirectories() {
        return packageSourceDirectories.getDirectories();
    }

    public List<Path> getBundledPackageSourceDirectories() {
        return packageSourceDirectories.getBundledDirectories();
    }

    public void addPackageSourceDirectory(Path directory) throws IOException {
        packageSourceDirectories.add(directory);
    }

    public void removePackageSourceDirectory(Path directory) throws IOException {
        packageSourceDirectories.remove(directory);
    }

    public SkinDescriptor getActiveSkin() {
        return activeSkin;
    }

    public boolean selectSkin(UUID skinUid) {
        return selectSkin(skinUid, 1);
    }

    public boolean selectSkin(UUID skinUid, int version) {
        SkinDescriptor selectedSkin = repository.findAll().stream()
                .filter(descriptor -> descriptor.manifest().getUid().equals(skinUid))
                .filter(descriptor -> descriptor.manifest().getVersion() == version)
                .findFirst()
                .orElse(null);
        if (selectedSkin == null) {
            return false;
        }
        preferences.saveSelectedSkin(selectedSkin.manifest().getUid(), selectedSkin.manifest().getVersion());
        if (selectedSkin.manifest().getUid().equals(activeSkin.manifest().getUid())
                && selectedSkin.manifest().getVersion() == activeSkin.manifest().getVersion()) {
            return false;
        }
        activeSkin = selectedSkin;
        applyActiveSkin();
        return true;
    }

    public List<String> getRestartRequiredChanges(UUID skinUid) {
        return getRestartRequiredChanges(skinUid, 1);
    }

    public List<String> getRestartRequiredChanges(UUID skinUid, int version) {
        SkinRestartSettings currentSettings = new SkinRestartSettings(SkinBootstrap.getStartupRenderingMode());
        return repository.findAll().stream()
                .filter(descriptor -> descriptor.manifest().getUid().equals(skinUid))
                .filter(descriptor -> descriptor.manifest().getVersion() == version)
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

    public int getSelectedSkinVersion() {
        return repository.findSelected(preferences.loadSelectedSkinUid(),
                preferences.loadSelectedSkinVersion()).manifest().getVersion();
    }

    public void refreshSelectedSkin() {
        activeSkin = repository.findSelected(preferences.loadSelectedSkinUid(), preferences.loadSelectedSkinVersion());
        applyActiveSkin();
    }

    public boolean wasLoadedThisSession(UUID skinUid) {
        return loadedSkins.stream().anyMatch(identity -> identity.startsWith(skinUid + ":"));
    }

    public boolean wasLoadedThisSession(UUID skinUid, int version) {
        return loadedSkins.contains(skinUid + ":" + version);
    }

    private void applyActiveSkin() {
        loadedSkins.add(identity(activeSkin));
        UiFonts.configure(activeSkin);
        UiIcons.configure(activeSkin);
        bindings.forEach(binding -> applySkin(binding.scene(), binding.root()));
    }

    private List<String> stylesheets(URL coreStylesheet, URL skinStylesheet) {
        List<String> stylesheets = new ArrayList<>();
        if (coreStylesheet != null) stylesheets.add(coreStylesheet.toExternalForm());
        if (skinStylesheet != null) stylesheets.add(skinStylesheet.toExternalForm());
        return stylesheets;
    }

    private String identity(SkinDescriptor skin) {
        return skin.manifest().getUid() + ":" + skin.manifest().getVersion();
    }

    private record SkinBinding(Scene scene, Parent root) {
    }

}
