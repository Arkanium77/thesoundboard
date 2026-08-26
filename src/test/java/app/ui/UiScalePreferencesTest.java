package app.ui;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class UiScalePreferencesTest {
    @Test
    void clampsScaleToConfiguredRange() {
        Assertions.assertThat(UiScalePreferences.clamp(0.1d, 0.25d, 2d)).isEqualTo(0.25d);
        Assertions.assertThat(UiScalePreferences.clamp(1.2d, 0.25d, 2d)).isEqualTo(1.2d);
        Assertions.assertThat(UiScalePreferences.clamp(3d, 0.25d, 2d)).isEqualTo(2d);
    }

    @Test
    void replacesNonFiniteScaleWithMinimum() {
        Assertions.assertThat(UiScalePreferences.clamp(Double.NaN, 0.25d, 2d)).isEqualTo(0.25d);
        Assertions.assertThat(UiScalePreferences.clamp(Double.POSITIVE_INFINITY, 0.25d, 2d)).isEqualTo(0.25d);
    }
}
