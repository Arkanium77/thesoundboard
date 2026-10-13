package app;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports startup failures without requiring the broken UI/audio dependency itself. Only the optional themed dialog
 * is loaded reflectively. Native fallbacks pass text as data (environment/argv), never interpolate it into scripts.
 * Logs live outside the copy-over installation. Early fallback text uses the OS language because localization
 * packages and their JSON reader may be the missing dependency. Linux delegates window/log ownership to its shell
 * launcher, which can also report failures occurring before Java starts, avoiding duplicate startup dialogs/logs.
 */
public final class StartupFailureReporter {
    private StartupFailureReporter() { }

    public static void report(Throwable failure) {
        String os = System.getProperty("os.name", "");
        String message = describe(failure, os, Locale.getDefault().getLanguage().equals("ru"));
        System.err.println(message);
        failure.printStackTrace(System.err);
        if (os.toLowerCase(Locale.ROOT).contains("linux")) return;
        try {
            Path directory = Path.of(System.getProperty("user.home"), ".thesoundboard", "logs");
            Files.createDirectories(directory);
            Path log = Files.createTempFile(directory, "startup-", ".log");
            StringWriter details = new StringWriter();
            failure.printStackTrace(new PrintWriter(details));
            Files.writeString(log, message + "\n\n" + details, StandardCharsets.UTF_8);
            message += "\n\nDiagnostics: " + log;
        } catch (IOException | RuntimeException exception) {
            System.err.println("Cannot save startup diagnostics: " + exception.getMessage());
        }
        try {
            Class.forName("app.ui.StartupErrorDialog").getMethod("show", String.class).invoke(null, message);
            return;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            // The original failure can prevent JavaFX itself from loading or leave its toolkit already shut down.
        }
        try {
            ProcessBuilder builder;
            if (os.toLowerCase(Locale.ROOT).contains("win")) {
                Path powershell = Path.of(System.getenv().getOrDefault("SystemRoot", "C:\\Windows"),
                        "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
                builder = new ProcessBuilder(powershell.toString(), "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                        "-Command", "Add-Type -AssemblyName System.Windows.Forms; "
                        + "[System.Windows.Forms.MessageBox]::Show($env:THESOUNDBOARD_STARTUP_ERROR, "
                        + "'The Soundboard', 'OK', 'None') | Out-Null");
                builder.environment().put("THESOUNDBOARD_STARTUP_ERROR", message);
            } else if (os.toLowerCase(Locale.ROOT).contains("mac")) {
                builder = new ProcessBuilder("/usr/bin/osascript", "-e", "on run argv", "-e",
                        "display dialog (item 1 of argv) with title \"The Soundboard\" buttons {\"OK\"} default button \"OK\"",
                        "-e", "end run", message);
            } else return;
            builder.inheritIO().start().waitFor();
        } catch (IOException | RuntimeException exception) {
            System.err.println("Cannot show startup diagnostics: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /** Classifies the complete cause chain: reflection and JavaFX wrappers otherwise hide the actual loader error. */
    static String describe(Throwable failure, String os, boolean russian) {
        StringBuilder chain = new StringBuilder();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        Throwable last = failure;
        while (current != null && seen.add(current)) {
            chain.append(current.getClass().getSimpleName()).append(": ").append(current.getMessage()).append('\n');
            last = current;
            current = current.getCause();
        }
        String text = chain.toString();
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("incompatible architecture") || lower.contains("wrong architecture") || lower.contains("bad cpu type") || lower.contains("not a valid win32")
                || lower.contains("wrong elf class")) {
            return russian ? "Архитектура приложения или библиотеки не подходит этому компьютеру. Используйте сборку для вашей ОС и процессора."
                    : "The application or library has the wrong architecture. Use the build for your operating system and processor.";
        }
        Matcher version = Pattern.compile("GLIBC(?:XX)?_[0-9.]+").matcher(text);
        if (version.find()) {
            return russian ? "Системная библиотека не поддерживает " + version.group() + ". Обновите её через менеджер пакетов вашей системы."
                    : "A system library does not provide " + version.group() + ". Update it using your system package manager.";
        }
        if (os.toLowerCase(Locale.ROOT).contains("mac") && (lower.contains("code signature")
                || lower.contains("library load disallowed") || lower.contains("not valid for use in process"))) {
            return russian ? "macOS заблокировала библиотеку приложения. Заново распакуйте полный архив; проверьте причину блокировки в настройках конфиденциальности и безопасности macOS."
                    : "macOS blocked an application library. Extract the complete archive again and check Privacy & Security settings for the reason.";
        }
        if (lower.contains("msvcp") || lower.contains("vcruntime")) {
            return russian ? "Не удалось загрузить Microsoft Visual C++ Runtime. Заново распакуйте полный архив приложения; при отсутствии системного runtime установите официальный Microsoft Visual C++ Redistributable для архитектуры сборки."
                    : "Cannot load Microsoft Visual C++ Runtime. Extract the complete application archive; if the system runtime is missing, install the official Microsoft Visual C++ Redistributable for the build architecture.";
        }
        if (lower.contains("noclassdeffounderror") || lower.contains("classnotfoundexception")
                || lower.contains("missing gstreamer") || lower.contains("invalid gstreamer")) {
            return russian ? "Не найдены необходимые файлы приложения. Заново распакуйте полный архив TheSoundboard."
                    : "Required application files are missing. Extract the complete TheSoundboard archive again.";
        }
        if (lower.contains("unsatisfiedlinkerror") || lower.contains("library not loaded")
                || lower.contains("cannot load gstreamer plugin")) {
            Matcher library = Pattern.compile("[A-Za-z0-9_+.-]+\\.(?:dll|dylib|so(?:\\.[0-9]+)*)").matcher(text);
            String name = library.find() ? " " + library.group() : "";
            return russian ? "Не удалось загрузить библиотеку" + name + ". Заново распакуйте полный архив приложения. Для системной библиотеки установите предоставляющий её пакет или обновление ОС."
                    : "Cannot load library" + name + ". Extract the complete application archive. For a system library, install its provider package or operating system update.";
        }
        String reason = last.getMessage() == null ? last.getClass().getSimpleName() : last.getMessage();
        if (reason.length() > 240) reason = reason.substring(0, 240) + "…";
        return (russian ? "Не удалось запустить TheSoundboard: " : "TheSoundboard could not start: ") + reason;
    }
}
