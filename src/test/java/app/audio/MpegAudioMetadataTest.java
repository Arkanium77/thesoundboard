package app.audio;

import app.support.TestDirectorySupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class MpegAudioMetadataTest {
    @ParameterizedTest
    @ValueSource(strings = {"mp4a", "alac", "fLaC", "Opus", ".mp3"})
    void findsAudioAfterMediaPayloadAndSkipsVideoTracks(String codec) throws Exception {
        byte[] file = concat(atom("mdat", new byte[1024 * 1024]),
                atom("moov", concat(track("vide", "avc1"), track("soun", codec))));
        Path path = fixture("sound.m4a", file);
        Assertions.assertThat(AudioEngineMode.GSTREAMER.supportsFile(path)).isTrue();
    }

    @Test
    void rejectsEncryptedDefaultTrackEvenIfAnotherAudioTrackIsPlayable() throws Exception {
        Path path = fixture("encrypted.m4a", atom("moov", concat(track("soun", "enca"), track("soun", "mp4a"))));
        Assertions.assertThat(AudioEngineMode.GSTREAMER.supportsFile(path)).isFalse();
        Files.write(path, atom("moov", track("vide", "avc1")));
        Assertions.assertThat(AudioEngineMode.GSTREAMER.supportsFile(path)).isFalse();
        Files.write(path, new byte[] {0, 0, 0, 4, 'm', 'o', 'o', 'v'});
        Assertions.assertThat(AudioEngineMode.GSTREAMER.supportsFile(path)).isFalse();
    }

    @Test
    void handlesExtendedAtomLengthsAndBoundsPathologicalMetadata() throws Exception {
        byte[] movie = atom("moov", track("soun", "mp4a"));
        ByteBuffer extended = ByteBuffer.allocate(movie.length + 8);
        extended.putInt(1).put("moov".getBytes(StandardCharsets.US_ASCII)).putLong(movie.length + 8L)
                .put(movie, 8, movie.length - 8);
        Path path = fixture("extended.m4a", extended.array());
        Assertions.assertThat(MpegAudioMetadata.supportsM4a(path)).isTrue();
        byte[][] atoms = new byte[4100][];
        for (int index = 0; index < atoms.length; index++) atoms[index] = atom("free", new byte[0]);
        Files.write(path, concat(concat(atoms), movie));
        Assertions.assertThat(MpegAudioMetadata.supportsM4a(path)).isFalse();
        Files.write(path, new byte[] {0, 0, 0, 1, 'm', 'o', 'o', 'v', -1, -1, -1, -1, -1, -1, -1, -1});
        Assertions.assertThat(MpegAudioMetadata.supportsM4a(path)).isFalse();
    }

    @Test
    void recognisesAdtsWithOptionalId3AndRejectsTruncatedFrames() throws Exception {
        byte[] adts = new byte[] {-1, -15, 80, -128, 1, 31, -4, 0};
        Path path = fixture("sound.aac", adts);
        Assertions.assertThat(AudioEngineMode.GSTREAMER.supportsFile(path)).isTrue();
        byte[] id3 = new byte[] {'I', 'D', '3', 4, 0, 0, 0, 0, 0, 4, 1, 2, 3, 4};
        Files.write(path, concat(id3, adts));
        Assertions.assertThat(MpegAudioMetadata.supportsAdts(path)).isTrue();
        Files.write(path, new byte[] {-1, -15, 80, -128, 1, 31, -4});
        Assertions.assertThat(MpegAudioMetadata.supportsAdts(path)).isFalse();
        adts[2] = 0x7c;
        Files.write(path, adts);
        Assertions.assertThat(MpegAudioMetadata.supportsAdts(path)).isFalse();
    }

    private byte[] track(String handler, String codec) throws Exception {
        byte[] identity = ByteBuffer.allocate(12).putLong(0).put(handler.getBytes(StandardCharsets.US_ASCII)).array();
        byte[] descriptions = concat(new byte[] {0, 0, 0, 0, 0, 0, 0, 1}, atom(codec, new byte[28]));
        return atom("trak", atom("mdia", concat(atom("hdlr", identity),
                atom("minf", atom("stbl", atom("stsd", descriptions))))));
    }

    private byte[] atom(String type, byte[] body) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(output)) {
            data.writeInt(body.length + 8);
            data.write(type.getBytes(StandardCharsets.US_ASCII));
            data.write(body);
        }
        return output.toByteArray();
    }

    private byte[] concat(byte[]... arrays) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] array : arrays) output.write(array);
        return output.toByteArray();
    }

    private Path fixture(String name, byte[] bytes) throws Exception {
        Path directory = TestDirectorySupport.createTempDirectory("mpeg-metadata-");
        return Files.write(directory.resolve(name), bytes);
    }
}
