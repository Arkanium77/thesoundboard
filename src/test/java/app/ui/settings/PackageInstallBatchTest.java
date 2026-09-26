package app.ui.settings;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

class PackageInstallBatchTest {
    @Test
    void waitsForReplacementOrFailureRecoveryAndIgnoresDuplicateCompletion() {
        List<Integer> started = new ArrayList<>();
        List<Runnable> completions = new ArrayList<>();
        PackageInstallBatch<Integer> batch = new PackageInstallBatch<>(List.of(1, 2, 3), (item, next) -> {
            started.add(item);
            completions.add(next);
        });
        batch.start();
        batch.start();
        Assertions.assertThat(started).containsExactly(1);
        completions.getFirst().run();
        completions.getFirst().run();
        Assertions.assertThat(started).containsExactly(1, 2);
        completions.get(1).run();
        Assertions.assertThat(started).containsExactly(1, 2, 3);
        completions.get(2).run();
        batch.start();
        Assertions.assertThat(started).containsExactly(1, 2, 3);
    }

    @Test
    void drainsSynchronousCancellationsWithoutGrowingCallStack() {
        List<Integer> items = IntStream.range(0, 20_000).boxed().toList();
        List<Integer> processed = new ArrayList<>();
        new PackageInstallBatch<>(items, (item, next) -> {
            processed.add(item);
            next.run();
        }).start();
        Assertions.assertThat(processed).containsExactlyElementsOf(items);
    }
}
