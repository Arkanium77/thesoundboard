package app.skin;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

class SkinPackageInstallerTest {
    private static final UUID MOON_UID = UUID.fromString("e30ed037-5703-45dd-95c7-4dbe1d196c93");
    @TempDir
    private Path temporaryDirectory;

    @Test
    void installsPackageAndPreservesUtf8EmojiName() throws IOException {
        Path skinsDirectory = temporaryDirectory.resolve("skins");
        Path packageFile = temporaryDirectory.resolve("moon.tsbs");
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("skin.yml", """
                uid: e30ed037-5703-45dd-95c7-4dbe1d196c93
                name: "🌙 Moon"
                skinVersion: 1
                stylesheet: skin.css
                fonts:
                  regular: regular.ttf
                  bold: bold.ttf
                  italic: italic.ttf
                """);
        entries.put("skin.css", ".soundboard-root { -fx-base: #222; }");
        entries.put("regular.ttf", "font");
        entries.put("bold.ttf", "font");
        entries.put("italic.ttf", "font");
        createPackage(packageFile, entries);
        SkinRepository repository = new SkinRepository(skinsDirectory);

        SkinDescriptor installed = new SkinPackageInstaller(repository).install(packageFile);

        Assertions.assertThat(installed.manifest().getName()).isEqualTo("🌙 Moon");
        Assertions.assertThat(skinsDirectory.resolve(MOON_UID + "/skin.yml")).isRegularFile();
        Assertions.assertThat(skinsDirectory.resolve("_packages").resolve(MOON_UID + ".tsbs")).isRegularFile();
    }

    @Test
    void replacesSameUidCompletelyAndCanExportAndDeleteIt() throws IOException {
        Path skinsDirectory = temporaryDirectory.resolve("skins");
        SkinRepository repository = new SkinRepository(skinsDirectory);
        SkinPackageInstaller installer = new SkinPackageInstaller(repository);
        Path firstPackage = temporaryDirectory.resolve("first.tsbs");
        Map<String, String> firstEntries = validSkinEntries("First");
        firstEntries.put("obsolete.txt", "remove me");
        createPackage(firstPackage, firstEntries);
        installer.install(firstPackage);

        Path secondPackage = temporaryDirectory.resolve("second.tsbs");
        Map<String, String> secondEntries = validSkinEntries("Second");
        secondEntries.put("new.txt", "new file");
        createPackage(secondPackage, secondEntries);

        Assertions.assertThatThrownBy(() -> installer.install(secondPackage))
                .isInstanceOf(SkinAlreadyInstalledException.class);
        SkinDescriptor replaced = installer.install(secondPackage, true);

        Assertions.assertThat(replaced.manifest().getName()).isEqualTo("Second");
        Assertions.assertThat(replaced.directory().resolve("obsolete.txt")).doesNotExist();
        Assertions.assertThat(replaced.directory().resolve("new.txt")).hasContent("new file");

        Path exportedPackage = temporaryDirectory.resolve("exported.tsbs");
        installer.export(replaced, exportedPackage);
        Assertions.assertThat(packageEntries(exportedPackage))
                .contains("skin.yml", "skin.css", "new.txt")
                .doesNotContain("obsolete.txt");

        Files.delete(secondPackage);
        installer.delete(replaced);
        Assertions.assertThat(replaced.directory()).doesNotExist();
        Assertions.assertThat(skinsDirectory.resolve("_packages").resolve(MOON_UID + ".tsbs")).doesNotExist();
    }

