package app.ui;

import app.skin.SkinDescriptor;
import app.skin.SkinManifest;
import javafx.scene.Parent;
import javafx.scene.text.Font;

import java.net.URL;

public final class UiFonts {
    private static URL regularFontResource;
    private static URL boldFontResource;
    private static URL italicFontResource;
    private static URL boldItalicFontResource;
    private static String fontFamily;
    private static boolean systemFont;
    private static boolean allowFallback;

    private UiFonts() {
    }

    public static void configure(SkinDescriptor skin) {
        allowFallback = skin.manifest().getFonts().isAllowFallback();
        if (SkinManifest.SYSTEM_FONT.equalsIgnoreCase(skin.manifest().getFonts().getRegular())) {
            regularFontResource = null;
            boldFontResource = null;
            italicFontResource = null;
            boldItalicFontResource = null;
            fontFamily = null;
            systemFont = true;
            return;
        }
        systemFont = false;
        regularFontResource = requireResource(skin, skin.manifest().getFonts().getRegular(), "regular");
        boldFontResource = skin.resolveResource(skin.manifest().getFonts().getBold());
        italicFontResource = skin.resolveResource(skin.manifest().getFonts().getItalic());
        boldItalicFontResource = skin.resolveResource(skin.manifest().getFonts().getBoldItalic());
        Font regularFont = loadFont(regularFontResource, "regular");
        loadOptionalFont(boldFontResource, "bold");
        loadOptionalFont(italicFontResource, "italic");
        loadOptionalFont(boldItalicFontResource, "boldItalic");
        fontFamily = regularFont.getFamily();
    }

    public static void applyApplicationFont(Parent root) {
        if (systemFont) {
            root.setStyle("");
            if (allowFallback) {
                SystemFontFallback.install(root);
            }
            return;
        }
        if (fontFamily == null) {
            throw new IllegalStateException("Skin fonts must be configured before creating the interface");
        }
        root.setStyle("-fx-font-family: '" + fontFamily + "';");
        if (allowFallback) {
            SystemFontFallback.install(root);
        }
    }

    private static URL requireResource(SkinDescriptor skin, String path, String role) {
        URL resource = skin.resolveResource(path);
        if (resource == null) {
            throw new IllegalStateException("Skin font is missing for role " + role + ": " + path);
        }
        return resource;
    }

    private static Font loadFont(URL resource, String role) {
        Font font = Font.loadFont(resource.toExternalForm(), Font.getDefault().getSize());
        if (font == null) {
            throw new IllegalStateException("Skin font could not be loaded for role " + role + ": " + resource);
        }
        return font;
    }

    private static void loadOptionalFont(URL resource, String role) {
        if (resource != null) {
            loadFont(resource, role);
        }
    }
}
