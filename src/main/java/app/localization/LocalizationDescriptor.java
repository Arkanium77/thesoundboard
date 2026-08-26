package app.localization;

import java.nio.file.Path;
import java.util.Map;

public record LocalizationDescriptor(LocalizationManifest manifest, Map<String, String> strings, Path directory, boolean builtIn) {
}
