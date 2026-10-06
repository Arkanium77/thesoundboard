# The Soundboard — Changes

Release descriptions, newest version first. This file is maintained in the repository and is not included in application archives.

## 1.3.0 — Unreleased

### Project navigation

- Quickly reopen the ten most recent projects, ordered by last access.
- Keep project navigation compact with a recent-project arrow beside Open Folder and a small Link button in the project tree header.

### Dialogs and popup menus

- Apply the active skin to error dialogs and popup menus, with concise error messages and no stock alert artwork.
- Use one confirmation when unlinking an audio folder.

### Linked audio folders

- Link audio folders and their subfolders into a project tree to reuse libraries across projects without copying audio files.
- Unlink a folder after confirmation to remove its project references and associated tracks while preserving the original files.

### Playback and waveforms

- Added streaming playback and waveforms for OGG Vorbis, Opus and FLAC, including FLAC inside OGG, through the default miniaudio engine.
- Read OGG duration from container metadata so streaming playback and seeking work when the decoder's length query is unavailable.
- Detect the codec inside OGG containers during library scans so unsupported files receive the existing unavailable-file appearance before playback.
- Automatically highlight the first track and display its waveform when tracks are added to an empty queue.
- Keep the repeat-current-track control on the queue so its state and playback behavior remain consistent when selecting another track.
- Buffer encoded waveform input to reduce small filesystem reads during analysis.

### Audio engine performance

- Made miniaudio the default playback engine; JavaFX remains available through `--audio-engine=javafx`.
- Reduced memory use during extended playback with miniaudio. In a one-hour Windows GUI comparison using a 96-MP3 project, median process memory after Stop decreased from approximately 2,481 to 491 MiB (80%).
- Reduced operating-system threads during playback of 35 simultaneous tracks from approximately 298 to 84 in the same comparison.
- Accelerated playback startup and seeking: median time until the playback position first advanced decreased from approximately 170 to 6 ms for a track's first Play, and from 40 to 12 ms for subsequent Plays.

## 1.2.0 — 2026-09-26

The Soundboard 1.2.0 improves responsiveness in large workspaces, prioritizes the selected track's waveform, strengthens saving and package operations, and fixes drag-and-drop feedback.

### Performance and waveforms

- Reduced playback and scrolling overhead in large workspaces. Off-screen tiles retain their positions while requiring less rendering work; drag-and-drop still accounts for them.
- Update visible playing items first, avoid unchanged time labels, and skip waveform redraws when the playhead remains within the same pixel.
- Reduced temporary allocations during UI updates and audio processing.
- Accelerated rescans and retained calculated waveforms for unchanged audio files.
- Share one audio-file index across queues and virtual tiles instead of copying a large library for each container.
- Prioritize the selected track's waveform over background preparation, with bounded background work and cache memory.
- Cancellation releases pending work and interrupts decoding; late results cannot repopulate a cleared cache.
- Reduced intermediate memory use when audio duration is initially unknown.
- Compact virtual-tile layouts without a visible waveform do not request its calculation. A previously calculated waveform may remain in the shared cache after a move, but these layouts do not require it on the next launch.
- Full background calculation of every waveform can take longer as a tradeoff for selected-track responsiveness. No persistent waveform cache was added.

### Playback, queues, and drag and drop

- Fixed outer insertion markers appearing while dragging inside a queue and remaining after returning from a workspace edge into a virtual tile.
- Clear markers, edge auto-scrolling, and scroll-area focus when a drag completes or is cancelled, including when nested controls consume events or a move removes the original tile. Cleanup cannot interfere with a newer drag.
- Show the workspace focus outline for keyboard navigation without leaving a mouse-induced outline around the viewport.
- Queue completion and error handling continue independently of visibility. Stale notifications from an earlier track cannot advance an updated queue.
- Avoid repeated queue sorting during ordinary reads and save automatic track selection changes.
- Preserve the player and playback position when moving an active track between standalone tiles, queues, and virtual tiles. An already playing destination queue retains playback priority.
- Reordering within a queue preserves its player; failed moves, including moves into full virtual tiles, leave the source and playback intact.
- Preserve container-specific insertion rules and identify the actual destination of a move directly.
- Preserve seek protection against delayed player positions jumping the playhead backward; newer seeks take priority over older requests.

### Saving and library rescans

- Serialize saves in the background and combine rapid changes to avoid rewriting the project after every small action.
- Closing, switching projects, and restarting wait for the latest save without blocking the UI on disk work.
- Failed saves remain available for retry and are not reported as successful.
- Preserve edits made while a library rescan is running and merge them with its results.
- Ignore late results from an old or closed project session.
- Strengthen project-file replacement and retain a backup of the last valid state. A damaged source project is protected from automatic overwriting, including on exit.

### Skins, localization, and settings

