package app.ui.waveform;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

class WaveformSeekStateTest {
    @Test
    void keepsRequestedPositionUntilPlayerSettlesOrDeadlineExpires() {
        AtomicLong clock = new AtomicLong();
        WaveformSeekState state = new WaveformSeekState(clock::get);
        state.updatePlayback(1_000d, 10_000d, 100d);
        state.beginDrag(80d, 100d);
        Assertions.assertThat(state.release(80d, 100d)).isEqualTo(8_000d);
        state.updatePlayback(1_100d, 10_000d, 100d);
        Assertions.assertThat(state.displayedMillis()).isEqualTo(8_000d);
        clock.set(1_499_999_999L);
        state.updatePlayback(1_100d, 10_000d, 100d);
        Assertions.assertThat(state.displayedMillis()).isEqualTo(8_000d);
        clock.incrementAndGet();
        Assertions.assertThat(state.updatePlayback(1_100d, 10_000d, 100d)).isTrue();
        Assertions.assertThat(state.displayedMillis()).isEqualTo(1_100d);
    }

    @Test
    void newerDragAndSeekWinOverPreviousRequestAndItsDeadline() {
        AtomicLong clock = new AtomicLong();
        WaveformSeekState state = new WaveformSeekState(clock::get);
        state.updatePlayback(0d, 10_000d, 100d);
        state.beginDrag(80d, 100d);
        state.release(80d, 100d);
        clock.set(1_000_000_000L);
        state.beginDrag(20d, 100d);
        state.updatePlayback(8_000d, 10_000d, 100d);
        Assertions.assertThat(state.displayedMillis()).isEqualTo(2_000d);
        state.release(20d, 100d);
        clock.set(1_500_000_000L);
        state.updatePlayback(8_000d, 10_000d, 100d);
        Assertions.assertThat(state.displayedMillis()).isEqualTo(2_000d);
        Assertions.assertThat(state.updatePlayback(2_750d, 10_000d, 100d)).isTrue();
        Assertions.assertThat(state.displayedMillis()).isEqualTo(2_750d);
    }

    @Test
    void usesWaveformDurationBeforeMediaReadyAndClampsDragToCanvas() {
        WaveformSeekState state = new WaveformSeekState(() -> 0L);
        Assertions.assertThat(state.canSeek()).isFalse();
        state.setWaveformDuration(10_000d);
        Assertions.assertThat(state.canSeek()).isTrue();
        state.beginDrag(-10d, 100d);
        Assertions.assertThat(state.displayedMillis()).isZero();
        state.dragTo(200d, 100d);
        Assertions.assertThat(state.displayedMillis()).isEqualTo(10_000d);
        state.updatePlayback(0d, 9_900d, 100d);
        Assertions.assertThat(state.totalMillis()).isEqualTo(10_000d);
    }

    @Test
    void playbackWithinSamePixelDoesNotRequestRedraw() {
        WaveformSeekState state = new WaveformSeekState(() -> 0L);
        state.updatePlayback(1_000d, 10_000d, 100d);
        Assertions.assertThat(state.updatePlayback(1_099d, 10_000d, 100d)).isFalse();
        Assertions.assertThat(state.updatePlayback(1_100d, 10_000d, 100d)).isTrue();
        Assertions.assertThat(state.updatePlayback(1_100d, 11_000d, 100d)).isTrue();
    }
}
