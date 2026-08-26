package app.localization;

public class LocalizationAlreadyInstalledException extends IllegalArgumentException {
    private final LocalizationManifest localization;

    public LocalizationAlreadyInstalledException(LocalizationManifest localization) {
        super("A localization with UID " + localization.getUid() + " is already installed");
        this.localization = localization;
    }

    public LocalizationManifest getLocalization() { return localization; }
}
