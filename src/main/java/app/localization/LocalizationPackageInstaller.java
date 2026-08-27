package app.localization;

import app.persistence.PackageSourceRegistry;
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
import java.util.ArrayList;
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
    private static final int MAX_MANIFEST_SIZE = 256 * 1024;
    private static final String MANIFEST_FILE = "localization.yml";
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
    private final LocalizationRepository repository;
    private final PackageSourceRegistry packageSources;

    public LocalizationPackageInstaller(LocalizationRepository repository) {
        this.repository = repository;
        this.packageSources = new PackageSourceRegistry(repository.getDirectory());
    }

    public LocalizationDescriptor install(Path packageFile, boolean replace) throws IOException {
        return install(packageFile, replace, null, true);
    }

    public boolean canUpdate(LocalizationDescriptor localization) {
        return localization != null
                && !localization.builtIn()
                && packageSources.isAvailable(localization.manifest().getUid());
    }

    public boolean isDiscovered(LocalizationDescriptor localization) {
        return localization != null
                && !localization.builtIn()
                && packageSources.isDiscovered(localization.manifest().getUid());
    }

    public LocalizationDescriptor update(LocalizationDescriptor localization) throws IOException {
        if (localization == null || localization.builtIn()) {
            throw new IllegalArgumentException("A user-installed localization is required");
        }
        UUID uid = localization.manifest().getUid();
        Path packageFile = packageSources.findAvailable(uid)
                .orElseThrow(() -> new IllegalArgumentException("The original localization package is not available"));
        return install(packageFile, true, uid, true);
    }

    public void installAvailablePackages(List<Path> sourceDirectories) {
        Map<UUID, List<Path>> discoveredSources = new LinkedHashMap<>();
        for (Path sourceDirectory : sourceDirectories) {
            if (!Files.isDirectory(sourceDirectory)) {
                continue;
            }
            try (var packages = Files.list(sourceDirectory)) {
                for (Path packageFile : packages
                        .filter(Files::isRegularFile)
                        .filter(this::hasPackageExtension)
                        .sorted()
                        .toList()) {
                    try {
                        LocalizationManifest manifest = readPackageManifest(packageFile);
                        UUID localizationUid = manifest.getUid();
                        if (Files.exists(repository.getDirectory().resolve(localizationUid.toString()))) {
                            addDiscoveredSource(discoveredSources, localizationUid, packageFile);
                        } else {
                            LocalizationDescriptor installed = install(packageFile, false, null, false);
                            addDiscoveredSource(discoveredSources, installed.manifest().getUid(), packageFile);
                        }
                    } catch (LocalizationAlreadyInstalledException exception) {
                        addDiscoveredSource(discoveredSources, exception.getLocalization().getUid(), packageFile);
                    } catch (IOException | IllegalArgumentException exception) {
                        // Invalid or unreadable packages remain absent from the localization list.
                    }
                }
            } catch (IOException | SecurityException exception) {
                // Other package source directories remain available when one cannot be read.
            }
        }
        packageSources.synchronizeDiscovered(discoveredSources);
    }

    private LocalizationDescriptor install(Path packageFile, boolean replace, UUID expectedUid,
                                           boolean rememberSource) throws IOException {
        requireExtension(packageFile);
        Path temporary = repository.getDirectory().resolve(".install-" + UUID.randomUUID());
        Files.createDirectories(temporary);
        try {
            extract(packageFile, temporary);
            LocalizationDescriptor localization = repository.loadDirectory(temporary);
            if (expectedUid != null && !expectedUid.equals(localization.manifest().getUid())) {
                throw new IllegalArgumentException("The remembered package belongs to a different localization");
            }
            Path target = repository.getDirectory().resolve(localization.manifest().getUid().toString());
            if (Files.exists(target) && !replace) throw new LocalizationAlreadyInstalledException(localization.manifest());
            deleteRecursively(target);
            Files.move(temporary, target);
            storePackage(packageFile, localization.manifest().getUid());
            if (rememberSource) {
                packageSources.remember(localization.manifest().getUid(), packageFile);
            }
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
        packageSources.forget(localization.manifest().getUid());
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

    private LocalizationManifest readPackageManifest(Path packageFile) throws IOException {
        requireExtension(packageFile);
        try (InputStream inputStream = Files.newInputStream(packageFile);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            int entryCount = 0;
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (++entryCount > MAX_ENTRIES) {
                    throw new IllegalArgumentException("The localization package contains too many files");
                }
                if (!entry.isDirectory() && MANIFEST_FILE.equals(entry.getName())) {
                    byte[] manifestBytes = zipInputStream.readNBytes(MAX_MANIFEST_SIZE + 1);
                    if (manifestBytes.length > MAX_MANIFEST_SIZE) {
                        throw new IllegalArgumentException("The localization manifest is too large");
                    }
                    LocalizationManifest manifest = mapper.readValue(manifestBytes, LocalizationManifest.class);
                    if (manifest.getUid() == null || LocalizationRepository.ENGLISH_UID.equals(manifest.getUid())) {
                        throw new IllegalArgumentException("The localization package does not contain a valid uid");
                    }
                    return manifest;
                }
            }
        }
        throw new IllegalArgumentException("The localization package must contain localization.yml at its root");
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
        Path storedPackage = packages.resolve(uid + ".tsbl");
        if (!source.toAbsolutePath().normalize().equals(storedPackage.toAbsolutePath().normalize())) {
            Files.copy(source, storedPackage, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void requireExtension(Path file) {
        if (!hasPackageExtension(file)) {
            throw new IllegalArgumentException("A .tsbl localization package is required");
        }
    }

    private boolean hasPackageExtension(Path file) {
        return file != null && file.getFileName().toString().toLowerCase().endsWith(".tsbl");
    }

    private void addDiscoveredSource(Map<UUID, List<Path>> discoveredSources, UUID uid, Path source) {
        discoveredSources.computeIfAbsent(uid, ignored -> new ArrayList<>()).add(source);
    }

    private void deleteRecursively(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            List<Path> ordered = paths.sorted(Comparator.reverseOrder()).toList();
            for (Path path : ordered) Files.deleteIfExists(path);
        }
    }
}
