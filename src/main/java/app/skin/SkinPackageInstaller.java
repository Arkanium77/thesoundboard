package app.skin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class SkinPackageInstaller {
    private static final int MAX_ENTRIES = 512;
    private static final long MAX_UNCOMPRESSED_SIZE = 128L * 1024L * 1024L;
    private static final String MANIFEST_FILE = "skin.yml";
    private static final String PENDING_DIRECTORY = "_pending";
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
    private final SkinRepository repository;

    public SkinPackageInstaller(SkinRepository repository) {
        this.repository = repository;
    }

    public SkinDescriptor install(Path packageFile) throws IOException {
        return install(packageFile, false);
    }

    public SkinDescriptor install(Path packageFile, boolean replaceExisting) throws IOException {
        requirePackageExtension(packageFile);
        Path skinsDirectory = repository.getExternalSkinsDirectory();
        Path temporaryDirectory = skinsDirectory.resolve(".install-" + UUID.randomUUID());
        Files.createDirectories(temporaryDirectory);
        try {
            extractPackage(packageFile, temporaryDirectory);
            SkinDescriptor validatedSkin = validateExtractedSkin(temporaryDirectory);
            UUID skinUid = validatedSkin.manifest().getUid();
            if (SkinRepository.DEFAULT_SKIN_UID.equals(skinUid)) {
                throw new IllegalArgumentException("The built-in default skin cannot be replaced");
            }
            Path targetDirectory = skinsDirectory.resolve(skinUid.toString());
            if (Files.exists(targetDirectory) && !replaceExisting) {
                throw new SkinAlreadyInstalledException(validatedSkin.manifest());
            }
            replaceInstallation(temporaryDirectory, targetDirectory, packageFile, skinUid);
            return repository.findAll().stream()
                    .filter(descriptor -> descriptor.manifest().getUid().equals(skinUid))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Installed skin could not be loaded"));
        } finally {
            deleteRecursively(temporaryDirectory);
        }
    }

    public SkinManifest scheduleReplacement(Path packageFile) throws IOException {
        requirePackageExtension(packageFile);
        Path pendingRoot = pendingRoot();
        Path temporaryDirectory = pendingRoot.resolve(".replacement-" + UUID.randomUUID());
        Files.createDirectories(temporaryDirectory);
        try {
            extractPackage(packageFile, temporaryDirectory);
            SkinDescriptor validatedSkin = repository.loadSkinDirectory(temporaryDirectory);
            UUID skinUid = validatedSkin.manifest().getUid();
            if (SkinRepository.DEFAULT_SKIN_UID.equals(skinUid)) {
                throw new IllegalArgumentException("The built-in default skin cannot be replaced");
            }
            Path replacementsDirectory = pendingRoot.resolve("replacements");
            Path packagesDirectory = pendingRoot.resolve("packages");
            Path targetDirectory = replacementsDirectory.resolve(skinUid.toString());
            Files.createDirectories(replacementsDirectory);
            Files.createDirectories(packagesDirectory);
            deleteRecursively(targetDirectory);
            Files.move(temporaryDirectory, targetDirectory);
            Files.copy(packageFile, packagesDirectory.resolve(skinUid + ".tsbs"), StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(pendingRoot.resolve("deletions").resolve(skinUid.toString()));
            return validatedSkin.manifest();
        } finally {
            deleteRecursively(temporaryDirectory);
        }
    }

    public void scheduleDeletion(SkinDescriptor skin) throws IOException {
        if (skin.isBuiltIn() || SkinRepository.DEFAULT_SKIN_UID.equals(skin.manifest().getUid())) {
            throw new IllegalArgumentException("The built-in default skin cannot be deleted");
        }
        UUID skinUid = skin.manifest().getUid();
        Path pendingRoot = pendingRoot();
        Path deletionsDirectory = pendingRoot.resolve("deletions");
        Files.createDirectories(deletionsDirectory);
        Files.writeString(deletionsDirectory.resolve(skinUid.toString()), "delete");
        deleteRecursively(pendingRoot.resolve("replacements").resolve(skinUid.toString()));
        Files.deleteIfExists(pendingRoot.resolve("packages").resolve(skinUid + ".tsbs"));
    }

    public static void applyPendingOperations(SkinRepository repository) {
        SkinPackageInstaller installer = new SkinPackageInstaller(repository);
        installer.applyPendingOperations();
    }

    public void export(SkinDescriptor skin, Path packageFile) throws IOException {
        if (skin.isBuiltIn()) {
            throw new IllegalArgumentException("The built-in default skin cannot be exported");
        }
        createPackage(skin.directory(), packageFile);
    }

    public void createPackage(Path skinDirectory, Path packageFile) throws IOException {
        if (skinDirectory == null || !Files.isDirectory(skinDirectory)) {
            throw new IllegalArgumentException("A skin folder is required");
        }
        SkinDescriptor skin = repository.loadSkinDirectory(skinDirectory);
        if (SkinRepository.DEFAULT_SKIN_UID.equals(skin.manifest().getUid())) {
            throw new IllegalArgumentException("The built-in default skin UID is reserved");
        }
        requirePackageExtension(packageFile);
        Path normalizedSkinDirectory = skinDirectory.toAbsolutePath().normalize();
        Path normalizedPackageFile = packageFile.toAbsolutePath().normalize();
        if (normalizedPackageFile.startsWith(normalizedSkinDirectory)) {
            throw new IllegalArgumentException("Save the package outside the skin source folder");
        }
        Path temporaryPackage = packageFile.resolveSibling(packageFile.getFileName() + ".tmp");
        Files.createDirectories(packageFile.toAbsolutePath().getParent());
        try {
            try (OutputStream outputStream = Files.newOutputStream(temporaryPackage);
                 ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);
                 var paths = Files.walk(skinDirectory)) {
                for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                    if (Files.isSymbolicLink(path)) {
                        continue;
                    }
                    String entryName = skinDirectory.relativize(path).toString().replace('\\', '/');
                    zipOutputStream.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, zipOutputStream);
                    zipOutputStream.closeEntry();
                }
            }
            Files.move(temporaryPackage, packageFile, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporaryPackage);
        }
    }

    public void delete(SkinDescriptor skin) throws IOException {
        if (skin.isBuiltIn() || SkinRepository.DEFAULT_SKIN_UID.equals(skin.manifest().getUid())) {
            throw new IllegalArgumentException("The built-in default skin cannot be deleted");
        }
        deleteRecursively(skin.directory());
        Files.deleteIfExists(repository.getExternalSkinsDirectory()
                .resolve("_packages")
                .resolve(skin.manifest().getUid() + ".tsbs"));
    }

    private SkinDescriptor validateExtractedSkin(Path temporaryDirectory) throws IOException {
        Path manifestPath = temporaryDirectory.resolve(MANIFEST_FILE);
        if (!Files.isRegularFile(manifestPath)) {
            throw new IllegalArgumentException("The skin package must contain skin.yml at its root");
        }
        SkinManifest manifest = mapper.readValue(manifestPath.toFile(), SkinManifest.class);
        if (manifest.getUid() == null) {
            throw new IllegalArgumentException("The skin package does not contain a valid uid");
        }
        return repository.loadSkinDirectory(temporaryDirectory);
    }

    private void replaceInstallation(Path temporaryDirectory, Path targetDirectory, Path packageFile, UUID skinUid) throws IOException {
        Path backupDirectory = targetDirectory.resolveSibling(".backup-" + skinUid);
        Path packagesDirectory = repository.getExternalSkinsDirectory().resolve("_packages");
        Path storedPackage = packagesDirectory.resolve(skinUid + ".tsbs");
        Path backupPackage = packagesDirectory.resolve(".backup-" + skinUid + ".tsbs");
        deleteRecursively(backupDirectory);
        Files.createDirectories(packagesDirectory);
        Files.deleteIfExists(backupPackage);
        boolean existingMoved = false;
        boolean packageMoved = false;
        try {
            if (Files.exists(targetDirectory)) {
                Files.move(targetDirectory, backupDirectory);
                existingMoved = true;
            }
            if (Files.exists(storedPackage)
                    && !packageFile.toAbsolutePath().normalize().equals(storedPackage.toAbsolutePath().normalize())) {
                Files.move(storedPackage, backupPackage);
                packageMoved = true;
            }
            Files.move(temporaryDirectory, targetDirectory);
            storePackageCopy(packageFile, skinUid);
        } catch (IOException | RuntimeException exception) {
            deleteRecursively(targetDirectory);
            Files.deleteIfExists(storedPackage);
            if (existingMoved && Files.exists(backupDirectory)) {
                Files.move(backupDirectory, targetDirectory);
            }
            if (packageMoved && Files.exists(backupPackage)) {
                Files.move(backupPackage, storedPackage);
            }
            throw exception;
        }
        try {
            deleteRecursively(backupDirectory);
            Files.deleteIfExists(backupPackage);
        } catch (IOException exception) {
            // The installation is committed; a stale backup is safer than rolling it back inconsistently.
        }
    }

    private void extractPackage(Path packageFile, Path targetDirectory) throws IOException {
        int entryCount = 0;
        long extractedSize = 0L;
        byte[] buffer = new byte[8192];
        try (InputStream inputStream = Files.newInputStream(packageFile);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    throw new IllegalArgumentException("The skin package contains too many files");
                }
                Path destination = targetDirectory.resolve(entry.getName()).normalize();
                if (!destination.startsWith(targetDirectory)) {
                    throw new IllegalArgumentException("The skin package contains an unsafe path: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                    continue;
                }
                Files.createDirectories(destination.getParent());
                try (OutputStream outputStream = Files.newOutputStream(destination)) {
                    int read;
                    while ((read = zipInputStream.read(buffer)) >= 0) {
                        extractedSize += read;
                        if (extractedSize > MAX_UNCOMPRESSED_SIZE) {
                            throw new IllegalArgumentException("The unpacked skin is too large");
                        }
                        outputStream.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    private void storePackageCopy(Path packageFile, UUID skinUid) throws IOException {
        Path packagesDirectory = repository.getExternalSkinsDirectory().resolve("_packages");
        Files.createDirectories(packagesDirectory);
        Path storedPackage = packagesDirectory.resolve(skinUid + ".tsbs");
        if (!packageFile.toAbsolutePath().normalize().equals(storedPackage.toAbsolutePath().normalize())) {
            Files.copy(packageFile, storedPackage, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void applyPendingOperations() {
        Path pendingRoot = pendingRoot();
        Path deletionsDirectory = pendingRoot.resolve("deletions");
        if (Files.isDirectory(deletionsDirectory)) {
            try (var markers = Files.list(deletionsDirectory)) {
                for (Path marker : markers.filter(Files::isRegularFile).toList()) {
                    try {
                        UUID skinUid = UUID.fromString(marker.getFileName().toString());
                        deleteRecursively(repository.getExternalSkinsDirectory().resolve(skinUid.toString()));
                        Files.deleteIfExists(repository.getExternalSkinsDirectory().resolve("_packages").resolve(skinUid + ".tsbs"));
                        Files.deleteIfExists(marker);
                    } catch (IOException | IllegalArgumentException exception) {
                        // Keep the marker and retry before UI resources are loaded on the next start.
                    }
                }
            } catch (IOException exception) {
                // Pending operations are best-effort and remain available for the next start.
            }
        }

        Path replacementsDirectory = pendingRoot.resolve("replacements");
        if (Files.isDirectory(replacementsDirectory)) {
            try (var replacements = Files.list(replacementsDirectory)) {
                for (Path replacement : replacements.filter(Files::isDirectory).toList()) {
                    try {
                        UUID skinUid = UUID.fromString(replacement.getFileName().toString());
                        Path packageFile = pendingRoot.resolve("packages").resolve(skinUid + ".tsbs");
                        replaceInstallation(
                                replacement,
                                repository.getExternalSkinsDirectory().resolve(skinUid.toString()),
                                packageFile,
                                skinUid
                        );
                        Files.deleteIfExists(packageFile);
                    } catch (IOException | IllegalArgumentException exception) {
                        // Keep the replacement and retry before UI resources are loaded on the next start.
                    }
                }
            } catch (IOException exception) {
                // Pending operations are best-effort and remain available for the next start.
            }
        }
        try {
            deleteIfEmpty(pendingRoot.resolve("deletions"));
            deleteIfEmpty(pendingRoot.resolve("replacements"));
            deleteIfEmpty(pendingRoot.resolve("packages"));
            deleteIfEmpty(pendingRoot);
        } catch (IOException exception) {
            // Empty maintenance directories are harmless.
        }
    }

    private Path pendingRoot() {
        return repository.getExternalSkinsDirectory().resolve(PENDING_DIRECTORY);
    }

    private void deleteIfEmpty(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var entries = Files.list(directory)) {
            if (entries.findAny().isEmpty()) {
                Files.deleteIfExists(directory);
            }
        }
    }

    private void requirePackageExtension(Path packageFile) {
        if (packageFile == null || !packageFile.getFileName().toString().toLowerCase().endsWith(".tsbs")) {
            throw new IllegalArgumentException("A .tsbs skin package is required");
        }
    }

    private void deleteRecursively(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            List<Path> pathsToDelete = paths.sorted(Comparator.reverseOrder()).toList();
            for (Path path : pathsToDelete) {
                Files.deleteIfExists(path);
            }
        }
    }
}
