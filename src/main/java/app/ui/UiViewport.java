package app.ui;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;

public final class UiViewport {
    private UiViewport() { }

    /** A scene can retain nodes clipped by a ScrollPane or a minimized window; these must not own animation work. */
    public static boolean isVisible(Node target) {
        if (target.getScene() == null || target.getScene().getWindow() == null
                || !target.getScene().getWindow().isShowing()) return false;
        if (target.getScene().getWindow() instanceof Stage stage && stage.isIconified()) return false;
        for (Node node = target; node != null; node = node.getParent()) {
            if (!node.isVisible()) return false;
            if (node instanceof ScrollPane scrollPane) {
                Node viewport = scrollPane.lookup(".viewport");
                if (viewport != null && !target.localToScene(target.getBoundsInLocal()).intersects(
                        viewport.localToScene(viewport.getBoundsInLocal()))) return false;
            }
        }
        return true;
    }
}
