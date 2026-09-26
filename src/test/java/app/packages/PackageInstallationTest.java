package app.packages;

import app.support.TestDirectorySupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class PackageInstallationTest {
    @Test
    void restoresBothOldCopiesWhenNewDirectoryCannotBeMoved() throws IOException {
        Path root = TestDirectorySupport.createTempDirectory("package-rollback-");
        Path target = Files.createDirectory(root.resolve("installed"));
        Files.writeString(target.resolve("content"), "old content");
        Path stored = Files.writeString(root.resolve("stored.zip"), "old archive");
        Path source = Files.writeString(root.resolve("new.zip"), "new archive");

        Assertions.assertThatThrownBy(() -> PackageInstallation.replace(root.resolve("missing"), target, source, stored))
                .isInstanceOf(IOException.class);

        Assertions.assertThat(target.resolve("content")).content().isEqualTo("old content");
        Assertions.assertThat(stored).content().isEqualTo("old archive");
    }

    @Test
    void keepsInstallationWhenSourceCannotBeStaged() throws IOException {
        Path root = TestDirectorySupport.createTempDirectory("package-source-failure-");
        Path target = Files.createDirectory(root.resolve("installed"));
        Files.writeString(target.resolve("content"), "old content");
        Path stored = Files.writeString(root.resolve("stored.zip"), "old archive");
        Path extracted = Files.createDirectory(root.resolve("new"));

        Assertions.assertThatThrownBy(() -> PackageInstallation.replace(extracted, target, root.resolve("missing"), stored))
                .isInstanceOf(IOException.class);

        Assertions.assertThat(target.resolve("content")).content().isEqualTo("old content");
        Assertions.assertThat(stored).content().isEqualTo("old archive");
        Assertions.assertThat(extracted).isDirectory();
    }

    @Test
    void canReplaceFromTheStoredArchiveItself() throws IOException {
        Path root = TestDirectorySupport.createTempDirectory("package-self-source-");
        Path target = Files.createDirectory(root.resolve("installed"));
        Files.writeString(target.resolve("content"), "old content");
        Path extracted = Files.createDirectory(root.resolve("new"));
        Files.writeString(extracted.resolve("content"), "new content");
        Path stored = Files.writeString(root.resolve("stored.zip"), "source archive");

        PackageInstallation.replace(extracted, target, stored, stored);

        Assertions.assertThat(target.resolve("content")).content().isEqualTo("new content");
        Assertions.assertThat(stored).content().isEqualTo("source archive");
    }
}
