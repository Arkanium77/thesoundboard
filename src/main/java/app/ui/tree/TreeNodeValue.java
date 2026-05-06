package app.ui.tree;

import java.util.UUID;

public class TreeNodeValue {
    private final TreeNodeType type;
    private final String label;
    private final UUID audioFileId;
    private final boolean missing;

    public TreeNodeValue(
            TreeNodeType type,
            String label,
            UUID audioFileId,
            boolean missing
    ) {
        this.type = type;
        this.label = label;
        this.audioFileId = audioFileId;
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

    public boolean isMissing() {
        return missing;
    }
}
