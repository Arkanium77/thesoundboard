package app.localization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class LocalizationPackageInstaller {
    private static final int MAX_ENTRIES = 64;
    private static final long MAX_SIZE = 4L * 1024L * 1024L;
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
    private final LocalizationRepository repository;

    public LocalizationPackageInstaller(LocalizationRepository repository) { this.repository = repository; }

    public LocalizationDescriptor install(Path packageFile, boolean replace) throws IOException {
        requireExtension(packageFile);
        Path temporary = repository.getDirectory().resolve(".install-" + UUID.randomUUID());
        Files.createDirectories(temporary);
        try {
            extract(packageFile, temporary);
            LocalizationDescriptor localization = repository.loadDirectory(temporary);
            Path target = repository.getDirectory().resolve(localization.manifest().getUid().toString());
            if (Files.exists(target) && !replace) throw new LocalizationAlreadyInstalledException(localization.manifest());
            deleteRecursively(target);
            Files.move(temporary, target);
            storePackage(packageFile, localization.manifest().getUid());
            return repository.findSelected(localization.manifest().getUid());
        } finally {
            deleteRecursively(temporary);
        }
    }

    public LocalizationDescriptor install(Path packageFile) throws IOException { return install(packageFile, false); }

    public void delete(LocalizationDescriptor localization) throws IOException {
        if (localization.builtIn()) throw new IllegalArgumentException("Built-in English cannot be deleted");
        deleteRecursively(localization.directory());
        Files.deleteIfExists(repository.getDirectory().resolve("_packages").resolve(localization.manifest().getUid() + ".tsbl"));
    }

    public void export(LocalizationDescriptor localization, Path target) throws IOException {
        if (localization.builtIn()) throw new IllegalArgumentException("Use Export Strings for built-in English");
        createPackage(localization.directory(), target);
    }

    public void createPackage(Path source, Path target) throws IOException {
        repository.loadDirectory(source);
        requireExtension(target);
        Path normalizedSource = source.toAbsolutePath().normalize();
        if (target.toAbsolutePath().normalize().startsWith(normalizedSource)) {
            throw new IllegalArgumentException("Save the package outside the localization source folder");
        }
        Files.createDirectories(target.toAbsolutePath().getParent());
        try (OutputStream output = Files.newOutputStream(target);
             ZipOutputStream zip = new ZipOutputStream(output);
             var paths = Files.walk(source)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                if (Files.isSymbolicLink(path)) continue;
                zip.putNextEntry(new ZipEntry(source.relativize(path).toString().replace('\\', '/')));
                Files.copy(path, zip);
                zip.closeEntry();
            }
        }
    }

    public void exportEnglishStrings(Path target) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (TextKey key : TextKey.values()) values.put(key.id(), key.english());
        Files.createDirectories(target.toAbsolutePath().getParent());
        mapper.writeValue(target.toFile(), values);
    }

    public int updateSource(Path source) throws IOException {
        LocalizationDescriptor localization = repository.loadDirectory(source);
        Path stringsFile = source.resolve(localization.manifest().getStrings()).normalize();
        Map<String, String> values = mapper.readValue(
                stringsFile.toFile(),
                new TypeReference<LinkedHashMap<String, String>>() { }
        );
        int added = 0;
        for (TextKey key : TextKey.values()) {
            if (!values.containsKey(key.id())) {
                values.put(key.id(), key.english());
                added++;
            }
        }
        Path temporaryFile = stringsFile.resolveSibling(stringsFile.getFileName() + ".tmp");
        try {
            mapper.writeValue(temporaryFile.toFile(), values);
            replaceFile(temporaryFile, stringsFile);
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
        return added;
    }

    public int updatePackage(Path packageFile) throws IOException {
        requireExtension(packageFile);
        Path workingDirectory = repository.getDirectory().resolve(".update-" + UUID.randomUUID());
        Path temporaryPackage = packageFile.resolveSibling(packageFile.getFileName() + ".updated.tsbl");
        Files.createDirectories(workingDirectory);
        try {
            extract(packageFile, workingDirectory);
            int added = updateSource(workingDirectory);
            createPackage(workingDirectory, temporaryPackage);
            replaceFile(temporaryPackage, packageFile);
            return added;
        } finally {
            Files.deleteIfExists(temporaryPackage);
            deleteRecursively(workingDirectory);
        }
    }

    private void replaceFile(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void extract(Path packageFile, Path target) throws IOException {
        int entries = 0;
        long size = 0;
        byte[] buffer = new byte[8192];
        try (InputStream input = Files.newInputStream(packageFile); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw new IllegalArgumentException("The localization package contains too many files");
                Path destination = target.resolve(entry.getName()).normalize();
                if (!destination.startsWith(target)) throw new IllegalArgumentException("The package contains an unsafe path");
                if (entry.isDirectory()) { Files.createDirectories(destination); continue; }
                Files.createDirectories(destination.getParent());
                try (OutputStream output = Files.newOutputStream(destination)) {
                    int read;
                    while ((read = zip.read(buffer)) >= 0) {
                        size += read;
                        if (size > MAX_SIZE) throw new IllegalArgumentException("The localization package is too large");
                        output.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    private void storePackage(Path source, UUID uid) throws IOException {
        Path packages = repository.getDirectory().resolve("_packages");
        Files.createDirectories(packages);
        Files.copy(source, packages.resolve(uid + ".tsbl"), StandardCopyOption.REPLACE_EXISTING);
    }

    private void requireExtension(Path file) {
        if (file == null || !file.getFileName().toString().toLowerCase().endsWith(".tsbl")) {
            throw new IllegalArgumentException("A .tsbl localization package is required");
        }
    }

    private void deleteRecursively(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            List<Path> ordered = paths.sorted(Comparator.reverseOrder()).toList();
            for (Path path : ordered) Files.deleteIfExists(path);
        }
    }
}
