package app.ui;

import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;

public final class InsertionMarkers {
    private InsertionMarkers() { }

    public static AnchorPane create(boolean leftSide, boolean compact) {
        AnchorPane marker = new AnchorPane();
        marker.setPrefWidth((compact ? 8d : 12d));
        marker.setMinWidth((compact ? 8d : 12d));
        marker.setMaxWidth((compact ? 8d : 12d));
        marker.setVisible(false);
        marker.setMouseTransparent(true);

        Region vertical = createMarkerSegment((compact ? 3d : 4d), -1d);
        Region top = createMarkerSegment((compact ? 7d : 10d), (compact ? 3d : 4d));
        Region bottom = createMarkerSegment((compact ? 7d : 10d), (compact ? 3d : 4d));

        if (leftSide) {
            AnchorPane.setLeftAnchor(vertical, 0d);
            AnchorPane.setLeftAnchor(top, 0d);
            AnchorPane.setLeftAnchor(bottom, 0d);
        } else {
            AnchorPane.setRightAnchor(vertical, 0d);
            AnchorPane.setRightAnchor(top, 0d);
            AnchorPane.setRightAnchor(bottom, 0d);
        }

        AnchorPane.setTopAnchor(vertical, 0d);
        AnchorPane.setBottomAnchor(vertical, 0d);
        AnchorPane.setTopAnchor(top, 0d);
        AnchorPane.setBottomAnchor(bottom, 0d);

        marker.getChildren().addAll(vertical, top, bottom);
        return marker;
    }

    private static Region createMarkerSegment(double width, double height) {
        Region region = new Region();
        region.getStyleClass().add("insertion-marker");
        region.setMinWidth(width);
        region.setPrefWidth(width);
        region.setMaxWidth(width);
        if (height > 0d) {
            region.setMinHeight(height);
            region.setPrefHeight(height);
            region.setMaxHeight(height);
        }
        return region;
    }

    public static void scale(AnchorPane marker, double scale, boolean compact) {
        width(marker, (compact ? 8d : 12d) * scale);
        width((Region) marker.getChildren().get(0), (compact ? 3d : 4d) * scale);
        for (int index = 1; index < 3; index++) {
            Region region = (Region) marker.getChildren().get(index);
            width(region, (compact ? 7d : 10d) * scale);
            double height = (compact ? 3d : 4d) * scale;
            region.setMinHeight(height);
            region.setPrefHeight(height);
            region.setMaxHeight(height);
        }
    }

    private static void width(Region region, double width) {
        region.setMinWidth(width);
        region.setPrefWidth(width);
        region.setMaxWidth(width);
    }
}
