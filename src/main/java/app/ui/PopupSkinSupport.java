package app.ui;

import javafx.collections.ListChangeListener;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.stage.PopupWindow;
import javafx.stage.Window;

import java.util.LinkedHashSet;
import java.util.Set;

/** Popup controls have their own scene and cannot inherit the skin's root palette from the owner. Copy the current
 * owner styles on each showing, including nested submenus and after a same-version skin update. Submenu skins read the owner scene stylesheet list,
 * so both the popup scene and its root must carry the palette; root-only styles leave submenus on Modena colors. A process-wide listener retains no
 * popup or skin service; ordinary windows and owners outside the application are ignored. */
public final class PopupSkinSupport {
    private static boolean installed;

    private PopupSkinSupport() { }

    public static void install() {
        if (installed) return;
        installed = true;
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                for (Window window : change.getAddedSubList()) {
                    if (window instanceof PopupWindow popup) apply(popup);
                }
            }
        });
    }

    private static void apply(PopupWindow popup) {
        Window owner = popup.getOwnerWindow();
        if (owner == null || owner.getScene() == null || popup.getScene() == null) return;
        while (owner instanceof PopupWindow parent && parent.getOwnerWindow() != null) owner = parent.getOwnerWindow();
        Scene source = owner.getScene();
        Parent sourceRoot = source.getRoot();
        if (!sourceRoot.getStyleClass().contains("soundboard-root")) {
            Node content = sourceRoot.lookup(".soundboard-root");
            if (!(content instanceof Parent parent)) return;
            sourceRoot = parent;
        }
        Parent root = popup.getScene().getRoot();
        Set<String> styles = new LinkedHashSet<>(source.getStylesheets());
        styles.addAll(sourceRoot.getStylesheets());
        popup.getScene().getStylesheets().setAll(styles);
        root.getStylesheets().setAll(styles);
        if (!root.getStyleClass().contains("soundboard-root")) root.getStyleClass().add("soundboard-root");
        root.setStyle(sourceRoot.getStyle() + (sourceRoot == source.getRoot() ? "" : ";" + source.getRoot().getStyle()));
    }
}
