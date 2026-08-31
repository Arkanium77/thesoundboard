package app.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WorkspaceVirtualTile {
    private UUID id;
    private int order;
    private VirtualTileLayout layout = VirtualTileLayout.TWO_BY_TWO;
    private List<VirtualTileTrack> tracks = new ArrayList<>();

    public WorkspaceVirtualTile() {
    }

    public WorkspaceVirtualTile(UUID id, int order, VirtualTileLayout layout) {
        this.id = id;
        this.order = order;
        this.layout = layout == null ? VirtualTileLayout.TWO_BY_TWO : layout;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }
    public VirtualTileLayout getLayout() { return layout == null ? VirtualTileLayout.TWO_BY_TWO : layout; }
    public void setLayout(VirtualTileLayout layout) { this.layout = layout == null ? VirtualTileLayout.TWO_BY_TWO : layout; }
    public List<VirtualTileTrack> getTracks() { return tracks; }
    public void setTracks(List<VirtualTileTrack> tracks) {
        this.tracks = tracks == null ? new ArrayList<>() : new ArrayList<>(tracks);
    }
}
