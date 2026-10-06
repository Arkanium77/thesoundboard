package app.audio;

import app.support.TestDirectorySupport;
import javafx.application.Platform;
import org.assertj.core.api.Assertions;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

final class AudioTestSupport {
    private AudioTestSupport() { }

    static void startJavaFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); });
        Assertions.assertThat(ready.await(10, TimeUnit.SECONDS)).as("JavaFX startup").isTrue();
    }

    static <T> T onFx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    static void runOnFx(Runnable action) throws Exception {
        onFx(() -> { action.run(); return null; });
    }

    static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!onFx(condition::getAsBoolean)) {
            if (System.nanoTime() >= deadline) throw new AssertionError("Audio condition did not complete in 10 seconds");
            Thread.sleep(10);
        }
    }

    static Path fixture(String extension) throws Exception {
        Path directory = TestDirectorySupport.createTempDirectory("audio-кириллица-");
        Path path = directory.resolve("Тестовый звук." + extension);
        try (InputStream stream = AudioTestSupport.class.getResourceAsStream("/audio/tone." + extension)) {
            Assertions.assertThat(stream).isNotNull();
            Files.copy(stream, path);
        }
        return path;
    }
}
