package app.ui.waveform;

import app.ui.UiViewport;

import app.waveform.WaveformData;
import app.waveform.WaveformDisplayMode;
import app.waveform.WaveformDisplaySettings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.animation.AnimationTimer;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableBooleanProperty;
import javafx.css.SimpleStyleableDoubleProperty;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleablePropertyFactory;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.util.Duration;

import java.util.List;
import java.util.function.Consumer;

public class WaveformSeekView extends Region {
    private static final int PLACEHOLDER_BAR_COUNT = 48;
    private static final double BAR_GAP = 1d;
    private static final double MIN_BAR_WIDTH = 2d;
    private static final double SEEK_SETTLE_THRESHOLD_MILLIS = 750d;
    private static final long SEEK_SETTLE_TIMEOUT_NANOS = 1_500_000_000L;
    private static final double MID_AMPLITUDE_POSITION = 0.72d;
    private static final double ADAPTIVE_DECIBEL_FLOOR = -72d;
    private static final double ADAPTIVE_DISPLAY_EXPONENT = 1.35d;
    private static final long ANIMATION_FRAME_NANOS = 33_333_333L;

    private static final Color DEFAULT_ACTIVE_BAR_COLOR = Color.web("#4a83d8");
    private static final Color DEFAULT_IDLE_BAR_COLOR = Color.web("#c7d3ea");
    private static final Color DEFAULT_PLAYHEAD_COLOR = Color.web("#2d5fb2");
    private static final Color DEFAULT_DISABLED_BAR_COLOR = Color.web("#d7dce7");
    private static final Color DEFAULT_LOW_AMPLITUDE_COLOR = Color.web("#2f9e44");
    private static final Color DEFAULT_MID_AMPLITUDE_COLOR = Color.web("#f0b429");
    private static final Color DEFAULT_HIGH_AMPLITUDE_COLOR = Color.web("#d64545");
    private static final Color DEFAULT_IDLE_LOW_AMPLITUDE_COLOR = Color.web("#8ac796");
    private static final Color DEFAULT_IDLE_MID_AMPLITUDE_COLOR = Color.web("#d8c98b");
    private static final Color DEFAULT_IDLE_HIGH_AMPLITUDE_COLOR = Color.web("#d59b9b");
    private static final Color DEFAULT_FIRE_LOW_COLOR = Color.web("#8b1608");
    private static final Color DEFAULT_FIRE_MID_COLOR = Color.web("#ff6a00");
    private static final Color DEFAULT_FIRE_HIGH_COLOR = Color.web("#fff08a");

