package app.support;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public final class RelativePathUtils {
    private RelativePathUtils() {
    }

    public static String normalize(Path path) {
        return normalize(path.toString());
    }

    public static String normalize(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "";
        }

        Path normalizedPath = Paths.get(relativePath).normalize();
        String normalized = normalizedPath.toString().replace('\\', '/');
        if (normalized.startsWith("./")) {
            return normalized.substring(2);
        }
        return normalized;
    }

    public static String displayName(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "";
        }

        String normalized = normalize(relativePath);
        int separatorIndex = normalized.lastIndexOf('/');
        return separatorIndex >= 0 ? normalized.substring(separatorIndex + 1) : normalized;
    }

    public static String normalizeExtension(String extension) {
        return extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
    }
}
