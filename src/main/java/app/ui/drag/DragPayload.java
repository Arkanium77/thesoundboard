package app.ui.drag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class DragPayload {
    private static final String AUDIO_FILE_PREFIX = "audio-files:";
    private static final String WORKSPACE_TRACK_PREFIX = "workspace-track:";
    private static final String WORKSPACE_QUEUE_PREFIX = "workspace-queue:";
    private static final String QUEUE_TRACK_PREFIX = "queue-track:";

    private DragPayload() {
    }

    public static String audioFiles(List<UUID> audioFileIds) {
        return AUDIO_FILE_PREFIX + audioFileIds.stream()
                .map(UUID::toString)
                .collect(Collectors.joining(","));
    }

    public static String workspaceTrack(UUID workspaceTrackId) {
        return WORKSPACE_TRACK_PREFIX + workspaceTrackId;
    }

    public static String workspaceQueue(UUID workspaceQueueId) {
        return WORKSPACE_QUEUE_PREFIX + workspaceQueueId;
    }

    public static String queueTrack(UUID queueId, UUID queueTrackId) {
        return QUEUE_TRACK_PREFIX + queueId + ":" + queueTrackId;
    }

    public static UUID parseWorkspaceTrackId(String payload) {
        return parsePrefixedUuid(payload, WORKSPACE_TRACK_PREFIX);
    }

    public static UUID parseWorkspaceQueueId(String payload) {
        return parsePrefixedUuid(payload, WORKSPACE_QUEUE_PREFIX);
    }

    public static QueueTrackRef parseQueueTrack(String payload) {
        if (payload == null || !payload.startsWith(QUEUE_TRACK_PREFIX)) {
            return null;
        }

        String[] parts = payload.substring(QUEUE_TRACK_PREFIX.length()).split(":");
        if (parts.length != 2) {
            return null;
        }

        try {
            return new QueueTrackRef(UUID.fromString(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static List<UUID> parseAudioFileIds(String payload) {
        if (payload == null || !payload.startsWith(AUDIO_FILE_PREFIX)) {
            return List.of();
        }

        List<UUID> audioFileIds = new ArrayList<>();
        for (String value : payload.substring(AUDIO_FILE_PREFIX.length()).split(",")) {
            String normalized = value.trim();
            if (normalized.isBlank()) {
                continue;
            }
            try {
                audioFileIds.add(UUID.fromString(normalized));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return audioFileIds;
    }

    private static UUID parsePrefixedUuid(String payload, String prefix) {
        if (payload == null || !payload.startsWith(prefix)) {
            return null;
        }

        try {
            return UUID.fromString(payload.substring(prefix.length()));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public record QueueTrackRef(UUID queueId, UUID queueTrackId) {
    }
}
