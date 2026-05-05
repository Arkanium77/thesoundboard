package app.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VirtualFolder {
    private UUID id;
    private String name;
    private List<UUID> childFolderIds = new ArrayList<>();
    private List<UUID> audioFileIds = new ArrayList<>();

    public VirtualFolder() {
    }

    public VirtualFolder(UUID id, String name) {
        this.id = id;
        this.name = name;
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

    public List<UUID> getChildFolderIds() {
        return childFolderIds;
    }

    public void setChildFolderIds(List<UUID> childFolderIds) {
        this.childFolderIds = childFolderIds == null ? new ArrayList<>() : new ArrayList<>(childFolderIds);
    }

    public List<UUID> getAudioFileIds() {
        return audioFileIds;
    }

    public void setAudioFileIds(List<UUID> audioFileIds) {
        this.audioFileIds = audioFileIds == null ? new ArrayList<>() : new ArrayList<>(audioFileIds);
    }
}
