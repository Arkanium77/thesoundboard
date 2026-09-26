package app.project;

import java.util.UUID;

/** Returns the actual destination identity instead of forcing callers to infer it by comparing mutable lists.
 * A same-container reorder retains the source ID; cross-container moves return the newly created ID. Rejected
 * moves have no destination and must not detach playback or trigger view replacement. This is not persisted. */
public record TrackMoveResult(UUID trackId) {
    public static TrackMoveResult rejected() { return new TrackMoveResult(null); }
    public boolean moved() { return trackId != null; }
}
