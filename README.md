# The Soundboard

The Soundboard is a desktop application for organizing and playing MP3 collections during games, streams, performances, and other live sessions. It keeps the library on disk, turns selected tracks into reusable tiles, and lets several independent queues live in one workspace.

The current release is **1.0.0**.

## Features

- Scan a folder and browse its MP3 files as a tree.
- Add individual tracks or multiple selections to the workspace with drag and drop.
- Group tracks into named queues and reorder tracks or workspace items.
- Play, pause, stop, seek, loop, and mute tracks independently.
- Control per-track volume and the workspace master volume.
- Adjust the complete interface scale independently from the workspace tile zoom.
- Render waveforms in the background and preload queued tracks.
- Save the workspace to `.soundboard-project.json` inside the selected folder.
- Rescan a changed library while preserving valid workspace entries and marking missing files.
- Install, export, update, and switch user-created skins and localizations.

## Running a release

Release archives are self-contained and include a Java runtime plus ready-to-use skin and localization packages under `TheSoundboard/assets`.

### Windows

1. Extract `thesoundboard-1.1.0-windows-x64.zip`.
2. Run `TheSoundboard/TheSoundboard.exe`.

### Linux

1. Extract `thesoundboard-1.1.0-linux-x64.tar.gz`.
2. Run `TheSoundboard/bin/TheSoundboard`.

### macOS

Download the unsigned DMG for Apple Silicon (`arm64`) or Intel (`x64`), open it, and run `TheSoundboard.app`. The packages are self-contained and require macOS 11 or later; Java does not need to be installed.

The application is not signed or notarized. On first launch, Gatekeeper may require opening it from Finder with **Control-click → Open**, then confirming **Open**. Do not disable Gatekeeper globally.

### Updating

To update within the same major version, extract the new release and copy its contents over the old application directory, replacing conflicting files. Installed skins and localization packages are stored under `~/.thesoundboard` (`%USERPROFILE%\.thesoundboard` on Windows), interface preferences use the operating-system preference store, and project state remains in the selected audio-library directory. Replacing the application files therefore does not erase installed user data. Files placed manually under `TheSoundboard/assets` are discovery sources rather than installed data and may be replaced by a future release, so keep the original package elsewhere when it must survive application-folder replacement.

The Linux build requires a graphical desktop and the system libraries normally required by JavaFX, including GTK 3 and the audio/media stack. MP3 is the only library format enabled by default.

## Using the application

Choose **Open Folder** and select the root of an MP3 library. Double-click a file or drag selected files from the project tree into the workspace. Use the workspace context menu to create a queue, then drag tracks into it. Changes are saved in the selected folder; keep `.soundboard-project.json` if you want to preserve the layout between launches.

The last successfully opened project is remembered. **Settings → General → Restore the last open session on startup** controls whether an ordinary launch reopens it and is disabled by default. Restarts initiated by the application always restore the current project so a required settings restart does not interrupt the session.

**Settings → General → Waveform calculation** switches between the original linear peak envelope, a linear RMS-energy envelope, and a perceptual RMS dB display. Real silence remains at zero in every mode. The selection is an application-wide display preference, so it can be switched while comparing different skins; both envelopes are extracted together and switching does not reread audio, alter playback volume, or change saved audio data.

Use the compact **− 100% +** control in the lower-right corner to resize the entire interface from 25% to 200%. Scaling changes only the content viewport and never resizes the application window; the selection is stored for the current operating-system user. Hold **Ctrl** and scroll over the workspace to adjust track and queue tiles independently of the interface scale.

## Skins

Open Settings with the gear button to select, install, export, replace, or delete skins. `.tsbs` packages can provide JavaFX CSS, regular/bold/italic fonts, background images, vector icons, and platform-specific rendering preferences. Ordinary appearance changes apply immediately; a renderer change offers a restart and restores the current project afterward.

The bundled examples include Night Mode, Sakura, Tactical Codec, and the classic media-player-inspired Retro Amp.

