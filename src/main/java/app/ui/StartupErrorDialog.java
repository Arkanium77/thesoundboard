package app.ui;

import app.skin.SkinService;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.WindowEvent;

import java.util.concurrent.CompletableFuture;

/**
 * Uses the selected skin when a startup failure occurs before JavaFX initialization. A previously initialized or
 * failed toolkit is deliberately not restarted: JavaFX cannot safely be launched twice. The Java-only reporter
 * then uses an OS fallback. No default status graphic is shown; the original cause is stated once in the body.
 */
public final class StartupErrorDialog {
    private StartupErrorDialog() { }

    public static void show(String message) throws Exception {
        CompletableFuture<Void> closed = new CompletableFuture<>();
        Platform.startup(() -> {
            try {
                SkinService skins = new SkinService();
                skins.prepareUiResources();
                Alert alert = new Alert(Alert.AlertType.NONE, message, ButtonType.OK);
                alert.setTitle("The Soundboard");
                alert.setHeaderText(null);
                alert.getDialogPane().setPrefWidth(620d);
                skins.apply(alert.getDialogPane());
                alert.setOnHidden(event -> closed.complete(null));
                alert.show();
                // Also observe direct window closure (e.g. desktop/session management), not only Dialog.close().
                alert.getDialogPane().getScene().getWindow().addEventHandler(WindowEvent.WINDOW_HIDDEN,
                        event -> closed.complete(null));
            } catch (RuntimeException | Error exception) {
                closed.completeExceptionally(exception);
            }
        });
        closed.get();
        Platform.exit();
    }
}
