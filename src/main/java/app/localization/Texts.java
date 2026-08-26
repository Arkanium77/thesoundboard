package app.localization;

public final class Texts {
    private static LocalizationService service = new LocalizationService();

    private Texts() { }

    public static void configure(LocalizationService localizationService) {
        service = localizationService;
    }

    public static String text(TextKey key) {
        return service.text(key);
    }

    public static String format(TextKey key, Object... arguments) {
        return service.format(key, arguments);
    }
}
