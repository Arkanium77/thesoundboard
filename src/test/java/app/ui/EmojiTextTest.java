package app.ui;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class EmojiTextTest {
    @Test
    void replacesEmojiWithBundledTwemojiAsset() {
        Assertions.assertThat(EmojiText.parse("🌙 Midnight"))
                .containsExactly(
                        new EmojiText.Segment(null, "1f319"),
                        new EmojiText.Segment(" Midnight", null)
                );
    }

    @Test
    void keepsComplexEmojiSequenceTogether() {
        Assertions.assertThat(EmojiText.parse("Family 👨‍👩‍👧‍👦"))
                .containsExactly(
                        new EmojiText.Segment("Family ", null),
                        new EmojiText.Segment(null, "1f468-200d-1f469-200d-1f467-200d-1f466")
                );
    }

    @Test
    void leavesUnsupportedCharactersAsText() {
        Assertions.assertThat(EmojiText.parse("Plain text"))
                .containsExactly(new EmojiText.Segment("Plain text", null));
    }
}
