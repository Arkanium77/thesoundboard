package app.project;

import app.model.AudioFile;
import app.model.LinkedAudioFolder;
import app.model.ProjectState;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** One lookup is shared by every container, avoiding library copies per queue. Source edits replace its maps on
 * the FX thread while existing players retain their resolved paths; project switches replace the whole index.
 * Resolving playable references performs no disk I/O, copies or decoding. Missing entries use a harmless placeholder
 * root only while constructing disabled views; unknown playable sources never fall back to the project. */
public final class AudioFileIndex {
    private Map<UUID, AudioFile> files;
    private Map<UUID, LinkedAudioFolder> folders = Map.of();

    public AudioFileIndex(List<AudioFile> audioFiles) {
        Map<UUID, AudioFile> indexed = new LinkedHashMap<>();
        for (AudioFile file : audioFiles) indexed.put(file.getId(), file);
        files = Map.copyOf(indexed);
    }

    public AudioFileIndex(ProjectState state) { update(state); }

    public void update(ProjectState state) {
        Map<UUID, AudioFile> indexed = new LinkedHashMap<>();
        state.getAudioFiles().forEach(file -> indexed.put(file.getId(), file));
        files = Map.copyOf(indexed);
        Map<UUID, LinkedAudioFolder> sources = new LinkedHashMap<>();
        state.getLinkedAudioFolders().forEach(folder -> sources.put(folder.getId(), folder));
        folders = Map.copyOf(sources);
    }

    public Path root(Path projectRoot, AudioFile file) {
        if (file.getLinkedFolderId() == null || file.isMissing()) return projectRoot;
        LinkedAudioFolder folder = folders.get(file.getLinkedFolderId());
        if (folder == null) throw new IllegalArgumentException("Unknown audio folder: " + file.getLinkedFolderId());
        return folder.resolve(projectRoot);
    }

    public Path resolve(Path projectRoot, AudioFile file) { return root(projectRoot, file).resolve(file.getRelativePath()); }

    public AudioFile get(UUID id) { return id == null ? null : files.get(id); }
    public Optional<AudioFile> find(UUID id) { return Optional.ofNullable(get(id)); }
    public AudioFile findOrMissing(UUID id) {
        return find(id).orElseGet(() -> new AudioFile(id, "", "Unknown file", true));
    }
}
