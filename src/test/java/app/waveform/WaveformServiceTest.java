package app.waveform;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

class WaveformServiceTest {
    @Test
    void cachesWaveformByPath() {
        AtomicInteger extractCalls = new AtomicInteger();
        WaveformExtractor waveformExtractor = audioPath -> {
            extractCalls.incrementAndGet();
            return new WaveformData(new double[]{0.1d, 0.8d, 0.3d});
        };

        WaveformService waveformService = new WaveformService(waveformExtractor);
        Path audioPath = Path.of("music", "theme.mp3");

        WaveformData firstWaveform = waveformService.loadWaveform(audioPath).join();
        WaveformData secondWaveform = waveformService.loadWaveform(audioPath).join();

        Assertions.assertThat(firstWaveform.copyAmplitudes()).containsExactly(0.1d, 0.8d, 0.3d);
        Assertions.assertThat(secondWaveform.copyAmplitudes()).containsExactly(0.1d, 0.8d, 0.3d);
        Assertions.assertThat(extractCalls.get()).isEqualTo(1);

        waveformService.shutdown();
    }

    @Test
    void clearsCachedWaveformsUnderRoot() {
        AtomicInteger extractCalls = new AtomicInteger();
        WaveformExtractor waveformExtractor = audioPath -> {
            extractCalls.incrementAndGet();
            return new WaveformData(new double[]{0.1d});
        };

        WaveformService waveformService = new WaveformService(waveformExtractor, 1);
        Path firstAudioPath = Path.of("project-a", "music", "first.mp3");
        Path secondAudioPath = Path.of("project-b", "music", "second.mp3");

        waveformService.loadWaveform(firstAudioPath).join();
        waveformService.loadWaveform(secondAudioPath).join();
        waveformService.clearUnderRoot(Path.of("project-a"));
        waveformService.loadWaveform(firstAudioPath).join();
        waveformService.loadWaveform(secondAudioPath).join();

        Assertions.assertThat(extractCalls.get()).isEqualTo(3);

        waveformService.shutdown();
    }

    @Test
    void clearsAllCachedWaveforms() {
        AtomicInteger extractCalls = new AtomicInteger();
        WaveformExtractor waveformExtractor = audioPath -> {
            extractCalls.incrementAndGet();
            return new WaveformData(new double[]{0.2d});
        };

        WaveformService waveformService = new WaveformService(waveformExtractor, 1);
        Path audioPath = Path.of("project-a", "music", "theme.mp3");

        waveformService.loadWaveform(audioPath).join();
        waveformService.clear();
        waveformService.loadWaveform(audioPath).join();

        Assertions.assertThat(extractCalls.get()).isEqualTo(2);

        waveformService.shutdown();
    }
}
