package app.ui;

import javafx.scene.Node;
import javafx.scene.layout.Region;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class UiIconsTest {
    @Test
    void exposesScaledLayoutBoundsInsteadOfOnlyTransformingSvgGeometry() {
        Node icon = UiIcons.play();
        Region iconRegion = (Region) icon;

        UiIcons.resize(icon, 0.25d);

        Assertions.assertThat(iconRegion.getMinWidth()).isEqualTo(4d);
        Assertions.assertThat(iconRegion.getPrefWidth()).isEqualTo(4d);
        Assertions.assertThat(iconRegion.getMaxWidth()).isEqualTo(4d);
        Assertions.assertThat(iconRegion.getPrefHeight()).isEqualTo(4d);
        Assertions.assertThat(icon.getTransforms()).isEmpty();

        UiIcons.resize(icon, 2d);

        Assertions.assertThat(iconRegion.getPrefWidth()).isEqualTo(32d);
        Assertions.assertThat(iconRegion.getPrefHeight()).isEqualTo(32d);
    }
}
