package app.localization;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;

class LocalizationPackageInstallerTest {
    @TempDir
    private Path temporaryDirectory;

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
