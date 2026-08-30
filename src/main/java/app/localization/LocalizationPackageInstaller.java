package app.localization;

import app.persistence.PackageSourceRegistry;
import app.packages.PackageIdentity;
import app.packages.PackageSourceDirectories;
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
import java.util.Optional;
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
    private final List<Path> protectedSourceDirectories;

    public LocalizationPackageInstaller(LocalizationRepository repository) {
        this(repository, PackageSourceDirectories.discover().localizationDirectories());
    }

    LocalizationPackageInstaller(LocalizationRepository repository, List<Path> protectedSourceDirectories) {
        this.repository = repository;
        this.packageSources = new PackageSourceRegistry(repository.getDirectory());
        this.protectedSourceDirectories = protectedSourceDirectories.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
    }

    public LocalizationDescriptor install(Path packageFile, boolean replace) throws IOException {
        return install(packageFile, replace, null, true);
    }

    public boolean canUpdate(LocalizationDescriptor localization) {
        return localization != null
                && !localization.builtIn()
                && findMatchingSource(localization.manifest()).isPresent();
    }

    public List<Path> findSources(LocalizationDescriptor localization) {
        if (localization == null || localization.builtIn()) {
            return List.of();
        }
        return findMatchingSources(localization.manifest());
    }

    public boolean isProtectedSource(Path source) {
        Path normalizedSource = source.toAbsolutePath().normalize();
        return protectedSourceDirectories.stream().anyMatch(normalizedSource::startsWith);
    }

    public void deleteSourceFile(LocalizationDescriptor localization, Path source) throws IOException {
        if (!findSources(localization).contains(source.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("The selected file is not a source of this localization version");
        }
        if (isProtectedSource(source)) {
            throw new IllegalArgumentException("Bundled package sources are protected and cannot be deleted");
        }
        Files.delete(source);
    }

    public boolean isDiscovered(LocalizationDescriptor localization) {
        return localization != null
                && !localization.builtIn()
                && packageSources.isDiscovered(localization.manifest().getUid(), localization.manifest().getVersion());
    }

    public LocalizationDescriptor update(LocalizationDescriptor localization) throws IOException {
        if (localization == null || localization.builtIn()) {
            throw new IllegalArgumentException("A user-installed localization is required");
        }
        UUID uid = localization.manifest().getUid();
        Path packageFile = findMatchingSource(localization.manifest())
                .orElseThrow(() -> new IllegalArgumentException("The original localization package is not available"));
        return install(packageFile, true, uid, true);
    }

    public void installAvailablePackages(List<Path> sourceDirectories) {
        Map<String, List<Path>> discoveredSources = new LinkedHashMap<>();
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
                        PackageIdentity identity = new PackageIdentity(localizationUid, manifest.getVersion());
                        if (Files.exists(repository.getDirectory().resolve(identity.storageName()))) {
                            addDiscoveredSource(discoveredSources, identity, packageFile);
                        } else if (isDisabled(identity)) {
                            addDiscoveredSource(discoveredSources, identity, packageFile);
                        } else {
                            LocalizationDescriptor installed = install(packageFile, false, null, false);
                            addDiscoveredSource(discoveredSources, identity(installed), packageFile);
                        }
                    } catch (LocalizationAlreadyInstalledException exception) {
                        LocalizationManifest manifest = exception.getLocalization();
                        addDiscoveredSource(discoveredSources,
                                new PackageIdentity(manifest.getUid(), manifest.getVersion()), packageFile);
                    } catch (IOException | IllegalArgumentException exception) {
                        // Invalid or unreadable packages remain absent from the localization list.
                    }
                }
            } catch (IOException | SecurityException exception) {
                // Other package source directories remain available when one cannot be read.
            }
        }
        packageSources.synchronizeVersionedDiscovered(discoveredSources);
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
            PackageIdentity identity = identity(localization);
            Path target = repository.getDirectory().resolve(identity.storageName());
            if (Files.exists(target) && !replace) throw new LocalizationAlreadyInstalledException(localization.manifest());
            deleteRecursively(target);
            Files.move(temporary, target);
            storePackage(packageFile, identity);
            if (rememberSource) {
                Files.deleteIfExists(disabledMarker(identity));
                packageSources.remember(identity.uid(), identity.version(), packageFile);
            }
            return repository.findSelected(identity.uid(), identity.version());
        } finally {
            deleteRecursively(temporary);
        }
    }

    public LocalizationDescriptor install(Path packageFile) throws IOException { return install(packageFile, false); }

    public void delete(LocalizationDescriptor localization) throws IOException {
        if (localization.builtIn()) throw new IllegalArgumentException("Built-in English cannot be deleted");
        PackageIdentity identity = identity(localization);
        if (!findSources(localization).isEmpty()) {
            Files.createDirectories(repository.getDirectory().resolve("_disabled"));
            Files.writeString(disabledMarker(identity), "installed-only");
        }
        deleteRecursively(localization.directory());
        Files.deleteIfExists(repository.getDirectory().resolve("_packages").resolve(identity.storageName() + ".tsbl"));
        packageSources.forget(identity.uid(), identity.version());
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

    private void storePackage(Path source, PackageIdentity identity) throws IOException {
        Path packages = repository.getDirectory().resolve("_packages");
        Files.createDirectories(packages);
        Path storedPackage = packages.resolve(identity.storageName() + ".tsbl");
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

    private void addDiscoveredSource(Map<String, List<Path>> discoveredSources, PackageIdentity identity, Path source) {
        discoveredSources.computeIfAbsent(identity.storageName(), ignored -> new ArrayList<>()).add(source);
    }

    private PackageIdentity identity(LocalizationDescriptor localization) {
        return new PackageIdentity(localization.manifest().getUid(), localization.manifest().getVersion());
    }

    private Path disabledMarker(PackageIdentity identity) {
        return repository.getDirectory().resolve("_disabled").resolve(identity.storageName());
    }

    private boolean isDisabled(PackageIdentity identity) {
        Path marker = disabledMarker(identity);
        if (!Files.isRegularFile(marker)) {
            return false;
        }
        try {
            if ("installed-only".equals(Files.readString(marker).trim())) {
                return true;
            }
            Files.deleteIfExists(marker);
            return false;
        } catch (IOException | SecurityException exception) {
            return true;
        }
    }

    private Optional<Path> findMatchingSource(LocalizationManifest installedManifest) {
        return findMatchingSources(installedManifest).stream().findFirst();
    }

    private List<Path> findMatchingSources(LocalizationManifest installedManifest) {
        return packageSources.findAvailableSources(installedManifest.getUid(), installedManifest.getVersion()).stream()
                .filter(source -> matches(source, installedManifest))
                .map(source -> source.toAbsolutePath().normalize())
                .distinct()
                .toList();
    }

    private boolean matches(Path source, LocalizationManifest installedManifest) {
        try {
            LocalizationManifest sourceManifest = readPackageManifest(source);
            return sourceManifest.getUid().equals(installedManifest.getUid())
                    && sourceManifest.getVersion() == installedManifest.getVersion();
        } catch (IOException | IllegalArgumentException exception) {
            return false;
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
