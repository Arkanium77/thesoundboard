package app.waveform;

import java.io.IOException;
import java.nio.file.Path;

public interface WaveformExtractor {
    WaveformData extract(Path audioPath) throws IOException;
}