- Run package installation, updates, export, creation, and source checks in the background; temporarily disable conflicting actions and closing that would interrupt file changes.
- Restore controls after success or failure and prevent duplicate completion from advancing a batch twice.
- Continue multi-file installation after handled failures or cancelled replacements, including failed skin replacement.
- Cache source metadata instead of repeatedly reading archives when lists refresh or selection changes.
- Preserve configured source priority and ordered discovery; damaged packages and inaccessible folders do not prevent processing other sources.
- Preserve source-based skin synchronization and local localization edits until an explicit update.
- Keep deferred replacement and deletion of skins whose resources are in use.
- Separate general, skin, and localization settings internally while retaining their existing behavior.
- Validate package paths and extraction limits, recover consistent installation state after interruption, and protect existing files when export fails.

### Maintainability and resource lifecycle

- Separate workspace transfers from main-window construction and seek calculations from waveform rendering.
- Reuse common mechanisms for time labels, insertion markers, scrolling titles, volume controls, waveform subscriptions, package discovery, ordered items, file indexing, and package file operations.
- Release unnecessary animations and subscriptions when components are hidden or removed.
- Extend automated coverage for saving, packages, queues, transfers, settings, seeking, and cancellation; document behavioral constraints beside their implementation.

### Measured improvements and compatibility

Measurements are specific to the test device and workload; results are not a guarantee for every project.

- In a 500-tile workspace, the main optimization stage reduced playback CPU usage by approximately 70% and temporary allocations by approximately 95%.
- Rescanning a project with a 1,000-track queue improved from approximately 5.13 to 1.22 seconds.
- After the subsequent refactoring, median first-visible-waveform time changed from 2.44 to 2.36 seconds; selected-track latency under background load improved from 241 to 202 ms. Cached selection remained around one UI frame (15.6 to 16.6 ms).
- Creating 200 queues for a 5,000-file library improved from 41.1 to 2.37 ms, with allocations reduced from 53.3 to 0.56 MB. This measures queue construction rather than complete project loading.
- These measurements cover different stages and their percentages must not be added. Faster full workspace startup and universal CPU reductions for small projects are not claimed.
- Project, skin, and localization formats and user-data locations remain compatible; no data migration is required.

**Full Changelog**: https://github.com/Arkanium77/thesoundboard/compare/v1.1.0...v1.2.0

## 1.1.0 — 2026-08-31

The Soundboard 1.1.0 is a major update to workspace organization, playback control, customization, and package management.

### Workspace and virtual tiles
- Added persistent virtual tiles for grouping tracks in compact 2×2, 2×3, 3×3, and 4×4 layouts.
- Virtual tiles use ordered, capacity-limited track lists: tracks can be inserted, removed, reordered, and moved freely between the workspace, queues, and other virtual tiles.
- Added per-track loop, mute, volume, and removal controls where the selected layout has room for them, plus long-title tooltips and a dedicated grab area.
- Added pointer-based edge auto-scrolling while dragging through large workspaces.

### Queues and playback
- Added individual volume settings for every queue track while keeping the queue's master volume control. Track volumes survive moves into and out of queues, and all queue track volumes can be reset together.
- A single click now focuses a queue track for inspection and editing without starting it; a double click starts playback. The player, timeline, and waveform follow the focused track while clearly preserving the currently playing item.
- Playback position, state, volume, and queue priority are now transferred consistently when a playing track is moved between containers.
- Fixed active-track highlighting, play/pause transitions, context-menu interruptions, volume changes during transfers, and tracks restarting merely because they were dragged.
- Fixed intermittent seeks jumping back to the beginning, including seeking before a stopped or not-yet-started track is played.

### Waveforms
- Added an application-wide waveform calculation setting with Peak — Linear, RMS — Linear, and RMS — dB modes. Peak and RMS data are extracted together, so changing the display mode does not reread the audio.
- Improved low-level detail while preserving true silence and avoiding misleading gaps in quieter material.
- Expanded skin author controls for waveform alignment, playhead and played/unplayed colors, amplitude-based palettes, and smooth or stepped color mixing.

### Skins and localization
- Added configurable source folders for both skins and localization packages, including automatic discovery of bundled packages and targeted scanning when a new source is added.
- Added version-aware package identities and side-by-side versions. Version labels stay hidden when only one version exists.
- Packages now remember their source and detect same-version content changes, allowing installed copies to refresh without manually updating every package.
- Added bulk selection for updating or deleting packages, protected bundled sources, clearer handling of packages with one or several sources, and explicit confirmation before deleting source files.
- Added shared core styling so status bars, backgrounds, selections, and other application chrome remain coherent while still being overridable by skin authors.
- Added the bundled **Retro Amp** and **Tactical Codec** skins, refined **Night Mode** and **Sakura**, and added **Leetspeak** as a localization-package example.

