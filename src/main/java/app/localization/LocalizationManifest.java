package app.localization;

import java.util.UUID;

public class LocalizationManifest {
    private UUID uid;
    private String name;
    private String languageTag;
    private int localizationVersion = 1;
    private String strings = "strings.yml";

    public UUID getUid() { return uid; }
    public void setUid(UUID uid) { this.uid = uid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLanguageTag() { return languageTag; }
    public void setLanguageTag(String languageTag) { this.languageTag = languageTag; }
    public int getLocalizationVersion() { return localizationVersion; }
    public void setLocalizationVersion(int localizationVersion) { this.localizationVersion = localizationVersion; }
    public String getStrings() { return strings; }
    public void setStrings(String strings) { this.strings = strings; }
}
