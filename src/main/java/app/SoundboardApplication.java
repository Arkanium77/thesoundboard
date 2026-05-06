package app;

import app.audio.AudioEngine;
import app.audio.JavaFxAudioEngine;
import app.config.AppConfig;
import app.config.AppConfigLoader;
import app.config.LoggingConfigurer;
import app.persistence.ProjectStateRepository;
import app.project.ProjectService;
import app.project.ProjectStateEditor;
import app.project.ProjectStateSynchronizer;
import app.scan.AudioScanner;
import app.ui.main.MainView;
import app.waveform.AudioInputStreamWaveformExtractor;
import app.waveform.WaveformService;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class SoundboardApplication extends Application {
    private MainView mainView;

    @Override
    public void start(Stage stage) {
        AppConfig appConfig = AppConfigLoader.load();
        LoggingConfigurer.configure(appConfig.getLogging());

        AudioScanner audioScanner = new AudioScanner(appConfig.getScanner().getSupportedExtensions());
        ProjectStateRepository projectStateRepository = new ProjectStateRepository(appConfig.getPersistence().getStateFileName());
        ProjectStateSynchronizer projectStateSynchronizer = new ProjectStateSynchronizer(appConfig.getSchemaVersion());
        ProjectService projectService = new ProjectService(
                appConfig.getSchemaVersion(),
                projectStateRepository,
                audioScanner,
                projectStateSynchronizer
        );
        ProjectStateEditor projectStateEditor = new ProjectStateEditor();
        AudioEngine audioEngine = new JavaFxAudioEngine();
        int waveformWorkerCount = Math.max(2, Math.min(Runtime.getRuntime().availableProcessors(), 8));
        WaveformService waveformService = new WaveformService(
                new AudioInputStreamWaveformExtractor(appConfig.getUi().getWaveformResolution()),
                waveformWorkerCount
        );

        mainView = new MainView(stage, appConfig, projectService, projectStateEditor, audioEngine, waveformService);

        Scene scene = new Scene(mainView, appConfig.getUi().getMinWidth(), appConfig.getUi().getMinHeight());
        stage.setTitle(appConfig.getTitle());
        stage.setMinWidth(appConfig.getUi().getMinWidth());
        stage.setMinHeight(appConfig.getUi().getMinHeight());
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (mainView != null) {
            mainView.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
