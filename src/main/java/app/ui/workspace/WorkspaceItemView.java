package app.ui.workspace;

import java.util.UUID;

public interface WorkspaceItemView {
    UUID getWorkspaceItemId();

    void setInsertionMarker(WorkspaceInsertionMarker insertionMarker);
}
