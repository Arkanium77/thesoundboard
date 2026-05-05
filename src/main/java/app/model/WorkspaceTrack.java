package app.model;

import java.util.UUID;

public class WorkspaceTrack {
    private UUID id;
    private UUID audioFileId;
    private int order;
    private double volume;
    private boolean loop;

    public WorkspaceTrack() {
    }

    public WorkspaceTrack(UUID id, UUID audioFileId, int order, double volume, boolean loop) {
        this.id = id;
        this.audioFileId = audioFileId;
        this.order = order;
        this.volume = volume;
        this.loop = loop;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAudioFileId() {
        return audioFileId;
    }

    public void setAudioFileId(UUID audioFileId) {
        this.audioFileId = audioFileId;
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

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }
}
