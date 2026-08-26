package app.ui;

import com.sun.javafx.font.CharToGlyphMapper;
import com.sun.javafx.font.PGFont;
import com.sun.javafx.font.PrismFontFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

final class SystemFontResolver {
    private static final Map<String, String> resolvedFamilies = new LinkedHashMap<>();

    private SystemFontResolver() {
    }

    static synchronized String resolve(String text, String preferredFamily, boolean bold, boolean italic) {
        if (text == null || text.isBlank()) {
            return preferredFamily;
        }
        String cacheKey = preferredFamily + '\n' + bold + '\n' + italic + '\n' + text;
        return resolvedFamilies.computeIfAbsent(cacheKey,
                ignored -> findFamily(text, preferredFamily, bold, italic));
    }

    private static String findFamily(String text, String preferredFamily, boolean bold, boolean italic) {
        PrismFontFactory fontFactory = PrismFontFactory.getFontFactory();
        if (canDisplay(fontFactory, preferredFamily, text)
                && supportsStyle(fontFactory, preferredFamily, bold, italic)) {
            return preferredFamily;
        }
        List<String> families = List.of(fontFactory.getFontFamilyNames());
        String styledFamily = findFamily(text, preferredFamily, families,
                (family, value) -> canDisplay(fontFactory, family, value)
                        && supportsStyle(fontFactory, family, bold, italic));
        if (!styledFamily.equals(preferredFamily)) {
            return styledFamily;
        }
        if (canDisplay(fontFactory, preferredFamily, text)) {
            return preferredFamily;
        }
        return findFamily(text, preferredFamily, families,
                (family, value) -> canDisplay(fontFactory, family, value));
    }

    private static boolean canDisplay(PrismFontFactory fontFactory, String family, String text) {
        PGFont font = fontFactory.createFont(family, false, false, 12f);
        CharToGlyphMapper glyphMapper = font.getFontResource().getGlyphMapper();
        int missingGlyph = glyphMapper.getMissingGlyphCode();
        return text.codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint))
                .allMatch(codePoint -> glyphMapper.charToGlyph(codePoint) != missingGlyph);
    }

    private static boolean supportsStyle(PrismFontFactory fontFactory, String family, boolean bold, boolean italic) {
        PGFont font = fontFactory.createFont(family, bold, italic, 12f);
        return font.getFontResource().isBold() == bold && font.getFontResource().isItalic() == italic;
    }

    static String findFamily(String text, String preferredFamily, List<String> families,
                             BiPredicate<String, String> glyphCoverage) {
        for (String family : families) {
            if (family.equalsIgnoreCase(preferredFamily) && glyphCoverage.test(family, text)) {
                return family;
            }
        }
        for (String family : families) {
            if (glyphCoverage.test(family, text)) {
                return family;
            }
        }
        return preferredFamily;
    }

}
