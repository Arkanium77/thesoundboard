package app.model;

import java.util.UUID;

public class VirtualTileTrack extends WorkspaceTrack {
    public VirtualTileTrack() {
    }

    public VirtualTileTrack(UUID id, UUID audioFileId, int order, double volume) {
        super(id, audioFileId, order, volume, false);
    }
}
