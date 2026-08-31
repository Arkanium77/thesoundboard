package app.ui;

import app.skin.SkinDescriptor;
import javafx.scene.Node;
import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UiIcons {
    private static final double ICON_SIZE = 16d;
    private static final double ICON_SCALE = 0.65d;
    private static final Map<String, String> FALLBACK_ICONS = createFallbackIcons();
    private static final List<WeakReference<IconPane>> iconPanes = new ArrayList<>();
    private static Map<String, List<String>> configuredIcons = Map.of();

    private UiIcons() {
    }

    public static Node play() {
        return create("play");
    }

    public static Node pause() {
        return create("pause");
    }

    public static Node stop() {
        return create("stop");
    }

    public static Node previous() {
        return create("previous");
    }

    public static Node next() {
        return create("next");
    }

    public static Node loop() {
        return create("loop");
    }

    public static Node refresh() {
        return create("refresh");
    }

    public static Node muted() {
        return create("muted");
    }

    public static Node unmuted() {
        return create("unmuted");
    }

    public static Node settings() {
        return create("settings");
    }

    public static void configure(SkinDescriptor skin) {
        Map<String, List<String>> icons = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : skin.manifest().getIcons().entrySet()) {
            URL resource = skin.resolveResource(entry.getValue());
            if (resource != null) {
                icons.put(entry.getKey(), loadSvgPaths(resource));
            }
        }
        configuredIcons = Map.copyOf(icons);
        iconPanes.removeIf(reference -> reference.get() == null);
        iconPanes.stream()
                .map(WeakReference::get)
                .forEach(IconPane::refreshGraphic);
    }

    public static void resize(Node icon, double scale) {
        if (!(icon instanceof IconPane iconPane)) {
            throw new IllegalArgumentException("Unsupported icon node");
        }
        iconPane.resizeIcon(scale);
    }

    private static Node create(String iconName) {
        IconPane iconPane = new IconPane(iconName);
        iconPanes.add(new WeakReference<>(iconPane));
        return iconPane;
    }

    private static List<SVGPath> createPaths(String iconName) {
        List<String> paths = configuredIcons.get(iconName);
        if (paths == null || paths.isEmpty()) {
            paths = List.of(FALLBACK_ICONS.get(iconName));
        }
        return paths.stream().map(UiIcons::createPath).toList();
    }

    private static SVGPath createPath(String content) {
        SVGPath icon = new SVGPath();
        icon.setContent(content);
        icon.setFill(Color.web("#333333"));
        icon.setMouseTransparent(true);
        icon.getStyleClass().add("soundboard-icon");
        return icon;
    }

    private static List<String> loadSvgPaths(URL resource) {
        try (InputStream inputStream = resource.openStream()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            NodeList pathNodes = factory.newDocumentBuilder().parse(inputStream).getElementsByTagName("path");
            List<String> paths = new ArrayList<>();
            for (int index = 0; index < pathNodes.getLength(); index++) {
                String path = pathNodes.item(index).getAttributes().getNamedItem("d").getNodeValue();
                if (!path.isBlank()) {
                    paths.add(path);
                }
            }
            if (paths.isEmpty()) {
                throw new IllegalArgumentException("SVG icon contains no paths: " + resource);
            }
            return List.copyOf(paths);
        } catch (IOException | ParserConfigurationException | SAXException exception) {
            throw new IllegalArgumentException("Failed to load SVG icon: " + resource, exception);
        }
    }

    private static Map<String, String> createFallbackIcons() {
        Map<String, String> icons = new LinkedHashMap<>();
        icons.put("play", "M8 5v14l11-7z");
        icons.put("pause", "M6 5h4v14H6zm8 0h4v14h-4z");
        icons.put("stop", "M6 6h12v12H6z");
        icons.put("previous", "M6 6h2v12H6zm12 0v12l-9-6z");
        icons.put("next", "M6 6v12l9-6zm10 0h2v12h-2z");
        icons.put("loop", "M7 7h10v3l4-4-4-4v3H7a5 5 0 0 0-5 5v2h2v-2a3 3 0 0 1 3-3zm10 10H7v-3l-4 4 4 4v-3h10a5 5 0 0 0 5-5v-2h-2v2a3 3 0 0 1-3 3z");
        icons.put("refresh", "M17.65 6.35A7.95 7.95 0 0 0 12 4a8 8 0 1 0 7.75 10h-2.1A6 6 0 1 1 12 6c1.66 0 3.14.69 4.22 1.78L13 11h7V4z");
        icons.put("muted", "M4 9v6h4l5 4V5L8 9zm11.5 3a3.5 3.5 0 0 0-1.5-2.87v5.74A3.5 3.5 0 0 0 15.5 12zm1.5-5.33v2.12L19.21 11H17v2h2.21L17 15.21v2.12L20.27 14l3.27 3.33v-2.12L21.33 13h2.21v-2h-2.21l2.21-2.21V6.67L20.27 10z");
        icons.put("unmuted", "M4 9v6h4l5 4V5L8 9zm11.5 3a3.5 3.5 0 0 0-1.5-2.87v5.74A3.5 3.5 0 0 0 15.5 12zM14 3.23v2.06a7 7 0 0 1 0 13.42v2.06a9 9 0 0 0 0-17.54zm3 8.77a5 5 0 0 0-3-4.58v2.24a3 3 0 0 1 0 4.68v2.24A5 5 0 0 0 17 12z");
        icons.put("settings", "M19.43 12.98c.04-.32.07-.65.07-.98s-.03-.66-.08-.98l2.11-1.65-2-3.46-2.49 1a7.2 7.2 0 0 0-1.69-.98L15 3.27h-4l-.4 2.65c-.61.25-1.17.59-1.69.98l-2.49-1-2 3.46 2.11 1.65c-.04.33-.08.66-.08.99s.03.66.08.98l-2.11 1.65 2 3.46 2.49-1c.52.4 1.08.73 1.69.98l.4 2.66h4l.4-2.65c.61-.25 1.17-.59 1.69-.98l2.49 1 2-3.46zM13 15.5A3.5 3.5 0 1 1 13 8a3.5 3.5 0 0 1 0 7.5z");
        return Map.copyOf(icons);
    }

    private static final class IconPane extends StackPane {
        private final String iconName;
        private final Group icon;

        private IconPane(String iconName) {
            this.iconName = iconName;
            this.icon = new Group();
            refreshGraphic();
            setMouseTransparent(true);
            getChildren().add(icon);
            resizeIcon(1d);
        }

        private void refreshGraphic() {
            icon.getChildren().setAll(createPaths(iconName));
        }

        private void resizeIcon(double scale) {
            double size = ICON_SIZE * scale;
            setMinSize(size, size);
            setPrefSize(size, size);
            setMaxSize(size, size);
            icon.setScaleX(ICON_SCALE * scale);
            icon.setScaleY(ICON_SCALE * scale);
        }
    }
}
