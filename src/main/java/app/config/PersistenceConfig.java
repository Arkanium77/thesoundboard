package app.config;

public class PersistenceConfig {
    private String stateFileName = ".soundboard-project.json";

    public String getStateFileName() {
        return stateFileName;
    }

    public void setStateFileName(String stateFileName) {
        this.stateFileName = stateFileName;
    }
}
