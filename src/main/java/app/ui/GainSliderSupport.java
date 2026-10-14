package app.ui;

import app.localization.TextKey;
import app.localization.Texts;
import app.model.TrackGain;
import javafx.geometry.Insets;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.VBox;

import java.util.Locale;
import java.util.function.DoubleConsumer;

/**
 * All track owners use the same dB control and the skin's existing label/slider/menu rules. Rebuild on popup open
 * so transferred or externally changed tracks cannot show stale trim. Like volume, update playback continuously
 * and persist only at the input boundary or a double-click reset. Quantize every input path to half a dB; opening
 * a popup must neither change the model nor save the project. A reset consumes its release to avoid a second save.
 */
public final class GainSliderSupport {
    private GainSliderSupport() { }

    public static CustomMenuItem create(double gainDb, DoubleConsumer changed, Runnable save) {
        Slider slider = new Slider(TrackGain.MIN_DB, TrackGain.MAX_DB, gainDb);
        slider.getStyleClass().add("track-gain-slider");
        slider.setBlockIncrement(0.5d);
        slider.setMajorTickUnit(10d);
        slider.setMinorTickCount(19);
        slider.setSnapToTicks(true);
        Label label = new Label();
        updateLabel(label, slider.getValue());
        slider.valueProperty().addListener((observable, oldValue, newValue) -> {
            double stepped = Math.round(newValue.doubleValue() * 2d) / 2d;
            if (Math.abs(stepped - newValue.doubleValue()) > 0.000001d) { slider.setValue(stepped); return; }
            changed.accept(stepped);
            updateLabel(label, stepped);
        });
        slider.setOnMouseReleased(event -> save.run());
        slider.setOnKeyReleased(event -> save.run());
        VBox box = new VBox(4d, label, slider);
        box.getStyleClass().add("track-gain-control");
        box.setPadding(new Insets(6d));
        boolean[] resetting = {false};
        box.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                resetting[0] = true;
                slider.setValue(0d);
                save.run();
                event.consume();
            }
        });
        box.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (resetting[0] && event.getButton() == MouseButton.PRIMARY) {
                resetting[0] = false;
                event.consume();
            }
        });
        return new CustomMenuItem(box, false);
    }

    private static void updateLabel(Label label, double gainDb) {
        String value = gainDb == 0d ? "0" : String.format(Locale.ROOT, "%+.1f", gainDb);
        label.setText(Texts.format(TextKey.TRACK_GAIN, value));
    }
}
