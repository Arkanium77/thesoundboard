package app.project;

import app.model.AudioFile;
import app.model.ProjectState;
import app.model.QueueTrack;
import app.model.WorkspaceQueue;
import app.model.WorkspaceTrack;
import app.model.VirtualTileTrack;
import app.model.WorkspaceVirtualTile;
import app.scan.ScannedAudioFile;
import app.support.RelativePathUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

public class ProjectStateSynchronizer {
    private final int schemaVersion;

    public ProjectStateSynchronizer(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    /** Source identity is separate from its path and label: identical filenames in different libraries stay
     * independent. Ignore results for unlinked sources so a stale asynchronous scan cannot resurrect them. */
    public ProjectState synchronize(ProjectState projectState, List<ScannedAudioFile> scannedAudioFiles) {
        Set<UUID> sources = projectState.getLinkedAudioFolders().stream().map(folder -> folder.getId()).collect(Collectors.toSet());
        Map<AudioPath, AudioFile> existingByPath = new LinkedHashMap<>();
        for (AudioFile audioFile : projectState.getAudioFiles()) {
            if (audioFile.getLinkedFolderId() == null || sources.contains(audioFile.getLinkedFolderId())) {
                existingByPath.put(new AudioPath(audioFile.getLinkedFolderId(), RelativePathUtils.normalize(audioFile.getRelativePath())), audioFile);
            }
        }

        Map<AudioPath, AudioFile> synchronizedFiles = new LinkedHashMap<>();
        for (ScannedAudioFile scannedAudioFile : scannedAudioFiles) {
            if (scannedAudioFile.getLinkedFolderId() != null && !sources.contains(scannedAudioFile.getLinkedFolderId())) continue;
            String normalizedRelativePath = RelativePathUtils.normalize(scannedAudioFile.getRelativePath());
            AudioPath key = new AudioPath(scannedAudioFile.getLinkedFolderId(), normalizedRelativePath);
            AudioFile existingAudioFile = existingByPath.remove(key);
            AudioFile synchronizedAudioFile = existingAudioFile == null
                    ? new AudioFile(UUID.randomUUID(), normalizedRelativePath, scannedAudioFile.getDisplayName(), false)
                    : existingAudioFile;

            synchronizedAudioFile.setRelativePath(normalizedRelativePath);
            synchronizedAudioFile.setLinkedFolderId(scannedAudioFile.getLinkedFolderId());
            synchronizedAudioFile.setDisplayName(scannedAudioFile.getDisplayName());
            synchronizedAudioFile.setMissing(false);
            synchronizedFiles.put(key, synchronizedAudioFile);
        }

        for (AudioFile missingAudioFile : existingByPath.values()) {
            missingAudioFile.setRelativePath(RelativePathUtils.normalize(missingAudioFile.getRelativePath()));
            if (missingAudioFile.getDisplayName() == null || missingAudioFile.getDisplayName().isBlank()) {
                missingAudioFile.setDisplayName(RelativePathUtils.displayName(missingAudioFile.getRelativePath()));
            }
            missingAudioFile.setMissing(true);
            synchronizedFiles.put(new AudioPath(missingAudioFile.getLinkedFolderId(), missingAudioFile.getRelativePath()), missingAudioFile);
        }

        List<AudioFile> audioFiles = new ArrayList<>(synchronizedFiles.values());
        audioFiles.sort(Comparator.comparing(AudioFile::getRelativePath, String.CASE_INSENSITIVE_ORDER));

        projectState.setSchemaVersion(schemaVersion);
        projectState.setAudioFiles(audioFiles);
        normalizeWorkspaceOrder(projectState);
        normalizeQueueTracks(projectState);
        normalizeVirtualTiles(projectState);
        return projectState;
    }

    private record AudioPath(UUID linkedFolderId, String relativePath) { }

    private void normalizeWorkspaceOrder(ProjectState projectState) {
        List<WorkspaceOrderEntry> entries = new ArrayList<>();
        for (WorkspaceTrack workspaceTrack : projectState.getWorkspaceTracks()) {
            entries.add(new WorkspaceOrderEntry(workspaceTrack.getOrder(), workspaceTrack::setOrder));
        }
        for (WorkspaceQueue workspaceQueue : projectState.getWorkspaceQueues()) {
            entries.add(new WorkspaceOrderEntry(workspaceQueue.getOrder(), workspaceQueue::setOrder));
        }
        for (WorkspaceVirtualTile tile : projectState.getWorkspaceVirtualTiles()) {
            entries.add(new WorkspaceOrderEntry(tile.getOrder(), tile::setOrder));
        }

        entries.sort(Comparator.comparingInt(WorkspaceOrderEntry::order));
        for (int index = 0; index < entries.size(); index++) {
            entries.get(index).orderSetter().accept(index);
        }
    }

    private void normalizeQueueTracks(ProjectState projectState) {
        for (WorkspaceQueue workspaceQueue : projectState.getWorkspaceQueues()) {
            List<QueueTrack> queueTracks = new ArrayList<>(workspaceQueue.getTracks());
            queueTracks.sort(Comparator.comparingInt(QueueTrack::getOrder));
            for (int trackIndex = 0; trackIndex < queueTracks.size(); trackIndex++) {
                queueTracks.get(trackIndex).setOrder(trackIndex);
            }
            workspaceQueue.setTracks(queueTracks);
            if (workspaceQueue.getSelectedTrackId() == null && !queueTracks.isEmpty()) {
                workspaceQueue.setSelectedTrackId(queueTracks.getFirst().getId());
            }
        }
    }

    private void normalizeVirtualTiles(ProjectState projectState) {
        for (WorkspaceVirtualTile tile : projectState.getWorkspaceVirtualTiles()) {
            List<VirtualTileTrack> tracks = new ArrayList<>(tile.getTracks());
            tracks.sort(Comparator.comparingInt(VirtualTileTrack::getOrder));
            if (tracks.size() > tile.getLayout().getCapacity()) {
                tracks = new ArrayList<>(tracks.subList(0, tile.getLayout().getCapacity()));
            }
            for (int index = 0; index < tracks.size(); index++) tracks.get(index).setOrder(index);
            tile.setTracks(tracks);
        }
    }

    private record WorkspaceOrderEntry(int order, java.util.function.IntConsumer orderSetter) {
    }
}
