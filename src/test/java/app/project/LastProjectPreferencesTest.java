package app.project;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;
import java.util.prefs.Preferences;

class LastProjectPreferencesTest {
    @Test
    void keepsTenSuccessfulOpensInAccessOrderAndMigratesLegacyEntry() throws Exception {
        Preferences node = Preferences.userRoot().node("thesoundboard-test-" + UUID.randomUUID());
        try {
            Path legacy = Path.of("legacy").toAbsolutePath().normalize();
            node.put("lastProjectRootPath", legacy.toString());
            LastProjectPreferences preferences = new LastProjectPreferences(node);
            Assertions.assertThat(preferences.loadRecent()).containsExactly(legacy);
            for (int index = 0; index < 12; index++) preferences.save(Path.of("project-" + index));
            Assertions.assertThat(preferences.loadRecent()).hasSize(10);
            Assertions.assertThat(preferences.loadRecent().getFirst()).isEqualTo(Path.of("project-11").toAbsolutePath());
            preferences.save(Path.of("project-5"));
            Assertions.assertThat(preferences.loadRecent()).hasSize(10).doesNotHaveDuplicates();
            Assertions.assertThat(preferences.loadRecent().getFirst()).isEqualTo(Path.of("project-5").toAbsolutePath());
            Assertions.assertThat(new LastProjectPreferences(node).loadRecent()).isEqualTo(preferences.loadRecent());
        } finally {
            node.removeNode();
        }
    }
}
