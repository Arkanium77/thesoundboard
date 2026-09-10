package app.packages;

import app.support.TestDirectorySupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class PackageArchiveWriterTest {
    @Test
    void failedExportPreservesThePreviousDestination() throws IOException {
        Path root = TestDirectorySupport.createTempDirectory("archive-export-");
        Path source = Files.createDirectory(root.resolve("source"));
        Files.writeString(source.resolve("one"), "first");
        Files.writeString(source.resolve("two"), "second");
        Path target = Files.writeString(root.resolve("package.zip"), "previous export");
        Assertions.assertThatThrownBy(() -> PackageArchiveWriter.write(source, target, 1, 1024))
                .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThat(target).content().isEqualTo("previous export");
        PackageArchiveWriter.write(source, target, 2, 1024);
        Assertions.assertThat(PackageExtraction.readEntry(target, "two", 2, 1024, 128)).isEqualTo("second".getBytes());
    }
}
