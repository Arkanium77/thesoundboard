package app.audio;

import app.support.TestDirectorySupport;
import org.assertj.core.api.Assertions;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.io.IOException;

class OggAudioMetadataTest {
    @ParameterizedTest
    @ValueSource(strings = {"ogg", "opus", "surround.opus", "flac.ogg"})
    void readsPlayableContainerDurationWithoutNativeDecoder(String extension) throws Exception {
        Path path = fixture(extension);
        Assertions.assertThat(OggAudioMetadata.isSupported(path)).isTrue();
        double expected = extension.equals("surround.opus") ? 1.013d : 1d;
        Assertions.assertThat(OggAudioMetadata.duration(path).toSeconds()).isCloseTo(expected, Offset.offset(0.001d));
    }

    @Test
    void countsChainedStreamsEvenWhenSerialNumbersAreReused() throws Exception {
        Path original = fixture("opus");
        Path chained = original.resolveSibling("chained.opus");
        try (var output = Files.newOutputStream(chained)) {
            Files.copy(original, output);
            Files.copy(original, output);
        }
        Assertions.assertThat(OggAudioMetadata.duration(chained).toSeconds()).isCloseTo(2d, Offset.offset(0.001d));
    }

    @Test
    void rejectsUnknownOggCodecsAndTruncatedContainersDuringScan() throws Exception {
        Path path = fixture("ogg");
        byte[] bytes = Files.readAllBytes(path);
        int packet = 27 + Byte.toUnsignedInt(bytes[26]);
        bytes[packet] = (byte) 0x80;
        bytes[packet + 1] = 't';
        bytes[packet + 2] = 'h';
        bytes[packet + 3] = 'e';
        bytes[packet + 4] = 'o';
        bytes[packet + 5] = 'r';
        bytes[packet + 6] = 'a';
        Files.write(path, bytes);
        Assertions.assertThat(AudioEngineMode.GSTREAMER.supportsFile(path)).isFalse();
        Assertions.assertThatThrownBy(() -> OggAudioMetadata.duration(path)).isInstanceOf(IOException.class);
        Files.write(path, new byte[]{'O', 'g', 'g', 'S'});
        Assertions.assertThat(OggAudioMetadata.isSupported(path)).isFalse();
    }

    @Test
    void recognizesOpusInsideOggAndRejectsMissingEndPage() throws Exception {
        Path path = fixture("opus");
        Path renamed = path.resolveSibling("opus.ogg");
        Files.move(path, renamed);
        Assertions.assertThat(AudioEngineMode.GSTREAMER.supportsFile(renamed)).isTrue();
        Assertions.assertThat(AudioEngineMode.JAVAFX.supportsFile(renamed)).isFalse();
        byte[] bytes = Files.readAllBytes(renamed);
        Files.write(renamed, Arrays.copyOf(bytes, bytes.length - 1));
        Assertions.assertThatThrownBy(() -> OggAudioMetadata.duration(renamed)).isInstanceOf(IOException.class);
    }

    private Path fixture(String extension) throws Exception {
        Path directory = TestDirectorySupport.createTempDirectory("ogg-metadata-");
        Path path = directory.resolve("tone." + extension);
        try (InputStream stream = getClass().getResourceAsStream("/audio/tone." + extension)) {
            Files.copy(stream, path);
        }
        return path;
    }
}
