package app;

import app.support.TestDirectorySupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

class StartupFailureReporterTest {
    @Test
    void bootstrapReportsMissingApplicationClassesWithOnlyJavaBaseAvailable() throws Exception {
        Path root = TestDirectorySupport.createTempDirectory("startup-bootstrap-");
        Path classes = Files.createDirectories(root.resolve("app"));
        for (String name : new String[]{"SoundboardBootstrap", "StartupFailureReporter"}) {
            try (var source = getClass().getResourceAsStream("/app/" + name + ".class")) {
                Assertions.assertThat(source).isNotNull();
                Files.copy(source, classes.resolve(name + ".class"));
            }
        }
        String java = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
        Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", java).toString(),
                "-Dos.name=Linux", "-Duser.language=en", "-cp", root.toString(), "app.SoundboardBootstrap")
                .redirectErrorStream(true).start();
        try {
            Assertions.assertThat(child.waitFor(10, TimeUnit.SECONDS)).isTrue();
            Assertions.assertThat(child.exitValue()).isEqualTo(1);
            Assertions.assertThat(new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                    .contains("Required application files are missing", "app.SoundboardLauncher");
        } finally {
            if (child.isAlive()) child.destroyForcibly();
        }
    }

    @Test
    void explainsMissingWindowsDllThroughWrappedCause() {
        Throwable failure = new RuntimeException("Application failed", new UnsatisfiedLinkError("Unable to load library gstreamer-1.0-0.dll: The specified module could not be found"));
        Assertions.assertThat(StartupFailureReporter.describe(failure, "Windows 11", false))
                .contains("gstreamer-1.0-0.dll", "Extract the complete application archive");
    }

    @Test
    void distinguishesArchitectureFromMissingRuntime() {
        Throwable failure = new UnsatisfiedLinkError("libgst.dylib: mach-o file, but is an incompatible architecture");
        Assertions.assertThat(StartupFailureReporter.describe(failure, "Mac OS X", false))
                .contains("wrong architecture").doesNotContain("provider package");
        Assertions.assertThat(StartupFailureReporter.describe(new UnsatisfiedLinkError("%1 is not a valid Win32 application"), "Windows 11", true))
                .contains("Архитектура");
    }

    @Test
    void explainsMacLibraryAndSecurityFailures() {
        Assertions.assertThat(StartupFailureReporter.describe(new UnsatisfiedLinkError("Library not loaded: @rpath/libgstapp-1.0.0.dylib"), "Mac OS X", false))
                .contains("libgstapp-1.0.0.dylib", "operating system update");
        Assertions.assertThat(StartupFailureReporter.describe(new UnsatisfiedLinkError("code signature invalid"), "Mac OS X", false))
                .contains("Privacy & Security");
    }

    @Test
    void distinguishesMissingJavaDependencyAndVisualCppRuntime() {
        Assertions.assertThat(StartupFailureReporter.describe(new NoClassDefFoundError("javafx/application/Application"), "Windows 11", false))
                .contains("Required application files", "Extract the complete");
        Assertions.assertThat(StartupFailureReporter.describe(new UnsatisfiedLinkError("msvcp140.dll not found"), "Windows 11", false))
                .contains("Microsoft Visual C++", "Redistributable");
    }

    @Test
    void preservesUnknownReasonWithoutDumpingTheStackTraceIntoTheMessage() {
        Assertions.assertThat(StartupFailureReporter.describe(new RuntimeException("outer", new IllegalStateException("Specific failure")), "Mac OS X", false))
                .isEqualTo("TheSoundboard could not start: Specific failure");
    }
}
