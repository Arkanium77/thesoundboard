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
                "-tsb-waveform-disabled-color"
        );
    }

    @Test
    void keepsCssMetadataAvailableAtClassLevel() {
        List<CssMetaData<? extends Styleable, ?>> cssMetaData = WaveformSeekView.getClassCssMetaData();

        Assertions.assertThat(cssMetaData)
                .extracting(CssMetaData::getProperty)
                .contains("-tsb-waveform-active-color");
    }
}
