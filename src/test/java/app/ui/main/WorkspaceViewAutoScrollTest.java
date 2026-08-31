package app.ui.main;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class WorkspaceViewAutoScrollTest {
    @Test
    void derivesDirectionAndSpeedFromPointerPositionInsideViewportEdges() {
        Assertions.assertThat(WorkspaceView.calculateAutoScrollVelocity(100d, 100d, 500d, 50d, 600d))
                .isEqualTo(-600d);
        Assertions.assertThat(WorkspaceView.calculateAutoScrollVelocity(125d, 100d, 500d, 50d, 600d))
                .isEqualTo(-300d);
        Assertions.assertThat(WorkspaceView.calculateAutoScrollVelocity(300d, 100d, 500d, 50d, 600d))
                .isZero();
        Assertions.assertThat(WorkspaceView.calculateAutoScrollVelocity(475d, 100d, 500d, 50d, 600d))
                .isEqualTo(300d);
        Assertions.assertThat(WorkspaceView.calculateAutoScrollVelocity(500d, 100d, 500d, 50d, 600d))
                .isEqualTo(600d);
    }

    @Test
    void ignoresPointerOutsideViewport() {
        Assertions.assertThat(WorkspaceView.calculateAutoScrollVelocity(99d, 100d, 500d, 50d, 600d))
                .isZero();
        Assertions.assertThat(WorkspaceView.calculateAutoScrollVelocity(501d, 100d, 500d, 50d, 600d))
                .isZero();
    }
}
