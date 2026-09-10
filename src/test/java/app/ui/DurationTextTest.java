package app.ui;

import javafx.util.Duration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class DurationTextTest {
    @Test
    void preservesClockFormatWithoutLocaleOrUnknownDurationArtifacts() {
        Assertions.assertThat(DurationText.format(Duration.seconds(65.9d))).isEqualTo("01:05");
        Assertions.assertThat(DurationText.format(Duration.seconds(6000))).isEqualTo("100:00");
        Assertions.assertThat(DurationText.format(Duration.UNKNOWN)).isEqualTo("00:00");
        Assertions.assertThat(DurationText.format(Duration.INDEFINITE)).isEqualTo("00:00");
        Assertions.assertThat(DurationText.format(Duration.seconds(-1))).isEqualTo("00:00");
    }
}
