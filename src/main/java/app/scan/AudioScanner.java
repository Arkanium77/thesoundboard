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
import java.util.concurrent.CancellationException;
import java.util.function.Predicate;

public class AudioScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioScanner.class);

    private final Set<String> supportedExtensions;
    private final Predicate<Path> supportedContents;

    public AudioScanner(Collection<String> supportedExtensions) {
        this(supportedExtensions, path -> true);
    }

    public AudioScanner(Collection<String> supportedExtensions, Predicate<Path> supportedContents) {
        this.supportedExtensions = supportedExtensions.stream()
                .map(RelativePathUtils::normalizeExtension)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        this.supportedContents = supportedContents;
    }

    public List<ScannedAudioFile> scan(Path rootPath) throws IOException {
        LOGGER.info("Starting scan for root folder {}", rootPath);

        try (Stream<Path> pathStream = Files.walk(rootPath)) {
            List<ScannedAudioFile> audioFiles = pathStream
                    .peek(path -> { if (Thread.currentThread().isInterrupted()) throw new CancellationException("Audio scan cancelled"); })
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedAudioFile)
                    .filter(supportedContents)
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
