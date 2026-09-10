package app.packages;

import app.persistence.AtomicFiles;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class PackageArchiveWriter {
    private PackageArchiveWriter() { }

    /** Build beside the destination and promote only a complete archive. A read failure, cancellation or import-limit
     * violation must leave an existing export untouched. Type-specific validation remains with the installer; the
     * same expanded-byte and entry limits ensure exports can be imported by that package format. */
    public static void write(Path source, Path target, int maximumEntries, long maximumBytes) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path staged = Files.createTempFile(target.toAbsolutePath().getParent(), ".package-", ".tmp");
        byte[] buffer = new byte[8192];
        long size = 0L;
        int entries = 0;
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(staged)); var paths = Files.walk(source)) {
                for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                    if (Files.isSymbolicLink(path)) continue;
                    if (++entries > maximumEntries) throw new IllegalArgumentException("The package contains too many files");
                    zip.putNextEntry(new ZipEntry(source.relativize(path).toString().replace('\\', '/')));
                    try (InputStream input = Files.newInputStream(path)) {
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Package export cancelled");
                            size += read;
                            if (size > maximumBytes) throw new IllegalArgumentException("The unpacked package is too large");
                            zip.write(buffer, 0, read);
                        }
                    }
                    zip.closeEntry();
                }
            }
            AtomicFiles.replace(staged, target);
        } finally { Files.deleteIfExists(staged); }
    }
}
