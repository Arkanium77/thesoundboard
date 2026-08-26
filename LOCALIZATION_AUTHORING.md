# Creating localizations

The Soundboard localizations are ZIP-compatible `.tsbl` packages. The package root contains `localization.yml` and the referenced strings file, normally `strings.yml`.

## Quick start

1. Open **Settings → Localization** and choose **Export Strings...** to generate the current English catalog.
2. Create a folder containing that `strings.yml` and a `localization.yml` manifest.
3. Translate values only. Stable keys such as `main.open_folder` identify interface functions and must not be renamed.
4. Choose **Create Package...**, select the source folder, and save the `.tsbl` outside that folder.
5. Install the package and inspect the main window, settings, dialogs, empty states, tooltips, tracks, and queues.

Editable examples and ready packages are available in [`examples/localizations`](examples/localizations). Rebuild them with `./gradlew packageExampleLocalizations` or `.\gradlew.bat packageExampleLocalizations`.

## Manifest

```yaml
uid: 2195946d-8797-4f48-a81b-a2a12d1c4a30
name: "🇷🇺 Русский"
languageTag: ru
localizationVersion: 1
strings: strings.yml
```

- `uid` is the stable identity. Keep it unchanged when publishing an update.
- `name` is UTF-8 and may contain emoji.
- `languageTag` should be a BCP 47 language tag such as `ru`, `ja`, or `pt-BR`.
- `localizationVersion` is currently `1`.
- `strings` is a safe path inside the package.

## Strings

The file is a UTF-8 YAML map:

```yaml
main.open_folder: "Открыть папку"
status.loaded: "Загружено: {0}"
localization.update_complete: "Добавлено новых строк: {0}"
```

Placeholders such as `{0}` are positional values formatted by the application and must remain in the translated value. Quote text when YAML punctuation or leading/trailing whitespace might be ambiguous.

Missing or blank values fall back to built-in English. Unknown keys are ignored at runtime, which keeps older packages usable after controls are removed. Localization selection and replacement are applied immediately without restarting.

## Updating an existing translation

Use **Settings → Localization → Update...** and select either the editable source folder or an existing `.tsbl` package. The updater:

- preserves every existing translation;
- preserves unknown legacy keys for the translator to review;
- appends newly introduced IDs with English values;
- replaces package files through a safe temporary result.

Search the updated `strings.yml` for newly appended English text, translate it, then package and reinstall using the same UID. Keep a source folder under version control; `.tsbl` is a distribution artifact, not the most convenient editing format.

## Installation and updates

Installed localizations live under `~/.thesoundboard/localizations` (`%USERPROFILE%\.thesoundboard\localizations` on Windows), outside the application installation. They survive ordinary updates where a new release is copied over an old one. Installing the same UID offers a complete replacement, while export creates a transferable `.tsbl` copy.
