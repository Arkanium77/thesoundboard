package app.packages;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class PackageSourceDirectoryRegistryTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void keepsBundledDirectoryFirstAndPersistsCustomDirectories() throws Exception {
        Path bundled = temporaryDirectory.resolve("application/assets/skins");
        Path firstCustom = temporaryDirectory.resolve("custom-one");
        Path secondCustom = temporaryDirectory.resolve("custom-two");
        Path storageFile = temporaryDirectory.resolve("user/skins/_source-directories");
        PackageSourceDirectoryRegistry registry = new PackageSourceDirectoryRegistry(storageFile, List.of(bundled));

        registry.add(firstCustom);
        registry.add(secondCustom);
        registry.add(firstCustom);

        PackageSourceDirectoryRegistry restored = new PackageSourceDirectoryRegistry(storageFile, List.of(bundled));
        Assertions.assertThat(restored.getDirectories()).containsExactly(
                bundled.toAbsolutePath().normalize(),
                firstCustom.toAbsolutePath().normalize(),
                secondCustom.toAbsolutePath().normalize()
        );
    }

    @Test
    void unlinksOnlyCustomDirectoryWithoutDeletingItsFiles() throws Exception {
        Path bundled = temporaryDirectory.resolve("application/assets/localization");
        Path custom = temporaryDirectory.resolve("custom");
        Path packageFile = custom.resolve("Example.tsbl");
        Files.createDirectories(custom);
        Files.writeString(packageFile, "package");
        PackageSourceDirectoryRegistry registry = new PackageSourceDirectoryRegistry(
                temporaryDirectory.resolve("user/localizations/_source-directories"),
                List.of(bundled)
        );
        registry.add(custom);

        registry.remove(bundled);
        registry.remove(custom);

        Assertions.assertThat(registry.getDirectories()).containsExactly(bundled.toAbsolutePath().normalize());
        Assertions.assertThat(packageFile).exists();
    }
}
