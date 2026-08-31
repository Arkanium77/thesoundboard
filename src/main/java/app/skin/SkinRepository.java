package app.skin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public class SkinRepository {
    public static final UUID DEFAULT_SKIN_UID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String DEFAULT_SKIN_RESOURCE = "/skins/default/skin.yml";
    private static final String DEFAULT_SKIN_ROOT = "/skins/default/";
    private static final String SKIN_MANIFEST_FILE = "skin.yml";
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
    private final Path externalSkinsDirectory;
    private List<SkinDescriptor> cachedSkins;

    public SkinRepository() {
        this(Path.of(System.getProperty("user.home"), ".thesoundboard", "skins"));
    }

    SkinRepository(Path externalSkinsDirectory) {
        this.externalSkinsDirectory = externalSkinsDirectory;
    }

    /**
     * Returns cached, already validated descriptors until an installer mutation invalidates them. Parsing every YAML
     * manifest and validating its resources on each settings navigation blocked the JavaFX thread and also allocated a
     * fresh parser graph for every skin. Installed package directories are mutated only through the installer, which
     * preserves the invariant that every successful replacement or deletion invalidates this snapshot.
     */
    public synchronized List<SkinDescriptor> findAll() {
        if (cachedSkins != null) return cachedSkins;
        List<SkinDescriptor> skins = new ArrayList<>();
        SkinDescriptor builtInDefault = loadBuiltInDefault();
        skins.add(builtInDefault);
        LinkedHashSet<String> skinUids = new LinkedHashSet<>();
        skinUids.add(DEFAULT_SKIN_UID + ":" + builtInDefault.manifest().getVersion());
        loadExternalSkins().stream()
                .filter(descriptor -> !DEFAULT_SKIN_UID.equals(descriptor.manifest().getUid()))
                .filter(descriptor -> skinUids.add(descriptor.manifest().getUid() + ":" + descriptor.manifest().getVersion()))
                .forEach(skins::add);
        skins.sort(Comparator.comparing(descriptor -> descriptor.manifest().getName(), String.CASE_INSENSITIVE_ORDER));
        cachedSkins = List.copyOf(skins);
        return cachedSkins;
    }

    synchronized void invalidate() {
        cachedSkins = null;
    }

    public SkinDescriptor findSelected(UUID selectedSkinUid) {
        return findAll().stream()
                .filter(descriptor -> descriptor.manifest().getUid().equals(selectedSkinUid))
                .findFirst()
                .orElseGet(this::loadBuiltInDefault);
    }

    public SkinDescriptor findSelected(UUID selectedSkinUid, int version) {
        return findAll().stream()
                .filter(descriptor -> descriptor.manifest().getUid().equals(selectedSkinUid))
                .filter(descriptor -> descriptor.manifest().getVersion() == version)
                .findFirst()
                .orElseGet(this::loadBuiltInDefault);
    }

    public Path getExternalSkinsDirectory() {
        return externalSkinsDirectory;
    }

    public SkinDescriptor loadSkinDirectory(Path directory) {
        return loadExternalSkin(directory)
                .orElseThrow(() -> new IllegalArgumentException("The skin folder is incomplete or invalid"));
    }

    private SkinDescriptor loadBuiltInDefault() {
        try (InputStream inputStream = SkinRepository.class.getResourceAsStream(DEFAULT_SKIN_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing built-in skin manifest: " + DEFAULT_SKIN_RESOURCE);
            }
            SkinManifest manifest = mapper.readValue(inputStream, SkinManifest.class);
            validateManifest(manifest, DEFAULT_SKIN_RESOURCE);
            SkinDescriptor descriptor = new SkinDescriptor(manifest, DEFAULT_SKIN_ROOT, null);
            validateRequiredResources(descriptor, DEFAULT_SKIN_RESOURCE);
            return descriptor;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load built-in skin", exception);
        }
    }

    private List<SkinDescriptor> loadExternalSkins() {
        if (!Files.isDirectory(externalSkinsDirectory)) {
            return List.of();
        }
        try (Stream<Path> directories = Files.list(externalSkinsDirectory)) {
            return directories
                    .filter(Files::isDirectory)
                    .map(this::loadExternalSkin)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    private Optional<SkinDescriptor> loadExternalSkin(Path directory) {
        Path manifestPath = directory.resolve(SKIN_MANIFEST_FILE);
        if (!Files.isRegularFile(manifestPath)) {
            return Optional.empty();
        }
        try (InputStream inputStream = Files.newInputStream(manifestPath)) {
            SkinManifest manifest = mapper.readValue(inputStream, SkinManifest.class);
            validateManifest(manifest, manifestPath.toString());
            SkinDescriptor descriptor = new SkinDescriptor(manifest, null, directory);
            validateRequiredResources(descriptor, manifestPath.toString());
            return Optional.of(descriptor);
        } catch (IOException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private void validateManifest(SkinManifest manifest, String source) {
        if (manifest.getSkinVersion() != 1) {
            throw new IllegalArgumentException("Unsupported skin version in " + source);
        }
        if (manifest.getVersion() < 1) {
            throw new IllegalArgumentException("Invalid skin package version in " + source);
        }
        if (manifest.getUid() == null) {
            throw new IllegalArgumentException("Missing skin uid in " + source);
        }
        if (manifest.getName() == null || manifest.getName().isBlank()) {
            throw new IllegalArgumentException("Missing skin name in " + source);
        }
        if (manifest.getFonts().getRegular() == null || manifest.getFonts().getRegular().isBlank()) {
            throw new IllegalArgumentException("Missing regular font in " + source);
        }
        if (manifest.getFonts().getBold() == null || manifest.getFonts().getBold().isBlank()) {
            throw new IllegalArgumentException("Missing bold font in " + source);
        }
        if (manifest.getFonts().getItalic() == null || manifest.getFonts().getItalic().isBlank()) {
            throw new IllegalArgumentException("Missing italic font in " + source);
        }
        if (manifest.getStylesheet() == null || manifest.getStylesheet().isBlank()) {
            throw new IllegalArgumentException("Missing stylesheet in " + source);
        }
        boolean regularUsesSystemFont = isSystemFont(manifest.getFonts().getRegular());
        boolean boldUsesSystemFont = isSystemFont(manifest.getFonts().getBold());
        boolean italicUsesSystemFont = isSystemFont(manifest.getFonts().getItalic());
        String boldItalic = manifest.getFonts().getBoldItalic();
        boolean boldItalicUsesSystemFont = boldItalic == null || boldItalic.isBlank() || isSystemFont(boldItalic);
        if (regularUsesSystemFont != boldUsesSystemFont
                || regularUsesSystemFont != italicUsesSystemFont
                || regularUsesSystemFont && !boldItalicUsesSystemFont) {
            throw new IllegalArgumentException("System and bundled font roles cannot be mixed in " + source);
        }
    }

    private void validateRequiredResources(SkinDescriptor descriptor, String source) {
        requireResource(descriptor, descriptor.manifest().getStylesheet(), "stylesheet", source);
        requireFontResource(descriptor, descriptor.manifest().getFonts().getRegular(), "regular font", source);
        requireFontResource(descriptor, descriptor.manifest().getFonts().getBold(), "bold font", source);
        requireFontResource(descriptor, descriptor.manifest().getFonts().getItalic(), "italic font", source);
        String boldItalic = descriptor.manifest().getFonts().getBoldItalic();
        if (boldItalic != null && !boldItalic.isBlank()) {
            requireFontResource(descriptor, boldItalic, "bold italic font", source);
        }
    }

    private void requireFontResource(SkinDescriptor descriptor, String path, String role, String source) {
        if (!isSystemFont(path)) {
            requireResource(descriptor, path, role, source);
        }
    }

    private boolean isSystemFont(String value) {
        return SkinManifest.SYSTEM_FONT.equalsIgnoreCase(value);
    }

    private void requireResource(SkinDescriptor descriptor, String path, String role, String source) {
        if (descriptor.resolveResource(path) == null
                || descriptor.directory() != null && !Files.isRegularFile(descriptor.directory().resolve(path).normalize())) {
            throw new IllegalArgumentException("Missing " + role + " in " + source + ": " + path);
        }
    }
}
