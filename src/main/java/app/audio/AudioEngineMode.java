package app.audio;

import app.support.RelativePathUtils;

import java.util.Collection;
import java.util.List;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Selects one backend for the entire process so comparisons cannot silently fall back to another decoder.
 * Streaming miniaudio is the default; JavaFX remains an explicit compatibility/comparison option.
 * Command-line selection also survives the existing application restart mechanism without persisted settings.
 * Scanning intersects configured extensions with this backend's capabilities; changing backend may mark unsupported
 * project entries missing, but the existing synchronizer retains their IDs and restores them on a supported rescan.
 */
public enum AudioEngineMode {
    JAVAFX,
    MINIAUDIO;

    public static AudioEngineMode fromArguments(List<String> arguments) {
        AudioEngineMode mode = MINIAUDIO;
        boolean selected = false;
        for (String argument : arguments) {
            if (!argument.startsWith("--audio-engine=")) continue;
            if (selected) throw new IllegalArgumentException("Specify --audio-engine only once");
            selected = true;
            mode = switch (argument.substring("--audio-engine=".length())) {
                case "javafx" -> JAVAFX;
                case "miniaudio" -> MINIAUDIO;
                default -> throw new IllegalArgumentException("Audio engine must be javafx or miniaudio: " + argument);
            };
        }
        return mode;
    }

    public AudioEngine createEngine() {
        return this == MINIAUDIO ? new MiniaudioAudioEngine() : new JavaFxAudioEngine();
    }

    public List<String> getSupportedExtensions() {
        return this == MINIAUDIO ? List.of("mp3", "wav", "ogg", "flac", "opus") : List.of("mp3", "wav");
    }

    public List<String> filterSupportedExtensions(Collection<String> configuredExtensions) {
        return configuredExtensions.stream().map(RelativePathUtils::normalizeExtension)
                .filter(getSupportedExtensions()::contains).distinct().toList();
    }

    /** Ogg is a container: unknown codecs must not appear playable merely because of their suffix.
     * Probe only the bounded identification header during background scans; native players remain lazy. */
    public boolean supportsFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return !name.endsWith(".ogg") && !name.endsWith(".opus")
                || this == MINIAUDIO && OggAudioMetadata.isSupported(path);
    }
}
