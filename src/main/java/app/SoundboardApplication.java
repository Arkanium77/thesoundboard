package app;

import app.audio.AudioEngine;
import app.audio.AudioEngineMode;
import app.audio.GstreamerRuntime;
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
import java.util.concurrent.TimeUnit;

public class SoundboardApplication extends Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(SoundboardApplication.class);
    private MainView mainView;
    private AudioEngine audioEngine;
    private WaveformService waveformService;
    private AudioEngineMode audioEngineMode;

    @Override
    public void start(Stage stage) {
        try {
            audioEngineMode = AudioEngineMode.fromArguments(getParameters().getRaw());
            initialize(stage);
        } catch (RuntimeException | Error exception) {
            try { stop(); } catch (RuntimeException failure) { exception.addSuppressed(failure); }
            throw exception;
        }
    }

    private void initialize(Stage stage) {
        AppConfig appConfig = AppConfigLoader.load();
        LoggingConfigurer.configure(appConfig.getLogging());
        SkinService skinService = new SkinService();
        skinService.prepareUiResources();
        LocalizationService localizationService = new LocalizationService();
        LastProjectPreferences lastProjectPreferences = new LastProjectPreferences();
        Texts.configure(localizationService);

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
        waveformService = new WaveformService(
                audioEngineMode.createWaveformExtractor(appConfig.getUi().getWaveformResolution()),
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

    /** Startup may fail even before the engine is created, after the launcher initialized GStreamer. Keep the selected
     * mode independently of the engine instance. Both close paths stop waveform pulls and drain playback before global
     * teardown, so native callbacks and the runtime executor cannot outlive failed startup or the UI. */
    @Override
    public void stop() {
        if (mainView != null) {
            mainView.shutdown();
        }
        if (waveformService != null) waveformService.shutdown();
        if (audioEngine != null) audioEngine.shutdown();
        if (audioEngineMode == AudioEngineMode.GSTREAMER) {
            try {
                if (waveformService != null && !waveformService.awaitShutdown(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("GStreamer waveform shutdown timed out");
                }
                GstreamerRuntime.shutdown();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("GStreamer shutdown was interrupted", exception);
            }
        }
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
