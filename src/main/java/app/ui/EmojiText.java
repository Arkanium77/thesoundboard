package app.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EmojiText {
    private static final int MAX_EMOJI_CODE_POINTS = 16;
    private static final double EMOJI_SIZE = 16d;
    private static final String RESOURCE_ROOT = "/emoji/twemoji/";
    private static final Map<String, Image> imageCache = new LinkedHashMap<>();

    private EmojiText() {
    }

    public static Node create(String value) {
        HBox content = new HBox(3d);
        content.setAlignment(Pos.CENTER_LEFT);
        for (Segment segment : parse(value == null ? "" : value)) {
            if (segment.emojiCode() == null) {
                content.getChildren().add(new Label(segment.value()));
            } else {
                ImageView imageView = new ImageView(loadImage(segment.emojiCode()));
                imageView.setFitWidth(EMOJI_SIZE);
                imageView.setFitHeight(EMOJI_SIZE);
                imageView.setPreserveRatio(true);
                imageView.setSmooth(true);
                content.getChildren().add(imageView);
            }
        }
        return content;
    }

    static List<Segment> parse(String value) {
        int[] codePoints = value.codePoints().toArray();
        List<Segment> segments = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int position = 0;
        while (position < codePoints.length) {
            EmojiMatch match = findLongestEmoji(codePoints, position);
            if (match == null) {
                text.appendCodePoint(codePoints[position]);
                position++;
                continue;
            }
            if (!text.isEmpty()) {
                segments.add(new Segment(text.toString(), null));
                text.setLength(0);
            }
            segments.add(new Segment(null, match.code()));
            position = match.end();
        }
        if (!text.isEmpty()) {
            segments.add(new Segment(text.toString(), null));
        }
        return List.copyOf(segments);
    }

    private static EmojiMatch findLongestEmoji(int[] codePoints, int start) {
        int maximumEnd = Math.min(codePoints.length, start + MAX_EMOJI_CODE_POINTS);
        for (int end = maximumEnd; end > start; end--) {
            String rawCode = codePointName(codePoints, start, end, false);
            if (resource(rawCode) != null) {
                return new EmojiMatch(rawCode, end);
            }
            String normalizedCode = codePointName(codePoints, start, end, true);
            if (!normalizedCode.equals(rawCode) && resource(normalizedCode) != null) {
                return new EmojiMatch(normalizedCode, end);
            }
        }
        return null;
    }

    private static String codePointName(int[] codePoints, int start, int end, boolean removeVariationSelectors) {
        StringBuilder name = new StringBuilder();
        for (int index = start; index < end; index++) {
            if (removeVariationSelectors && (codePoints[index] == 0xfe0e || codePoints[index] == 0xfe0f)) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append('-');
            }
            name.append(Integer.toHexString(codePoints[index]));
        }
        return name.toString();
    }

    private static Image loadImage(String emojiCode) {
        return imageCache.computeIfAbsent(emojiCode, code -> {
            URL resource = resource(code);
            if (resource == null) {
                throw new IllegalStateException("Missing Twemoji resource: " + code);
            }
            return new Image(resource.toExternalForm(), EMOJI_SIZE, EMOJI_SIZE, true, true);
        });
    }

    private static URL resource(String emojiCode) {
        return EmojiText.class.getResource(RESOURCE_ROOT + emojiCode + ".png");
    }

    record Segment(String value, String emojiCode) {
    }

    private record EmojiMatch(String code, int end) {
    }
}