    @Test
    void appliesScheduledReplacementAndDeletionBeforeNextSkinLoad() throws IOException {
        Path skinsDirectory = temporaryDirectory.resolve("skins");
        SkinRepository repository = new SkinRepository(skinsDirectory);
        SkinPackageInstaller installer = new SkinPackageInstaller(repository);
        Path firstPackage = temporaryDirectory.resolve("first.tsbs");
        createPackage(firstPackage, validSkinEntries("First"));
        installer.install(firstPackage);

        Path replacementPackage = temporaryDirectory.resolve("replacement.tsbs");
        createPackage(replacementPackage, validSkinEntries("Replacement"));
        SkinManifest scheduledManifest = installer.scheduleReplacement(replacementPackage);

        Assertions.assertThat(scheduledManifest.getName()).isEqualTo("Replacement");
        Assertions.assertThat(repository.findSelected(MOON_UID).manifest().getName()).isEqualTo("First");

        SkinPackageInstaller.applyPendingOperations(repository);

        SkinDescriptor replaced = repository.findSelected(MOON_UID);
        Assertions.assertThat(replaced.manifest().getName()).isEqualTo("Replacement");

        Files.delete(replacementPackage);
        installer.scheduleDeletion(replaced);
        SkinPackageInstaller.applyPendingOperations(repository);

        Assertions.assertThat(repository.findAll())
                .singleElement()
                .satisfies(skin -> Assertions.assertThat(skin.isBuiltIn()).isTrue());
        Assertions.assertThat(skinsDirectory.resolve("_pending")).doesNotExist();
    }

    @Test
    void updatesFromRememberedPackageWhenSourceReappears() throws IOException {
        Path skinsDirectory = temporaryDirectory.resolve("skins");
        Path packageFile = temporaryDirectory.resolve("source").resolve("moon.tsbs");
        Files.createDirectories(packageFile.getParent());
        createPackage(packageFile, validSkinEntries("First"));
        SkinPackageInstaller installer = new SkinPackageInstaller(new SkinRepository(skinsDirectory));
        SkinDescriptor installed = installer.install(packageFile);

        Files.delete(packageFile);

        Assertions.assertThat(installer.canUpdate(installed)).isFalse();

        createPackage(packageFile, validSkinEntries("Updated"));
        SkinDescriptor updated = installer.update(installed);

        Assertions.assertThat(installer.canUpdate(updated)).isTrue();
        Assertions.assertThat(updated.manifest().getName()).isEqualTo("Updated");

        Files.delete(packageFile);
        installer.delete(updated);

        Assertions.assertThat(skinsDirectory.resolve("_sources").resolve(MOON_UID + ".path")).doesNotExist();
    }

