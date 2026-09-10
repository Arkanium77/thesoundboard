package app.ui;

import javafx.scene.control.Slider;

import java.util.function.DoubleConsumer;

/**
 * Applies volume continuously but requests persistence at the end of mouse or keyboard input. Programmatic slider
 * updates (for example queue selection) must not request saves, and a drag release must not save twice through both
 * valueChanging and mouseReleased listeners. All volume controls share this input boundary.
 */
public final class VolumeSliderSupport {
    private VolumeSliderSupport() {
    }

    public static void bind(Slider slider, DoubleConsumer volumeChanged, Runnable save) {
        slider.valueProperty().addListener((observable, oldValue, newValue) -> volumeChanged.accept(newValue.doubleValue() / 100d));
        slider.setOnMouseReleased(event -> save.run());
        slider.setOnKeyReleased(event -> save.run());
    }
}
