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
version: 1
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

- `uid` identifies the skin family, not a display name. Keep it unchanged between releases.
- `name` is UTF-8 and may contain emoji. Emoji are rendered from bundled Twemoji images.
- `skinVersion` is the package-format version and is currently `1`.
- `version` is a positive, monotonically increasing skin revision starting at `1`. Different versions of the same UID are installed side by side. Older packages without this field are treated as version 1.
- `stylesheet` is required.
- `regular`, `bold`, and `italic` are required. `boldItalic` is optional.
- Use `system` for every font role to use the platform UI font. Do not mix `system` roles and font files.
- `allowFallback` defaults to `true`. Set it to `false` only when unsupported characters are intentionally allowed to remain missing.
- Icons are optional. Omitted roles use built-in vector icons. Supported roles are `play`, `pause`, `stop`, `previous`, `next`, `loop`, `muted`, and `unmuted`.
- Rendering values are `AUTOMATIC`, `HARDWARE`, or `SOFTWARE`. A rendering change requires an application restart; ordinary CSS, fonts, and icons are applied immediately.

Bundle the license required by every included font, image, or icon. Do not assume that a freely downloadable asset permits redistribution.

## CSS surface

Skin CSS may use JavaFX selectors and properties. Application classes include:

Avoid `-fx-effect` on controls or repeated workspace elements such as `.button`, `.track-tile`, `.queue-tile`, and
`.virtual-tile`. JavaFX renders shadows and blurs through intermediate off-screen images; changing a waveform inside
one affected tile can invalidate effects across a large scene and force Windows DWM to composite hundreds of megabytes
of temporary textures per frame. Prefer layered `-fx-background-color`, asymmetric borders, background insets, and
gradients for depth. Effects remain supported for small, isolated, infrequently repainted decorations.

- application: `.soundboard-root`, `.main-header`, `.status-bar`;
- project tree: `.project-pane`, `.project-tree-container`, `.project-tree-background`, `.project-tree`;
- workspace: `.workspace-pane`, `.workspace-header`, `.workspace-view`, `.workspace-background`, `.workspace-scroll`, `.workspace-content`;
- items: `.track-tile`, `.queue-tile`, `.queue-chip`, `.insertion-marker`, `.soundboard-icon`, `.waveform-seek-view`;
- settings: `.settings-window`, `.settings-navigation`, `.settings-content`, `.settings-title`;
- dialogs: `.settings-dialog` plus `.settings-confirmation-dialog`, `.settings-information-dialog`, `.settings-error-dialog`, or `.restart-confirmation-dialog`.

Queue chips expose `selected`, `playing`, `paused`, `finished`, and `ready` pseudo-classes. Standard JavaFX selectors such as `.button`, `.label`, `.slider`, and `.scroll-pane` remain available.

Waveform colors are customizable through the `.waveform-seek-view` selector:

```css
.waveform-seek-view {
    -tsb-waveform-active-color: #8f334d;
    -tsb-waveform-idle-color: #ddbfc5;
    -tsb-waveform-playhead-color: #6f263c;
    -tsb-waveform-disabled-color: #cbbfc0;
}
```

Waveforms are centered and use the active/idle colors by default. A skin can instead anchor bars to the bottom and color them dynamically by amplitude:

```css
.waveform-seek-view {
    -tsb-waveform-bottom-aligned: true;
    -tsb-waveform-amplitude-coloring: true;
    -tsb-waveform-smooth-coloring: true;
    -tsb-waveform-low-color: #39ff53;
    -tsb-waveform-mid-color: #e0cd30;
    -tsb-waveform-high-color: #ff5533;
    -tsb-waveform-idle-low-color: #45784d;
    -tsb-waveform-idle-mid-color: #82782c;
    -tsb-waveform-idle-high-color: #86463a;
}
```

When amplitude coloring is enabled, played and unplayed bars use their respective low, mid, and high palettes instead of the playback-progress active/idle colors. Smooth coloring interpolates between palette colors; when disabled, the same colors form three discrete bands. All colors are freely selectable. The playhead color remains independent. Waveform calculation is an application-wide user preference rather than a skin property, so the same skin can be compared using peak-linear, RMS-linear, and RMS-dB envelopes.

The built-in Canvas renderer is selected with `-tsb-waveform-rendering`: `bars` or the experimental `fire`. These are controlled application renderers rather than executable skin code. `fire` remains available for skin experiments but is not enabled by the bundled skins and may change in a future release. Fire also supports `-tsb-waveform-fire-low-color`, `-tsb-waveform-fire-mid-color`, and `-tsb-waveform-fire-high-color`. Its `-tsb-waveform-animation-speed` accepts values such as `1` for normal speed, `0.5` for half speed, and `2` for double speed. `-tsb-waveform-animation-amplitude` controls the animated height deviation (`0.03` is the default; values are capped at `0.5`). Motion runs only over the played part while the owning track is actively playing; the future part and paused, stopped, or hidden waveforms remain static.

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

Release builds include ready-to-use packages under `TheSoundboard/assets/skins`. The application scans an ordered list of package-source directories on startup and whenever **Settings → Skins** is refreshed. A valid package that is not installed yet is imported automatically, while a package with an installed UID becomes an update source without silently replacing the installed skin. Dropping another `.tsbs` into this directory makes it visible after reopening the section or pressing its loop button. Files in the application directory are replaceable release assets; retain a separate copy if a custom package must survive application-folder replacement.

The application also remembers the original `.tsbs` path selected through the installation dialog. If that file exists it takes priority over automatically discovered sources; otherwise updates fall back through the configured source directories in order. Skins installed by an earlier application version need either one manual replacement or a matching discovered package before their source becomes known.

An installed skin can be exported back to `.tsbs`. On Windows, a previously loaded font may remain locked until exit; affected replacement or deletion is safely staged and the application offers to restart.
