package app.ui.workspace;

import app.audio.AudioEngine;
import app.model.AudioFile;
import app.model.VirtualTileTrack;
import app.model.WorkspaceVirtualTile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class WorkspaceVirtualTileItem {
    private final Path rootPath;
    private final WorkspaceVirtualTile tile;
    private final List<AudioFile> audioFiles;
    private final AudioEngine audioEngine;
    private final Consumer<Exception> errorHandler;
    private final Map<UUID, WorkspaceTrackItem> trackItems = new LinkedHashMap<>();
    private double masterVolume;

    public WorkspaceVirtualTileItem(Path rootPath, WorkspaceVirtualTile tile, List<AudioFile> audioFiles,
                                    AudioEngine audioEngine, double masterVolume, Consumer<Exception> errorHandler) {
        this.rootPath = rootPath;
        this.tile = tile;
        this.audioFiles = audioFiles;
        this.audioEngine = audioEngine;
        this.masterVolume = masterVolume;
        this.errorHandler = errorHandler;
        refreshAfterMutation();
    }

    public WorkspaceVirtualTile getTile() { return tile; }
    public List<WorkspaceTrackItem> getTrackItems() { return List.copyOf(trackItems.values()); }
    public WorkspaceTrackItem getTrackItem(UUID trackId) { return trackItems.get(trackId); }

    public void refreshAfterMutation() {
        Map<UUID, WorkspaceTrackItem> previous = new LinkedHashMap<>(trackItems);
        trackItems.clear();
        tile.getTracks().stream().sorted(Comparator.comparingInt(VirtualTileTrack::getOrder)).forEach(track -> {
            WorkspaceTrackItem existing = previous.remove(track.getId());
            trackItems.put(track.getId(), existing == null ? createTrackItem(track) : existing);
        });
        previous.values().forEach(WorkspaceTrackItem::dispose);
    }

    public void setMasterVolume(double masterVolume) {
        this.masterVolume = masterVolume;
        trackItems.values().forEach(item -> item.setMasterVolume(masterVolume));
    }

    public void stop() { trackItems.values().forEach(WorkspaceTrackItem::stop); }
    public void pauseIfPlaying() { trackItems.values().forEach(WorkspaceTrackItem::pauseIfPlaying); }
    public void resumeIfPaused() { trackItems.values().forEach(WorkspaceTrackItem::resumeIfPaused); }
    public void dispose() { new ArrayList<>(trackItems.values()).forEach(WorkspaceTrackItem::dispose); trackItems.clear(); }

    private WorkspaceTrackItem createTrackItem(VirtualTileTrack track) {
        AudioFile audioFile = audioFiles.stream().filter(file -> file.getId().equals(track.getAudioFileId()))
                .findFirst().orElseGet(() -> new AudioFile(track.getAudioFileId(), "", "Unknown file", true));
        return new WorkspaceTrackItem(rootPath, track, audioFile, audioEngine, masterVolume, errorHandler);
    }
}
