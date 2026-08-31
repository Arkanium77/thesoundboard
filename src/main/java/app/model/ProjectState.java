package app.model;

import java.util.ArrayList;
import java.util.List;

public class ProjectState {
    private int schemaVersion = 2;
    private Double masterVolume = 1.0d;
    private List<AudioFile> audioFiles = new ArrayList<>();
    private List<WorkspaceTrack> workspaceTracks = new ArrayList<>();
    private List<WorkspaceQueue> workspaceQueues = new ArrayList<>();
    private List<WorkspaceVirtualTile> workspaceVirtualTiles = new ArrayList<>();

    public ProjectState() {
    }

    public ProjectState(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public static ProjectState empty(int schemaVersion) {
        return new ProjectState(schemaVersion);
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public double getMasterVolume() {
        return masterVolume == null ? 1.0d : masterVolume;
    }

    public void setMasterVolume(Double masterVolume) {
        this.masterVolume = masterVolume == null ? 1.0d : masterVolume;
    }

    public List<AudioFile> getAudioFiles() {
        return audioFiles;
    }

    public void setAudioFiles(List<AudioFile> audioFiles) {
        this.audioFiles = audioFiles == null ? new ArrayList<>() : new ArrayList<>(audioFiles);
    }

    public List<WorkspaceTrack> getWorkspaceTracks() {
        return workspaceTracks;
    }

    public void setWorkspaceTracks(List<WorkspaceTrack> workspaceTracks) {
        this.workspaceTracks = workspaceTracks == null ? new ArrayList<>() : new ArrayList<>(workspaceTracks);
    }

    public List<WorkspaceQueue> getWorkspaceQueues() {
        return workspaceQueues;
    }

    public void setWorkspaceQueues(List<WorkspaceQueue> workspaceQueues) {
        this.workspaceQueues = workspaceQueues == null ? new ArrayList<>() : new ArrayList<>(workspaceQueues);
    }

    public List<WorkspaceVirtualTile> getWorkspaceVirtualTiles() { return workspaceVirtualTiles; }
    public void setWorkspaceVirtualTiles(List<WorkspaceVirtualTile> workspaceVirtualTiles) {
        this.workspaceVirtualTiles = workspaceVirtualTiles == null ? new ArrayList<>() : new ArrayList<>(workspaceVirtualTiles);
    }
}
