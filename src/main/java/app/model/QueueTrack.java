package app.model;

import java.util.UUID;

public class QueueTrack {
    public static final double DEFAULT_VOLUME = 0.8d;
    private UUID id;
    private UUID audioFileId;
    private int order;
    private Integer shuffledOrder;
    private boolean loop;
    private double volume = DEFAULT_VOLUME;

    public QueueTrack() {
    }

    public QueueTrack(UUID id, UUID audioFileId, int order, boolean loop) {
        this.id = id;
        this.audioFileId = audioFileId;
        this.order = order;
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

    public Integer getShuffledOrder() {
        return shuffledOrder;
    }

    public void setShuffledOrder(Integer shuffledOrder) {
        this.shuffledOrder = shuffledOrder;
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }
}
