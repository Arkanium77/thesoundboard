package app.ui.waveform;

import javafx.css.CssMetaData;
import javafx.css.Styleable;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class WaveformSeekViewTest {
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
