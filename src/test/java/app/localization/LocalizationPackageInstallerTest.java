package app.localization;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;

class LocalizationPackageInstallerTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void discoveryKeepsInstalledEditsUntilExplicitUpdate() throws IOException {
        Path sources = Files.createDirectories(temporaryDirectory.resolve("sources"));
        LocalizationRepository repository = new LocalizationRepository(temporaryDirectory.resolve("installed"));
        LocalizationPackageInstaller installer = new LocalizationPackageInstaller(repository);
        Path archive = sources.resolve("Russian.tsbl");
        installer.createPackage(Path.of("examples", "localizations", "Russian", "source"), archive);
        LocalizationDescriptor installed = installer.install(archive);
        Path strings = installed.directory().resolve(installed.manifest().getStrings());
        String original = Files.readString(strings);
        FileTime timestamp = FileTime.fromMillis(1_000_000L);
        Files.setLastModifiedTime(strings, timestamp);
        List<LocalizationDescriptor> snapshot = repository.findAll();

        installer.installAvailablePackages(List.of(sources));
        installer.update(installed);
        Assertions.assertThat(Files.getLastModifiedTime(strings)).isEqualTo(timestamp);
        Assertions.assertThat(repository.findAll()).isSameAs(snapshot);

        Files.writeString(strings, "main.save: edited locally");
        installer.installAvailablePackages(List.of(sources));
        Assertions.assertThat(strings).content().isEqualTo("main.save: edited locally");
        installer.update(installed);
        Assertions.assertThat(strings).content().isEqualTo(original);
        Assertions.assertThat(repository.findAll()).isNotSameAs(snapshot);
        installer.delete(repository.findSelected(installed.manifest().getUid(), installed.manifest().getVersion()));
        Assertions.assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void exampleLocalizationsCanBePackagedInstalledAndReplaced() throws IOException {
        LocalizationRepository repository = new LocalizationRepository(temporaryDirectory.resolve("installed"));
        LocalizationPackageInstaller installer = new LocalizationPackageInstaller(repository);
        Path russianPackage = temporaryDirectory.resolve("Russian.tsbl");
        Path imperialPackage = temporaryDirectory.resolve("Imperial.tsbl");

        installer.createPackage(Path.of("examples", "localizations", "Russian", "source"), russianPackage);
        installer.createPackage(Path.of("examples", "localizations", "Pre-Revolutionary Russian", "source"), imperialPackage);
        LocalizationDescriptor russian = installer.install(russianPackage);
        LocalizationDescriptor imperial = installer.install(imperialPackage);

        Assertions.assertThat(russian.strings()).containsEntry("main.save", "Сохранить");
        Assertions.assertThat(imperial.strings()).containsEntry("main.save", "Сберечь");
        Assertions.assertThat(repository.findAll()).hasSize(3);
        Assertions.assertThatThrownBy(() -> installer.install(russianPackage))
                .isInstanceOf(LocalizationAlreadyInstalledException.class);
        Assertions.assertThat(installer.install(russianPackage, true).manifest().getName()).isEqualTo("🇷🇺 Русский");
    }

    @Test
    void exportsCompleteEnglishTemplate() throws IOException {
        LocalizationPackageInstaller installer = new LocalizationPackageInstaller(
                new LocalizationRepository(temporaryDirectory.resolve("installed"))
        );
        Path target = temporaryDirectory.resolve("strings.yml");

        installer.exportEnglishStrings(target);

        Assertions.assertThat(target).content().contains("main.open_folder", "Open Folder", "dialog.restart.title");
    }

