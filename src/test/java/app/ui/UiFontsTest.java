package app.ui;

import app.skin.SkinDescriptor;
import app.skin.SkinManifest;
import javafx.scene.layout.Pane;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class UiFontsTest {
    @Test
    void leavesJavaFxSystemFontAndFallbackSelectionUntouched() {
        SkinManifest manifest = new SkinManifest();
        manifest.getFonts().setRegular(SkinManifest.SYSTEM_FONT);
        manifest.getFonts().setBold(SkinManifest.SYSTEM_FONT);
        manifest.getFonts().setItalic(SkinManifest.SYSTEM_FONT);
        manifest.getFonts().setBoldItalic(SkinManifest.SYSTEM_FONT);
        SkinDescriptor skin = new SkinDescriptor(manifest, "/skins/default/", null);
        Pane root = new Pane();
        root.setStyle("-fx-font-family: 'Temporary';");

        UiFonts.configure(skin);
        UiFonts.applyApplicationFont(root);

        Assertions.assertThat(root.getStyle()).isEmpty();
    }
}
