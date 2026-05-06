package app.ui.waveform;

import app.waveform.WaveformData;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.function.Consumer;

public class WaveformSeekView extends Region {
    private static final int PLACEHOLDER_BAR_COUNT = 48;
    private static final double BAR_GAP = 1d;
    private static final double MIN_BAR_WIDTH = 2d;
    private static final double SEEK_SETTLE_THRESHOLD_MILLIS = 750d;
    private static final long SEEK_SETTLE_TIMEOUT_NANOS = 1_500_000_000L;

    private static final Color ACTIVE_BAR_COLOR = Color.web("#4a83d8");
    private static final Color IDLE_BAR_COLOR = Color.web("#c7d3ea");
    private static final Color ACTIVE_PLAYHEAD_COLOR = Color.web("#2d5fb2");
    private static final Color DISABLED_BAR_COLOR = Color.web("#d7dce7");

    private final Canvas canvas = new Canvas();

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
        getChildren().add(canvas);
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
        double barWidth = Math.max(MIN_BAR_WIDTH, (width - (barCount - 1d) * BAR_GAP) / barCount);
        double progressX = totalMillis <= 0d ? 0d : Math.max(0d, Math.min(width, resolveDisplayedMillis() / totalMillis * width));
        double centerY = height / 2d;
        double maxBarHeight = Math.max(4d, height - 2d);

        for (int index = 0; index < barCount; index++) {
            double amplitude = resolveAmplitude(index, barCount);
            double barHeight = Math.max(2d, amplitude * maxBarHeight);
            double x = index * (barWidth + BAR_GAP);
            double y = centerY - barHeight / 2d;
            double barCenterX = x + barWidth / 2d;
            graphicsContext.setFill(resolveBarColor(barCenterX <= progressX));
            graphicsContext.fillRoundRect(x, y, barWidth, barHeight, barWidth, barWidth);
        }

        graphicsContext.setFill(isDisabled() ? DISABLED_BAR_COLOR : ACTIVE_PLAYHEAD_COLOR);
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

        double peak = 0d;
        for (int index = startIndex; index < endIndex; index++) {
            peak = Math.max(peak, waveformData.amplitudeAt(index));
        }
        return Math.max(0.08d, peak);
    }

    private Color resolveBarColor(boolean active) {
        if (isDisabled()) {
            return DISABLED_BAR_COLOR;
        }
        return active ? ACTIVE_BAR_COLOR : IDLE_BAR_COLOR;
    }
}
