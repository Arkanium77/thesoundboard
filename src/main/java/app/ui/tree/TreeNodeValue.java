package app.ui.tree;

import java.util.UUID;

public class TreeNodeValue {
    private final TreeNodeType type;
    private final String label;
    private final UUID audioFileId;
    private final UUID virtualFolderId;
    private final UUID parentVirtualFolderId;
    private final boolean missing;

    public TreeNodeValue(
            TreeNodeType type,
            String label,
            UUID audioFileId,
            UUID virtualFolderId,
            UUID parentVirtualFolderId,
            boolean missing
    ) {
        this.type = type;
        this.label = label;
        this.audioFileId = audioFileId;
        this.virtualFolderId = virtualFolderId;
        this.parentVirtualFolderId = parentVirtualFolderId;
        this.missing = missing;
    }

    public TreeNodeType getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public UUID getAudioFileId() {
        return audioFileId;
    }

    public UUID getVirtualFolderId() {
        return virtualFolderId;
    }

    public UUID getParentVirtualFolderId() {
        return parentVirtualFolderId;
    }

    public boolean isMissing() {
        return missing;
    }
}
