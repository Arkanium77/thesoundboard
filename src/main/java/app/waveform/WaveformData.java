package app.waveform;

public class WaveformData {
    private static final WaveformData EMPTY = new WaveformData(new double[0]);

    private final double[] amplitudes;

    public WaveformData(double[] amplitudes) {
        this.amplitudes = amplitudes == null ? new double[0] : amplitudes.clone();
    }

    public static WaveformData empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return amplitudes.length == 0;
    }

    public int size() {
        return amplitudes.length;
    }

    public double amplitudeAt(int index) {
        if (index < 0 || index >= amplitudes.length) {
            return 0d;
        }
        return amplitudes[index];
    }

    public double[] copyAmplitudes() {
        return amplitudes.clone();
    }
}
