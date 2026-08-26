package app.localization;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class LocalizationRepository {
    public static final UUID ENGLISH_UID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
    private final Path directory;

    public LocalizationRepository() {
        this(Path.of(System.getProperty("user.home"), ".thesoundboard", "localizations"));
    }

    LocalizationRepository(Path directory) {
        this.directory = directory;
    }

    public List<LocalizationDescriptor> findAll() {
        List<LocalizationDescriptor> result = new ArrayList<>();
        result.add(english());
        if (Files.isDirectory(directory)) {
            try (var directories = Files.list(directory)) {
                directories.filter(Files::isDirectory).map(this::load).flatMap(Optional::stream).forEach(result::add);
            } catch (IOException exception) {
                // Built-in English remains available.
            }
        }
        result.sort(Comparator.comparing(item -> item.manifest().getName(), String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    public LocalizationDescriptor findSelected(UUID uid) {
        return findAll().stream().filter(item -> item.manifest().getUid().equals(uid)).findFirst().orElseGet(this::english);
    }

    public LocalizationDescriptor loadDirectory(Path source) {
        return load(source).orElseThrow(() -> new IllegalArgumentException("The localization folder is incomplete or invalid"));
    }

    public Path getDirectory() { return directory; }

    private LocalizationDescriptor english() {
        LocalizationManifest manifest = new LocalizationManifest();
        manifest.setUid(ENGLISH_UID);
        manifest.setName("English");
        manifest.setLanguageTag("en");
        Map<String, String> strings = new LinkedHashMap<>();
        for (TextKey key : TextKey.values()) {
            strings.put(key.id(), key.english());
        }
        return new LocalizationDescriptor(manifest, Map.copyOf(strings), null, true);
    }

    private Optional<LocalizationDescriptor> load(Path source) {
        Path manifestFile = source.resolve("localization.yml");
        if (!Files.isRegularFile(manifestFile)) return Optional.empty();
        try {
            LocalizationManifest manifest = mapper.readValue(manifestFile.toFile(), LocalizationManifest.class);
            if (manifest.getUid() == null || manifest.getName() == null || manifest.getName().isBlank()
                    || manifest.getLocalizationVersion() != 1 || ENGLISH_UID.equals(manifest.getUid())) return Optional.empty();
            Path stringsFile = source.resolve(manifest.getStrings()).normalize();
            if (!stringsFile.startsWith(source.normalize()) || !Files.isRegularFile(stringsFile)) return Optional.empty();
            Map<String, String> raw = mapper.readValue(stringsFile.toFile(), new TypeReference<LinkedHashMap<String, String>>() {});
            Map<String, String> known = new LinkedHashMap<>();
            for (TextKey key : TextKey.values()) {
                String value = raw.get(key.id());
                if (value != null && !value.isBlank()) known.put(key.id(), value);
            }
            return Optional.of(new LocalizationDescriptor(manifest, Map.copyOf(known), source, false));
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }
}
