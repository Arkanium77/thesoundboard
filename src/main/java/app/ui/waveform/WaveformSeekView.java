package app.ui.waveform;

import app.waveform.WaveformData;
import app.waveform.WaveformDisplayMode;
import app.waveform.WaveformDisplaySettings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableBooleanProperty;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleablePropertyFactory;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
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
    private final ChangeListener<WaveformDisplayMode> displayModeListener =
            (observable, oldValue, newValue) -> redraw();
    private final WeakChangeListener<WaveformDisplayMode> weakDisplayModeListener =
            new WeakChangeListener<>(displayModeListener);

    private WaveformData waveformData = WaveformData.empty();
    private Consumer<Duration> seekHandler = duration -> {
    };
    private double totalMillis = 1d;
    private double currentMillis;
    private boolean dragging;
    private double dragMillis;
    private Double pendingSeekMillis;
    private long pendingSeekDeadlineNanos;

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
        WaveformDisplaySettings.modeProperty().addListener(weakDisplayModeListener);
        widthProperty().addListener((observable, oldValue, newValue) -> redraw());
        heightProperty().addListener((observable, oldValue, newValue) -> redraw());
        disabledProperty().addListener((observable, oldValue, newValue) -> redraw());
        setFocusTraversable(false);

        setOnMousePressed(this::handleMousePressed);
        setOnMouseDragged(this::handleMouseDragged);
        setOnMouseReleased(this::handleMouseReleased);
    }

    public void setWaveformData(WaveformData waveformData) {
        this.waveformData = waveformData == null ? WaveformData.empty() : waveformData;
        redraw();
    }

    public void setPlaybackPosition(Duration currentTime, Duration totalTime) {
        totalMillis = Math.max(totalTime == null ? 0d : totalTime.toMillis(), 1d);
        currentMillis = Math.max(currentTime == null ? 0d : currentTime.toMillis(), 0d);

        if (pendingSeekMillis != null) {
            if (Math.abs(currentMillis - pendingSeekMillis) <= SEEK_SETTLE_THRESHOLD_MILLIS
                    || System.nanoTime() >= pendingSeekDeadlineNanos) {
                pendingSeekMillis = null;
            }
        }

        redraw();
    }

    public void setSeekHandler(Consumer<Duration> seekHandler) {
        this.seekHandler = seekHandler == null ? duration -> {
        } : seekHandler;
    }

    public Duration getDisplayedPosition() {
        return Duration.millis(resolveDisplayedMillis());
    }

    @Override
    protected void layoutChildren() {
        canvas.setWidth(snapSizeX(getWidth()));
        canvas.setHeight(snapSizeY(getHeight()));
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
        if (isDisabled() || totalMillis <= 0d) {
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

    private void redraw() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        GraphicsContext graphicsContext = canvas.getGraphicsContext2D();
        graphicsContext.clearRect(0d, 0d, width, height);

        if (width <= 0d || height <= 0d) {
            return;
        }

        int barCount = resolveBarCount(width);
        double[] amplitudes = new double[barCount];
        for (int index = 0; index < barCount; index++) {
            amplitudes[index] = resolveAmplitude(index, barCount);
        }
        if (WaveformDisplaySettings.getMode() == WaveformDisplayMode.RMS_DB && !waveformData.isEmpty()) {
            amplitudes = toDecibelDisplay(amplitudes);
        }
        double barWidth = Math.max(MIN_BAR_WIDTH, (width - (barCount - 1d) * BAR_GAP) / barCount);
        double progressX = totalMillis <= 0d ? 0d : Math.max(0d, Math.min(width, resolveDisplayedMillis() / totalMillis * width));
        double centerY = height / 2d;
        double maxBarHeight = Math.max(4d, height - 2d);

        for (int index = 0; index < barCount; index++) {
            double amplitude = amplitudes[index];
            double barHeight = amplitude <= 0d ? 0d : Math.max(2d, amplitude * maxBarHeight);
            double x = index * (barWidth + BAR_GAP);
            double y = bottomAligned.get() ? height - barHeight : centerY - barHeight / 2d;
            double barCenterX = x + barWidth / 2d;
            if (barHeight > 0d) {
                graphicsContext.setFill(resolveBarColor(barCenterX <= progressX, amplitude));
                graphicsContext.fillRoundRect(x, y, barWidth, barHeight, barWidth, barWidth);
            }
        }

        graphicsContext.setFill(isDisabled() ? disabledBarColor.get() : playheadColor.get());
        graphicsContext.fillRoundRect(Math.max(0d, progressX - 1d), 0d, 2d, height, 2d, 2d);
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
