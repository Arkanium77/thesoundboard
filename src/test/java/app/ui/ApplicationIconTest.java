package app.ui;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

class ApplicationIconTest {
    @Test
    void providesSquareApplicationIconWithTransparency() throws IOException {
        try (InputStream iconStream = ApplicationIconTest.class.getResourceAsStream("/icons/app-icon.png")) {
            Assertions.assertThat(iconStream).isNotNull();

            BufferedImage icon = ImageIO.read(iconStream);

            Assertions.assertThat(icon.getWidth()).isEqualTo(512);
            Assertions.assertThat(icon.getHeight()).isEqualTo(512);
            Assertions.assertThat(icon.getColorModel().hasAlpha()).isTrue();
            Assertions.assertThat(icon.getRGB(0, 0) >>> 24).isZero();
        }
    }
}
