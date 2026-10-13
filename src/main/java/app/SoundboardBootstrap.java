package app;

import java.lang.reflect.InvocationTargetException;

/**
 * Keeps the packaged entry point independent of JavaFX, JNA and codecs. Directly entering the JavaFX launcher can
 * fail class verification before main executes when a dependency is missing, preventing useful diagnostics.
 * Reflection deliberately defers those dependencies until this Java-only failure boundary is installed.
 */
public final class SoundboardBootstrap {
    private SoundboardBootstrap() { }

    public static void main(String[] args) {
        try {
            Class.forName("app.SoundboardLauncher").getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            Throwable failure = exception instanceof InvocationTargetException invocation
                    && invocation.getCause() != null ? invocation.getCause() : exception;
            StartupFailureReporter.report(failure);
            System.exit(1);
        }
    }
}
