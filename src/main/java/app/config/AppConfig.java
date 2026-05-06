package app.config;

public class AppConfig {
    private String title = "The Soundboard";
    private int schemaVersion = 2;
    private PersistenceConfig persistence = new PersistenceConfig();
    private ScannerConfig scanner = new ScannerConfig();
    private WorkspaceConfig workspace = new WorkspaceConfig();
    private UiConfig ui = new UiConfig();
    private LoggingConfig logging = new LoggingConfig();

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public PersistenceConfig getPersistence() {
        return persistence;
    }

    public void setPersistence(PersistenceConfig persistence) {
        this.persistence = persistence == null ? new PersistenceConfig() : persistence;
    }

    public ScannerConfig getScanner() {
        return scanner;
    }

    public void setScanner(ScannerConfig scanner) {
        this.scanner = scanner == null ? new ScannerConfig() : scanner;
    }

    public WorkspaceConfig getWorkspace() {
        return workspace;
    }

    public void setWorkspace(WorkspaceConfig workspace) {
        this.workspace = workspace == null ? new WorkspaceConfig() : workspace;
    }

    public UiConfig getUi() {
        return ui;
    }

    public void setUi(UiConfig ui) {
        this.ui = ui == null ? new UiConfig() : ui;
    }

    public LoggingConfig getLogging() {
        return logging;
    }

    public void setLogging(LoggingConfig logging) {
        this.logging = logging == null ? new LoggingConfig() : logging;
    }
}
