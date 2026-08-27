package app.packages;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class PackageSourceDirectoryRegistry {
    private final Path storageFile;
    private final List<Path> bundledDirectories;

    public PackageSourceDirectoryRegistry(Path storageFile, List<Path> bundledDirectories) {
        this.storageFile = storageFile;
        this.bundledDirectories = normalize(bundledDirectories);
    }

    public List<Path> getDirectories() {
        LinkedHashSet<Path> directories = new LinkedHashSet<>(bundledDirectories);
        directories.addAll(getCustomDirectories());
        return List.copyOf(directories);
    }

    public List<Path> getBundledDirectories() {
        return bundledDirectories;
    }

    public List<Path> getCustomDirectories() {
        if (!Files.isRegularFile(storageFile)) {
            return List.of();
        }
        try {
            return normalize(Files.readAllLines(storageFile, StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.isBlank())
                    .map(Path::of)
                    .toList()).stream()
                    .filter(path -> !bundledDirectories.contains(path))
                    .toList();
        } catch (IOException | RuntimeException exception) {
            return List.of();
        }
    }

    public void add(Path directory) throws IOException {
        Path normalizedDirectory = normalize(directory);
        if (bundledDirectories.contains(normalizedDirectory)) {
            return;
        }
        List<Path> directories = new ArrayList<>(getCustomDirectories());
        if (directories.contains(normalizedDirectory)) {
            return;
        }
        directories.add(normalizedDirectory);
        save(directories);
    }

    public void remove(Path directory) throws IOException {
        Path normalizedDirectory = normalize(directory);
        if (bundledDirectories.contains(normalizedDirectory)) {
            return;
        }
        List<Path> directories = new ArrayList<>(getCustomDirectories());
        directories.remove(normalizedDirectory);
        save(directories);
    }

    private void save(List<Path> directories) throws IOException {
        Files.createDirectories(storageFile.getParent());
        Files.write(
                storageFile,
                directories.stream().map(Path::toString).toList(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    private static List<Path> normalize(List<Path> directories) {
        LinkedHashSet<Path> normalized = new LinkedHashSet<>();
        directories.stream().map(PackageSourceDirectoryRegistry::normalize).forEach(normalized::add);
        return List.copyOf(normalized);
    }

    private static Path normalize(Path directory) {
        return directory.toAbsolutePath().normalize();
    }
}
