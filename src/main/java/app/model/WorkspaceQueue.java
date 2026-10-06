package app.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WorkspaceQueue {
    private UUID id;
    private String name;
    private int order;
    private double volume = 0.8d;
    private boolean loopQueue;
    private Boolean loopCurrentTrack;
    private boolean shuffleEnabled;
    private UUID selectedTrackId;
    private List<QueueTrack> tracks = new ArrayList<>();

    public WorkspaceQueue() {
    }

    public WorkspaceQueue(UUID id, String name, int order, double volume, boolean loopQueue) {
        this.id = id;
        this.name = name;
        this.order = order;
        this.volume = volume;
        this.loopQueue = loopQueue;
        this.loopCurrentTrack = false;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    public boolean isLoopQueue() {
        return loopQueue;
    }

    public void setLoopQueue(boolean loopQueue) {
        this.loopQueue = loopQueue;
    }

    public boolean isShuffleEnabled() {
        return shuffleEnabled;
    }

    /** Null identifies older projects that stored this control on individual tracks. The workspace resolves it
     * once from the previously selected track; explicit false must survive saves independently of legacy flags. */
    public Boolean getLoopCurrentTrack() {
        return loopCurrentTrack;
    }

    public void setLoopCurrentTrack(Boolean loopCurrentTrack) {
        this.loopCurrentTrack = loopCurrentTrack;
    }

    public void setShuffleEnabled(boolean shuffleEnabled) {
        this.shuffleEnabled = shuffleEnabled;
    }

    public UUID getSelectedTrackId() {
        return selectedTrackId;
    }

    public void setSelectedTrackId(UUID selectedTrackId) {
        this.selectedTrackId = selectedTrackId;
    }

    public List<QueueTrack> getTracks() {
        return tracks;
    }

    public void setTracks(List<QueueTrack> tracks) {
        this.tracks = tracks == null ? new ArrayList<>() : new ArrayList<>(tracks);
    }
}
