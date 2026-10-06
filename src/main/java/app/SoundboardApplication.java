package app;

import app.audio.AudioEngine;
import app.audio.AudioEngineMode;
import app.config.AppConfig;
import app.config.AppConfigLoader;
import app.config.LoggingConfigurer;
import app.persistence.ProjectStateRepository;
import app.localization.LocalizationService;
import app.localization.TextKey;
import app.localization.Texts;
import app.project.ProjectService;
import app.project.LastProjectPreferences;
import app.project.ProjectStateEditor;
import app.project.ProjectStateSynchronizer;
import app.scan.AudioScanner;
import app.skin.SkinService;
import app.ui.UiScalePane;
import app.ui.UiScalePreferences;
import app.ui.main.MainView;
import app.waveform.AudioInputStreamWaveformExtractor;
import app.waveform.WaveformService;
import app.waveform.WaveformDisplaySettings;
import app.waveform.WaveformPreferences;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

public class SoundboardApplication extends Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(SoundboardApplication.class);
    private MainView mainView;
    private AudioEngine audioEngine;

    @Override
    public void start(Stage stage) {
        AppConfig appConfig = AppConfigLoader.load();
        LoggingConfigurer.configure(appConfig.getLogging());
        SkinService skinService = new SkinService();
        skinService.prepareUiResources();
        LocalizationService localizationService = new LocalizationService();
        LastProjectPreferences lastProjectPreferences = new LastProjectPreferences();
        Texts.configure(localizationService);

        AudioEngineMode audioEngineMode = AudioEngineMode.fromArguments(getParameters().getRaw());
        LOGGER.info("Selected audio engine: {}", audioEngineMode);
        AudioScanner audioScanner = new AudioScanner(audioEngineMode.filterSupportedExtensions(
                appConfig.getScanner().getSupportedExtensions()), audioEngineMode::supportsFile);
        ProjectStateRepository projectStateRepository = new ProjectStateRepository(appConfig.getPersistence().getStateFileName());
        ProjectStateSynchronizer projectStateSynchronizer = new ProjectStateSynchronizer(appConfig.getSchemaVersion());
        ProjectService projectService = new ProjectService(
                appConfig.getSchemaVersion(),
                projectStateRepository,
                audioScanner,
                projectStateSynchronizer
        );
        ProjectStateEditor projectStateEditor = new ProjectStateEditor();
        audioEngine = audioEngineMode.createEngine();
        int waveformWorkerCount = Math.max(2, Math.min(Runtime.getRuntime().availableProcessors(), 8));
        WaveformService waveformService = new WaveformService(
                new AudioInputStreamWaveformExtractor(appConfig.getUi().getWaveformResolution()),
                waveformWorkerCount
        );

        UiScalePreferences uiScalePreferences = new UiScalePreferences();
        DoubleProperty uiScale = new SimpleDoubleProperty(uiScalePreferences.load(appConfig.getUi()));
        uiScale.addListener((observable, oldValue, newValue) ->
                uiScalePreferences.save(newValue.doubleValue(), appConfig.getUi())
        );
        WaveformPreferences waveformPreferences = new WaveformPreferences();
        WaveformDisplaySettings.setMode(waveformPreferences.loadMode());

        mainView = new MainView(stage, appConfig, projectService, projectStateEditor, audioEngine, waveformService,
                uiScale, skinService, localizationService, lastProjectPreferences, waveformPreferences);
        UiScalePane uiScalePane = new UiScalePane(mainView, uiScale);

        Scene scene = new Scene(
                uiScalePane,
                appConfig.getUi().getMinWidth(),
                appConfig.getUi().getMinHeight()
        );
        skinService.apply(scene, mainView);
        applyApplicationIcon(stage);
        stage.setTitle(localizationService.text(TextKey.APP_TITLE));
        applyStageMinimumSize(stage, appConfig);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> { event.consume(); mainView.prepareClose(() -> { stage.hide(); Platform.exit(); }); });
        stage.show();
        boolean applicationRestart = getParameters().getRaw().contains(AppRestarter.RESTORE_SESSION_ARGUMENT);
        mainView.restoreLastProject(applicationRestart);
    }

    @Override
    public void stop() {
        if (mainView != null) {
            mainView.shutdown();
        }
        if (audioEngine != null) audioEngine.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void applyApplicationIcon(Stage stage) {
        URL iconResource = SoundboardApplication.class.getResource("/icons/app-icon.png");
        if (iconResource == null) {
            throw new IllegalStateException("Application icon resource is missing: /icons/app-icon.png");
        }
        stage.getIcons().add(new Image(iconResource.toExternalForm()));
    }

    private void applyStageMinimumSize(Stage stage, AppConfig appConfig) {
        stage.setMinWidth(appConfig.getUi().getMinWidth());
        stage.setMinHeight(appConfig.getUi().getMinHeight());
    }
}