    private static final StyleablePropertyFactory<WaveformSeekView> STYLEABLE_PROPERTY_FACTORY =
            new StyleablePropertyFactory<>(Region.getClassCssMetaData());
    private static final CssMetaData<WaveformSeekView, Color> ACTIVE_BAR_COLOR_META_DATA =
            STYLEABLE_PROPERTY_FACTORY.createColorCssMetaData(
                    "-tsb-waveform-active-color",
                    view -> view.activeBarColor,
                    DEFAULT_ACTIVE_BAR_COLOR
            );
    private static final CssMetaData<WaveformSeekView, Color> IDLE_BAR_COLOR_META_DATA =
            STYLEABLE_PROPERTY_FACTORY.createColorCssMetaData(
                    "-tsb-waveform-idle-color",
                    view -> view.idleBarColor,
                    DEFAULT_IDLE_BAR_COLOR
            );
    private static final CssMetaData<WaveformSeekView, Color> PLAYHEAD_COLOR_META_DATA =
            STYLEABLE_PROPERTY_FACTORY.createColorCssMetaData(
                    "-tsb-waveform-playhead-color",
                    view -> view.playheadColor,
                    DEFAULT_PLAYHEAD_COLOR
            );
    private static final CssMetaData<WaveformSeekView, Color> DISABLED_BAR_COLOR_META_DATA =
            STYLEABLE_PROPERTY_FACTORY.createColorCssMetaData(
                    "-tsb-waveform-disabled-color",
                    view -> view.disabledBarColor,
                    DEFAULT_DISABLED_BAR_COLOR
            );
    private static final CssMetaData<WaveformSeekView, Boolean> BOTTOM_ALIGNED_META_DATA =
            STYLEABLE_PROPERTY_FACTORY.createBooleanCssMetaData(
                    "-tsb-waveform-bottom-aligned",
                    view -> view.bottomAligned,
                    false
            );
    private static final CssMetaData<WaveformSeekView, Boolean> AMPLITUDE_COLORING_META_DATA =
            STYLEABLE_PROPERTY_FACTORY.createBooleanCssMetaData(
                    "-tsb-waveform-amplitude-coloring",
                    view -> view.amplitudeColoring,
                    false
            );
    private static final CssMetaData<WaveformSeekView, Boolean> SMOOTH_COLORING_META_DATA =
            STYLEABLE_PROPERTY_FACTORY.createBooleanCssMetaData(
                    "-tsb-waveform-smooth-coloring",
                    view -> view.smoothColoring,
                    false
            );
    private static final CssMetaData<WaveformSeekView, Color> LOW_AMPLITUDE_COLOR_META_DATA =
            STYLEABLE_PROPERTY_FACTORY.createColorCssMetaData(
                    "-tsb-waveform-low-color",
                    view -> view.lowAmplitudeColor,
                    DEFAULT_LOW_AMPLITUDE_COLOR
            );
    private static final CssMetaData<WaveformSeekView, Color> MID_AMPLITUDE_COLOR_META_DATA =
            STYLEABLE_PROPERTY_FACTORY.createColorCssMetaData(
                    "-tsb-waveform-mid-color",
                    view -> view.midAmplitudeColor,
                    DEFAULT_MID_AMPLITUDE_COLOR
            );
    private static final CssMetaData<WaveformSeekView, Color> HIGH_AMPLITUDE_COLOR_META_DATA =
            STYLEABLE_PROPERTY_FACTORY.createColorCssMetaData(
                    "-tsb-waveform-high-color",
                    view -> view.highAmplitudeColor,
                    DEFAULT_HIGH_AMPLITUDE_COLOR
            );
    private static final CssMetaData<WaveformSeekView, Color> IDLE_LOW_AMPLITUDE_COLOR_META_DATA =
            STYLEABLE_PROPERTY_FACTORY.createColorCssMetaData(
                    "-tsb-waveform-idle-low-color",
                    view -> view.idleLowAmplitudeColor,
                    DEFAULT_IDLE_LOW_AMPLITUDE_COLOR
            );
    private static final CssMetaData<WaveformSeekView, Color> IDLE_MID_AMPLITUDE_COLOR_META_DATA =
            STYLEABLE_PROPERTY_FACTORY.createColorCssMetaData(
                    "-tsb-waveform-idle-mid-color",
                    view -> view.idleMidAmplitudeColor,
                    DEFAULT_IDLE_MID_AMPLITUDE_COLOR
            );
    private static final CssMetaData<WaveformSeekView, Color> IDLE_HIGH_AMPLITUDE_COLOR_META_DATA =
            STYLEABLE_PROPERTY_FACTORY.createColorCssMetaData(
                    "-tsb-waveform-idle-high-color",
                    view -> view.idleHighAmplitudeColor,
                    DEFAULT_IDLE_HIGH_AMPLITUDE_COLOR
            );
    private static final CssMetaData<WaveformSeekView, WaveformRendering> RENDERING_META_DATA =
            STYLEABLE_PROPERTY_FACTORY.createEnumCssMetaData(
                    WaveformRendering.class, "-tsb-waveform-rendering", view -> view.rendering, WaveformRendering.BARS
            );
    private static final CssMetaData<WaveformSeekView, Color> FIRE_LOW_COLOR_META_DATA =
            STYLEABLE_PROPERTY_FACTORY.createColorCssMetaData(
                    "-tsb-waveform-fire-low-color", view -> view.fireLowColor, DEFAULT_FIRE_LOW_COLOR
            );
    private static final CssMetaData<WaveformSeekView, Color> FIRE_MID_COLOR_META_DATA =
            STYLEABLE_PROPERTY_FACTORY.createColorCssMetaData(
                    "-tsb-waveform-fire-mid-color", view -> view.fireMidColor, DEFAULT_FIRE_MID_COLOR
            );
    private static final CssMetaData<WaveformSeekView, Color> FIRE_HIGH_COLOR_META_DATA =
            STYLEABLE_PROPERTY_FACTORY.createColorCssMetaData(
                    "-tsb-waveform-fire-high-color", view -> view.fireHighColor, DEFAULT_FIRE_HIGH_COLOR
            );
    private static final CssMetaData<WaveformSeekView, Number> ANIMATION_SPEED_META_DATA =
            STYLEABLE_PROPERTY_FACTORY.createSizeCssMetaData(
                    "-tsb-waveform-animation-speed", view -> view.animationSpeed, 1d
            );
    private static final CssMetaData<WaveformSeekView, Number> ANIMATION_AMPLITUDE_META_DATA =
            STYLEABLE_PROPERTY_FACTORY.createSizeCssMetaData(
                    "-tsb-waveform-animation-amplitude", view -> view.animationAmplitude, 0.03d
            );

