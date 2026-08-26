package app.ui;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.layout.Region;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class UiScalePaneTest {
    @Test
    void laysOutContentAtNativeSizeAndChangesInheritedFontSize() {
        Region content = new Region();
        DoubleProperty scale = new SimpleDoubleProperty(1.2d);
        UiScalePane scalePane = new UiScalePane(content, scale);

        scalePane.resize(1200d, 720d);
        scalePane.layout();

        Assertions.assertThat(content.getWidth()).isEqualTo(1200d);
        Assertions.assertThat(content.getHeight()).isEqualTo(720d);
        Assertions.assertThat(content.getTransforms()).isEmpty();
        Assertions.assertThat(scalePane.getStyle()).contains("15.6px");

        scale.set(1.5d);
        scalePane.layout();

        Assertions.assertThat(content.getWidth()).isEqualTo(1200d);
        Assertions.assertThat(content.getHeight()).isEqualTo(720d);
        Assertions.assertThat(scalePane.getStyle()).contains("19.5px");
        Assertions.assertThat(scalePane.getScale()).isEqualTo(1.5d);
        Assertions.assertThat(scalePane.getWidth()).isEqualTo(1200d);
        Assertions.assertThat(scalePane.getHeight()).isEqualTo(720d);
    }

    @Test
    void keepsNativeContentBoundsAtMinimumAndMaximumScale() {
        Region content = new Region();
        DoubleProperty scale = new SimpleDoubleProperty(0.25d);
        UiScalePane scalePane = new UiScalePane(content, scale);
        scalePane.resize(1280d, 760d);

        scalePane.layout();

        Assertions.assertThat(content.getWidth()).isEqualTo(1280d);
        Assertions.assertThat(content.getHeight()).isEqualTo(760d);
        Assertions.assertThat(scalePane.getStyle()).contains("3.25px");

        scale.set(2d);
        scalePane.layout();

        Assertions.assertThat(content.getWidth()).isEqualTo(1280d);
        Assertions.assertThat(content.getHeight()).isEqualTo(760d);
        Assertions.assertThat(scalePane.getStyle()).contains("26.0px");
        Assertions.assertThat(content.getTransforms()).isEmpty();
    }

}
