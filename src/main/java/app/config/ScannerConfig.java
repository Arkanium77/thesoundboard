package app.config;

import java.util.ArrayList;
import java.util.List;

public class ScannerConfig {
    private List<String> supportedExtensions = new ArrayList<>(List.of("mp3"));

    public List<String> getSupportedExtensions() {
        return supportedExtensions;
    }

    public void setSupportedExtensions(List<String> supportedExtensions) {
        this.supportedExtensions = supportedExtensions == null ? new ArrayList<>() : new ArrayList<>(supportedExtensions);
    }
}
