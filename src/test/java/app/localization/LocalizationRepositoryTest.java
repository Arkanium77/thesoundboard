package app.localization;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

class LocalizationRepositoryTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void ignoresUnknownStringsAndFallsBackForMissingStrings() throws IOException {
        UUID uid = UUID.fromString("2195946d-8797-4f48-a81b-a2a12d1c4a30");
        Path source = temporaryDirectory.resolve(uid.toString());
        Files.createDirectories(source);
        Files.writeString(source.resolve("localization.yml"), """
                uid: 2195946d-8797-4f48-a81b-a2a12d1c4a30
                name: Test
                languageTag: ru
                localizationVersion: 1
                strings: strings.yml
                """);
        Files.writeString(source.resolve("strings.yml"), """
                main.save: Сберечь
                removed.old.button: Устаревшая строка
                """);
        LocalizationRepository repository = new LocalizationRepository(temporaryDirectory);

        LocalizationDescriptor descriptor = repository.findSelected(uid);

        Assertions.assertThat(descriptor.strings()).containsEntry("main.save", "Сберечь");
        Assertions.assertThat(descriptor.strings()).doesNotContainKey("removed.old.button");
        LocalizationService service = new LocalizationService(repository, new LocalizationPreferences());
        Assertions.assertThat(descriptor.strings().getOrDefault(TextKey.MAIN_OPEN_FOLDER.id(), TextKey.MAIN_OPEN_FOLDER.english()))
                .isEqualTo("Open Folder");
        Assertions.assertThat(service.getAvailable()).hasSize(2);
    }
}
