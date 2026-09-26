package app.packages;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class PackageExtraction {
    private PackageExtraction() { }

    /** Both package formats share traversal, duplicate and expanded-byte limits. Extraction targets are fresh private
     * staging directories; no symlink entries are created and existing files are never followed/overwritten. Limits
     * count streamed bytes, not untrusted ZIP headers. Interruption is safe before the install transaction starts. */
    public static void extract(Path archive, Path target, int maximumEntries, long maximumBytes) throws IOException {
        Path root = target.toAbsolutePath().normalize();
        Set<Path> seen = new HashSet<>();
        int entries = 0;
        long size = 0L;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Package extraction cancelled");
                if (++entries > maximumEntries) throw new IllegalArgumentException("The package contains too many files");
                String name = entry.getName().replace('\\', '/');
                Path destination = root.resolve(name).normalize();
                if (!destination.startsWith(root) || destination.equals(root) || name.contains(":") || !seen.add(destination)) {
                    throw new IllegalArgumentException("The package contains an unsafe path or duplicate entry: " + name);
                }
                for (Path parent = destination; parent != null && parent.startsWith(root); parent = parent.getParent()) {
                    if (Files.isSymbolicLink(parent)) throw new IllegalArgumentException("The package contains a symbolic link");
                }
                if (entry.isDirectory()) { Files.createDirectories(destination); continue; }
                Files.createDirectories(destination.getParent());
                try (OutputStream output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Package extraction cancelled");
                        size += read;
                        if (size > maximumBytes) throw new IllegalArgumentException("The unpacked package is too large");
                        output.write(buffer, 0, read);
                    }
                }
            }
        }
    }
    /** Manifest discovery must bound skipped entries too: ZipInputStream.getNextEntry otherwise drains arbitrary
     * expanded data before reaching the manifest. This reader never trusts entry-size headers for either budget. */
    public static byte[] readEntry(Path archive, String name, int maximumEntries, long maximumBytes, int entryLimit) throws IOException {
        byte[] buffer = new byte[8192];
        int entries = 0;
        long size = 0L;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > maximumEntries) throw new IllegalArgumentException("The package contains too many files");
                boolean selected = !entry.isDirectory() && name.equals(entry.getName());
                ByteArrayOutputStream result = selected ? new ByteArrayOutputStream() : null;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Package inspection cancelled");
                    size += read;
                    if (size > maximumBytes) throw new IllegalArgumentException("The unpacked package is too large");
                    if (selected) {
                        if (result.size() + read > entryLimit) throw new IllegalArgumentException("The package manifest is too large");
                        result.write(buffer, 0, read);
                    }
                }
                if (selected) return result.toByteArray();
            }
        }
        throw new IllegalArgumentException("The package must contain " + name + " at its root");
    }

}
