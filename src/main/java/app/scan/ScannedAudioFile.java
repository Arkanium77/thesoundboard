package app.scan;

public class ScannedAudioFile {
    private final String relativePath;
    private final String displayName;

    public ScannedAudioFile(String relativePath, String displayName) {
        this.relativePath = relativePath;
        this.displayName = displayName;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getDisplayName() {
        return displayName;
    }
}
