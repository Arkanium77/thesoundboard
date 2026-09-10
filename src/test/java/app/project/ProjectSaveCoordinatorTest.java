package app.project;

import app.model.ProjectState;
import app.persistence.ProjectStateRepository;
import app.support.TestDirectorySupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

class ProjectSaveCoordinatorTest {
    @Test
    void foregroundSaveWinsOverAnAlreadyRunningBackgroundWrite() throws Exception {
        Path root = TestDirectorySupport.createTempDirectory("save-order-");
        ProjectStateRepository repository = new ProjectStateRepository("state.json");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ProjectService service = new ProjectService(2, repository, null, null) {
            @Override
            public void saveProject(Path path, ProjectState state) throws IOException {
                if (state.getMasterVolume() == 0.2d) {
                    entered.countDown();
                    try {
                        if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Writer did not release");
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IOException(exception);
                    }
                }
                super.saveProject(path, state);
            }
        };
        ProjectSaveCoordinator coordinator = new ProjectSaveCoordinator(service);
        ProjectState older = new ProjectState(2);
        older.setMasterVolume(0.2d);
        ProjectState latest = new ProjectState(2);
        latest.setMasterVolume(0.8d);
        int generation = coordinator.invalidate();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var background = executor.submit(() -> coordinator.save(root, older, generation));
            try {
                Assertions.assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                CountDownLatch foregroundStarted = new CountDownLatch(1);
                var foreground = executor.submit(() -> {
                    foregroundStarted.countDown();
                    coordinator.saveNow(root, latest);
                    return true;
                });
                Assertions.assertThat(foregroundStarted.await(5, TimeUnit.SECONDS)).isTrue();
                release.countDown();
                background.get(5, TimeUnit.SECONDS);
                foreground.get(5, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
        Assertions.assertThat(repository.load(root).orElseThrow().getMasterVolume()).isEqualTo(0.8d);
        Assertions.assertThat(coordinator.save(root, older, generation)).isFalse();
    }
    @Test
    void coalescesWaitingSavesWithoutBlockingSubmissionAndContinuesAfterFailure() throws Exception {
        Path root = TestDirectorySupport.createTempDirectory("coalesced-save-");
        ProjectStateRepository repository = new ProjectStateRepository("state.json");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ProjectSaveCoordinator coordinator = new ProjectSaveCoordinator(new ProjectService(2, repository, null, null) {
            @Override
            public void saveProject(Path path, ProjectState state) throws IOException {
                if (state.getMasterVolume() == 0.1d) {
                    entered.countDown();
                    try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("timeout"); }
                    catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IOException(exception); }
                    throw new IOException("controlled failure");
                }
                super.saveProject(path, state);
            }
        });
        try {
            var first = coordinator.submit(snapshot(root, 0.1d));
            Assertions.assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = coordinator.submit(snapshot(root, 0.2d));
            var last = coordinator.submit(snapshot(root, 0.8d));
            Assertions.assertThat(second.get(1, TimeUnit.SECONDS)).isFalse();
            release.countDown();
            Assertions.assertThatThrownBy(() -> first.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IOException.class);
            Assertions.assertThat(last.get(5, TimeUnit.SECONDS)).isTrue();
            Assertions.assertThat(repository.load(root).orElseThrow().getMasterVolume()).isEqualTo(0.8d);
        } finally { release.countDown(); coordinator.shutdown(); }
    }

    private ProjectSessionController.Snapshot snapshot(Path root, double volume) {
        ProjectState state = new ProjectState(2);
        state.setMasterVolume(volume);
        return new ProjectSessionController.Snapshot(UUID.randomUUID(), root, state, 1L);
    }

}
