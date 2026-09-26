package app.project;

import app.model.AudioFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** One lookup snapshot is shared by every workspace container instead of copying the entire library into each
 * queue. A workspace rebuild after load/rescan must replace this index together with its playback items, so removed
 * files and updated missing flags cannot be served from a previous project. It owns no players or mutable caches. */
public final class AudioFileIndex {
    private final Map<UUID, AudioFile> files;

    public AudioFileIndex(List<AudioFile> audioFiles) {
        Map<UUID, AudioFile> indexed = new LinkedHashMap<>();
        for (AudioFile file : audioFiles) indexed.put(file.getId(), file);
        files = Map.copyOf(indexed);
    }

    public AudioFile get(UUID id) { return id == null ? null : files.get(id); }
    public Optional<AudioFile> find(UUID id) { return Optional.ofNullable(get(id)); }
    public AudioFile findOrMissing(UUID id) {
        return find(id).orElseGet(() -> new AudioFile(id, "", "Unknown file", true));
    }
}
