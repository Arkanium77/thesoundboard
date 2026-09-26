package app.ui.settings;

import app.localization.LocalizationService;
import app.localization.TextKey;
import app.project.LastProjectPreferences;
import app.waveform.WaveformDisplaySettings;
import app.waveform.WaveformDisplayMode;
import app.waveform.WaveformPreferences;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Builds preference controls when opened so external preference changes are reflected without retaining stale controls. */
final class GeneralSettingsPage {
    private final LocalizationService localizationService;
    private final LastProjectPreferences lastProjectPreferences;
    private final WaveformPreferences waveformPreferences;

    GeneralSettingsPage(LocalizationService localizationService, LastProjectPreferences lastProjectPreferences,
                        WaveformPreferences waveformPreferences) {
        this.localizationService = localizationService;
        this.lastProjectPreferences = lastProjectPreferences;
        this.waveformPreferences = waveformPreferences;
    }

    Node build() {
        Label title = new Label(localizationService.text(TextKey.SETTINGS_GENERAL));
        title.getStyleClass().add("settings-title");
        CheckBox restoreSession = new CheckBox(localizationService.text(TextKey.SETTINGS_RESTORE_SESSION));
        restoreSession.setSelected(lastProjectPreferences.isRestoreOnStart());
        restoreSession.selectedProperty().addListener((observable, oldValue, selected) ->
                lastProjectPreferences.setRestoreOnStart(selected)
        );
        Label waveformModeLabel = new Label(localizationService.text(TextKey.SETTINGS_WAVEFORM_MODE));
        ComboBox<WaveformModeOption> waveformMode = new ComboBox<>();
        waveformMode.getItems().addAll(
                new WaveformModeOption(WaveformDisplayMode.PEAK_LINEAR,
                        localizationService.text(TextKey.SETTINGS_WAVEFORM_MODE_PEAK_LINEAR)),
                new WaveformModeOption(WaveformDisplayMode.RMS_LINEAR,
                        localizationService.text(TextKey.SETTINGS_WAVEFORM_MODE_RMS_LINEAR)),
                new WaveformModeOption(WaveformDisplayMode.RMS_DB,
                        localizationService.text(TextKey.SETTINGS_WAVEFORM_MODE_RMS_DB))
        );
        waveformMode.getSelectionModel().select(waveformMode.getItems().stream()
                .filter(option -> option.mode() == WaveformDisplaySettings.getMode())
                .findFirst()
                .orElse(waveformMode.getItems().get(1)));
        waveformMode.valueProperty().addListener((observable, oldValue, selected) -> {
            if (selected == null) return;
            WaveformDisplaySettings.setMode(selected.mode());
            waveformPreferences.saveMode(selected.mode());
        });
        VBox waveformModeBox = new VBox(6d, waveformModeLabel, waveformMode);
        VBox body = new VBox(16d, title, restoreSession, waveformModeBox);
        body.setPadding(new Insets(16d));
        return body;
    }

    private record WaveformModeOption(WaveformDisplayMode mode, String label) {
        @Override public String toString() { return label; }
    }
}
