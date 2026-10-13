package app.waveform;

import app.audio.GstreamerRuntime;
import com.sun.jna.Library;
import com.sun.jna.Pointer;
import javafx.util.Duration;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.elements.AppSink;
import org.freedesktop.gstreamer.glib.Natives;
import org.freedesktop.gstreamer.lowlevel.GstNative;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Uses the same native decoder family as playback, without a device or clock synchronization. Only two native
 * buffers and a bounded peak/RMS accumulator are retained; no PCM files or whole recordings are cached. Timed
 * pulls are a small JNA extension to the stable binding, whose blocking pull cannot observe cancellation. The
 * semaphore limits decoder pressure even for direct callers. The scheduler uses the same concurrency limit so
 * speculative work waits in its priority queue, where a selected track can overtake it, instead of at this semaphore.
 * All source channels contribute to the envelope; downmixing could cancel out a transient. Starting with one-frame
 * buckets preserves detail in short sound effects while adaptive merging bounds long recordings to the same memory.
 * Caps and PCM buffers are borrowed from each Sample in this binding. Unmap before disposing the owning Sample;
 * separately disposing a borrowed wrapper can invalidate native storage still owned by the sample or another decoder.
 */
public class GstreamerWaveformExtractor implements WaveformExtractor {
    private final int resolution;
    private static final int MAX_CONCURRENT_LOADS = 2;
    private final Semaphore decoders = new Semaphore(MAX_CONCURRENT_LOADS);

    public GstreamerWaveformExtractor(int resolution) {
        this.resolution = resolution;
    }

    @Override
    public int getMaxConcurrentLoads() { return MAX_CONCURRENT_LOADS; }

    @Override
    public WaveformData extract(Path audioPath) throws IOException {
        GstreamerRuntime.initialize();
        try {
            decoders.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Waveform cancelled", exception);
        }
        try {
            return decode(audioPath);
        } finally {
            decoders.release();
        }
    }

    private WaveformData decode(Path path) throws IOException {
        Pipeline pipeline = (Pipeline) Gst.parseLaunch("filesrc name=source ! decodebin ! audioconvert ! "
                + "audio/x-raw,format=F32LE,layout=interleaved ! "
                + "appsink name=wave sync=false max-buffers=2 drop=false wait-on-eos=false");
        AppSink sink = (AppSink) pipeline.getElementByName("wave");
        Element source = pipeline.getElementByName("source");
        Bus bus = pipeline.getBus();
        AtomicReference<String> failure = new AtomicReference<>();
        bus.connect((Bus.ERROR) (object, code, message) -> failure.set(message));
        WaveformAccumulator accumulator = new WaveformAccumulator(resolution, 1);
        long frames = 0;
        int sampleRate = 0;
        long lastData = System.nanoTime();
        try {
            source.set("location", path.toAbsolutePath().toString());
            pipeline.setState(State.PLAYING);
            while (true) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Waveform cancelled");
                if (failure.get() != null) throw new IOException(failure.get());
                Pointer pointer = TimedPull.API.gst_app_sink_try_pull_sample(Natives.getRawPointer(sink),
                        TimeUnit.MILLISECONDS.toNanos(100));
                if (pointer == null) {
                    if (sink.isEOS()) break;
                    if (System.nanoTime() - lastData > TimeUnit.SECONDS.toNanos(10)) {
                        throw new IOException("Audio decoder did not produce PCM");
                    }
                    continue;
                }
                Sample sample = Natives.callerOwnsReturn(pointer, Sample.class);
                try {
                    int rate = sample.getCaps().getStructure(0).getInteger("rate");
                    int channels = sample.getCaps().getStructure(0).getInteger("channels");
                    if (channels < 1 || channels > 64) throw new IOException("Unexpected decoded channel count");
                    if (sampleRate != 0 && sampleRate != rate) throw new IOException("Audio sample rate changed");
                    sampleRate = rate;
                    Buffer buffer = sample.getBuffer();
                    ByteBuffer bytes = buffer.map(false);
                    if (bytes == null) throw new IOException("Cannot map decoded PCM");
                    try {
                        bytes.order(ByteOrder.LITTLE_ENDIAN);
                        int frameBytes = channels * Float.BYTES;
                        if (bytes.remaining() > 1024 * 1024 || bytes.remaining() % frameBytes != 0) {
                            throw new IOException("Unexpected decoded PCM buffer size");
                        }
                        while (bytes.remaining() >= frameBytes) {
                            double amplitude = 0d;
                            for (int channel = 0; channel < channels; channel++) {
                                amplitude = Math.max(amplitude, Math.abs(bytes.getFloat()));
                            }
                            if (!Double.isFinite(amplitude)) throw new IOException("Non-finite decoded PCM");
                            accumulator.add(amplitude);
                            frames++;
                        }
                    } finally {
                        buffer.unmap();
                    }
                } finally {
                    sample.dispose();
                }
                lastData = System.nanoTime();
            }
            if (failure.get() != null) throw new IOException(failure.get());
            if (frames == 0 || sampleRate <= 0) throw new IOException("Audio stream contains no PCM");
            return accumulator.finish(Duration.millis(frames * 1000d / sampleRate));
        } finally {
            pipeline.setState(State.NULL);
            pipeline.getState(5, TimeUnit.SECONDS);
            source.dispose();
            sink.dispose();
            bus.dispose();
            pipeline.dispose();
        }
    }

    private interface TimedPull extends Library {
        TimedPull API = GstNative.load("gstapp", TimedPull.class);

        Pointer gst_app_sink_try_pull_sample(Pointer sink, long timeout);
    }
}