    private final Canvas canvas = new Canvas();
    private final StyleableObjectProperty<Color> activeBarColor = new SimpleStyleableObjectProperty<>(
            ACTIVE_BAR_COLOR_META_DATA, this, "activeBarColor", DEFAULT_ACTIVE_BAR_COLOR
    );
    private final StyleableObjectProperty<Color> idleBarColor = new SimpleStyleableObjectProperty<>(
            IDLE_BAR_COLOR_META_DATA, this, "idleBarColor", DEFAULT_IDLE_BAR_COLOR
    );
    private final StyleableObjectProperty<Color> playheadColor = new SimpleStyleableObjectProperty<>(
            PLAYHEAD_COLOR_META_DATA, this, "playheadColor", DEFAULT_PLAYHEAD_COLOR
    );
    private final StyleableObjectProperty<Color> disabledBarColor = new SimpleStyleableObjectProperty<>(
            DISABLED_BAR_COLOR_META_DATA, this, "disabledBarColor", DEFAULT_DISABLED_BAR_COLOR
    );
    private final StyleableBooleanProperty bottomAligned = new SimpleStyleableBooleanProperty(
            BOTTOM_ALIGNED_META_DATA, this, "bottomAligned", false
    );
    private final StyleableBooleanProperty amplitudeColoring = new SimpleStyleableBooleanProperty(
            AMPLITUDE_COLORING_META_DATA, this, "amplitudeColoring", false
    );
    private final StyleableBooleanProperty smoothColoring = new SimpleStyleableBooleanProperty(
            SMOOTH_COLORING_META_DATA, this, "smoothColoring", false
    );
    private final StyleableObjectProperty<Color> lowAmplitudeColor = new SimpleStyleableObjectProperty<>(
            LOW_AMPLITUDE_COLOR_META_DATA, this, "lowAmplitudeColor", DEFAULT_LOW_AMPLITUDE_COLOR
    );
    private final StyleableObjectProperty<Color> midAmplitudeColor = new SimpleStyleableObjectProperty<>(
            MID_AMPLITUDE_COLOR_META_DATA, this, "midAmplitudeColor", DEFAULT_MID_AMPLITUDE_COLOR
    );
    private final StyleableObjectProperty<Color> highAmplitudeColor = new SimpleStyleableObjectProperty<>(
            HIGH_AMPLITUDE_COLOR_META_DATA, this, "highAmplitudeColor", DEFAULT_HIGH_AMPLITUDE_COLOR
    );
    private final StyleableObjectProperty<Color> idleLowAmplitudeColor = new SimpleStyleableObjectProperty<>(
            IDLE_LOW_AMPLITUDE_COLOR_META_DATA, this, "idleLowAmplitudeColor", DEFAULT_IDLE_LOW_AMPLITUDE_COLOR
    );
    private final StyleableObjectProperty<Color> idleMidAmplitudeColor = new SimpleStyleableObjectProperty<>(
            IDLE_MID_AMPLITUDE_COLOR_META_DATA, this, "idleMidAmplitudeColor", DEFAULT_IDLE_MID_AMPLITUDE_COLOR
    );
    private final StyleableObjectProperty<Color> idleHighAmplitudeColor = new SimpleStyleableObjectProperty<>(
            IDLE_HIGH_AMPLITUDE_COLOR_META_DATA, this, "idleHighAmplitudeColor", DEFAULT_IDLE_HIGH_AMPLITUDE_COLOR
    );
    private final StyleableObjectProperty<WaveformRendering> rendering = new SimpleStyleableObjectProperty<>(
            RENDERING_META_DATA, this, "rendering", WaveformRendering.BARS
    );
    private final StyleableObjectProperty<Color> fireLowColor = new SimpleStyleableObjectProperty<>(
            FIRE_LOW_COLOR_META_DATA, this, "fireLowColor", DEFAULT_FIRE_LOW_COLOR
    );
    private final StyleableObjectProperty<Color> fireMidColor = new SimpleStyleableObjectProperty<>(
            FIRE_MID_COLOR_META_DATA, this, "fireMidColor", DEFAULT_FIRE_MID_COLOR
    );
    private final StyleableObjectProperty<Color> fireHighColor = new SimpleStyleableObjectProperty<>(
            FIRE_HIGH_COLOR_META_DATA, this, "fireHighColor", DEFAULT_FIRE_HIGH_COLOR
    );
    private final StyleableDoubleProperty animationSpeed = new SimpleStyleableDoubleProperty(
            ANIMATION_SPEED_META_DATA, this, "animationSpeed", 1d
    );
    private final StyleableDoubleProperty animationAmplitude = new SimpleStyleableDoubleProperty(
            ANIMATION_AMPLITUDE_META_DATA, this, "animationAmplitude", 0.03d
    );
    private final ChangeListener<WaveformDisplayMode> displayModeListener =
            (observable, oldValue, newValue) -> redraw();
    private final WeakChangeListener<WaveformDisplayMode> weakDisplayModeListener =
            new WeakChangeListener<>(displayModeListener);

