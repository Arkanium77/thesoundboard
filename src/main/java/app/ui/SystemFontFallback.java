package app.ui;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;

final class SystemFontFallback {
    private static final String INSTALLED_KEY = SystemFontFallback.class.getName() + ".installed";

    private SystemFontFallback() {
    }

    static void install(Parent root) {
        if (root.getScene() == null) {
            return;
        }
        Platform.runLater(() -> {
            root.applyCss();
            register(root);
        });
    }

    private static void register(Node node) {
        if (node.getProperties().putIfAbsent(INSTALLED_KEY, true) != null) {
            return;
        }
        if (node instanceof Labeled labeled) {
            register(labeled.textProperty(), labeled.fontProperty());
        } else if (node instanceof TextInputControl textInput) {
            register(textInput.textProperty(), textInput.fontProperty());
        } else if (node instanceof Text text) {
            register(text.textProperty(), text.fontProperty());
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(SystemFontFallback::register);
            parent.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change -> {
                while (change.next()) {
                    List<Node> addedNodes = new ArrayList<>(change.getAddedSubList());
                    Platform.runLater(() -> {
                        parent.applyCss();
                        addedNodes.forEach(SystemFontFallback::register);
                    });
                }
            });
        }
    }

    private static void register(StringProperty textProperty, ObjectProperty<Font> fontProperty) {
        if (fontProperty.isBound()) {
            return;
        }
        FontFallbackState state = new FontFallbackState(fontProperty.get());
        textProperty.addListener((observable, oldValue, newValue) -> apply(fontProperty, state, newValue));
        fontProperty.addListener((observable, oldValue, newValue) -> {
            if (state.applying) {
                return;
            }
            state.baseFont = newValue;
            apply(fontProperty, state, textProperty.get());
        });
        apply(fontProperty, state, textProperty.get());
    }

    private static void apply(ObjectProperty<Font> fontProperty, FontFallbackState state, String text) {
        state.applying = true;
        try {
            fontProperty.set(resolve(state.baseFont, text));
        } finally {
            state.applying = false;
        }
    }

    private static Font resolve(Font baseFont, String text) {
        String style = baseFont.getStyle().toLowerCase();
        FontWeight weight = style.contains("bold") ? FontWeight.BOLD : FontWeight.NORMAL;
        FontPosture posture = style.contains("italic") ? FontPosture.ITALIC : FontPosture.REGULAR;
        String family = SystemFontResolver.resolve(
                text,
                baseFont.getFamily(),
                weight == FontWeight.BOLD,
                posture == FontPosture.ITALIC
        );
        return Font.font(family, weight, posture, baseFont.getSize());
    }

    private static final class FontFallbackState {
        private Font baseFont;
        private boolean applying;

        private FontFallbackState(Font baseFont) {
            this.baseFont = baseFont;
        }
    }
}