### Interface, performance, and packaging
- Improved compact-tile sizing and controls, inactive selections, button readability, settings layout, status-bar height, and theming of the complete application window.
- Reduced settings-page stalls through cached package metadata, incremental source scans, and fewer unnecessary list rebuilds.
- Reduced needless waveform redraws, CSS work, GPU load, and memory churn in static views.
- The application and process are now consistently identified as **The Soundboard**.
- Fixed bundled skin and localization discovery in Linux packages and ensured all native release archives carry the bundled assets.

### What's Changed
- Release candidate 1.1.0 by @Arkanium77 in https://github.com/Arkanium77/thesoundboard/pull/1

**Full Changelog**: https://github.com/Arkanium77/thesoundboard/compare/v1.0.0...v1.1.0

## 1.0.0 — 2026-08-27

The first public release of **The Soundboard** — a cross-platform desktop soundboard for organizing and playing MP3 collections during games, streams, performances, and other live sessions.

### Audio library and workspace

- Open any folder containing an MP3 collection and browse it as a project tree.
- Rescan the library after files have been added, removed, or renamed.
- Add individual tracks or multiple selected files to the workspace.
- Add tracks by double-clicking or using drag and drop.
- Arrange reusable track and queue tiles freely within the workspace.
- Save the complete workspace layout to `.soundboard-project.json` inside the selected project folder.
- Rebuild a saved workspace when necessary.
- Preserve valid workspace items during rescans and clearly mark missing audio files.

### Track playback

- Play, pause, stop, and seek through tracks independently.
- Enable continuous looping for individual tracks.
- Mute individual tracks without changing their volume setting.
- Adjust the volume of every track separately.
- Control all playback through a shared master-volume slider.
- See the current playback position, total duration, and playback status directly on each tile.

### Waveforms

- Generate real waveforms from MP3 audio in the background.
- Display waveforms on both standalone track tiles and queue players.
- Seek directly by clicking or dragging across a waveform.
- Cache calculated waveform data so UI scaling and redraws do not repeatedly decode audio.
- Preload waveform and playback data for queued tracks.

### Queues

- Create multiple independent named queues in one workspace.
- Add library tracks or existing workspace tracks to a queue.
- Move tracks between queues using drag and drop.
- Reorder tracks inside a queue.
- Move complete queues around the workspace.
- Select and remove individual queue entries.
- Rename queues directly in the workspace.
- Play the previous or next track manually.
- Enable shuffle playback.
- Repeat the current track or loop the complete queue.
- Adjust queue volume independently.
- Track the active, paused, completed, and ready queue entries visually.

### Interface and scaling

- Scale the complete interface from 25% to 200%.
- Scale workspace tiles independently from the rest of the interface.
- Switch the compact `− / +` controls between interface and workspace scaling.
- Keep manual window resizing independent from interface scaling.
- Persist the selected interface scale for the current operating-system user.
- Use portable vector controls for playback, volume, looping, navigation, and settings icons.
- Display bundled emoji images consistently instead of relying on platform-specific emoji glyphs.

### Skins

- Switch the complete visual appearance through installable `.tsbs` skin packages.
- Install, replace, export, delete, and reinstall third-party skins.
- Create a ready-to-share skin package from an editable source folder.
- Keep every skin identified by a stable UID independent from its display name.
- Protect and automatically restore the built-in default skin.
- Apply ordinary visual changes immediately.
- Detect renderer-related changes separately and offer an automatic application restart only when required.
- Customize JavaFX CSS, colors, controls, tiles, dialogs, settings windows, backgrounds, shadows, and vector icons.
- Bundle regular, bold, and italic fonts.
- Allow or explicitly disable system font fallback.
- Add background images independently to the header, project-tree area, project list, workspace, and footer.
- Control background-image opacity without creating separate image files.
- Use bundled **Night Mode** and **Sakura** examples as editable references and ready-to-install packages.

### Localization

- Switch the running interface between localization packages without restarting.
- Install, replace, export, delete, and reinstall `.tsbl` packages.
- Create localization packages from editable source folders.
- Export the complete built-in English string catalog for translators.
- Update an older localization folder or package with newly introduced string IDs.
- Preserve existing translations while adding new strings in English.
- Use stable synthetic string IDs instead of English text as identifiers.
- Fall back to English for missing translations.
- Ignore obsolete or unknown localization entries safely.
- Support placeholders in translated messages.
- Include complete Russian and pre-revolutionary Russian example localizations.

### Sessions and updates

- Remember the last successfully opened project.
- Optionally restore the previous session during an ordinary application launch.
- Always restore the active project after an application-initiated restart.
- Store imported skins, localizations, preferences, and project state outside the replaceable application files.
- Support straightforward same-major-version updates by copying a new release over the previous installation without erasing user data.

### Cross-platform packages

Self-contained packages include the required Java runtime; a separate Java installation is not needed:

- Windows x64
- Linux x64
- macOS Intel x64
- macOS Apple Silicon arm64

> MP3 is the audio format supported by this release. macOS packages are currently unsigned and not notarized.
