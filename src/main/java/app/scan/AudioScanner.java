package app.scan;

import app.support.RelativePathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AudioScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioScanner.class);

    private final Set<String> supportedExtensions;

    public AudioScanner(Collection<String> supportedExtensions) {
        this.supportedExtensions = supportedExtensions.stream()
                .map(RelativePathUtils::normalizeExtension)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public List<ScannedAudioFile> scan(Path rootPath) throws IOException {
        LOGGER.info("Starting scan for root folder {}", rootPath);

        try (Stream<Path> pathStream = Files.walk(rootPath)) {
            List<ScannedAudioFile> audioFiles = pathStream
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedAudioFile)
                    .map(path -> toScannedAudioFile(rootPath, path))
                    .sorted(Comparator.comparing(ScannedAudioFile::getRelativePath, String.CASE_INSENSITIVE_ORDER))
                    .toList();

            LOGGER.info("Finished scan for root folder {}. Found {} audio files", rootPath, audioFiles.size());
            return audioFiles;
        }
    }

    private boolean isSupportedAudioFile(Path path) {
        String fileName = path.getFileName().toString();
        int extensionSeparatorIndex = fileName.lastIndexOf('.');
        if (extensionSeparatorIndex < 0 || extensionSeparatorIndex == fileName.length() - 1) {
            return false;
        }

        String extension = fileName.substring(extensionSeparatorIndex + 1).toLowerCase(Locale.ROOT);
        return supportedExtensions.contains(extension);
    }

    private ScannedAudioFile toScannedAudioFile(Path rootPath, Path filePath) {
        Path relativePath = rootPath.relativize(filePath);
        String normalizedRelativePath = RelativePathUtils.normalize(relativePath);
        return new ScannedAudioFile(normalizedRelativePath, filePath.getFileName().toString());
    }
}
