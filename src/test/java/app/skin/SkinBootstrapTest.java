package app.skin;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class SkinBootstrapTest {
    @Test
    void resolvesRenderingModeForEverySupportedDesktopPlatform() {
        SkinManifest manifest = new SkinManifest();
        manifest.getRendering().setWindows(RenderingMode.SOFTWARE);
        manifest.getRendering().setLinux(RenderingMode.HARDWARE);
        manifest.getRendering().setMacos(RenderingMode.AUTOMATIC);

        Assertions.assertThat(SkinBootstrap.resolveRenderingMode(manifest, "Windows 11")).isEqualTo(RenderingMode.SOFTWARE);
        Assertions.assertThat(SkinBootstrap.resolveRenderingMode(manifest, "Linux")).isEqualTo(RenderingMode.HARDWARE);
        Assertions.assertThat(SkinBootstrap.resolveRenderingMode(manifest, "Mac OS X")).isEqualTo(RenderingMode.AUTOMATIC);
    }
}
