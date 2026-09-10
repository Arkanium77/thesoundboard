package app.waveform;

import javafx.util.Duration;

/**
 * Stores at most four buckets per output point, merging equal-duration neighbors when full. Energy remains a sum
 * with a frame count, rather than an average of averages, so a partial final bucket cannot exaggerate its RMS.
 * Both envelopes use the same buckets and digital silence stays exactly zero after normalization.
 */
final class WaveformAccumulator {
    private final int resolution;
    private final double[] energies;
    private final double[] peaks;
    private final long[] counts;
    private int completed;
    private long bucketFrames;

    WaveformAccumulator(int resolution, long bucketFrames) {
        this.resolution = resolution;
        this.bucketFrames = bucketFrames;
        energies = new double[resolution * 4];
        peaks = new double[energies.length];
        counts = new long[energies.length];
    }

    void add(double amplitude) {
        energies[completed] += amplitude * amplitude;
        peaks[completed] = Math.max(peaks[completed], amplitude);
        if (++counts[completed] < bucketFrames) return;
        completed++;
        if (completed < energies.length) return;
        for (int index = 0; index < completed / 2; index++) {
            energies[index] = energies[index * 2] + energies[index * 2 + 1];
            peaks[index] = Math.max(peaks[index * 2], peaks[index * 2 + 1]);
            counts[index] = counts[index * 2] + counts[index * 2 + 1];
        }
        completed /= 2;
        bucketFrames *= 2;
        for (int index = completed; index < energies.length; index++) {
            energies[index] = 0d;
            peaks[index] = 0d;
            counts[index] = 0L;
        }
    }

    WaveformData finish(Duration duration) {
        int size = completed + (counts[completed] > 0L ? 1 : 0);
        double[] outputPeaks = new double[resolution];
        double[] outputRms = new double[resolution];
        for (int index = 0; index < resolution; index++) {
            int start = index * size / resolution;
            int end = Math.min(size, Math.max(start + 1, (index + 1) * size / resolution));
            double energy = 0d;
            long frames = 0L;
            for (int bucket = start; bucket < end; bucket++) {
                outputPeaks[index] = Math.max(outputPeaks[index], peaks[bucket]);
                energy += energies[bucket];
                frames += counts[bucket];
            }
            outputRms[index] = frames == 0L ? 0d : Math.sqrt(energy / frames);
        }
        normalize(outputPeaks);
        normalize(outputRms);
        return new WaveformData(outputPeaks, outputRms, duration);
    }

    private void normalize(double[] values) {
        double maximum = 0d;
        for (double value : values) maximum = Math.max(maximum, value);
        if (maximum == 0d) return;
        for (int index = 0; index < values.length; index++) values[index] /= maximum;
    }
}
