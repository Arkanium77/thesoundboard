package app.persistence;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class AtomicFiles {
    private AtomicFiles() { }

    /** Force staged bytes before promotion. On filesystems without atomic replacement, retain a separate rollback
     * copy until promotion succeeds. This protects against reported move failures; Java cannot portably force the
     * parent directory on Windows, so abrupt power loss still depends on filesystem durability guarantees. */
    public static void replace(Path staged, Path target) throws IOException {
        try (FileChannel channel = FileChannel.open(staged, StandardOpenOption.WRITE)) { channel.force(true); }
        try {
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Path backup = null;
            if (Files.exists(target)) {
                backup = Files.createTempFile(target.toAbsolutePath().getParent(), ".rollback-", ".tmp");
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException failure) {
                if (backup != null) {
                    try { Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING); }
                    catch (IOException restoreFailure) { failure.addSuppressed(restoreFailure); throw failure; }
                }
                throw failure;
            }
            if (backup != null) Files.deleteIfExists(backup);
        }
    }
}
