package app.skin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class SkinManifest {
    public static final String SYSTEM_FONT = "system";
    private UUID uid;
    private String name;
    private int skinVersion = 1;
    private String stylesheet;
    private Fonts fonts = new Fonts();
    private Map<String, String> icons = new LinkedHashMap<>();
    private Rendering rendering = new Rendering();

    public UUID getUid() {
        return uid;
    }

    public void setUid(UUID uid) {
        this.uid = uid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSkinVersion() {
        return skinVersion;
    }

    public void setSkinVersion(int skinVersion) {
        this.skinVersion = skinVersion;
    }

    public String getStylesheet() {
        return stylesheet;
    }

    public void setStylesheet(String stylesheet) {
        this.stylesheet = stylesheet;
    }

    public Fonts getFonts() {
        return fonts;
    }

    public void setFonts(Fonts fonts) {
        this.fonts = fonts == null ? new Fonts() : fonts;
    }

    public Map<String, String> getIcons() {
        return icons;
    }

    public void setIcons(Map<String, String> icons) {
        this.icons = icons == null ? new LinkedHashMap<>() : new LinkedHashMap<>(icons);
    }

    public Rendering getRendering() {
        return rendering;
    }

    public void setRendering(Rendering rendering) {
        this.rendering = rendering == null ? new Rendering() : rendering;
    }

    public static class Fonts {
        private String regular;
        private String bold;
        private String italic;
        private String boldItalic;
        private boolean allowFallback = true;

        public String getRegular() {
            return regular;
        }

        public void setRegular(String regular) {
            this.regular = regular;
        }

        public String getBold() {
            return bold;
        }

        public void setBold(String bold) {
            this.bold = bold;
        }

        public String getItalic() {
            return italic;
        }

        public void setItalic(String italic) {
            this.italic = italic;
        }

        public String getBoldItalic() {
            return boldItalic;
        }

        public void setBoldItalic(String boldItalic) {
            this.boldItalic = boldItalic;
        }

        public boolean isAllowFallback() {
            return allowFallback;
        }

        public void setAllowFallback(boolean allowFallback) {
            this.allowFallback = allowFallback;
        }
    }

    public static class Rendering {
        private RenderingMode windows = RenderingMode.AUTOMATIC;
        private RenderingMode linux = RenderingMode.AUTOMATIC;
        private RenderingMode macos = RenderingMode.AUTOMATIC;

        public RenderingMode getWindows() {
            return windows;
        }

        public void setWindows(RenderingMode windows) {
            this.windows = windows == null ? RenderingMode.AUTOMATIC : windows;
        }

        public RenderingMode getLinux() {
            return linux;
        }

        public void setLinux(RenderingMode linux) {
            this.linux = linux == null ? RenderingMode.AUTOMATIC : linux;
        }

        public RenderingMode getMacos() {
            return macos;
        }

        public void setMacos(RenderingMode macos) {
            this.macos = macos == null ? RenderingMode.AUTOMATIC : macos;
        }
    }
}
