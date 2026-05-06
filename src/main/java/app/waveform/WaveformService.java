package app.waveform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

public class WaveformService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WaveformService.class);

    private final WaveformExtractor waveformExtractor;
    private final ExecutorService executorService;
    private final Map<Path, CacheEntry> cache = new ConcurrentHashMap<>();
    private final AtomicInteger pendingLoadCount = new AtomicInteger();

    public WaveformService(WaveformExtractor waveformExtractor) {
        this(waveformExtractor, 2);
    }

    public WaveformService(WaveformExtractor waveformExtractor, int workerCount) {
        this.waveformExtractor = waveformExtractor;
        this.executorService = Executors.newFixedThreadPool(Math.max(workerCount, 1), new WaveformThreadFactory());
    }

    public CompletableFuture<WaveformData> loadWaveform(Path audioPath) {
        if (audioPath == null) {
            return CompletableFuture.completedFuture(WaveformData.empty());
        }

        Path normalizedPath = audioPath.toAbsolutePath().normalize();
        CacheEntry cacheEntry = cache.computeIfAbsent(normalizedPath, this::createCacheEntry);
        return cacheEntry.result();
    }

    public void preloadWaveforms(Collection<Path> audioPaths) {
        if (audioPaths == null || audioPaths.isEmpty()) {
            return;
        }

        for (Path audioPath : audioPaths) {
            loadWaveform(audioPath);
        }
    }

    public int getPendingLoadCount() {
        return pendingLoadCount.get();
    }

    public void clear() {
        for (CacheEntry cacheEntry : cache.values()) {
            cancel(cacheEntry);
        }
        cache.clear();
    }

    public void clearUnderRoot(Path rootPath) {
        if (rootPath == null) {
            return;
        }

        Path normalizedRootPath = rootPath.toAbsolutePath().normalize();
        for (Map.Entry<Path, CacheEntry> entry : cache.entrySet()) {
            if (!entry.getKey().startsWith(normalizedRootPath)) {
                continue;
            }

            CacheEntry removedEntry = cache.remove(entry.getKey());
            if (removedEntry != null) {
                cancel(removedEntry);
            }
        }
    }

    public void shutdown() {
        clear();
        executorService.shutdownNow();
    }

    private WaveformData extract(Path audioPath) {
        try {
            return waveformExtractor.extract(audioPath);
        } catch (Exception exception) {
            LOGGER.warn("Failed to extract waveform for {}", audioPath, exception);
            return WaveformData.empty();
        }
    }

    private CacheEntry createCacheEntry(Path audioPath) {
        CompletableFuture<WaveformData> result = new CompletableFuture<>();
        AtomicBoolean finished = new AtomicBoolean();
        pendingLoadCount.incrementAndGet();
        Future<?> future = executorService.submit(() -> {
            try {
                result.complete(extract(audioPath));
            } catch (Exception exception) {
                result.complete(WaveformData.empty());
            } finally {
                markFinished(finished);
            }
        });
        return new CacheEntry(result, future, finished);
    }

    private void cancel(CacheEntry cacheEntry) {
        cacheEntry.future().cancel(true);
        cacheEntry.result().complete(WaveformData.empty());
        markFinished(cacheEntry.finished());
    }

    private void markFinished(AtomicBoolean finished) {
        if (finished.compareAndSet(false, true)) {
            pendingLoadCount.decrementAndGet();
        }
    }

    private static final class WaveformThreadFactory implements ThreadFactory {
        private final AtomicInteger threadIndex = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "waveform-loader-" + threadIndex.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }

    private record CacheEntry(
            CompletableFuture<WaveformData> result,
            Future<?> future,
            AtomicBoolean finished
    ) {
    }
}
