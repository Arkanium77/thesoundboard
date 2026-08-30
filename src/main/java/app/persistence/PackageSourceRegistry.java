package app.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class PackageSourceRegistry {
    private static final String SOURCES_DIRECTORY = "_sources";
    private static final String SOURCE_FILE_EXTENSION = ".path";
    private static final String DISCOVERED_SOURCES_FILE_EXTENSION = ".sources";
    private final Path directory;

    public PackageSourceRegistry(Path packageDirectory) {
        this.directory = packageDirectory.resolve(SOURCES_DIRECTORY);
    }

    public void remember(UUID uid, Path source) {
        remember(uid, 1, source);
    }

    public void remember(UUID uid, int version, Path source) {
        if (uid == null || source == null) {
            return;
        }
        try {
            Files.createDirectories(directory);
            Files.writeString(sourceFile(key(uid, version)), source.toAbsolutePath().normalize().toString());
        } catch (IOException | SecurityException exception) {
            // Manual package installation remains available when the source path cannot be remembered.
        }
    }

    public Optional<Path> find(UUID uid) {
        if (uid == null) {
            return Optional.empty();
        }
        try {
            return findAll(uid).stream().findFirst();
        } catch (SecurityException exception) {
            return Optional.empty();
        }
    }

    public Optional<Path> findAvailable(UUID uid) {
        return findAll(uid).stream().filter(Files::isRegularFile).findFirst();
    }

    public Optional<Path> findAvailable(UUID uid, int version) {
        if (uid == null) {
            return Optional.empty();
        }
        return findAll(key(uid, version)).stream().filter(Files::isRegularFile).findFirst();
    }

    public List<Path> findAvailableSources(UUID uid, int version) {
        if (uid == null) {
            return List.of();
        }
        return findAll(key(uid, version)).stream().filter(Files::isRegularFile).toList();
    }

    public boolean isAvailable(UUID uid) {
        return findAvailable(uid).isPresent();
    }

    public boolean isAvailable(UUID uid, int version) {
        return findAvailable(uid, version).isPresent();
    }

    public boolean isDiscovered(UUID uid) {
        return isDiscovered(uid, 1);
    }

    public boolean isDiscovered(UUID uid, int version) {
        if (uid == null) {
            return false;
        }
        List<Path> discoveredSources = new ArrayList<>();
        readPaths(discoveredSourcesFile(key(uid, version)), discoveredSources);
        return discoveredSources.stream().anyMatch(Files::isRegularFile);
    }

    public void synchronizeDiscovered(Map<UUID, List<Path>> discoveredSources) {
        Map<String, List<Path>> versioned = new LinkedHashMap<>();
        discoveredSources.forEach((uid, paths) -> versioned.put(key(uid, 1), paths));
        synchronizeVersionedDiscovered(versioned);
    }

    public void synchronizeVersionedDiscovered(Map<String, List<Path>> discoveredSources) {
        try {
            Files.createDirectories(directory);
            try (var existingSources = Files.list(directory)) {
                for (Path sourceFile : existingSources
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(DISCOVERED_SOURCES_FILE_EXTENSION))
                        .toList()) {
                    Files.deleteIfExists(sourceFile);
                }
            }
            for (Map.Entry<String, List<Path>> entry : discoveredSources.entrySet()) {
                Set<Path> normalizedSources = new LinkedHashSet<>();
                for (Path source : entry.getValue()) {
                    normalizedSources.add(source.toAbsolutePath().normalize());
                }
                if (!normalizedSources.isEmpty()) {
                    Files.write(
                            discoveredSourcesFile(entry.getKey()),
                            normalizedSources.stream().map(Path::toString).toList()
                    );
                }
            }
            deleteDirectoryIfEmpty();
        } catch (IOException | SecurityException exception) {
            // Manual installation remains available when discovered sources cannot be synchronized.
        }
    }

    public void forget(UUID uid) {
        forget(uid, 1);
    }

    public void forget(UUID uid, int version) {
        if (uid == null) {
            return;
        }
        try {
            Files.deleteIfExists(sourceFile(key(uid, version)));
            Files.deleteIfExists(discoveredSourcesFile(key(uid, version)));
            deleteDirectoryIfEmpty();
        } catch (IOException | SecurityException exception) {
            // A stale source path is ignored when its package is no longer installed.
        }
    }

    private Path sourceFile(String key) {
        return directory.resolve(key + SOURCE_FILE_EXTENSION);
    }

    private Path discoveredSourcesFile(String key) {
        return directory.resolve(key + DISCOVERED_SOURCES_FILE_EXTENSION);
    }

    private List<Path> findAll(UUID uid) {
        return findAll(key(uid, 1));
    }

    private List<Path> findAll(String key) {
        if (key == null) {
            return List.of();
        }
        List<Path> sources = new ArrayList<>();
        readPaths(sourceFile(key), sources);
        readPaths(discoveredSourcesFile(key), sources);
        return List.copyOf(sources);
    }

    public static String key(UUID uid, int version) {
        if (uid == null) {
            return null;
        }
        return version == 1 ? uid.toString() : uid + "-v" + version;
    }

    private void readPaths(Path sourceFile, List<Path> sources) {
        if (!Files.isRegularFile(sourceFile)) {
            return;
        }
        try {
            for (String value : Files.readAllLines(sourceFile)) {
                if (!value.isBlank()) {
                    sources.add(Path.of(value.trim()));
                }
            }
        } catch (IOException | InvalidPathException | SecurityException exception) {
            // Other remembered paths remain available when one source file is invalid.
        }
    }

    private void deleteDirectoryIfEmpty() throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        boolean empty;
        try (var entries = Files.list(directory)) {
            empty = entries.findAny().isEmpty();
        }
        if (empty) {
            Files.deleteIfExists(directory);
        }
    }
}
