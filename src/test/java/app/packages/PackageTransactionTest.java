package app.packages;

import app.support.TestDirectorySupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class PackageTransactionTest {
    @Test
    void startupRollsBackInterruptedReplacementOfBothCopies() throws IOException {
        Path root = TestDirectorySupport.createTempDirectory("interrupted-install-");
        Path target = Files.createDirectory(root.resolve("installed"));
        Files.writeString(target.resolve("content"), "old");
        Path archive = Files.writeString(root.resolve("stored.zip"), "old archive");
        Path backup = root.resolve(".backup-directory-test");
        Path backupArchive = root.resolve(".backup-archive-test");
        PackageTransaction.begin(target, archive, backup, backupArchive, "test");
        Files.move(target, backup);
        Files.move(archive, backupArchive);
        Files.createDirectory(target);
        Files.writeString(target.resolve("content"), "new");
        PackageInstallation.recover(root);
        PackageInstallation.recover(root);
        Assertions.assertThat(target.resolve("content")).content().isEqualTo("old");
        Assertions.assertThat(archive).content().isEqualTo("old archive");
        Assertions.assertThat(backup).doesNotExist();
    }

    @Test
    void committedReplacementSurvivesInterruptedCleanup() throws IOException {
        Path root = TestDirectorySupport.createTempDirectory("committed-install-");
        Path target = Files.createDirectory(root.resolve("installed"));
        Files.writeString(target.resolve("content"), "old");
        Path archive = Files.writeString(root.resolve("stored.zip"), "old archive");
        Path backup = root.resolve(".backup-directory-test");
        Path backupArchive = root.resolve(".backup-archive-test");
        PackageTransaction transaction = PackageTransaction.begin(target, archive, backup, backupArchive, "test");
        Files.move(target, backup);
        Files.move(archive, backupArchive);
        Files.createDirectory(target);
        Files.writeString(target.resolve("content"), "new");
        Files.writeString(archive, "new archive");
        transaction.commit();
        PackageInstallation.recover(root);
        Assertions.assertThat(target.resolve("content")).content().isEqualTo("new");
        Assertions.assertThat(archive).content().isEqualTo("new archive");
        Assertions.assertThat(backup).doesNotExist();
    }
}
