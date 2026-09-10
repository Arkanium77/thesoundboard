package app.ui;

import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/** Composes scrolling without changing skin-owned labels or viewport geometry. Animation is retained while its
 * distance is unchanged, stopped when clipped/hidden, and explicitly released by the owning view on disposal. */
public final class TitleScrollAnimation {
    public enum Style { TILE, CHIP, MINI }
    private final Pane viewport;
    private final Label label;
    private final Rectangle clip;
    private final Style style;
    private SequentialTransition animation;
    private double distance;

    public TitleScrollAnimation(Pane viewport, Label label, Rectangle clip, Style style) {
        this.viewport = viewport;
        this.label = label;
        this.clip = clip;
        this.style = style;
    }

    public void update(boolean hovered, double minimumHeight) {
        clip.setWidth(Math.max(viewport.getWidth(), 0d));
        clip.setHeight(Math.max(viewport.getHeight(), minimumHeight));
        double overflow = label.getLayoutBounds().getWidth() - viewport.getWidth();
        double threshold = style == Style.TILE ? 4d : style == Style.MINI ? 2d : 0d;
        if (!hovered || viewport.getWidth() <= 0d || overflow <= threshold || !UiViewport.isVisible(viewport)) {
            stop();
            return;
        }
        double nextDistance = overflow + (style == Style.CHIP ? 12d : 0d);
        if (animation != null && animation.getStatus() == Animation.Status.RUNNING && nextDistance == distance) return;
        stop();
        distance = nextDistance;
        double leftSeconds = switch (style) {
            case TILE -> Math.max(distance / 35d, 2.5d);
            case CHIP -> Math.max(distance / 28d, 2.4d);
            case MINI -> Math.max(1.2d, distance * 0.035d);
        };
        double rightSeconds = style == Style.TILE ? 1.2d : style == Style.CHIP ? 0.01d : Math.max(0.6d, distance * 0.018d);
        TranslateTransition left = new TranslateTransition(Duration.seconds(leftSeconds), label);
        left.setFromX(0d);
        left.setToX(-distance);
        TranslateTransition right = new TranslateTransition(Duration.seconds(rightSeconds), label);
        right.setFromX(-distance);
        right.setToX(0d);
        animation = new SequentialTransition(new PauseTransition(Duration.seconds(1d)), left,
                new PauseTransition(Duration.seconds(style == Style.TILE ? 0.8d : style == Style.CHIP ? 0.7d : 1d)), right);
        if (style == Style.TILE) animation.getChildren().add(new PauseTransition(Duration.seconds(0.8d)));
        animation.setCycleCount(Animation.INDEFINITE);
        animation.currentTimeProperty().addListener(observable -> {
            if (!UiViewport.isVisible(viewport)) stop();
        });
        animation.playFromStart();
    }

    public void stop() {
        if (animation != null) {
            SequentialTransition previous = animation;
            animation = null;
            previous.stop();
        }
        label.setTranslateX(0d);
    }
}
