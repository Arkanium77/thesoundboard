package app.config;

public class UiConfig {
    private double minWidth = 1280d;
    private double minHeight = 760d;
    private double treeWidth = 360d;
    private double trackTileWidth = 260d;
    private double trackTileHeight = 195d;

    public double getMinWidth() {
        return minWidth;
    }

    public void setMinWidth(double minWidth) {
        this.minWidth = minWidth;
    }

    public double getMinHeight() {
        return minHeight;
    }

    public void setMinHeight(double minHeight) {
        this.minHeight = minHeight;
    }

    public double getTreeWidth() {
        return treeWidth;
    }

    public void setTreeWidth(double treeWidth) {
        this.treeWidth = treeWidth;
    }

    public double getTrackTileWidth() {
        return trackTileWidth;
    }

    public void setTrackTileWidth(double trackTileWidth) {
        this.trackTileWidth = trackTileWidth;
    }

    public double getTrackTileHeight() {
        return trackTileHeight;
    }

    public void setTrackTileHeight(double trackTileHeight) {
        this.trackTileHeight = trackTileHeight;
    }
}
