package app.skin;

import java.util.ArrayList;
import java.util.List;

public record SkinRestartSettings(RenderingMode renderingMode) {
    public static SkinRestartSettings from(SkinManifest manifest) {
        return new SkinRestartSettings(SkinBootstrap.resolveRenderingMode(manifest));
    }

    public List<String> differencesFrom(SkinRestartSettings current) {
        List<String> differences = new ArrayList<>();
        if (renderingMode != current.renderingMode) {
            differences.add("graphics rendering mode");
        }
        return List.copyOf(differences);
    }
}
