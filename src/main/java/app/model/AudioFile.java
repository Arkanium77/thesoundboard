package app.model;

import java.util.UUID;

public class AudioFile {
    private UUID id;
    private String relativePath;
    private String displayName;
    private boolean missing;

    public AudioFile() {
    }

    public AudioFile(UUID id, String relativePath, String displayName, boolean missing) {
        this.id = id;
        this.relativePath = relativePath;
        this.displayName = displayName;
        this.missing = missing;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isMissing() {
        return missing;
    }

    public void setMissing(boolean missing) {
        this.missing = missing;
    }
}
