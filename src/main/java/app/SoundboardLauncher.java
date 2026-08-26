package app;

import app.skin.SkinBootstrap;
import javafx.application.Application;

public final class SoundboardLauncher {
    private SoundboardLauncher() {
    }

    public static void main(String[] args) {
        SkinBootstrap.applySelectedSkinRendering();
        Application.launch(SoundboardApplication.class, args);
    }
}
