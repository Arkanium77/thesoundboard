package app.waveform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Bounds speculative preloading and completed cache storage while keeping explicit view requests reliable. Required
 * requests are never dropped to make room for prefetch; a selected preloaded entry is promoted ahead of speculative
 * work. Only completed envelopes count toward the LRU budget, and clearing removes queued tasks as well as interrupting
 * decoders. Consumers may retain their own immutable envelopes after eviction without invalidating displayed data.
 */
public class WaveformService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WaveformService.class);
    private static final long MAX_CACHE_BYTES = 32L * 1024L * 1024L;
    private static final int MAX_CACHE_ENTRIES = 2048;
    private final WaveformExtractor waveformExtractor;
    private final ThreadPoolExecutor executorService;
    private final Map<Path, LoadTask> pending = new LinkedHashMap<>();
    private final Map<Path, CachedWaveform> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final int preloadLimit;
    private static final int MAX_PENDING_REQUESTS = 4096;
    private final AtomicInteger activeLoads = new AtomicInteger();
    private long cacheBytes;
    private long sequence;
    private boolean closed;
    private long cacheGeneration;

    public WaveformService(WaveformExtractor waveformExtractor) {
        this(waveformExtractor, 2);
    }

    public WaveformService(WaveformExtractor waveformExtractor, int workerCount) {
        this.waveformExtractor = waveformExtractor;
        int workers = Math.max(workerCount, 1);
        preloadLimit = workers * 4;
        executorService = new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<>(), runnable -> {
                    Thread thread = new Thread(runnable, "waveform-loader");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    public synchronized CompletableFuture<WaveformData> loadWaveform(Path audioPath) {
        return load(audioPath, false);
    }

    public synchronized void preloadWaveforms(Collection<Path> audioPaths) {
        if (audioPaths == null) return;
        for (Path audioPath : audioPaths) {
            if (pending.size() >= preloadLimit) break;
            load(audioPath, true);
        }
    }

    private CompletableFuture<WaveformData> load(Path audioPath, boolean speculative) {
        if (audioPath == null || closed) return CompletableFuture.completedFuture(WaveformData.empty());
        Path path = audioPath.toAbsolutePath().normalize();
        LoadTask existing = pending.get(path);
        if (existing != null) {
            if (!speculative && existing.speculative && executorService.remove(existing)) {
                existing.speculative = false;
                executorService.execute(existing);
            }
            return existing.result;
        }
        if (pending.size() >= MAX_PENDING_REQUESTS) return CompletableFuture.completedFuture(WaveformData.empty());
        LoadTask task = new LoadTask(path, speculative, sequence++);
        pending.put(path, task);
        executorService.execute(task);
        return task.result;
    }

    public synchronized int getPendingLoadCount() {
        return Math.max(pending.size(), activeLoads.get());
    }

    public synchronized void clear() {
        cacheGeneration++;
        for (LoadTask task : pending.values().toArray(LoadTask[]::new)) task.cancel(true);
        pending.clear();
        executorService.purge();
        cache.clear();
        cacheBytes = 0L;
    }

    public synchronized void clearUnderRoot(Path rootPath) {
        cacheGeneration++;
        if (rootPath == null) return;
        Path root = rootPath.toAbsolutePath().normalize();
        for (LoadTask task : pending.values().toArray(LoadTask[]::new)) {
            if (task.path.startsWith(root)) task.cancel(true);
        }
        executorService.purge();
        cache.entrySet().removeIf(entry -> {
            if (!entry.getKey().startsWith(root)) return false;
            cacheBytes -= bytes(entry.getValue().data());
            return true;
        });
    }

    public synchronized void shutdown() {
        closed = true;
        clear();
        executorService.shutdownNow();
    }

    /** Metadata is read on decoder workers, including warm hits. A rescan can retain envelopes without stale
     * path-only hits or filesystem calls on JavaFX. Resolution/algorithm are fixed for this service instance;
     * a new extractor/service starts an empty cache. Cancelled workers remain counted until their run exits. */
    private WaveformData extract(Path path) {
        long generation;
        synchronized (this) { generation = cacheGeneration; }
        FileStamp before = stamp(path);
        synchronized (this) {
            CachedWaveform cached = cache.get(path);
            if (cached != null && cached.stamp().equals(before)) return cached.data();
            if (cached != null) cacheBytes -= bytes(cache.remove(path).data());
        }
        try {
            WaveformData data = waveformExtractor.extract(path);
            if (!Thread.currentThread().isInterrupted() && !data.isEmpty() && before.equals(stamp(path))) {
                synchronized (this) {
                    if (closed || generation != cacheGeneration) return data;
                    CachedWaveform previous = cache.put(path, new CachedWaveform(before, data));
                    if (previous != null) cacheBytes -= bytes(previous.data());
                    cacheBytes += bytes(data);
                    while (cacheBytes > MAX_CACHE_BYTES || cache.size() > MAX_CACHE_ENTRIES) {
                        Path oldest = cache.keySet().iterator().next();
                        cacheBytes -= bytes(cache.remove(oldest).data());
                    }
                }
            }
            return data;
        } catch (Exception exception) {
            if (!Thread.currentThread().isInterrupted()) LOGGER.warn("Failed to extract waveform for {}", path, exception);
            return WaveformData.empty();
        }
    }

    private FileStamp stamp(Path path) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            return new FileStamp(attributes.size(), attributes.lastModifiedTime());
        } catch (IOException exception) {
            return new FileStamp(-1L, FileTime.fromMillis(0L));
        }
    }

    private record FileStamp(long size, FileTime modified) { }
    private record CachedWaveform(FileStamp stamp, WaveformData data) { }

    private long bytes(WaveformData data) {
        return (long) data.size() * Double.BYTES * 2;
    }

    private final class LoadTask extends FutureTask<WaveformData> implements Comparable<LoadTask> {
        private final Path path;
        private final long order;
        private boolean speculative;
        private final CompletableFuture<WaveformData> result = new CompletableFuture<>();

        private LoadTask(Path path, boolean speculative, long order) {
            super(() -> extract(path));
            this.path = path;
            this.speculative = speculative;
            this.order = order;
        }

        @Override
        public void run() {
            activeLoads.incrementAndGet();
            try { super.run(); } finally { activeLoads.decrementAndGet(); }
        }

        @Override
        public int compareTo(LoadTask other) {
            int priority = Boolean.compare(speculative, other.speculative);
            return priority == 0 ? Long.compare(order, other.order) : priority;
        }

        @Override
        protected void done() {
            WaveformData data = WaveformData.empty();
            if (!isCancelled()) {
                try {
                    data = get();
                } catch (Exception exception) {
                    LOGGER.debug("Waveform task did not complete for {}", path, exception);
                }
            }
            synchronized (WaveformService.this) {
                pending.remove(path, this);
            }
            result.complete(data);
        }
    }
}
