package app.packages;

import java.util.UUID;

public record PackageIdentity(UUID uid, int version) {
    public PackageIdentity {
        if (uid == null) {
            throw new IllegalArgumentException("Package UID is required");
        }
        if (version < 1) {
            throw new IllegalArgumentException("Package version must be positive");
        }
    }

    public String storageName() {
        return version == 1 ? uid.toString() : uid + "-v" + version;
    }

    public static PackageIdentity parse(String value) {
        int separator = value.lastIndexOf("-v");
        if (separator < 0) {
            return new PackageIdentity(UUID.fromString(value), 1);
        }
        return new PackageIdentity(UUID.fromString(value.substring(0, separator)),
                Integer.parseInt(value.substring(separator + 2)));
    }
}
