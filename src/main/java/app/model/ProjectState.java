package app.model;

import java.util.ArrayList;
import java.util.List;

public class ProjectState {
    private int schemaVersion = 1;
    private List<AudioFile> audioFiles = new ArrayList<>();
    private List<VirtualFolder> virtualFolders = new ArrayList<>();
    private List<WorkspaceTrack> workspaceTracks = new ArrayList<>();

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

    public List<AudioFile> getAudioFiles() {
        return audioFiles;
    }

    public void setAudioFiles(List<AudioFile> audioFiles) {
        this.audioFiles = audioFiles == null ? new ArrayList<>() : new ArrayList<>(audioFiles);
    }

    public List<VirtualFolder> getVirtualFolders() {
        return virtualFolders;
    }

    public void setVirtualFolders(List<VirtualFolder> virtualFolders) {
        this.virtualFolders = virtualFolders == null ? new ArrayList<>() : new ArrayList<>(virtualFolders);
    }

    public List<WorkspaceTrack> getWorkspaceTracks() {
        return workspaceTracks;
    }

    public void setWorkspaceTracks(List<WorkspaceTrack> workspaceTracks) {
        this.workspaceTracks = workspaceTracks == null ? new ArrayList<>() : new ArrayList<>(workspaceTracks);
    }
}
