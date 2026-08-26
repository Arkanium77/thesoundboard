package app.skin;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;

public record SkinDescriptor(SkinManifest manifest, String classpathRoot, Path directory) {
    public URL resolveResource(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return null;
        }
        if (classpathRoot != null) {
            String resolvedPath = resourcePath.startsWith("/")
                    ? resourcePath
                    : classpathRoot + resourcePath;
            return SkinDescriptor.class.getResource(resolvedPath);
        }

        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        Path resolvedPath = normalizedDirectory.resolve(resourcePath).normalize();
        if (!resolvedPath.startsWith(normalizedDirectory)) {
            throw new IllegalArgumentException("Skin resource escapes its directory: " + resourcePath);
        }
        try {
            return resolvedPath.toUri().toURL();
        } catch (MalformedURLException exception) {
            throw new IllegalArgumentException("Invalid skin resource path: " + resourcePath, exception);
        }
    }

    public boolean isBuiltIn() {
        return classpathRoot != null;
    }
}
