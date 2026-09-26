package app.packages;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class PackageDiscoveryTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void preservesDirectoryPriorityAndSortedFilesWhileSkippingBrokenArchives() throws IOException {
        Path first = Files.createDirectory(temporaryDirectory.resolve("z-first"));
        Path second = Files.createDirectory(temporaryDirectory.resolve("a-second"));
        Path z = Files.writeString(first.resolve("z.pkg"), "valid");
        Path bad = Files.writeString(first.resolve("a.pkg"), "invalid");
        Path a = Files.writeString(second.resolve("a.pkg"), "valid");
        Files.writeString(first.resolve("ignored.txt"), "ignored");
        Files.createDirectory(first.resolve("directory.pkg"));
        List<Path> visited = new ArrayList<>();
        PackageDiscovery.visit(List.of(first, temporaryDirectory.resolve("absent"), second),
                path -> path.toString().endsWith(".pkg"), path -> {
                    visited.add(path);
                    if (path.equals(bad)) throw new IOException("Invalid archive");
                });
        Assertions.assertThat(visited).containsExactly(bad, z, a);
    }
}
