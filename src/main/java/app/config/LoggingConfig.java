package app.config;

public class LoggingConfig {
    private String level = "INFO";
    private String pattern = "%d{yyyy-MM-dd HH:mm:ss} %-5level [%thread] %logger - %msg%n";

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }
}
