package app.skin;

public class SkinAlreadyInstalledException extends IllegalArgumentException {
    private final SkinManifest skin;

    public SkinAlreadyInstalledException(SkinManifest skin) {
        super("Skin '" + skin.getName() + "' is already installed");
        this.skin = skin;
    }

    public SkinManifest getSkin() {
        return skin;
    }
}
