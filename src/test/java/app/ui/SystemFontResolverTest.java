package app.ui;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class SystemFontResolverTest {
    @Test
    void selectsAnInstalledFontThatCanDisplayTheWholeString() {
        String text = "日本語";

        String family = SystemFontResolver.findFamily(
                text,
                "System UI",
                List.of("System UI", "Japanese fallback"),
                (candidate, value) -> candidate.equals("Japanese fallback")
        );

        Assertions.assertThat(family).isEqualTo("Japanese fallback");
    }
}
