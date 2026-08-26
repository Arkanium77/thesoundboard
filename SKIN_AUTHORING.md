# Creating skins

The Soundboard skins are ZIP-compatible `.tsbs` packages. The package root must contain `skin.yml`; paths in the manifest are relative to that root and cannot escape it.

## Quick start

1. Copy an editable example from [`examples/skins`](examples/skins).
2. Generate a new UUID and keep it unchanged for every later release of the same skin.
3. Edit `skin.yml`, `skin.css`, and the resources beneath them.
4. In **Settings → Skins**, choose **Create Package...**, select the folder containing `skin.yml`, and save the resulting `.tsbs` outside that folder.
5. Install the package and test it at several UI and workspace scales on every target platform.

The examples keep their editable `source` folder beside a ready-to-install package. Maintainers can rebuild all examples with `./gradlew packageExampleSkins` or `.\gradlew.bat packageExampleSkins`.

## Manifest

```yaml
uid: 783ee231-ab2b-4ad4-9ccf-0caaf5f624de
name: "My Skin 🎨"
skinVersion: 1
stylesheet: skin.css
fonts:
  regular: fonts/Regular.ttf
  bold: fonts/Bold.ttf
  italic: fonts/Italic.ttf
  boldItalic: fonts/BoldItalic.ttf # optional
  allowFallback: true
icons:
  play: icons/play.svg
  pause: icons/pause.svg
rendering:
  windows: AUTOMATIC
  linux: AUTOMATIC
  macos: AUTOMATIC
```

- `uid` is package identity, not a display name. Reinstalling the same UID completely replaces the installed skin.
- `name` is UTF-8 and may contain emoji. Emoji are rendered from bundled Twemoji images.
- `skinVersion` is currently `1`.
- `stylesheet` is required.
- `regular`, `bold`, and `italic` are required. `boldItalic` is optional.
- Use `system` for every font role to use the platform UI font. Do not mix `system` roles and font files.
- `allowFallback` defaults to `true`. Set it to `false` only when unsupported characters are intentionally allowed to remain missing.
- Icons are optional. Omitted roles use built-in vector icons. Supported roles are `play`, `pause`, `stop`, `previous`, `next`, `loop`, `muted`, and `unmuted`.
- Rendering values are `AUTOMATIC`, `HARDWARE`, or `SOFTWARE`. A rendering change requires an application restart; ordinary CSS, fonts, and icons are applied immediately.

Bundle the license required by every included font, image, or icon. Do not assume that a freely downloadable asset permits redistribution.

## CSS surface

Skin CSS may use JavaFX selectors and properties. Application classes include:

- application: `.soundboard-root`, `.main-header`, `.status-bar`;
- project tree: `.project-pane`, `.project-tree-container`, `.project-tree-background`, `.project-tree`;
- workspace: `.workspace-pane`, `.workspace-header`, `.workspace-view`, `.workspace-background`, `.workspace-scroll`, `.workspace-content`;
- items: `.track-tile`, `.queue-tile`, `.queue-chip`, `.insertion-marker`, `.soundboard-icon`;
- settings: `.settings-window`, `.settings-navigation`, `.settings-content`, `.settings-title`;
- dialogs: `.settings-dialog` plus `.settings-confirmation-dialog`, `.settings-information-dialog`, `.settings-error-dialog`, or `.restart-confirmation-dialog`.

Queue chips expose `selected`, `playing`, `paused`, `finished`, and `ready` pseudo-classes. Standard JavaFX selectors such as `.button`, `.label`, `.slider`, and `.scroll-pane` remain available.

Relative URLs resolve from `skin.css`:

```css
.workspace-background {
    -fx-background-image: url("images/workspace.png");
    -fx-background-size: cover;
    -fx-opacity: 0.45;
}

.track-tile, .queue-tile {
    -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.20), 8, 0.1, 0, 2);
}
```

Use `.workspace-background` and `.project-tree-background` for translucent artwork: they are dedicated layers, so opacity does not fade text or controls. Skins cannot alter application layout or native title bars.

## Installation and updates

Installed skins live under `~/.thesoundboard/skins` (`%USERPROFILE%\.thesoundboard\skins` on Windows), outside the application directory. They survive replacement-style application updates. The default skin is built in and cannot be replaced or removed.

An installed skin can be exported back to `.tsbs`. On Windows, a previously loaded font may remain locked until exit; affected replacement or deletion is safely staged and the application offers to restart.