    @Test
    void updatesSourceAndPackageWithoutChangingTranslationsOrObsoleteStrings() throws IOException {
        Path source = temporaryDirectory.resolve("old-source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("localization.yml"), """
                uid: 2195946d-8797-4f48-a81b-a2a12d1c4a30
                name: Old Russian
                languageTag: ru
                localizationVersion: 1
                strings: strings.yml
                """);
        Files.writeString(source.resolve("strings.yml"), """
                main.save: Сберечь перевод
                removed.old.button: Оставить как есть
                """);
        LocalizationPackageInstaller installer = new LocalizationPackageInstaller(
                new LocalizationRepository(temporaryDirectory.resolve("installed"))
        );

        int sourceAdditions = installer.updateSource(source);

        Assertions.assertThat(sourceAdditions).isGreaterThan(0);
        Assertions.assertThat(source.resolve("strings.yml")).content()
                .contains("main.save", "Сберечь перевод", "removed.old.button", "Оставить как есть", "main.open_folder");

        Files.writeString(source.resolve("strings.yml"), """
                main.save: Пакетный перевод
                removed.package.string: Не удалять
                """);
        Path packageFile = temporaryDirectory.resolve("old.tsbl");
        installer.createPackage(source, packageFile);

        int packageAdditions = installer.updatePackage(packageFile);
        String packagedStrings = readPackageEntry(packageFile, "strings.yml");

        Assertions.assertThat(packageAdditions).isGreaterThan(0);
        Assertions.assertThat(packagedStrings)
                .contains("main.save", "Пакетный перевод", "removed.package.string", "Не удалять", "main.open_folder");
    }

    @Test
    void replacesInstalledLocalizationFromRememberedPackage() throws IOException {
        Path source = temporaryDirectory.resolve("source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("localization.yml"), """
                uid: 2195946d-8797-4f48-a81b-a2a12d1c4a30
                name: Remembered
                languageTag: ru
                localizationVersion: 1
                strings: strings.yml
                """);
        Files.writeString(source.resolve("strings.yml"), "main.save: Первый");
        Path packageFile = temporaryDirectory.resolve("packages").resolve("remembered.tsbl");
        Files.createDirectories(packageFile.getParent());
        LocalizationRepository repository = new LocalizationRepository(temporaryDirectory.resolve("installed"));
        LocalizationPackageInstaller installer = new LocalizationPackageInstaller(repository);
        installer.createPackage(source, packageFile);
        LocalizationDescriptor installed = installer.install(packageFile);

        Files.delete(packageFile);

        Assertions.assertThat(installer.canUpdate(installed)).isFalse();

        Files.writeString(source.resolve("strings.yml"), "main.save: Второй");
        installer.createPackage(source, packageFile);
        LocalizationDescriptor updated = installer.update(installed);

        Assertions.assertThat(installer.canUpdate(updated)).isTrue();
        Assertions.assertThat(updated.strings()).containsEntry("main.save", "Второй");

        Files.delete(packageFile);
        installer.delete(updated);

        Assertions.assertThat(repository.getDirectory()
                        .resolve("_sources")
                        .resolve("2195946d-8797-4f48-a81b-a2a12d1c4a30.path"))
                .doesNotExist();
    }

    @Test
    void keepsVersionsOfSameLocalizationInstalledSeparately() throws IOException {
        Path source = temporaryDirectory.resolve("versioned-source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("localization.yml"), """
                uid: 2195946d-8797-4f48-a81b-a2a12d1c4a30
                name: Versioned
                languageTag: en-x-versioned
                localizationVersion: 1
                version: 1
                strings: strings.yml
                """);
        Files.writeString(source.resolve("strings.yml"), "main.save: One");
        Path firstPackage = temporaryDirectory.resolve("version-one.tsbl");
        Path secondPackage = temporaryDirectory.resolve("version-two.tsbl");
        LocalizationRepository repository = new LocalizationRepository(temporaryDirectory.resolve("installed"));
        LocalizationPackageInstaller installer = new LocalizationPackageInstaller(repository);
        installer.createPackage(source, firstPackage);
        installer.install(firstPackage);
        Files.writeString(source.resolve("localization.yml"), Files.readString(source.resolve("localization.yml"))
                .replace("version: 1", "version: 2"));
        Files.writeString(source.resolve("strings.yml"), "main.save: Two");
        installer.createPackage(source, secondPackage);
        installer.install(secondPackage);

        Assertions.assertThat(repository.findAll().stream()
                        .filter(item -> item.manifest().getUid().toString().startsWith("2195946d")))
                .extracting(item -> item.manifest().getVersion())
                .containsExactlyInAnyOrder(1, 2);
        Assertions.assertThat(repository.getDirectory().resolve("2195946d-8797-4f48-a81b-a2a12d1c4a30-v2"))
                .isDirectory();
        LocalizationDescriptor first = repository.findSelected(
                UUID.fromString("2195946d-8797-4f48-a81b-a2a12d1c4a30"), 1);
        installer.delete(first);
        installer.installAvailablePackages(List.of(temporaryDirectory));

        Assertions.assertThat(firstPackage).isRegularFile();
        Assertions.assertThat(repository.findAll().stream()
                .filter(item -> item.manifest().getUid().equals(first.manifest().getUid())))
                .extracting(item -> item.manifest().getVersion())
                .containsExactly(2);
        Files.writeString(repository.getDirectory().resolve("_disabled")
                .resolve("2195946d-8797-4f48-a81b-a2a12d1c4a30"), "disabled");
        installer.installAvailablePackages(List.of(temporaryDirectory));

        Assertions.assertThat(repository.findAll().stream()
                        .filter(item -> item.manifest().getUid().equals(first.manifest().getUid())))
                .extracting(item -> item.manifest().getVersion())
                .containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void protectsBundledLocalizationSourceFromDeletion() throws IOException {
        Path protectedDirectory = temporaryDirectory.resolve("assets").resolve("localization");
        Files.createDirectories(protectedDirectory);
        Path packageFile = protectedDirectory.resolve("Russian.tsbl");
        LocalizationRepository repository = new LocalizationRepository(temporaryDirectory.resolve("installed"));
        LocalizationPackageInstaller installer = new LocalizationPackageInstaller(repository, List.of(protectedDirectory));
        installer.createPackage(Path.of("examples", "localizations", "Russian", "source"), packageFile);
        LocalizationDescriptor installed = installer.install(packageFile);

        Assertions.assertThatThrownBy(() -> installer.deleteSourceFile(installed, packageFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("protected");
        Assertions.assertThat(packageFile).isRegularFile();
        Assertions.assertThat(installed.directory()).isDirectory();
    }

    @Test
    void discoversLocalizationsFromMultipleOrderedSourceDirectories() throws IOException {
        Path editableSource = temporaryDirectory.resolve("editable");
        Files.createDirectories(editableSource);
        Files.writeString(editableSource.resolve("localization.yml"), """
                uid: 2195946d-8797-4f48-a81b-a2a12d1c4a30
                name: Discovered
                languageTag: ru
                localizationVersion: 1
                strings: strings.yml
                """);
        Path firstSource = temporaryDirectory.resolve("first-source");
        Path secondSource = temporaryDirectory.resolve("second-source");
        Files.createDirectories(firstSource);
        Files.createDirectories(secondSource);
        LocalizationRepository repository = new LocalizationRepository(temporaryDirectory.resolve("installed"));
        LocalizationPackageInstaller installer = new LocalizationPackageInstaller(repository);
        Files.writeString(editableSource.resolve("strings.yml"), "main.save: Первый источник");
        installer.createPackage(editableSource, firstSource.resolve("Russian.tsbl"));
        Files.writeString(editableSource.resolve("strings.yml"), "main.save: Второй источник");
        installer.createPackage(editableSource, secondSource.resolve("Russian.tsbl"));

        installer.installAvailablePackages(List.of(firstSource, secondSource));

        LocalizationDescriptor discovered = repository.findSelected(
                UUID.fromString("2195946d-8797-4f48-a81b-a2a12d1c4a30")
        );
        Assertions.assertThat(discovered.strings()).containsEntry("main.save", "Первый источник");
        Assertions.assertThat(installer.isDiscovered(discovered)).isTrue();

        installer.deleteSourceFile(discovered, firstSource.resolve("Russian.tsbl"));
        Assertions.assertThat(discovered.directory()).isDirectory();
        Assertions.assertThat(installer.canUpdate(discovered)).isTrue();
        LocalizationDescriptor updatedFromFallback = installer.update(discovered);

        Assertions.assertThat(updatedFromFallback.strings()).containsEntry("main.save", "Второй источник");

        installer.deleteSourceFile(updatedFromFallback, secondSource.resolve("Russian.tsbl"));

        Assertions.assertThat(secondSource.resolve("Russian.tsbl")).doesNotExist();
        Assertions.assertThat(updatedFromFallback.directory()).isDirectory();
        Assertions.assertThat(installer.canUpdate(updatedFromFallback)).isFalse();
    }

    private String readPackageEntry(Path packageFile, String entryName) throws IOException {
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(packageFile))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    return new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IllegalArgumentException("Missing package entry: " + entryName);
    }
}
