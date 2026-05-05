package app.config;

public class WorkspaceConfig {
    private double defaultVolume = 0.8d;
    private double defaultMasterVolume = 1.0d;
    private boolean defaultLoop;
    private int progressRefreshMillis = 200;

    public double getDefaultVolume() {
        return defaultVolume;
    }

    public void setDefaultVolume(double defaultVolume) {
        this.defaultVolume = defaultVolume;
    }

    public double getDefaultMasterVolume() {
        return defaultMasterVolume;
    }

    public void setDefaultMasterVolume(double defaultMasterVolume) {
        this.defaultMasterVolume = defaultMasterVolume;
    }

    public boolean isDefaultLoop() {
        return defaultLoop;
    }

    public void setDefaultLoop(boolean defaultLoop) {
        this.defaultLoop = defaultLoop;
    }

    public int getProgressRefreshMillis() {
        return progressRefreshMillis;
    }

    public void setProgressRefreshMillis(int progressRefreshMillis) {
        this.progressRefreshMillis = progressRefreshMillis;
    }
}
