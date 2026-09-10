package app.packages;

import app.persistence.AtomicFiles;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;

/** A durable intent precedes both renames. Until a commit marker is durable, startup restores the old pair;
 * after commit it only removes backups. Relative paths are confined to the package root before recovery mutates
 * anything. The journal survives failed rollback, making recovery repeatable instead of guessing backup ownership. */
final class PackageTransaction {
    private final Path root;
    private final Path journal;
    private final Properties values;

    private PackageTransaction(Path root, Path journal, Properties values) {
        this.root = root;
        this.journal = journal;
        this.values = values;
    }

    static PackageTransaction begin(Path target, Path archive, Path backup, Path backupArchive, String id) throws IOException {
        Path root = target.toAbsolutePath().getParent().normalize();
        Properties values = new Properties();
        values.setProperty("target", root.relativize(target.toAbsolutePath().normalize()).toString());
        values.setProperty("archive", root.relativize(archive.toAbsolutePath().normalize()).toString());
        values.setProperty("backup", root.relativize(backup.toAbsolutePath().normalize()).toString());
        values.setProperty("backupArchive", root.relativize(backupArchive.toAbsolutePath().normalize()).toString());
        values.setProperty("hadTarget", Boolean.toString(Files.exists(target)));
        values.setProperty("hadArchive", Boolean.toString(Files.exists(archive)));
        PackageTransaction transaction = new PackageTransaction(root, root.resolve(".transaction-" + id + ".properties"), values);
        transaction.write();
        return transaction;
    }

    void commit() throws IOException { values.setProperty("committed", "true"); write(); }
    void forget() throws IOException { Files.deleteIfExists(journal); }

    private void write() throws IOException {
        Path temporary = Files.createTempFile(root, ".transaction-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) { values.store(output, "Package replacement"); }
            AtomicFiles.replace(temporary, journal);
        } finally { Files.deleteIfExists(temporary); }
    }

    static void recoverAll(Path directory) {
        Path root = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) return;
        try (var files = Files.list(root)) {
            for (Path journal : files.filter(path -> path.getFileName().toString().startsWith(".transaction-")
                    && path.getFileName().toString().endsWith(".properties")).toList()) {
                Properties values = new Properties();
                try (InputStream input = Files.newInputStream(journal)) { values.load(input); }
                new PackageTransaction(root, journal, values).recover();
            }
        } catch (IOException exception) { throw new UncheckedIOException("Package recovery failed in " + root, exception); }
    }

    private Path path(String key) throws IOException {
        String value = values.getProperty(key);
        if (value == null) throw new IOException("Incomplete package journal");
        Path path = root.resolve(value).normalize();
        if (path.equals(root) || !path.startsWith(root)) throw new IOException("Unsafe package journal path");
        for (Path parent = path; parent != null && parent.startsWith(root); parent = parent.getParent()) {
            if (Files.isSymbolicLink(parent)) throw new IOException("Symbolic link in package journal");
        }
        return path;
    }

    private void recover() throws IOException {
        Path target = path("target");
        Path archive = path("archive");
        Path backup = path("backup");
        Path backupArchive = path("backupArchive");
        if (!Boolean.parseBoolean(values.getProperty("committed"))) {
            restore(target, backup, Boolean.parseBoolean(values.getProperty("hadTarget")));
            restore(archive, backupArchive, Boolean.parseBoolean(values.getProperty("hadArchive")));
        }
        remove(backup);
        remove(backupArchive);
        forget();
    }

    private void restore(Path target, Path backup, boolean existed) throws IOException {
        if (Files.exists(backup)) { remove(target); Files.move(backup, target); }
        else if (!existed) remove(target);
        else if (!Files.exists(target)) throw new IOException("Both package and backup are missing: " + target);
    }

    private void remove(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var files = Files.walk(path)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
        }
    }
}
