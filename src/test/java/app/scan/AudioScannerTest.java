package app.scan;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class AudioScannerTest {
    @TempDir
    Path tempDir;

    @Test
    void scansRecursivelyAndStoresRelativePaths() throws IOException {
        Path nestedFolder = Files.createDirectories(tempDir.resolve("battle").resolve("dragons"));
        Files.writeString(nestedFolder.resolve("roar.mp3"), "audio");

        AudioScanner audioScanner = new AudioScanner(List.of("mp3"));

        List<ScannedAudioFile> scannedAudioFiles = audioScanner.scan(tempDir);

        Assertions.assertThat(scannedAudioFiles)
                .extracting(ScannedAudioFile::getRelativePath)
                .containsExactly("battle/dragons/roar.mp3");
    }

    @Test
    void ignoresFilesWithUnsupportedExtensions() throws IOException {
        Files.writeString(tempDir.resolve("roar.mp3"), "audio");
        Files.writeString(tempDir.resolve("notes.txt"), "text");
        Files.writeString(tempDir.resolve("cover.wav"), "audio");

        AudioScanner audioScanner = new AudioScanner(List.of("mp3"));

        List<ScannedAudioFile> scannedAudioFiles = audioScanner.scan(tempDir);

        Assertions.assertThat(scannedAudioFiles)
                .extracting(ScannedAudioFile::getRelativePath)
                .containsExactly("roar.mp3");
    }

    @Test
    void handlesExtensionCaseInsensitively() throws IOException {
        Files.writeString(tempDir.resolve("roar.MP3"), "audio");
        Files.writeString(tempDir.resolve("wind.Mp3"), "audio");
        Files.writeString(tempDir.resolve("rain.mp3"), "audio");

        AudioScanner audioScanner = new AudioScanner(List.of("mp3"));

        List<ScannedAudioFile> scannedAudioFiles = audioScanner.scan(tempDir);

        Assertions.assertThat(scannedAudioFiles)
                .extracting(ScannedAudioFile::getRelativePath)
                .containsExactly("rain.mp3", "roar.MP3", "wind.Mp3");
    }
}
