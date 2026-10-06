package app.audio;

import com.badlogic.gdx.Gdx;
import games.rednblack.miniaudio.MiniAudio;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Loads JNI without opening an audio device, including on CI runners without speakers. */
class MiniaudioNativeLibraryTest {
    @Test
    void loadsBundledNativeWithoutStartingLibGdx() throws Exception {
        Class.forName(MiniAudio.class.getName());
        Assertions.assertThat(Gdx.app).isNull();
        Assertions.assertThat(Gdx.audio).isNull();
    }

    @Test
    void shipsEveryReleaseArchitectureAndMacLibrariesOnlyLinkSystemDependencies() throws Exception {
        byte[] windows = binary("gdx-miniaudio64.dll");
        ByteBuffer pe = ByteBuffer.wrap(windows).order(ByteOrder.LITTLE_ENDIAN);
        int peHeader = pe.getInt(0x3c);
        Assertions.assertThat(pe.getShort(peHeader + 4)).isEqualTo((short) 0x8664);
        ByteBuffer elf = ByteBuffer.wrap(binary("libgdx-miniaudio64.so")).order(ByteOrder.LITTLE_ENDIAN);
        Assertions.assertThat(elf.getShort(18)).isEqualTo((short) 62);
        verifyMac("libgdx-miniaudio64.dylib", 0x01000007);
        verifyMac("libgdx-miniaudioarm64.dylib", 0x0100000c);
    }

    private void verifyMac(String name, int cpuType) throws Exception {
        byte[] bytes = binary(name);
        ByteBuffer macho = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        Assertions.assertThat(macho.getInt(0)).isEqualTo(0xfeedfacf);
        Assertions.assertThat(macho.getInt(4)).isEqualTo(cpuType);
        int commands = macho.getInt(16);
        int offset = 32;
        for (int index = 0; index < commands; index++) {
            int command = macho.getInt(offset);
            int size = macho.getInt(offset + 4);
            if (command == 0xc || command == 0x80000018 || command == 0x8000001f) {
                int start = offset + macho.getInt(offset + 8);
                int end = start;
                while (end < offset + size && bytes[end] != 0) end++;
                String dependency = new String(bytes, start, end - start, StandardCharsets.UTF_8);
                Assertions.assertThat(dependency).matches("^/(usr/lib|System/Library)/.*");
            }
            offset += size;
        }
    }

    private byte[] binary(String name) throws Exception {
        try (InputStream stream = MiniAudio.class.getResourceAsStream("/" + name)) {
            Assertions.assertThat(stream).as("Bundled native %s", name).isNotNull();
            return stream.readAllBytes();
        }
    }
}
