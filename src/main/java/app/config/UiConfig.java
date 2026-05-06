package app.config;

public class UiConfig {
    private double minWidth = 1280d;
    private double minHeight = 760d;
    private double treeWidth = 360d;
    private double trackTileWidth = 260d;
    private double trackTileHeight = 195d;
    private double minTileScale = 0.6d;
    private double maxTileScale = 1.8d;
    private double tileZoomStep = 0.1d;
    private int queueWidthInTiles = 3;

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

    public double getMinTileScale() {
        return minTileScale;
    }

    public void setMinTileScale(double minTileScale) {
        this.minTileScale = minTileScale;
    }

    public double getMaxTileScale() {
        return maxTileScale;
    }

    public void setMaxTileScale(double maxTileScale) {
        this.maxTileScale = maxTileScale;
    }

    public double getTileZoomStep() {
        return tileZoomStep;
    }

    public void setTileZoomStep(double tileZoomStep) {
        this.tileZoomStep = tileZoomStep;
    }

    public int getQueueWidthInTiles() {
        return queueWidthInTiles;
    }

    public void setQueueWidthInTiles(int queueWidthInTiles) {
        this.queueWidthInTiles = queueWidthInTiles;
    }
}
