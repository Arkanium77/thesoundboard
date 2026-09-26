package app.project;

import app.model.ProjectState;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

class ProjectSessionControllerTest {
    @Test
    void loadingAndFailureKeepThePreviousRootAndStateTogether() {
        ProjectSessionController session = new ProjectSessionController(2);
        ProjectState first = new ProjectState(2);
        session.completeLoad(session.beginLoad(), result("first", first, null));
        long oldRequest = session.beginLoad();
        long newRequest = session.beginLoad();
        Assertions.assertThat(session.completeLoad(oldRequest, result("stale", new ProjectState(2), null))).isFalse();
        session.failLoad(newRequest);
        Assertions.assertThat(session.getRootPath()).isEqualTo(Path.of("first"));
        Assertions.assertThat(session.getState()).isSameAs(first);
        Assertions.assertThat(session.isLoading()).isFalse();
    }

    @Test
    void snapshotsAreDetachedAndOldAcknowledgementsCannotClearNewEdits() {
        ProjectSessionController session = new ProjectSessionController(2);
        session.completeLoad(session.beginLoad(), result("first", new ProjectState(2), null));
        session.getState().setMasterVolume(0.2d);
        session.changed();
        ProjectSessionController.Snapshot snapshot = session.snapshot();
        session.getState().setMasterVolume(0.8d);
        session.changed();
        Assertions.assertThat(snapshot.state().getMasterVolume()).isEqualTo(0.2d);
        Assertions.assertThat(session.saved(snapshot)).isFalse();
        Assertions.assertThat(session.isDirty()).isTrue();
        session.completeLoad(session.beginLoad(), result("first", new ProjectState(2), null));
        Assertions.assertThat(session.saved(snapshot)).isFalse();
        Assertions.assertThat(session.isDirty()).isFalse();
    }

    @Test
    void closeInvalidatesLoadAndUnreadableProjectsRemainProtectedUntilSavedExplicitly() {
        ProjectSessionController session = new ProjectSessionController(2);
        session.completeLoad(session.beginLoad(), result("broken", new ProjectState(2), new IOException("broken JSON")));
        Assertions.assertThat(session.isWritable()).isFalse();
        long loading = session.beginLoad();
        session.beginClose();
        Assertions.assertThat(session.completeLoad(loading, result("late", new ProjectState(2), null))).isFalse();
        session.cancelClose();
        session.changed();
        Assertions.assertThat(session.isDirty()).isTrue();
        Assertions.assertThat(session.isWritable()).isFalse();
        Assertions.assertThat(session.saved(session.snapshot())).isTrue();
        Assertions.assertThat(session.isWritable()).isTrue();
    }

    private ProjectLoadResult result(String path, ProjectState state, Exception failure) {
        return new ProjectLoadResult(Path.of(path), state, List.of(), failure);
    }
}
