package app.project;

import app.model.AudioFile;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class AudioFileIndexTest {
    @Test
    void rebuiltSnapshotUsesRescanMetadataAndDoesNotRetainRemovedFiles() {
        UUID id = UUID.randomUUID();
        AudioFile original = new AudioFile(id, "a.mp3", "A", false);
        List<AudioFile> files = new ArrayList<>(List.of(original));
        AudioFileIndex first = new AudioFileIndex(files);
        files.clear();
        AudioFile missing = new AudioFile(id, "a.mp3", "A", true);
        AudioFileIndex rescanned = new AudioFileIndex(List.of(missing));
        Assertions.assertThat(first.get(id)).isSameAs(original);
        Assertions.assertThat(rescanned.get(id)).isSameAs(missing);
        AudioFileIndex removed = new AudioFileIndex(List.of());
        Assertions.assertThat(removed.get(id)).isNull();
        Assertions.assertThat(removed.findOrMissing(id).isMissing()).isTrue();
        Assertions.assertThat(removed.findOrMissing(id).getId()).isEqualTo(id);
    }
}
