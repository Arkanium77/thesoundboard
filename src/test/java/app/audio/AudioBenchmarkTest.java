package app.audio;

import com.sun.management.OperatingSystemMXBean;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Measures playback alone with identical looping MP3 sources, excluding waveform workers and application UI.
 * CPU is expressed against one logical core; JVM heap excludes native allocations and is not process RSS.
 * These observations have no machine-dependent pass threshold and are not a substitute for audible latency tests.
 */
@Tag("audio-benchmark")
class AudioBenchmarkTest {
    @Test
    void comparesIdleAndConcurrentPlayback() throws Exception {
        AudioTestSupport.startJavaFx();
        OperatingSystemMXBean operatingSystem = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        Assertions.assertThat(operatingSystem.getProcessCpuTime()).isNotNegative();
        Path fixture = AudioTestSupport.fixture("mp3");
        List<String> results = new ArrayList<>();
        results.add("engine,playing_tracks,cpu_percent_one_core,jvm_heap_mib,java_threads");
        for (AudioEngineMode mode : AudioEngineMode.values()) {
            AudioEngine engine = AudioTestSupport.onFx(mode::createEngine);
            List<PlayingTrack> tracks = new ArrayList<>();
            try {
                for (int count : List.of(1, 4, 16)) {
                    while (tracks.size() < count) {
                        PlayingTrack track = AudioTestSupport.onFx(() -> engine.createTrack(fixture));
                        AudioTestSupport.runOnFx(() -> { track.setVolume(0d); track.setLoop(true); });
                        AudioTestSupport.await(() -> track.getTotalDuration().toSeconds() > 0.8d);
                        tracks.add(track);
                    }
                    AudioTestSupport.runOnFx(() -> tracks.forEach(PlayingTrack::play));
                    Thread.sleep(500);
                    results.add(measure(mode, count, operatingSystem));
                    AudioTestSupport.runOnFx(() -> tracks.forEach(PlayingTrack::stop));
                }
                Thread.sleep(500);
                results.add(measure(mode, 0, operatingSystem));
            } finally {
                AudioTestSupport.runOnFx(() -> { tracks.forEach(PlayingTrack::dispose); engine.shutdown(); });
            }
        }
        Path output = Path.of("build", "reports", "audio-comparison.csv");
        Files.createDirectories(output.getParent());
        Files.write(output, results);
        results.forEach(System.out::println);
    }

    private String measure(AudioEngineMode mode, int count, OperatingSystemMXBean operatingSystem) throws Exception {
        long cpuBefore = operatingSystem.getProcessCpuTime();
        long started = System.nanoTime();
        Thread.sleep(TimeUnit.SECONDS.toMillis(3));
        long elapsed = System.nanoTime() - started;
        double cpu = 100d * (operatingSystem.getProcessCpuTime() - cpuBefore) / elapsed;
        double heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / (1024d * 1024d);
        int threads = ManagementFactory.getThreadMXBean().getThreadCount();
        return String.format(Locale.ROOT, "%s,%d,%.2f,%.2f,%d", mode, count, cpu, heap, threads);
    }
}
