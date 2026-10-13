package app.audio;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class AudioEngineModeTest {
    @Test
    void usesGstreamerByDefaultAndKeepsExplicitBackendOverrides() {
        Assertions.assertThat(AudioEngineMode.fromArguments(List.of())).isEqualTo(AudioEngineMode.GSTREAMER);
        Assertions.assertThat(AudioEngineMode.fromArguments(List.of("--restore-session"))).isEqualTo(AudioEngineMode.GSTREAMER);
        Assertions.assertThat(AudioEngineMode.fromArguments(List.of("--restore-session", "--audio-engine=miniaudio")))
                .isEqualTo(AudioEngineMode.MINIAUDIO);
        Assertions.assertThat(AudioEngineMode.fromArguments(List.of("--audio-engine=javafx")))
                .isEqualTo(AudioEngineMode.JAVAFX);
        Assertions.assertThat(AudioEngineMode.fromArguments(List.of("--audio-engine=gstreamer")))
                .isEqualTo(AudioEngineMode.GSTREAMER);
    }

    @Test
    void rejectsUnknownAndConflictingSelectionsInsteadOfFallingBack() {
        Assertions.assertThatThrownBy(() -> AudioEngineMode.fromArguments(List.of("--audio-engine=other")))
                .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> AudioEngineMode.fromArguments(
                List.of("--audio-engine=miniaudio", "--audio-engine=javafx")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void respectsScannerConfigurationWithoutAdvertisingUnsupportedBackendFormats() {
        List<String> configured = List.of("MP3", " wav ", "ogg", "flac", "opus", "aac", "mp3");
        Assertions.assertThat(AudioEngineMode.JAVAFX.filterSupportedExtensions(configured))
                .containsExactly("mp3", "wav");
        Assertions.assertThat(AudioEngineMode.MINIAUDIO.filterSupportedExtensions(configured))
                .containsExactly("mp3", "wav", "ogg", "flac", "opus");
        Assertions.assertThat(AudioEngineMode.MINIAUDIO.filterSupportedExtensions(List.of("mp3")))
                .containsExactly("mp3");
        Assertions.assertThat(AudioEngineMode.GSTREAMER.filterSupportedExtensions(configured))
                .containsExactly("mp3", "wav", "ogg", "flac", "opus", "aac");
    }
}
