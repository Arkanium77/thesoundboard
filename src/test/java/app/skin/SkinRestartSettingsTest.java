package app.skin;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class SkinRestartSettingsTest {
    @Test
    void reportsOnlyRestartSensitiveDifferences() {
        SkinRestartSettings current = new SkinRestartSettings(RenderingMode.AUTOMATIC);

        Assertions.assertThat(new SkinRestartSettings(RenderingMode.AUTOMATIC).differencesFrom(current)).isEmpty();
        Assertions.assertThat(new SkinRestartSettings(RenderingMode.SOFTWARE).differencesFrom(current))
                .containsExactly("graphics rendering mode");
    }
}
