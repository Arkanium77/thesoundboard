package app.audio;

import java.nio.file.Path;

public interface AudioEngine {
    PlayingTrack createTrack(Path audioPath);
}
