package app.audio;

import app.support.RelativePathUtils;
import app.waveform.AudioInputStreamWaveformExtractor;
import app.waveform.GstreamerWaveformExtractor;
import app.waveform.WaveformExtractor;

import java.util.Collection;
import java.util.List;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Selects one backend for the entire process so comparisons cannot silently fall back to another decoder.
 * GStreamer is the default; JavaFX remains an explicit comparison/recovery option.
 * GStreamer waveform uses its own decoder family as well, so format support does not depend on Java Sound SPIs.
 * Command-line selection also survives the existing application restart mechanism without persisted settings.
 * Scanning intersects configured extensions with this backend's capabilities; changing backend may mark unsupported
 * project entries missing, but the existing synchronizer retains their IDs and restores them on a supported rescan.
 */
public enum AudioEngineMode {
    JAVAFX,
    GSTREAMER;

    public static AudioEngineMode fromArguments(List<String> arguments) {
        AudioEngineMode mode = GSTREAMER;
        boolean selected = false;
        for (String argument : arguments) {
            if (!argument.startsWith("--audio-engine=")) continue;
            if (selected) throw new IllegalArgumentException("Specify --audio-engine only once");
            selected = true;
            mode = switch (argument.substring("--audio-engine=".length())) {
                case "javafx" -> JAVAFX;
                case "gstreamer" -> GSTREAMER;
                default -> throw new IllegalArgumentException("Audio engine must be javafx or gstreamer: " + argument);
            };
        }
        return mode;
    }

    public AudioEngine createEngine() {
        return switch (this) {
            case JAVAFX -> new JavaFxAudioEngine();
            case GSTREAMER -> new GstreamerAudioEngine();
        };
    }

    public WaveformExtractor createWaveformExtractor(int resolution) {
        return this == GSTREAMER ? new GstreamerWaveformExtractor(resolution)
                : new AudioInputStreamWaveformExtractor(resolution);
    }

    public List<String> getSupportedExtensions() {
        return switch (this) {
            case JAVAFX -> List.of("mp3", "wav");
            case GSTREAMER -> List.of("mp3", "wav", "ogg", "flac", "opus", "aac", "m4a", "aiff", "aif");
        };
    }

    public List<String> filterSupportedExtensions(Collection<String> configuredExtensions) {
        return configuredExtensions.stream().map(RelativePathUtils::normalizeExtension)
                .filter(getSupportedExtensions()::contains).distinct().toList();
    }

    /** Containers must not appear playable merely because of their suffix. Probe bounded identification metadata
     * during background scans; native players and waveform decoding remain lazy, including large linked libraries. */
    public boolean supportsFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (this == GSTREAMER && name.endsWith(".m4a")) return MpegAudioMetadata.supportsM4a(path);
        if (this == GSTREAMER && name.endsWith(".aac")) return MpegAudioMetadata.supportsAdts(path);
        return !name.endsWith(".ogg") && !name.endsWith(".opus")
                || this != JAVAFX && OggAudioMetadata.isSupported(path);
    }
}
