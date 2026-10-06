package app.ui.tree;

import java.util.UUID;

public class TreeNodeValue {
    private final TreeNodeType type;
    private final String label;
    private final UUID audioFileId;
    private final boolean missing;
    private final UUID linkedFolderId;

    public TreeNodeValue(
            TreeNodeType type,
            String label,
            UUID audioFileId,
            boolean missing
    ) {
        this(type, label, audioFileId, missing, null);
    }

    public TreeNodeValue(TreeNodeType type, String label, UUID audioFileId, boolean missing, UUID linkedFolderId) {
        this.type = type;
        this.label = label;
        this.audioFileId = audioFileId;
        this.missing = missing;
        this.linkedFolderId = linkedFolderId;
    }

    public UUID getLinkedFolderId() { return linkedFolderId; }

    public TreeNodeType getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public UUID getAudioFileId() {
        return audioFileId;
    }

    public boolean isMissing() {
        return missing;
    }
}
