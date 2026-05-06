package app.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TestDirectorySupport {
    private static final Path BASE_DIRECTORY = Path.of("build", "test-work");

    private TestDirectorySupport() {
    }

    public static Path createTempDirectory(String prefix) throws IOException {
        Files.createDirectories(BASE_DIRECTORY);
        return Files.createTempDirectory(BASE_DIRECTORY, prefix);
    }
}
