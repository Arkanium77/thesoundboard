package app.waveform;

/**
 * Separates waveform measurement from visual scaling so additional algorithms can be introduced without another
 * boolean preference or a migration of the settings UI. Peak-linear preserves the original transient-oriented
 * display, RMS-linear shows average energy directly, and RMS-dB applies the perceptual curve after RMS extraction.
 * Enum names are persisted; existing constants must therefore never be renamed or reused for different behavior.
 */
public enum WaveformDisplayMode {
    PEAK_LINEAR,
    RMS_LINEAR,
    RMS_DB
}
