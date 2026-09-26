package app.packages;

import app.support.TestDirectorySupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class PackageExtractionTest {
    @Test
    void boundsExpandedBytesAndRejectsDuplicateNormalizedPaths() throws IOException {
        Path root = TestDirectorySupport.createTempDirectory("extraction-limits-");
        Path archive = root.resolve("large.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("content"));
            zip.write(new byte[4096]);
            zip.closeEntry();
        }
        Assertions.assertThatThrownBy(() -> PackageExtraction.extract(archive, root.resolve("large"), 10, 128))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("too large");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("content"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("./content"));
            zip.closeEntry();
        }
        Assertions.assertThatThrownBy(() -> PackageExtraction.extract(archive, root.resolve("duplicate"), 10, 128))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate");
    }
    @Test
    void manifestInspectionBoundsDataBeforeTheManifest() throws IOException {
        Path root = TestDirectorySupport.createTempDirectory("manifest-prefix-limit-");
        Path archive = root.resolve("package.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("padding"));
            zip.write(new byte[4096]);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("skin.yml"));
            zip.write(new byte[]{1});
            zip.closeEntry();
        }
        Assertions.assertThatThrownBy(() -> PackageExtraction.readEntry(archive, "skin.yml", 10, 128, 64))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("too large");
    }

}
