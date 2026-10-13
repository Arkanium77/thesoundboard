package app.config;

import java.util.ArrayList;
import java.util.List;

public class ScannerConfig {
    private List<String> supportedExtensions = new ArrayList<>(List.of("mp3", "wav", "ogg", "flac", "opus", "aac", "m4a", "aiff", "aif"));

    public List<String> getSupportedExtensions() {
        return supportedExtensions;
    }

    public void setSupportedExtensions(List<String> supportedExtensions) {
        this.supportedExtensions = supportedExtensions == null ? new ArrayList<>() : new ArrayList<>(supportedExtensions);
    }
}
