package app.ui;

import javafx.beans.value.ObservableDoubleValue;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

public class UiScalePane extends StackPane {
    private static final double BASE_FONT_SIZE = 13d;

    private double scale;

    public UiScalePane(Region content, ObservableDoubleValue scaleValue) {
        getChildren().add(content);
        setScale(scaleValue.doubleValue());
        scaleValue.addListener((observable, oldValue, newValue) -> setScale(newValue.doubleValue()));
    }

    public double getScale() {
        return scale;
    }

    private void setScale(double scale) {
        this.scale = requirePositiveScale(scale);
        setStyle("-fx-font-size: " + (BASE_FONT_SIZE * scale) + "px;");
    }

    private double requirePositiveScale(double scale) {
        if (!Double.isFinite(scale) || scale <= 0d) {
            throw new IllegalArgumentException("UI scale must be a positive finite number");
        }
        return scale;
    }
}
