package app.waveform;

import javafx.util.Duration;
import org.assertj.core.api.Assertions;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class WaveformAccumulatorTest {
    @Test
    void weightsShortFinalBucketByActualFrameCount() {
        WaveformAccumulator accumulator = new WaveformAccumulator(2, 2);
        for (int index = 0; index < 4; index++) accumulator.add(1d);
        accumulator.add(0d);
        WaveformData result = accumulator.finish(Duration.seconds(5));
        Assertions.assertThat(result.rmsAmplitudeAt(0)).isEqualTo(1d);
        Assertions.assertThat(result.rmsAmplitudeAt(1)).isCloseTo(Math.sqrt(2d / 3d), Offset.offset(0.000001d));
        Assertions.assertThat(result.peakAmplitudeAt(1)).isEqualTo(1d);
    }

    @Test
    void preservesSilenceAndConstantAmplitudeAcrossRepeatedCompaction() {
        WaveformAccumulator silent = new WaveformAccumulator(32, 2);
        WaveformAccumulator constant = new WaveformAccumulator(32, 2);
        for (int index = 0; index < 100_000; index++) {
            silent.add(0d);
            constant.add(0.5d);
        }
        WaveformData silence = silent.finish(Duration.seconds(10));
        WaveformData tone = constant.finish(Duration.seconds(10));
        Assertions.assertThat(silence.copyAmplitudes()).containsOnly(0d);
        Assertions.assertThat(tone.copyAmplitudes()).containsOnly(1d);
        Assertions.assertThat(tone.size()).isEqualTo(32);
        Assertions.assertThat(tone.getDuration()).isEqualTo(Duration.seconds(10));
    }
}
