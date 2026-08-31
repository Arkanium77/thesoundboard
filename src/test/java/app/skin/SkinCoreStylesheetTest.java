package app.skin;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

class SkinCoreStylesheetTest {
    @Test
    void keepsStatusBarStylingAvailableForEverySkin() throws IOException {
        try (InputStream stream = SkinCoreStylesheetTest.class.getResourceAsStream("/skins/core.css")) {
            Assertions.assertThat(stream).isNotNull();
            String stylesheet = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            Assertions.assertThat(stylesheet)
                    .contains(".soundboard-root")
                    .contains(".status-bar")
                    .contains("-fx-background-color: -fx-background")
                    .contains(".status-bar .scale-adjust-button");
        }
    }
}
