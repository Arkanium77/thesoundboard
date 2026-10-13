package app;

import javafx.application.Platform;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppRestarter {
    public static final String RESTORE_SESSION_ARGUMENT = "--restore-session";

    /**
     * Linux packages enter through a shell wrapper so missing native libraries can be reported before the JVM starts.
     * ProcessHandle identifies its .bin child; restarting must reenter the wrapper while preserving the arguments
     * and session restoration. Other platform and development launch commands remain unchanged.
     */
    public void restart() throws IOException {
        ProcessHandle.Info processInfo = ProcessHandle.current().info();
        String command = processInfo.command()
                .orElseThrow(() -> new IllegalStateException("The current application command is unavailable"));
        if (command.endsWith("/TheSoundboard.bin")) {
            Path launcher = Path.of(command.substring(0, command.length() - ".bin".length()));
            if (Files.isExecutable(launcher)) command = launcher.toString();
        }
        List<String> restartCommand = new ArrayList<>();
        restartCommand.add(command);
        processInfo.arguments().map(Arrays::asList).ifPresent(restartCommand::addAll);
        if (!restartCommand.contains(RESTORE_SESSION_ARGUMENT)) restartCommand.add(RESTORE_SESSION_ARGUMENT);
        new ProcessBuilder(restartCommand)
                .directory(Path.of(System.getProperty("user.dir")).toFile())
                .start();
        Platform.exit();
    }
}
