package app.skin;

import java.util.Locale;
import java.util.UUID;

public final class SkinBootstrap {
    private static UUID startupSkinUid = SkinRepository.DEFAULT_SKIN_UID;
    private static RenderingMode startupRenderingMode = RenderingMode.AUTOMATIC;

    private SkinBootstrap() {
    }

    public static void applySelectedSkinRendering() {
        SkinPackageInstaller.applyPendingOperations(new SkinRepository());
        SkinPreferences preferences = new SkinPreferences();
        SkinDescriptor skin = new SkinRepository().findSelected(preferences.loadSelectedSkinUid());
        RenderingMode renderingMode = resolveRenderingMode(skin.manifest());
        applyRenderingMode(renderingMode);
        startupSkinUid = skin.manifest().getUid();
        startupRenderingMode = renderingMode;
    }

    public static UUID getStartupSkinUid() {
        return startupSkinUid;
    }

    public static RenderingMode getStartupRenderingMode() {
        return startupRenderingMode;
    }

    public static RenderingMode resolveRenderingMode(SkinManifest manifest) {
        return resolveRenderingMode(manifest, System.getProperty("os.name", ""));
    }

    static RenderingMode resolveRenderingMode(SkinManifest manifest, String osName) {
        String operatingSystem = osName.toLowerCase(Locale.ROOT);
        if (operatingSystem.contains("windows")) {
            return manifest.getRendering().getWindows();
        }
        if (operatingSystem.contains("linux")) {
            return manifest.getRendering().getLinux();
        }
        if (operatingSystem.contains("mac")) {
            return manifest.getRendering().getMacos();
        }
        return RenderingMode.AUTOMATIC;
    }

    static void applyRenderingMode(RenderingMode renderingMode) {
        if (renderingMode == RenderingMode.SOFTWARE) {
            System.setProperty("prism.order", "sw");
        } else if (renderingMode == RenderingMode.HARDWARE) {
            String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            System.setProperty("prism.order", operatingSystem.contains("windows") ? "d3d" : "es2");
        } else {
            System.clearProperty("prism.order");
        }
    }
}
