package app.waveform;

import app.support.TestDirectorySupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicInteger;

class WaveformServiceTest {
    @Test
    void limitsSpeculationAndPromotesExplicitRequests() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> extracted = Collections.synchronizedList(new ArrayList<>());
        WaveformService service = new WaveformService(path -> {
            extracted.add(path.getFileName().toString());
            if (path.getFileName().toString().equals("first")) {
                started.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new InterruptedIOException("Timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("Cancelled");
                }
            }
            return new WaveformData(new double[]{1d});
        }, 1);
        try {
            service.preloadWaveforms(List.of(Path.of("first"), Path.of("second"), Path.of("third"),
                    Path.of("fourth"), Path.of("not-admitted")));
            Assertions.assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            Assertions.assertThat(service.getPendingLoadCount()).isEqualTo(4);
            var selected = service.loadWaveform(Path.of("fourth"));
            release.countDown();
            selected.get(5, TimeUnit.SECONDS);
            Assertions.assertThat(List.copyOf(extracted).subList(0, 2)).containsExactly("first", "fourth");
        } finally {
            release.countDown();
            service.shutdown();
        }
    }

    @Test
    void clearingInterruptsRunningDecoderAndCompletesQueuedRequests() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        WaveformService service = new WaveformService(path -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return WaveformData.empty();
        }, 1);
        try {
            var running = service.loadWaveform(Path.of("first"));
            Assertions.assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            var queued = service.loadWaveform(Path.of("second"));
            service.clear();
            Assertions.assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
            Assertions.assertThat(running.get(5, TimeUnit.SECONDS).isEmpty()).isTrue();
            Assertions.assertThat(queued.get(5, TimeUnit.SECONDS).isEmpty()).isTrue();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (service.getPendingLoadCount() != 0 && System.nanoTime() < deadline) Thread.sleep(5);
            Assertions.assertThat(service.getPendingLoadCount()).isZero();
        } finally {
            service.shutdown();
        }
        Assertions.assertThat(service.loadWaveform(Path.of("after-shutdown")).join().isEmpty()).isTrue();
    }

    @Test
    void evictsOldCompletedEntriesWithoutBreakingExistingConsumers() {
        AtomicInteger extractions = new AtomicInteger();
        WaveformService service = new WaveformService(path -> {
            extractions.incrementAndGet();
            return new WaveformData(new double[]{1d});
        }, 1);
        try {
            WaveformData retained = service.loadWaveform(Path.of("0")).join();
            for (int index = 1; index <= 2048; index++) service.loadWaveform(Path.of(Integer.toString(index))).join();
            service.loadWaveform(Path.of("0")).join();
            Assertions.assertThat(extractions.get()).isEqualTo(2050);
            Assertions.assertThat(retained.copyAmplitudes()).containsExactly(1d);
        } finally {
            service.shutdown();
        }
    }

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
    @Test
    void invalidatesChangedFilesAndRetriesFailuresWithoutInvalidatingSilence() throws Exception {
        Path root = TestDirectorySupport.createTempDirectory("waveform-metadata-");
        Path file = Files.writeString(root.resolve("audio"), "a");
        AtomicInteger calls = new AtomicInteger();
        WaveformService service = new WaveformService(path -> {
            int call = calls.incrementAndGet();
            return call == 3 ? WaveformData.empty() : new WaveformData(new double[]{0d});
        }, 1);
        try {
            service.loadWaveform(file).get(5, TimeUnit.SECONDS);
            service.loadWaveform(file).get(5, TimeUnit.SECONDS);
            Assertions.assertThat(calls.get()).isEqualTo(1);
            Files.writeString(file, "changed size");
            service.loadWaveform(file).get(5, TimeUnit.SECONDS);
            Assertions.assertThat(calls.get()).isEqualTo(2);
            service.clear();
            Assertions.assertThat(service.loadWaveform(file).get(5, TimeUnit.SECONDS).isEmpty()).isTrue();
            Assertions.assertThat(service.loadWaveform(file).get(5, TimeUnit.SECONDS).isEmpty()).isFalse();
            Assertions.assertThat(calls.get()).isEqualTo(4);
        } finally { service.shutdown(); }
    }

    @Test
    void cancelledDecoderIsCountedUntilItActuallyExitsAndCannotPublishStaleCache() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        WaveformService service = new WaveformService(path -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                try { release.await(); }
                catch (InterruptedException exception) {
                    interrupted.countDown();
                    try { release.await(); } catch (InterruptedException again) { Thread.currentThread().interrupt(); }
                }
            }
            return new WaveformData(new double[]{1d});
        }, 1);
        try {
            service.loadWaveform(Path.of("cancelled"));
            Assertions.assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            service.clear();
            Assertions.assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
            Assertions.assertThat(service.getPendingLoadCount()).isEqualTo(1);
            release.countDown();
            service.loadWaveform(Path.of("cancelled")).get(5, TimeUnit.SECONDS);
            Assertions.assertThat(calls.get()).isEqualTo(2);
        } finally { release.countDown(); service.shutdown(); }
    }

}
