package app.ui.drag;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class DragPayloadTest {
    @Test
    void roundTripsAudioFilePayload() {
        UUID firstAudioFileId = UUID.randomUUID();
        UUID secondAudioFileId = UUID.randomUUID();

        String payload = DragPayload.audioFiles(List.of(firstAudioFileId, secondAudioFileId));

        Assertions.assertThat(DragPayload.parseAudioFileIds(payload))
                .containsExactly(firstAudioFileId, secondAudioFileId);
    }

    @Test
    void parsesWorkspacePayloads() {
        UUID workspaceTrackId = UUID.randomUUID();
        UUID workspaceQueueId = UUID.randomUUID();

        Assertions.assertThat(DragPayload.parseWorkspaceTrackId(DragPayload.workspaceTrack(workspaceTrackId)))
                .isEqualTo(workspaceTrackId);
        Assertions.assertThat(DragPayload.parseWorkspaceQueueId(DragPayload.workspaceQueue(workspaceQueueId)))
                .isEqualTo(workspaceQueueId);
    }

    @Test
    void parsesQueueTrackPayload() {
        UUID queueId = UUID.randomUUID();
        UUID queueTrackId = UUID.randomUUID();

        DragPayload.QueueTrackRef queueTrackRef = DragPayload.parseQueueTrack(DragPayload.queueTrack(queueId, queueTrackId));

        Assertions.assertThat(queueTrackRef).isNotNull();
        Assertions.assertThat(queueTrackRef.queueId()).isEqualTo(queueId);
        Assertions.assertThat(queueTrackRef.queueTrackId()).isEqualTo(queueTrackId);
    }
}
