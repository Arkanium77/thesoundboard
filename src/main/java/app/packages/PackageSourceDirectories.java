package app.packages;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

public record PackageSourceDirectories(
        List<Path> skinDirectories,
        List<Path> localizationDirectories
) {
    private static final String JPACKAGE_APP_PATH = "jpackage.app-path";

    public PackageSourceDirectories {
        skinDirectories = List.copyOf(skinDirectories);
        localizationDirectories = List.copyOf(localizationDirectories);
    }

    public static PackageSourceDirectories discover() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path applicationDirectory = resolveApplicationDirectory(
                System.getProperty(JPACKAGE_APP_PATH),
                workingDirectory
        );
        return fromApplicationDirectory(applicationDirectory);
    }

    public static PackageSourceDirectories fromApplicationDirectory(Path applicationDirectory) {
        Path assetsDirectory = applicationDirectory.resolve("assets");
        return new PackageSourceDirectories(
                List.of(assetsDirectory.resolve("skins")),
                List.of(assetsDirectory.resolve("localization"))
        );
    }

    public static PackageSourceDirectories empty() {
        return new PackageSourceDirectories(List.of(), List.of());
    }

    static Path resolveApplicationDirectory(String jpackageAppPath, Path workingDirectory) {
        if (jpackageAppPath == null || jpackageAppPath.isBlank()) {
            return workingDirectory.toAbsolutePath().normalize();
        }
        try {
            Path launcher = Path.of(jpackageAppPath).toAbsolutePath().normalize();
            Path applicationDirectory = launcher.getParent();
            if (applicationDirectory != null
                    && "MacOS".equals(applicationDirectory.getFileName().toString())
                    && applicationDirectory.getParent() != null
                    && "Contents".equals(applicationDirectory.getParent().getFileName().toString())) {
                applicationDirectory = applicationDirectory.getParent();
            }
            return applicationDirectory == null
                    ? workingDirectory.toAbsolutePath().normalize()
                    : applicationDirectory;
        } catch (InvalidPathException exception) {
            return workingDirectory.toAbsolutePath().normalize();
        }
    }
}
