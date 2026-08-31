package app.packages;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class PackageSourceDirectoriesTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void resolvesAssetsBesidePackagedApplicationLauncher() {
        Path applicationDirectory = temporaryDirectory.resolve("TheSoundboard");
        Path launcher = applicationDirectory.resolve("TheSoundboard.exe");

        Path resolved = PackageSourceDirectories.resolveApplicationDirectory(
                launcher.toString(),
                temporaryDirectory.resolve("working")
        );
        PackageSourceDirectories sources = PackageSourceDirectories.fromApplicationDirectory(resolved);

        Assertions.assertThat(resolved).isEqualTo(applicationDirectory.toAbsolutePath().normalize());
        Assertions.assertThat(sources.skinDirectories())
                .containsExactly(applicationDirectory.toAbsolutePath().normalize().resolve("assets/skins"));
        Assertions.assertThat(sources.localizationDirectories())
                .containsExactly(applicationDirectory.toAbsolutePath().normalize().resolve("assets/localization"));
    }

    @Test
    void resolvesAssetsInsideMacosContentsDirectory() {
        Path contentsDirectory = temporaryDirectory.resolve("TheSoundboard.app").resolve("Contents");
        Path launcher = contentsDirectory.resolve("MacOS").resolve("TheSoundboard");

        Path resolved = PackageSourceDirectories.resolveApplicationDirectory(
                launcher.toString(),
                temporaryDirectory.resolve("working")
        );

        Assertions.assertThat(resolved).isEqualTo(contentsDirectory.toAbsolutePath().normalize());
    }

    @Test
    void resolvesAssetsAboveLinuxBinDirectory() {
        Path applicationDirectory = temporaryDirectory.resolve("TheSoundboard");
        Path launcher = applicationDirectory.resolve("bin").resolve("TheSoundboard");

        Path resolved = PackageSourceDirectories.resolveApplicationDirectory(
                launcher.toString(), temporaryDirectory.resolve("working"));
        PackageSourceDirectories sources = PackageSourceDirectories.fromApplicationDirectory(resolved);

        Assertions.assertThat(resolved).isEqualTo(applicationDirectory.toAbsolutePath().normalize());
        Assertions.assertThat(sources.skinDirectories())
                .containsExactly(applicationDirectory.toAbsolutePath().normalize().resolve("assets/skins"));
        Assertions.assertThat(sources.localizationDirectories())
                .containsExactly(applicationDirectory.toAbsolutePath().normalize().resolve("assets/localization"));
    }

    @Test
    void fallsBackToWorkingDirectoryOutsidePackagedApplication() {
        Path workingDirectory = temporaryDirectory.resolve("working");

        Path resolved = PackageSourceDirectories.resolveApplicationDirectory("", workingDirectory);

        Assertions.assertThat(resolved).isEqualTo(workingDirectory.toAbsolutePath().normalize());
    }
}
