package app.waveform;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class WaveformDataTest {
    @Test
    void keepsPeakAndRootMeanSquareEnvelopesOnTheSameTimeline() {
        WaveformData waveformData = new WaveformData(
                new double[]{0.9d, 0.6d},
                new double[]{0.4d, 0.3d}
        );

        Assertions.assertThat(waveformData.peakAmplitudeAt(0)).isEqualTo(0.9d);
        Assertions.assertThat(waveformData.rmsAmplitudeAt(0)).isEqualTo(0.4d);
        Assertions.assertThat(waveformData.copyPeakAmplitudes()).containsExactly(0.9d, 0.6d);
        Assertions.assertThat(waveformData.copyRmsAmplitudes()).containsExactly(0.4d, 0.3d);
    }

    @Test
    void rejectsEnvelopesWithDifferentTimelines() {
        Assertions.assertThatThrownBy(() -> new WaveformData(
                        new double[]{0.9d},
                        new double[]{0.4d, 0.3d}
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same size");
    }
}
