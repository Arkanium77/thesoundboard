package app.ui.waveform;

import app.waveform.WaveformData;
import javafx.util.Duration;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import java.util.concurrent.atomic.AtomicReference;
import javafx.event.EventType;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class WaveformSeekViewTest {
    @Test
    void onlyPrimaryMouseInputMovesThePlayheadAndCommitsSeek() {
        WaveformSeekView view = new WaveformSeekView();
        view.resize(200d, 40d);
        view.setWaveformData(new WaveformData(new double[]{0.5d}, new double[]{0.3d}, Duration.seconds(10)));
        view.setPlaybackPosition(Duration.seconds(2), Duration.seconds(10));
        AtomicReference<Duration> seek = new AtomicReference<>();
        view.setSeekHandler(seek::set);
        view.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, MouseButton.SECONDARY, false));
        view.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, MouseButton.SECONDARY, false));
        view.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, MouseButton.SECONDARY, false));
        Assertions.assertThat(seek.get()).isNull();
        Assertions.assertThat(view.getDisplayedPosition()).isEqualTo(Duration.seconds(2));
        view.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY, true));
        view.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, MouseButton.SECONDARY, true));
        Assertions.assertThat(seek.get()).isNull();
        view.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY, false));
        Assertions.assertThat(seek.get()).isEqualTo(Duration.seconds(8));
        view.dispose();
    }

    private MouseEvent mouse(EventType<MouseEvent> type, MouseButton button, boolean primaryDown) {
        return new MouseEvent(type, 160d, 20d, 160d, 20d, button, 1,
                false, false, false, false, primaryDown, false, button == MouseButton.SECONDARY,
                false, button == MouseButton.SECONDARY, true, null);
    }

    @Test
    void exposesSkinCustomizableWaveformColors() {
        WaveformSeekView view = new WaveformSeekView();

        List<String> cssProperties = view.getCssMetaData().stream()
                .map(CssMetaData::getProperty)
                .toList();

        Assertions.assertThat(view.getStyleClass()).contains("waveform-seek-view");
        Assertions.assertThat(cssProperties).contains(
                "-tsb-waveform-active-color",
                "-tsb-waveform-idle-color",
                "-tsb-waveform-playhead-color",
                "-tsb-waveform-disabled-color",
                "-tsb-waveform-bottom-aligned",
                "-tsb-waveform-amplitude-coloring",
                "-tsb-waveform-smooth-coloring",
                "-tsb-waveform-low-color",
                "-tsb-waveform-mid-color",
                "-tsb-waveform-high-color",
                "-tsb-waveform-idle-low-color",
                "-tsb-waveform-idle-mid-color",
                "-tsb-waveform-idle-high-color",
                "-tsb-waveform-rendering",
                "-tsb-waveform-fire-low-color",
                "-tsb-waveform-fire-mid-color",
                "-tsb-waveform-fire-high-color",
                "-tsb-waveform-animation-speed",
                "-tsb-waveform-animation-amplitude"
        );
    }

    @Test
    void keepsCssMetadataAvailableAtClassLevel() {
        List<CssMetaData<? extends Styleable, ?>> cssMetaData = WaveformSeekView.getClassCssMetaData();

        Assertions.assertThat(cssMetaData)
                .extracting(CssMetaData::getProperty)
                .contains("-tsb-waveform-active-color");
    }

    @Test
    void decibelDisplayPreservesSilenceAndExpandsQuietNonZeroLevels() {
        double[] original = {0d, 0.001d, 0.01d, 0.1d, 0.5d, 1d};

        double[] adapted = WaveformSeekView.toDecibelDisplay(original);

        Assertions.assertThat(adapted).hasSize(original.length);
        Assertions.assertThat(adapted[0]).isZero();
        Assertions.assertThat(adapted[1]).isPositive();
        Assertions.assertThat(adapted).isSorted();
        Assertions.assertThat(adapted[3]).isGreaterThan(original[3]);
        Assertions.assertThat(adapted[4]).isLessThan(0.95d);
        Assertions.assertThat(adapted[adapted.length - 1]).isEqualTo(1d);
    }
}
