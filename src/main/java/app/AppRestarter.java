package app;

import javafx.application.Platform;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppRestarter {
    public static final String RESTORE_SESSION_ARGUMENT = "--restore-session";

    public void restart() throws IOException {
        ProcessHandle.Info processInfo = ProcessHandle.current().info();
        String command = processInfo.command()
                .orElseThrow(() -> new IllegalStateException("The current application command is unavailable"));
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