    private WaveformData waveformData = WaveformData.empty();
    private Consumer<Duration> seekHandler = duration -> {
    };
    private double totalMillis = 1d;
    private double playerTotalMillis;
    private double waveformTotalMillis;
    private double currentMillis;
    private boolean dragging;
    private double dragMillis;
    private Double pendingSeekMillis;
    private long pendingSeekDeadlineNanos;
    private boolean playing;
    private boolean disposed;
    private boolean animationRunning;
    private WaveformData cachedWaveformData;
    private WaveformDisplayMode cachedDisplayMode;
    private double[] cachedAmplitudes = new double[0];
    private long lastAnimationFrame;
    private double animationPhase;
    private final AnimationTimer animationTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            if (lastAnimationFrame == 0L) lastAnimationFrame = now;
            if (now - lastAnimationFrame < ANIMATION_FRAME_NANOS) return;
            animationPhase += (now - lastAnimationFrame) / 1_000_000_000d * Math.max(0.05d, animationSpeed.get());
            lastAnimationFrame = now;
            redraw();
        }
    };

    public WaveformSeekView() {
        getStyleClass().add("waveform-seek-view");
        getChildren().add(canvas);
        activeBarColor.addListener((observable, oldValue, newValue) -> redraw());
        idleBarColor.addListener((observable, oldValue, newValue) -> redraw());
        playheadColor.addListener((observable, oldValue, newValue) -> redraw());
        disabledBarColor.addListener((observable, oldValue, newValue) -> redraw());
        bottomAligned.addListener((observable, oldValue, newValue) -> redraw());
        amplitudeColoring.addListener((observable, oldValue, newValue) -> redraw());
        smoothColoring.addListener((observable, oldValue, newValue) -> redraw());
        lowAmplitudeColor.addListener((observable, oldValue, newValue) -> redraw());
        midAmplitudeColor.addListener((observable, oldValue, newValue) -> redraw());
        highAmplitudeColor.addListener((observable, oldValue, newValue) -> redraw());
        idleLowAmplitudeColor.addListener((observable, oldValue, newValue) -> redraw());
        idleMidAmplitudeColor.addListener((observable, oldValue, newValue) -> redraw());
        idleHighAmplitudeColor.addListener((observable, oldValue, newValue) -> redraw());
        rendering.addListener((observable, oldValue, newValue) -> { updateAnimation(); redraw(); });
        fireLowColor.addListener((observable, oldValue, newValue) -> redraw());
        fireMidColor.addListener((observable, oldValue, newValue) -> redraw());
        fireHighColor.addListener((observable, oldValue, newValue) -> redraw());
        sceneProperty().addListener((observable, oldValue, newValue) -> updateAnimation());
        WaveformDisplaySettings.modeProperty().addListener(weakDisplayModeListener);
        widthProperty().addListener((observable, oldValue, newValue) -> redraw());
        heightProperty().addListener((observable, oldValue, newValue) -> redraw());
        disabledProperty().addListener((observable, oldValue, newValue) -> redraw());
        setFocusTraversable(false);

        setOnMousePressed(this::handleMousePressed);
        setOnMouseDragged(this::handleMouseDragged);
        setOnMouseReleased(this::handleMouseReleased);
    }

    /** Detaches the shared display-mode listener immediately; weak listeners alone accumulate until a mode change.
     * Stopping the timer before detaching also prevents a removed FIRE canvas from retaining its view graph. */
    public void dispose() {
        setPlaying(false);
        disposed = true;
        WaveformDisplaySettings.modeProperty().removeListener(weakDisplayModeListener);
        seekHandler = position -> { };
        setWaveformData(WaveformData.empty());
        cachedWaveformData = null;
        cachedAmplitudes = new double[0];
    }

    public void setWaveformData(WaveformData waveformData) {
        this.waveformData = waveformData == null ? WaveformData.empty() : waveformData;
        waveformTotalMillis = Math.max(this.waveformData.getDuration().toMillis(), 0d);
        totalMillis = Math.max(Math.max(playerTotalMillis, waveformTotalMillis), 1d);
        redraw();
    }

    /**
     * Redraws only when the displayed timeline can actually change. Every workspace item polls playback state, and
     * repainting every stopped Canvas on every poll forces JavaFX to continuously upload identical textures to the
     * GPU. Pending seeks still force the transition redraw so this optimization must never hide seek settlement.
     */
    public void setPlaybackPosition(Duration currentTime, Duration totalTime) {
        updateAnimation();
        playerTotalMillis = Math.max(totalTime == null ? 0d : totalTime.toMillis(), 0d);
        double nextTotalMillis = Math.max(Math.max(playerTotalMillis, waveformTotalMillis), 1d);
        double nextCurrentMillis = Math.max(currentTime == null ? 0d : currentTime.toMillis(), 0d);
        boolean changed = Double.compare(totalMillis, nextTotalMillis) != 0
                || (long) (currentMillis / totalMillis * getWidth())
                != (long) (nextCurrentMillis / nextTotalMillis * getWidth());
        totalMillis = nextTotalMillis;
        currentMillis = nextCurrentMillis;

        if (pendingSeekMillis != null) {
            if (Math.abs(currentMillis - pendingSeekMillis) <= SEEK_SETTLE_THRESHOLD_MILLIS
                    || System.nanoTime() >= pendingSeekDeadlineNanos) {
                pendingSeekMillis = null;
                changed = true;
            }
        }

        if (changed) redraw();
    }

    public void setSeekHandler(Consumer<Duration> seekHandler) {
        this.seekHandler = seekHandler == null ? duration -> {
        } : seekHandler;
    }

    /**
     * Controls renderer animation from actual playback state rather than elapsed position. Media time can remain
     * non-zero while paused, so deriving animation from position would keep every paused fire waveform consuming
     * frames. Only a visible FIRE view in PLAYING state owns an animation timer; all other states are fully static,
     * which is the performance invariant future animated modes must preserve. Animation is also clipped logically to
     * the played part of the waveform so future audio remains a stable navigation reference.
     */
    public void setPlaying(boolean playing) {
        if (disposed) return;
        // Polling views repeatedly report the same state; restarting or stopping the timer would redraw static canvases.
        if (this.playing == playing) return;
        this.playing = playing;
        updateAnimation();
    }

    private void updateAnimation() {
        boolean animate = playing && rendering.get() == WaveformRendering.FIRE && isInViewport();
        if (animate == animationRunning) return;
        animationRunning = animate;
        if (animate) {
            animationTimer.start();
        } else {
            animationTimer.stop();
            lastAnimationFrame = 0L;
            redraw();
        }
    }

    /**
     * Scene membership alone includes scrolled-out canvases. Shared playback polling re-evaluates the
     * viewport so invisible FIRE views release their timers and resume on return without affecting audio playback.
     */
    private boolean isInViewport() { return UiViewport.isVisible(this); }

    public Duration getDisplayedPosition() {
        return Duration.millis(resolveDisplayedMillis());
    }

    @Override
    protected void layoutChildren() {
        double width = snapSizeX(getWidth());
        double height = snapSizeY(getHeight());
        if (canvas.getWidth() == width && canvas.getHeight() == height) return;
        canvas.setWidth(width);
        canvas.setHeight(height);
        redraw();
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return STYLEABLE_PROPERTY_FACTORY.getCssMetaData();
    }

    private void handleMousePressed(MouseEvent event) {
        if (isDisabled() || playerTotalMillis <= 0d && waveformTotalMillis <= 0d) {
            return;
        }

        dragging = true;
        dragMillis = toMillis(event.getX());
        redraw();
        event.consume();
    }

    private void handleMouseDragged(MouseEvent event) {
        if (!dragging || isDisabled()) {
            return;
        }

        dragMillis = toMillis(event.getX());
        redraw();
        event.consume();
    }

    private void handleMouseReleased(MouseEvent event) {
        if (!dragging || isDisabled()) {
            return;
        }

        dragMillis = toMillis(event.getX());
        dragging = false;
        pendingSeekMillis = dragMillis;
        pendingSeekDeadlineNanos = System.nanoTime() + SEEK_SETTLE_TIMEOUT_NANOS;
        seekHandler.accept(Duration.millis(dragMillis));
        redraw();
        event.consume();
    }

    private double toMillis(double x) {
        double width = Math.max(getWidth(), 1d);
        double clampedX = Math.max(0d, Math.min(width, x));
        return clampedX / width * totalMillis;
    }

    private double resolveDisplayedMillis() {
        if (dragging) {
            return dragMillis;
        }
        if (pendingSeekMillis != null) {
            return pendingSeekMillis;
        }
        return currentMillis;
    }

    /**
     * Draws one of a closed set of application-owned renderers while preserving sampled amplitudes, progress, seek
     * behavior and disabled state. Skins select and parameterize a renderer through CSS but cannot supply executable
     * drawing code; this keeps third-party packages safe and renderer cost predictable. Fire deliberately respects
     * bottom alignment and amplitude coloring independently: geometry remains native to the skin, while palettes such
     * as Retro Amp can map animated height to color instead of losing their intensity semantics. New modes must retain
     * exact zero as silence and must not start animation outside {@link #setPlaying(boolean)}.
     */
    private void redraw() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        GraphicsContext graphicsContext = canvas.getGraphicsContext2D();
        graphicsContext.clearRect(0d, 0d, width, height);

        if (width <= 0d || height <= 0d) {
            return;
        }

        int barCount = resolveBarCount(width);
        double[] amplitudes = displayAmplitudes(barCount);
        double barWidth = Math.max(MIN_BAR_WIDTH, (width - (barCount - 1d) * BAR_GAP) / barCount);
        double progressX = totalMillis <= 0d ? 0d : Math.max(0d, Math.min(width, resolveDisplayedMillis() / totalMillis * width));
        double centerY = height / 2d;
        double maxBarHeight = Math.max(4d, height - 2d);

        WaveformRendering renderingMode = rendering.get();
        for (int index = 0; index < barCount; index++) {
            double amplitude = amplitudes[index];
            double colorAmplitude = amplitude;
            double barCenterX = index * (barWidth + BAR_GAP) + barWidth / 2d;
            boolean played = barCenterX <= progressX;
            if (renderingMode == WaveformRendering.FIRE && played && amplitude > 0d) {
                double oscillation = Math.sin(animationPhase * 8d + index * 1.73d);
                double motion = Math.max(0d, Math.min(0.5d, animationAmplitude.get()));
                double flame = 1d - motion + motion * oscillation;
                amplitude = Math.max(0d, Math.min(1d, amplitude * flame));
                if (playing) colorAmplitude = Math.max(0d, Math.min(1d, colorAmplitude + oscillation * 0.14d));
            }
            double barHeight = amplitude <= 0d ? 0d : Math.max(2d, amplitude * maxBarHeight);
            double x = index * (barWidth + BAR_GAP);
            boolean anchored = bottomAligned.get();
            double y = anchored ? height - barHeight : centerY - barHeight / 2d;
            if (barHeight > 0d) {
                if (renderingMode == WaveformRendering.FIRE && played && !amplitudeColoring.get()) {
                    Color low = fireLowColor.get();
                    Color mid = fireMidColor.get();
                    Color high = fireHighColor.get();
                    graphicsContext.setFill(new LinearGradient(0d, y + barHeight, 0d, y, false,
                            CycleMethod.NO_CYCLE, new Stop(0d, low), new Stop(0.55d, mid), new Stop(1d, high)));
                } else {
                    graphicsContext.setFill(resolveBarColor(played, colorAmplitude));
                }
                graphicsContext.fillRoundRect(x, y, barWidth, barHeight, barWidth, barWidth);
            }
        }

        graphicsContext.setFill(isDisabled() ? disabledBarColor.get() : playheadColor.get());
        graphicsContext.fillRoundRect(Math.max(0d, progressX - 1d), 0d, 2d, height, 2d, 2d);
    }

    /**
     * Progress and FIRE phase do not change sampled amplitudes. Cache their reduction and dB conversion until the
     * envelope, display mode or bar count changes; otherwise every animation frame repeats identical array work.
     */
    private double[] displayAmplitudes(int barCount) {
        WaveformDisplayMode mode = WaveformDisplaySettings.getMode();
        if (cachedWaveformData == waveformData && cachedDisplayMode == mode && cachedAmplitudes.length == barCount) {
            return cachedAmplitudes;
        }
        double[] amplitudes = new double[barCount];
        for (int index = 0; index < barCount; index++) amplitudes[index] = resolveAmplitude(index, barCount);
        if (mode == WaveformDisplayMode.RMS_DB && !waveformData.isEmpty()) amplitudes = toDecibelDisplay(amplitudes);
        cachedWaveformData = waveformData;
        cachedDisplayMode = mode;
        cachedAmplitudes = amplitudes;
        return amplitudes;
    }

    private int resolveBarCount(double width) {
        int maxVisualBars = Math.max((int) Math.floor(width / (MIN_BAR_WIDTH + BAR_GAP)), 12);
        if (!waveformData.isEmpty()) {
            return Math.max(12, Math.min(waveformData.size(), maxVisualBars));
        }
        return Math.max(12, Math.min(PLACEHOLDER_BAR_COUNT, maxVisualBars));
    }

    private double resolveAmplitude(int barIndex, int barCount) {
        if (waveformData.isEmpty()) {
            double phase = (double) barIndex / Math.max(barCount - 1, 1);
            return 0.22d + 0.18d * Math.abs(Math.sin(phase * Math.PI * 3d));
        }

        int startIndex = (int) Math.floor((double) barIndex * waveformData.size() / barCount);
        int endIndex = (int) Math.floor((double) (barIndex + 1) * waveformData.size() / barCount);
        if (endIndex <= startIndex) {
            endIndex = Math.min(startIndex + 1, waveformData.size());
        }

        double squaredAmplitudeSum = 0d;
        for (int index = startIndex; index < endIndex; index++) {
            double amplitude = WaveformDisplaySettings.getMode() == WaveformDisplayMode.PEAK_LINEAR
                    ? waveformData.peakAmplitudeAt(index)
                    : waveformData.rmsAmplitudeAt(index);
            squaredAmplitudeSum += amplitude * amplitude;
        }
        return Math.sqrt(squaredAmplitudeSum / Math.max(endIndex - startIndex, 1));
    }

    private Color resolveBarColor(boolean active, double amplitude) {
        if (isDisabled()) {
            return disabledBarColor.get();
        }
        if (amplitudeColoring.get()) {
            Color low = active ? lowAmplitudeColor.get() : idleLowAmplitudeColor.get();
            Color mid = active ? midAmplitudeColor.get() : idleMidAmplitudeColor.get();
            Color high = active ? highAmplitudeColor.get() : idleHighAmplitudeColor.get();
            return amplitudeColor(amplitude, low, mid, high);
        }
        return active ? activeBarColor.get() : idleBarColor.get();
    }

    private Color amplitudeColor(double amplitude, Color low, Color mid, Color high) {
        double clampedAmplitude = Math.max(0d, Math.min(1d, amplitude));
        if (!smoothColoring.get()) {
            if (clampedAmplitude < 0.45d) return low;
            return clampedAmplitude < 0.82d ? mid : high;
        }
        if (clampedAmplitude <= MID_AMPLITUDE_POSITION) {
            return low.interpolate(mid, clampedAmplitude / MID_AMPLITUDE_POSITION);
        }
        return mid.interpolate(high,
                (clampedAmplitude - MID_AMPLITUDE_POSITION) / (1d - MID_AMPLITUDE_POSITION));
    }

    /**
     * Maps normalized RMS amplitudes to a fixed perceptual dB display rather than stretching each track by its
     * percentiles. Per-track stretching made ordinary quiet passages look like silence and made comparisons depend
     * on a track's own distribution. A fixed -72 dB floor preserves exact digital silence as zero, keeps low
     * non-zero material visible, and compresses the crowded upper range consistently across tracks. The exponent
     * reserves visual height for genuinely strong material without altering playback or persisted waveform data.
     * Any change to extraction, the dB floor, or the curve must update this explanation and the stepped-level tests.
     */
    static double[] toDecibelDisplay(double[] amplitudes) {
        if (amplitudes == null || amplitudes.length == 0) return new double[0];
        double[] adapted = new double[amplitudes.length];
        for (int index = 0; index < amplitudes.length; index++) {
            double amplitude = Math.max(0d, Math.min(1d, amplitudes[index]));
            if (amplitude == 0d) continue;
            double decibels = 20d * Math.log10(amplitude);
            double normalized = Math.max(0d, Math.min(1d,
                    (decibels - ADAPTIVE_DECIBEL_FLOOR) / -ADAPTIVE_DECIBEL_FLOOR));
            adapted[index] = Math.pow(normalized, ADAPTIVE_DISPLAY_EXPONENT);
        }
        return adapted;
    }
}
