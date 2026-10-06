package app.scan;

import java.util.UUID;

public class ScannedAudioFile {
    private final String relativePath;
    private final String displayName;
    private final UUID linkedFolderId;

    public ScannedAudioFile(String relativePath, String displayName) {
        this(relativePath, displayName, null);
    }

    public ScannedAudioFile(String relativePath, String displayName, UUID linkedFolderId) {
        this.relativePath = relativePath;
        this.displayName = displayName;
        this.linkedFolderId = linkedFolderId;
    }

    public UUID getLinkedFolderId() { return linkedFolderId; }

    public String getRelativePath() {
        return relativePath;
    }

    public String getDisplayName() {
        return displayName;
    }
}
