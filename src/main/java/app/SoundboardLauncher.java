package app;

import app.skin.SkinBootstrap;
import app.audio.AudioEngineMode;
import app.audio.GstreamerRuntime;
import javafx.application.Application;

import java.util.List;

public final class SoundboardLauncher {
    private SoundboardLauncher() {
    }

    /** GStreamer configures native loader/environment paths before JavaFX starts its native worker threads.
     * Late lazy initialization would modify process environment concurrently with another native framework. */
    public static void main(String[] args) {
        SkinBootstrap.applySelectedSkinRendering();
        if (AudioEngineMode.fromArguments(List.of(args)) == AudioEngineMode.GSTREAMER) {
            GstreamerRuntime.initialize();
        }
        Application.launch(SoundboardApplication.class, args);
    }
}
