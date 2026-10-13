package app.waveform;

import java.io.IOException;
import java.nio.file.Path;

public interface WaveformExtractor {
    /**
     * Keeps waiting work in the service's priority queue rather than inside a decoder's concurrency guard.
     * Otherwise speculative tasks already occupying workers can block a newly selected track from promotion.
     * Extractors without an internal limit retain the service's configured worker count.
     */
    default int getMaxConcurrentLoads() { return Integer.MAX_VALUE; }

    WaveformData extract(Path audioPath) throws IOException;
}
