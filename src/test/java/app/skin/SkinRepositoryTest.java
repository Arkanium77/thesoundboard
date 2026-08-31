package app.skin;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

class SkinRepositoryTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void loadsBuiltInSkinWithAllRequiredResources() {
        SkinDescriptor skin = new SkinRepository(temporaryDirectory).findSelected(SkinRepository.DEFAULT_SKIN_UID);

        Assertions.assertThat(skin.isBuiltIn()).isTrue();
        Assertions.assertThat(skin.manifest().getVersion()).isEqualTo(2);
        Assertions.assertThat(skin.resolveResource(skin.manifest().getStylesheet())).isNotNull();
        Assertions.assertThat(skin.manifest().getFonts().getRegular()).isEqualTo(SkinManifest.SYSTEM_FONT);
        Assertions.assertThat(skin.manifest().getFonts().getBold()).isEqualTo(SkinManifest.SYSTEM_FONT);
        Assertions.assertThat(skin.manifest().getFonts().getItalic()).isEqualTo(SkinManifest.SYSTEM_FONT);
        Assertions.assertThat(skin.manifest().getFonts().getBoldItalic()).isEqualTo(SkinManifest.SYSTEM_FONT);
        Assertions.assertThat(skin.manifest().getFonts().isAllowFallback()).isTrue();
        Assertions.assertThat(skin.manifest().getIcons()).allSatisfy((name, path) ->
                Assertions.assertThat(skin.resolveResource(path)).as(name).isNotNull()
        );
    }

    @Test
    void loadsValidExternalSkinAndRejectsEscapingResourcePaths() throws IOException {
        Path skinDirectory = temporaryDirectory.resolve("midnight");
        Files.createDirectories(skinDirectory);
        Files.writeString(skinDirectory.resolve("skin.yml"), """
                uid: 86b64861-502f-4ca4-b0b4-ed19a6601eee
                name: Midnight
                skinVersion: 1
                stylesheet: skin.css
                fonts:
                  regular: regular.ttf
                  bold: bold.ttf
                  italic: italic.ttf
                """);
        Files.writeString(skinDirectory.resolve("skin.css"), "");
        Files.writeString(skinDirectory.resolve("regular.ttf"), "font");
        Files.writeString(skinDirectory.resolve("bold.ttf"), "font");
        Files.writeString(skinDirectory.resolve("italic.ttf"), "font");

        SkinDescriptor skin = new SkinRepository(temporaryDirectory)
                .findSelected(UUID.fromString("86b64861-502f-4ca4-b0b4-ed19a6601eee"));

        Assertions.assertThat(skin.manifest().getName()).isEqualTo("Midnight");
        Assertions.assertThatThrownBy(() -> skin.resolveResource("../outside.css"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ignoresExternalSkinThatAttemptsToReplaceBuiltInDefault() throws IOException {
        Path skinDirectory = temporaryDirectory.resolve("replacement");
        Files.createDirectories(skinDirectory);
        Files.writeString(skinDirectory.resolve("skin.yml"), """
                uid: 00000000-0000-0000-0000-000000000001
                name: Replacement
                skinVersion: 1
                stylesheet: skin.css
                fonts:
                  regular: regular.ttf
                  bold: bold.ttf
                  italic: italic.ttf
                """);
        Files.writeString(skinDirectory.resolve("skin.css"), "");
        Files.writeString(skinDirectory.resolve("regular.ttf"), "font");
        Files.writeString(skinDirectory.resolve("bold.ttf"), "font");
        Files.writeString(skinDirectory.resolve("italic.ttf"), "font");

        SkinRepository repository = new SkinRepository(temporaryDirectory);

        Assertions.assertThat(repository.findAll()).hasSize(1);
        Assertions.assertThat(repository.findSelected(SkinRepository.DEFAULT_SKIN_UID).isBuiltIn()).isTrue();
    }

    @Test
    void acceptsPortableSystemFontRolesWithoutFontFiles() throws IOException {
        UUID skinUid = UUID.fromString("e52124e8-bbba-4773-bc25-44301f7a60ed");
        Path skinDirectory = temporaryDirectory.resolve(skinUid.toString());
        Files.createDirectories(skinDirectory);
        Files.writeString(skinDirectory.resolve("skin.yml"), """
                uid: e52124e8-bbba-4773-bc25-44301f7a60ed
                name: System UI
                skinVersion: 1
                stylesheet: skin.css
                fonts:
                  regular: system
                  bold: system
                  italic: system
                  boldItalic: system
                  allowFallback: false
                """);
        Files.writeString(skinDirectory.resolve("skin.css"), "");

        SkinDescriptor skin = new SkinRepository(temporaryDirectory).findSelected(skinUid);

        Assertions.assertThat(skin.manifest().getName()).isEqualTo("System UI");
        Assertions.assertThat(skin.directory()).isEqualTo(skinDirectory);
        Assertions.assertThat(skin.manifest().getFonts().isAllowFallback()).isFalse();
    }
}
