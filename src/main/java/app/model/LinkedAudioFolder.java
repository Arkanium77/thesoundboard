package app.model;

import java.nio.file.Path;
import java.util.UUID;

/** A project-owned reference, never a copied library. Relative locations survive moving a project together with
 * its libraries; the explicit flag prevents foreign absolute paths from being mistaken for relative locations. */
public class LinkedAudioFolder {
    private UUID id;
    private String location;
    private String name;
    private boolean relative;
    private boolean unavailable;

    public LinkedAudioFolder() { }

    public LinkedAudioFolder(UUID id, String location, boolean relative) {
        this.id = id;
        this.location = location;
        this.relative = relative;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public boolean isRelative() { return relative; }
    public void setRelative(boolean relative) { this.relative = relative; }
    public boolean isUnavailable() { return unavailable; }
    public void setUnavailable(boolean unavailable) { this.unavailable = unavailable; }

    public Path resolve(Path projectRoot) {
        Path path = Path.of(location);
        if (!relative && !path.isAbsolute()) throw new IllegalArgumentException("Unavailable absolute library path: " + location);
        return (relative ? projectRoot.resolve(path) : path).toAbsolutePath().normalize();
    }

    public String displayName() {
        if (name != null && !name.isBlank()) return name;
        String normalized = location.replace('\\', '/');
        int end = normalized.length();
        while (end > 1 && normalized.charAt(end - 1) == '/') end--;
        normalized = normalized.substring(0, end);
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }
}
