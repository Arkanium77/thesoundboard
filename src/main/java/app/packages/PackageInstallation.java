package app.packages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Replaces the unpacked cache and its source archive as one recoverable operation. The archive is staged before any
 * installed data is moved, including when the source is the stored archive itself. Rollback touches only paths this
 * invocation actually replaced; failure to move an old installation must never trigger deletion of that installation.
 * Backups are retained if rollback fails, and cleanup after commit cannot turn a successful install into a failure.
 * Callers own type-specific validation, cache invalidation and source registry updates.
 */
public final class PackageInstallation {
    private static final Logger LOGGER = LoggerFactory.getLogger(PackageInstallation.class);

    private PackageInstallation() {
    }

    public static void replace(Path extracted, Path target, Path sourceArchive, Path storedArchive) throws IOException {
        Files.createDirectories(storedArchive.getParent());
        Path stagedArchive = Files.createTempFile(storedArchive.getParent(), ".install-", ".tmp");
        String suffix = UUID.randomUUID().toString();
        Path backup = target.resolveSibling(".backup-directory-" + suffix);
        Path backupArchive = storedArchive.resolveSibling(".backup-archive-" + suffix);
        PackageTransaction transaction = null;
        boolean movedDirectory = false;
        boolean movedArchive = false;
        boolean installedDirectory = false;
        boolean installedArchive = false;
        try {
            Files.copy(sourceArchive, stagedArchive, StandardCopyOption.REPLACE_EXISTING);
            transaction = PackageTransaction.begin(target, storedArchive, backup, backupArchive, suffix);
            if (Files.exists(target)) {
                Files.move(target, backup);
                movedDirectory = true;
            }
            if (Files.exists(storedArchive)) {
                Files.move(storedArchive, backupArchive);
                movedArchive = true;
            }
            Files.move(extracted, target);
            installedDirectory = true;
            Files.move(stagedArchive, storedArchive);
            installedArchive = true;
            transaction.commit();
        } catch (IOException | RuntimeException exception) {
            try {
                if (installedDirectory) deleteDirectory(target);
                if (movedDirectory) Files.move(backup, target);
            } catch (IOException | RuntimeException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            try {
                if (installedArchive) Files.deleteIfExists(storedArchive);
                if (movedArchive) Files.move(backupArchive, storedArchive);
            } catch (IOException | RuntimeException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            if (transaction != null && exception.getSuppressed().length == 0) {
                try { transaction.forget(); } catch (IOException cleanupFailure) { exception.addSuppressed(cleanupFailure); }
            }
            throw exception;
        } finally {
            try {
                Files.deleteIfExists(stagedArchive);
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Could not remove staged package {}", stagedArchive, exception);
            }
        }
        try {
            deleteDirectory(backup);
            Files.deleteIfExists(backupArchive);
            transaction.forget();
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Committed package retains backup {}", backup, exception);
        }
    }

    public static void recover(Path directory) { PackageTransaction.recoverAll(directory); }

    public static boolean hasSameArchive(Path source, Path stored) throws IOException {
        return Files.isRegularFile(stored) && Files.mismatch(source, stored) == -1L;
    }

    /**
     * Avoids temporary extraction and disk writes for unchanged packages without trusting ZIP identity alone: users
     * can edit or remove unpacked resources independently. Stream comparisons retain that repair behavior and reject
     * added files, unsafe paths and oversized input. The limits match the largest supported package type.
     */
    public static boolean matchesDirectory(Path archive, Path directory) throws IOException {
        byte[] expected = new byte[8192];
        byte[] actual = new byte[8192];
        Set<Path> files = new HashSet<>();
        int entries = 0;
        long size = 0L;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 512) return false;
                Path target = directory.resolve(entry.getName()).normalize();
                if (!target.startsWith(directory.normalize())) return false;
                if (entry.isDirectory()) continue;
                if (!files.add(target) || !Files.isRegularFile(target) || Files.isSymbolicLink(target)) return false;
                try (InputStream input = Files.newInputStream(target)) {
                    int read;
                    while ((read = zip.read(expected)) != -1) {
                        size += read;
                        if (size > 128L * 1024L * 1024L || input.readNBytes(actual, 0, read) != read
                                || !Arrays.equals(expected, 0, read, actual, 0, read)) return false;
                    }
                    if (input.read() != -1) return false;
                }
            }
        }
        try (var paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile).count() == files.size();
        }
    }

    public static boolean isInstallationDirectory(Path path) {
        String name = path.getFileName().toString();
        return Files.isDirectory(path) && !name.startsWith(".install-") && !name.startsWith(".backup-")
                && !name.startsWith(".replacement-");
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
