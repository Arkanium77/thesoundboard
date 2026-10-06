package app.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

import java.util.function.Consumer;

/** Shared confirmation shell for package-source deletion and project unlinking. Callers supply consequence text
 * and styling; closing the dialog cancels, and this helper never performs the action or touches source files. */
public final class ConfirmationDialogs {
    private ConfirmationDialogs() { }

    public static boolean confirm(Window owner, Consumer<Alert> style, Alert.AlertType type,
                                  String title, String header, String message) {
        Alert alert = new Alert(type, message, ButtonType.CANCEL, ButtonType.OK);
        alert.initOwner(owner);
        style.accept(alert);
        alert.setTitle(title);
        alert.setHeaderText(header);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
}
