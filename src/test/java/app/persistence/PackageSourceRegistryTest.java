package app.persistence;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class PackageSourceRegistryTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void remembersMissingSourceAndDetectsWhenItReappears() throws IOException {
        UUID uid = UUID.fromString("e30ed037-5703-45dd-95c7-4dbe1d196c93");
        Path source = temporaryDirectory.resolve("packages").resolve("skin.tsbs");
        PackageSourceRegistry registry = new PackageSourceRegistry(temporaryDirectory.resolve("installed"));

        registry.remember(uid, source);

        Assertions.assertThat(registry.find(uid)).contains(source.toAbsolutePath().normalize());
        Assertions.assertThat(registry.isAvailable(uid)).isFalse();

        Files.createDirectories(source.getParent());
        Files.writeString(source, "package");

        Assertions.assertThat(registry.isAvailable(uid)).isTrue();

        registry.forget(uid);

        Assertions.assertThat(registry.find(uid)).isEmpty();
        Assertions.assertThat(temporaryDirectory.resolve("installed").resolve("_sources")).doesNotExist();
    }

    @Test
    void prefersManualSourceAndFallsBackThroughOrderedDiscoveredSources() throws IOException {
        UUID uid = UUID.fromString("e30ed037-5703-45dd-95c7-4dbe1d196c93");
        Path manualSource = temporaryDirectory.resolve("manual.tsbs");
        Path firstDiscoveredSource = temporaryDirectory.resolve("first").resolve("skin.tsbs");
        Path secondDiscoveredSource = temporaryDirectory.resolve("second").resolve("skin.tsbs");
        Files.writeString(manualSource, "manual");
        Files.createDirectories(firstDiscoveredSource.getParent());
        Files.createDirectories(secondDiscoveredSource.getParent());
        Files.writeString(firstDiscoveredSource, "first");
        Files.writeString(secondDiscoveredSource, "second");
        PackageSourceRegistry registry = new PackageSourceRegistry(temporaryDirectory.resolve("installed"));
        registry.remember(uid, manualSource);
        registry.synchronizeDiscovered(Map.of(uid, List.of(firstDiscoveredSource, secondDiscoveredSource)));

        Assertions.assertThat(registry.findAvailable(uid)).contains(manualSource.toAbsolutePath().normalize());
        Assertions.assertThat(registry.isDiscovered(uid)).isTrue();

        Files.delete(manualSource);

        Assertions.assertThat(registry.findAvailable(uid)).contains(firstDiscoveredSource.toAbsolutePath().normalize());

        Files.delete(firstDiscoveredSource);

        Assertions.assertThat(registry.findAvailable(uid)).contains(secondDiscoveredSource.toAbsolutePath().normalize());

        Files.delete(secondDiscoveredSource);

        Assertions.assertThat(registry.isDiscovered(uid)).isFalse();
    }

    @Test
    void forgetsDiscoveredSourcesWhenSynchronizationFolderIsUnlinked() throws IOException {
        UUID uid = UUID.fromString("e30ed037-5703-45dd-95c7-4dbe1d196c93");
        Path discoveredSource = temporaryDirectory.resolve("linked").resolve("skin.tsbs");
        Files.createDirectories(discoveredSource.getParent());
        Files.writeString(discoveredSource, "package");
        PackageSourceRegistry registry = new PackageSourceRegistry(temporaryDirectory.resolve("installed"));
        registry.synchronizeDiscovered(Map.of(uid, List.of(discoveredSource)));

        registry.synchronizeDiscovered(Map.of());

        Assertions.assertThat(registry.findAvailable(uid)).isEmpty();
        Assertions.assertThat(discoveredSource).exists();
    }

    @Test
    void keepsSourcesForPackageVersionsSeparate() throws IOException {
        UUID uid = UUID.fromString("e30ed037-5703-45dd-95c7-4dbe1d196c93");
        Path firstSource = temporaryDirectory.resolve("one.tsbs");
        Path secondSource = temporaryDirectory.resolve("two.tsbs");
        Files.writeString(firstSource, "one");
        Files.writeString(secondSource, "two");
        PackageSourceRegistry registry = new PackageSourceRegistry(temporaryDirectory.resolve("installed"));

        registry.remember(uid, 1, firstSource);
        registry.remember(uid, 2, secondSource);

        Assertions.assertThat(registry.findAvailableSources(uid, 1)).containsExactly(firstSource.toAbsolutePath());
        Assertions.assertThat(registry.findAvailableSources(uid, 2)).containsExactly(secondSource.toAbsolutePath());

        registry.forget(uid, 1);

        Assertions.assertThat(registry.findAvailableSources(uid, 1)).isEmpty();
        Assertions.assertThat(registry.findAvailableSources(uid, 2)).containsExactly(secondSource.toAbsolutePath());
    }
}
