package app.waveform;

import javafx.util.Duration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class WaveformDataTest {
    @Test
    void retainsDurationForSeekingBeforeMediaPlayerCreation() {
        WaveformData waveformData = new WaveformData(
                new double[]{0.2d, 0.4d}, new double[]{0.1d, 0.3d}, Duration.seconds(12));

        Assertions.assertThat(waveformData.getDuration()).isEqualTo(Duration.seconds(12));
    }

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
