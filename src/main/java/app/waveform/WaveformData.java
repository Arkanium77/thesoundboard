package app.waveform;

public class WaveformData {
    private static final WaveformData EMPTY = new WaveformData(new double[0], new double[0]);

    private final double[] peakAmplitudes;
    private final double[] rmsAmplitudes;

    public WaveformData(double[] amplitudes) {
        this(amplitudes, amplitudes);
    }

    /**
     * Stores peak and RMS envelopes together so changing the display algorithm is instantaneous and never requires
     * decoding the audio file again. Both arrays must describe the same time buckets; mismatched input is rejected
     * rather than silently displaying two modes with different timelines.
     */
    public WaveformData(double[] peakAmplitudes, double[] rmsAmplitudes) {
        this.peakAmplitudes = peakAmplitudes == null ? new double[0] : peakAmplitudes.clone();
        this.rmsAmplitudes = rmsAmplitudes == null ? new double[0] : rmsAmplitudes.clone();
        if (this.peakAmplitudes.length != this.rmsAmplitudes.length) {
            throw new IllegalArgumentException("Peak and RMS waveform data must have the same size");
        }
    }

    public static WaveformData empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return rmsAmplitudes.length == 0;
    }

    public int size() {
        return rmsAmplitudes.length;
    }

    public double amplitudeAt(int index) {
        return rmsAmplitudeAt(index);
    }

    public double peakAmplitudeAt(int index) {
        if (index < 0 || index >= peakAmplitudes.length) {
            return 0d;
        }
        return peakAmplitudes[index];
    }

    public double rmsAmplitudeAt(int index) {
        if (index < 0 || index >= rmsAmplitudes.length) return 0d;
        return rmsAmplitudes[index];
    }

    public double[] copyAmplitudes() {
        return rmsAmplitudes.clone();
    }

    public double[] copyPeakAmplitudes() { return peakAmplitudes.clone(); }
    public double[] copyRmsAmplitudes() { return rmsAmplitudes.clone(); }
}
