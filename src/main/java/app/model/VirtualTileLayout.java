package app.model;

public enum VirtualTileLayout {
    TWO_BY_TWO(2, 2),
    TWO_BY_THREE(2, 3),
    THREE_BY_THREE(3, 3),
    FOUR_BY_FOUR(4, 4);

    private final int columns;
    private final int rows;

    VirtualTileLayout(int columns, int rows) {
        this.columns = columns;
        this.rows = rows;
    }

    public int getColumns() { return columns; }
    public int getRows() { return rows; }
    public int getCapacity() { return columns * rows; }
}