    @Test
    void keepsVersionsOfSameSkinInstalledSeparately() throws IOException {
        Path firstPackage = temporaryDirectory.resolve("skin-one.tsbs");
        Path secondPackage = temporaryDirectory.resolve("skin-two.tsbs");
        Map<String, String> firstEntries = validSkinEntries("Version One");
        Map<String, String> secondEntries = validSkinEntries("Version Two");
        secondEntries.put("skin.yml", secondEntries.get("skin.yml").replace("skinVersion: 1",
                "skinVersion: 1\nversion: 2"));
        createPackage(firstPackage, firstEntries);
        createPackage(secondPackage, secondEntries);
        SkinRepository repository = new SkinRepository(temporaryDirectory.resolve("skins"));
        SkinPackageInstaller installer = new SkinPackageInstaller(repository);

        installer.install(firstPackage);
        installer.install(secondPackage);

        Assertions.assertThat(repository.findAll().stream().filter(skin -> MOON_UID.equals(skin.manifest().getUid())))
                .extracting(skin -> skin.manifest().getVersion())
                .containsExactlyInAnyOrder(1, 2);
        Assertions.assertThat(repository.getExternalSkinsDirectory().resolve(MOON_UID + "-v2")).isDirectory();
        SkinDescriptor first = repository.findSelected(MOON_UID, 1);
        installer.delete(first);
        installer.installAvailablePackages(List.of(temporaryDirectory));

        Assertions.assertThat(firstPackage).isRegularFile();
        Assertions.assertThat(repository.findAll().stream().filter(skin -> MOON_UID.equals(skin.manifest().getUid())))
                .extracting(skin -> skin.manifest().getVersion())
                .containsExactly(2);
        Files.writeString(repository.getExternalSkinsDirectory().resolve("_disabled").resolve(MOON_UID.toString()),
                "disabled");
        installer.installAvailablePackages(List.of(temporaryDirectory));

        Assertions.assertThat(repository.findAll().stream().filter(skin -> MOON_UID.equals(skin.manifest().getUid())))
                .extracting(skin -> skin.manifest().getVersion())
                .containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void protectsBundledSkinSourceFromDeletion() throws IOException {
        Path protectedDirectory = temporaryDirectory.resolve("assets").resolve("skins");
        Files.createDirectories(protectedDirectory);
        Path packageFile = protectedDirectory.resolve("moon.tsbs");
        createPackage(packageFile, validSkinEntries("Protected"));
        SkinRepository repository = new SkinRepository(temporaryDirectory.resolve("skins"));
        SkinPackageInstaller installer = new SkinPackageInstaller(repository, List.of(protectedDirectory));
        SkinDescriptor installed = installer.install(packageFile);

        Assertions.assertThatThrownBy(() -> installer.deleteSourceFile(installed, packageFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("protected");
        Assertions.assertThat(packageFile).isRegularFile();
        Assertions.assertThat(installed.directory()).isDirectory();
    }

    @Test
    void schedulesUpdateFromRememberedPackage() throws IOException {
        Path skinsDirectory = temporaryDirectory.resolve("skins");
        Path packageFile = temporaryDirectory.resolve("moon.tsbs");
        createPackage(packageFile, validSkinEntries("First"));
        SkinRepository repository = new SkinRepository(skinsDirectory);
        SkinPackageInstaller installer = new SkinPackageInstaller(repository);
        SkinDescriptor installed = installer.install(packageFile);
        createPackage(packageFile, validSkinEntries("Scheduled"));

        SkinManifest scheduled = installer.scheduleUpdate(installed);
        SkinPackageInstaller.applyPendingOperations(repository);

        Assertions.assertThat(scheduled.getName()).isEqualTo("Scheduled");
        Assertions.assertThat(repository.findSelected(MOON_UID).manifest().getName()).isEqualTo("Scheduled");
    }

    @Test
    void discoversPackagesFromMultipleOrderedSourceDirectories() throws IOException {
        Path firstSource = temporaryDirectory.resolve("first-source");
        Path secondSource = temporaryDirectory.resolve("second-source");
        Files.createDirectories(firstSource);
        Files.createDirectories(secondSource);
        Path firstPackage = firstSource.resolve("moon.tsbs");
        Path secondPackage = secondSource.resolve("moon.tsbs");
        createPackage(firstPackage, validSkinEntries("First Source"));
        createPackage(secondPackage, validSkinEntries("Second Source"));
        SkinRepository repository = new SkinRepository(temporaryDirectory.resolve("skins"));
        SkinPackageInstaller installer = new SkinPackageInstaller(repository);

        installer.installAvailablePackages(List.of(firstSource, secondSource));

        SkinDescriptor discovered = repository.findSelected(MOON_UID);
        Assertions.assertThat(discovered.manifest().getName()).isEqualTo("First Source");
        Assertions.assertThat(installer.isDiscovered(discovered)).isTrue();

        createPackage(firstPackage, validSkinEntries("Updated First Source"));
        installer.installAvailablePackages(List.of(firstSource, secondSource));

        SkinDescriptor automaticallyUpdated = repository.findSelected(MOON_UID);
        Assertions.assertThat(automaticallyUpdated.manifest().getName()).isEqualTo("Updated First Source");

        installer.deleteSourceFile(automaticallyUpdated, firstPackage);
        Assertions.assertThat(automaticallyUpdated.directory()).isDirectory();
        Assertions.assertThat(installer.canUpdate(automaticallyUpdated)).isTrue();
        SkinDescriptor updatedFromFallback = installer.update(automaticallyUpdated);

        Assertions.assertThat(updatedFromFallback.manifest().getName()).isEqualTo("Second Source");

        installer.deleteSourceFile(updatedFromFallback, secondPackage);

        Assertions.assertThat(secondPackage).doesNotExist();
        Assertions.assertThat(updatedFromFallback.directory()).isDirectory();
        Assertions.assertThat(installer.canUpdate(updatedFromFallback)).isFalse();
    }

    @Test
    void protectsBuiltInDefaultFromExportAndDeletion() {
        SkinRepository repository = new SkinRepository(temporaryDirectory.resolve("skins"));
        SkinPackageInstaller installer = new SkinPackageInstaller(repository);
        SkinDescriptor defaultSkin = repository.findSelected(SkinRepository.DEFAULT_SKIN_UID);

        Assertions.assertThatThrownBy(() -> installer.export(defaultSkin, temporaryDirectory.resolve("default.tsbs")))
                .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> installer.delete(defaultSkin))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createsInstallablePackageFromSkinFolder() throws IOException {
        Path sourceDirectory = temporaryDirectory.resolve("Sakura");
        Files.createDirectories(sourceDirectory);
        for (Map.Entry<String, String> entry : validSkinEntries("Sakura").entrySet()) {
            Path target = sourceDirectory.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, entry.getValue(), StandardCharsets.UTF_8);
        }
        Files.createDirectories(sourceDirectory.resolve("images"));
        Files.writeString(sourceDirectory.resolve("images/background.png"), "image", StandardCharsets.UTF_8);
        SkinPackageInstaller installer = new SkinPackageInstaller(
                new SkinRepository(temporaryDirectory.resolve("skins"))
        );
        Path packageFile = temporaryDirectory.resolve("Sakura.tsbs");

        installer.createPackage(sourceDirectory, packageFile);

        Assertions.assertThat(packageEntries(packageFile))
                .contains("skin.yml", "skin.css", "images/background.png");
        Assertions.assertThat(installer.install(packageFile).manifest().getName()).isEqualTo("Sakura");
    }

    @Test
    void rejectsPackageDestinationInsideSourceFolder() throws IOException {
        Path sourceDirectory = temporaryDirectory.resolve("skin");
        Files.createDirectories(sourceDirectory);
        for (Map.Entry<String, String> entry : validSkinEntries("Nested").entrySet()) {
            Files.writeString(sourceDirectory.resolve(entry.getKey()), entry.getValue(), StandardCharsets.UTF_8);
        }
        SkinPackageInstaller installer = new SkinPackageInstaller(
                new SkinRepository(temporaryDirectory.resolve("skins"))
        );

        Assertions.assertThatThrownBy(() -> installer.createPackage(
                        sourceDirectory,
                        sourceDirectory.resolve("nested.tsbs")
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside");
    }

    @Test
    void repositorySkinExamplesCanBePackagedAndInstalled() throws IOException {
        SkinPackageInstaller installer = new SkinPackageInstaller(
                new SkinRepository(temporaryDirectory.resolve("installed-skins"))
        );
        Path nightPackage = temporaryDirectory.resolve("Night Mode.tsbs");
        Path sakuraPackage = temporaryDirectory.resolve("Sakura.tsbs");
        Path retroAmpPackage = temporaryDirectory.resolve("Retro Amp.tsbs");

        installer.createPackage(Path.of("examples", "skins", "Night Mode", "source"), nightPackage);
        installer.createPackage(Path.of("examples", "skins", "Sakura", "source"), sakuraPackage);
        installer.createPackage(Path.of("examples", "skins", "Retro Amp", "source"), retroAmpPackage);

        Assertions.assertThat(installer.install(nightPackage).manifest().getName()).isEqualTo("🌙 Night Mode");
        Assertions.assertThat(installer.install(sakuraPackage).manifest().getName()).isEqualTo("🌸 Sakura");
        Assertions.assertThat(installer.install(retroAmpPackage).manifest().getName()).isEqualTo("⚡ Retro Amp");
        Assertions.assertThat(packageEntries(sakuraPackage))
                .contains("images/header.png", "images/workspace.png", "fonts/NotoSansJP-Regular.ttf");
    }

    @Test
    void checkedInExamplePackagesCanBeInstalled() throws IOException {
        SkinPackageInstaller installer = new SkinPackageInstaller(
                new SkinRepository(temporaryDirectory.resolve("installed-examples"))
        );

        SkinDescriptor nightMode = installer.install(Path.of(
                "examples", "skins", "Night Mode", "Night Mode.tsbs"
        ));
        SkinDescriptor sakura = installer.install(Path.of(
                "examples", "skins", "Sakura", "Sakura.tsbs"
        ));
        SkinDescriptor retroAmp = installer.install(Path.of(
                "examples", "skins", "Retro Amp", "Retro Amp.tsbs"
        ));
        SkinDescriptor tacticalCodec = installer.install(Path.of(
                "examples", "skins", "Tactical Codec", "Tactical Codec.tsbs"
        ));

        Assertions.assertThat(nightMode.manifest().getName()).isEqualTo("🌙 Night Mode");
        Assertions.assertThat(sakura.manifest().getName()).isEqualTo("🌸 Sakura");
        Assertions.assertThat(retroAmp.manifest().getName()).isEqualTo("⚡ Retro Amp");
        Assertions.assertThat(tacticalCodec.manifest().getName()).isEqualTo("📟 Tactical Codec");
        Assertions.assertThat(nightMode.manifest().getVersion()).isEqualTo(2);
        Assertions.assertThat(sakura.manifest().getVersion()).isEqualTo(2);
        Assertions.assertThat(retroAmp.manifest().getVersion()).isEqualTo(1);
        Assertions.assertThat(tacticalCodec.manifest().getVersion()).isEqualTo(2);
    }

    @Test
    void rejectsPackageUsingReservedDefaultUid() throws IOException {
        Path packageFile = temporaryDirectory.resolve("fake-default.tsbs");
        Map<String, String> entries = validSkinEntries("Fake Default");
        entries.put("skin.yml", entries.get("skin.yml").replace(MOON_UID.toString(),
                SkinRepository.DEFAULT_SKIN_UID.toString()));
        createPackage(packageFile, entries);
        SkinPackageInstaller installer = new SkinPackageInstaller(
                new SkinRepository(temporaryDirectory.resolve("skins"))
        );

        Assertions.assertThatThrownBy(() -> installer.install(packageFile))
                .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThat(new SkinRepository(temporaryDirectory.resolve("skins")).findAll())
                .singleElement()
                .satisfies(skin -> Assertions.assertThat(skin.isBuiltIn()).isTrue());
    }

    @Test
    void rejectsPackageEntryOutsideInstallationDirectory() throws IOException {
        Path packageFile = temporaryDirectory.resolve("unsafe.tsbs");
        createPackage(packageFile, Map.of("../outside.txt", "unsafe"));
        SkinPackageInstaller installer = new SkinPackageInstaller(new SkinRepository(temporaryDirectory.resolve("skins")));

        Assertions.assertThatThrownBy(() -> installer.install(packageFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe path");
        Assertions.assertThat(temporaryDirectory.resolve("outside.txt")).doesNotExist();
    }

    private void createPackage(Path packageFile, Map<String, String> entries) throws IOException {
        try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(packageFile))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                outputStream.putNextEntry(new ZipEntry(entry.getKey()));
                outputStream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                outputStream.closeEntry();
            }
        }
    }

    private Map<String, String> validSkinEntries(String name) {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("skin.yml", """
                uid: e30ed037-5703-45dd-95c7-4dbe1d196c93
                name: %s
                skinVersion: 1
                stylesheet: skin.css
                fonts:
                  regular: regular.ttf
                  bold: bold.ttf
                  italic: italic.ttf
                """.formatted(name));
        entries.put("skin.css", ".soundboard-root {}");
        entries.put("regular.ttf", "font");
        entries.put("bold.ttf", "font");
        entries.put("italic.ttf", "font");
        return entries;
    }

    private Set<String> packageEntries(Path packageFile) throws IOException {
        Set<String> entries = new LinkedHashSet<>();
        try (ZipInputStream inputStream = new ZipInputStream(Files.newInputStream(packageFile))) {
            ZipEntry entry;
            while ((entry = inputStream.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }
        return entries;
    }
}