See [Creating skins](SKIN_AUTHORING.md) for the manifest, CSS surface, packaging workflow, fallback behavior, resource licensing, and editable examples.

## Localization

Open **Settings → Localization** to install, select, export, replace, delete, create, or update `.tsbl` packages. Skin and localization manifests use a positive, monotonically increasing `version` number starting at `1`; packages with the same UID and different versions are installed separately. A package version is shown in the interface only when several versions of the same package are installed. Packages without this field remain compatible and are treated as version 1. Skin and localization installers accept multiple packages from one folder, and each section can synchronize additional user-selected source folders. **Delete** can remove only the installed copy while retaining its source. With one source, **Delete from source** removes both copies after two confirmations. With several sources, the application asks which source is intended and removes only the selected source while another source keeps the package installed. A manually reinstalled package becomes available again. Files in the bundled application source are protected from physical deletion. The complete English catalog can be exported for translation. Stable synthetic string IDs preserve compatibility when controls move or English wording changes; missing values fall back to English and unknown legacy values are ignored. Switching or replacing a localization refreshes the running interface immediately.

The release includes Russian, Pre-Revolutionary Russian, and language-neutral Leetspeak example packages. Leetspeak is intended as an accessible reference for localization authors who do not read Russian.

Create a 2 × 2, 2 × 3, 3 × 3, or 4 × 4 virtual tile from the workspace context menu to place several independently controlled audio files in the space of one regular tile. Its tracks form an ordered, capacity-limited list: drag them before or after another mini tile to reorder them, and move tracks between the workspace, queues, and virtual tiles. The 2 × 2 layout includes waveform and mute controls; denser layouts reduce the visible controls to fit. Volume, looping, mute, and removal remain available from each mini tile's context menu.

See [Creating localizations](LOCALIZATION_AUTHORING.md) for manifests, placeholders, string updates, packaging, and the Russian examples.

## Building from source

Prerequisites:

- JDK 21 with `jpackage`
- Windows, Linux, or macOS for that platform's native package

Use the Gradle wrapper shipped with the repository:

```shell
# Linux
./gradlew build
./gradlew run
./gradlew release
```

```powershell
# Windows
.\gradlew.bat build
.\gradlew.bat run
.\gradlew.bat release
```

`release` runs verification and creates a self-contained artifact in `build/release`. Packaging is host-native because JavaFX, the bundled runtime, and macOS app metadata are platform-specific:

- `thesoundboard-<version>-windows-<arch>.zip`;
- `thesoundboard-<version>-linux-<arch>.tar.gz`;
- `thesoundboard-<version>-macos-arm64.dmg` or `thesoundboard-<version>-macos-x64.dmg`.

The macOS bundle is named `TheSoundboard.app`, uses bundle identifier `team.isaz.thesoundboard` and category `public.app-category.music`, and is intentionally produced without signing or notarization. The canonical application artwork is [`assets/branding/tsblogo2.png`](assets/branding/tsblogo2.png); macOS runners generate `.icns` with [`scripts/macos/create-icon.sh`](scripts/macos/create-icon.sh).

## Versioning

The project follows [Semantic Versioning 2.0.0](https://semver.org/). The authoritative version is declared in `build.gradle.kts` and is embedded in the application JAR and native package metadata.

## Contributing

Contributions are welcome. Please keep changes focused, follow the style of the surrounding code, add or update tests where behavior changes, and run `./gradlew check` (or `.\gradlew.bat check`) before submitting a pull request. See [CONTRIBUTING.md](CONTRIBUTING.md) for the fork-and-pull-request workflow and review requirements. By contributing, you agree that your contribution may be distributed under the repository's MIT License.

## License

Copyright 2026 Arkanium77 & ISAZ Team. The source code may be used, copied, modified, and redistributed under the [MIT License](LICENSE), provided the original copyright and license notice are retained.

Third-party components retain their respective licenses; see [NOTICE](NOTICE).
