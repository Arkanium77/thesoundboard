package app.audio;

import java.nio.file.Path;

public interface AudioEngine {
    PlayingTrack createTrack(Path audioPath);

    /** Shared native engines outlive individual tracks and must close after all workspace owners release them. */
    default void shutdown() { }
}
