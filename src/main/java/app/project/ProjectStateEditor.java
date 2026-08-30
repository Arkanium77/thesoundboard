package app.project;

import app.model.AudioFile;
import app.model.ProjectState;
import app.model.QueueTrack;
import app.model.WorkspaceQueue;
import app.model.WorkspaceTrack;
import app.model.VirtualTileTrack;
import app.model.VirtualTileLayout;
import app.model.WorkspaceVirtualTile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;

public class ProjectStateEditor {
    public WorkspaceTrack addWorkspaceTrack(ProjectState projectState, UUID audioFileId, double volume, boolean loop) {
        return addWorkspaceTracks(projectState, List.of(audioFileId), volume, loop, null, false).getFirst();
    }

    public List<WorkspaceTrack> addWorkspaceTracks(
            ProjectState projectState,
            List<UUID> audioFileIds,
            double volume,
            boolean loop,
            UUID targetWorkspaceItemId,
            boolean placeAfter
    ) {
        normalizeWorkspaceOrder(projectState);

        List<WorkspaceTrack> createdTracks = new ArrayList<>();
        int insertOrder = resolveWorkspaceInsertOrder(projectState, targetWorkspaceItemId, placeAfter);
        for (WorkspaceTrack existingTrack : projectState.getWorkspaceTracks()) {
            if (existingTrack.getOrder() >= insertOrder) {
                existingTrack.setOrder(existingTrack.getOrder() + audioFileIds.size());
            }
        }
        for (WorkspaceQueue existingQueue : projectState.getWorkspaceQueues()) {
            if (existingQueue.getOrder() >= insertOrder) {
                existingQueue.setOrder(existingQueue.getOrder() + audioFileIds.size());
            }
        }
        for (WorkspaceVirtualTile existingTile : projectState.getWorkspaceVirtualTiles()) {
            if (existingTile.getOrder() >= insertOrder) existingTile.setOrder(existingTile.getOrder() + audioFileIds.size());
        }
        for (UUID audioFileId : audioFileIds) {
            if (audioFileId == null) {
                continue;
            }

            WorkspaceTrack workspaceTrack = new WorkspaceTrack(
                    UUID.randomUUID(),
                    audioFileId,
                    insertOrder + createdTracks.size(),
                    volume,
                    loop
            );
            createdTracks.add(workspaceTrack);
        }

        projectState.getWorkspaceTracks().addAll(createdTracks);
        normalizeWorkspaceOrder(projectState);
        return createdTracks;
    }

    public WorkspaceQueue createWorkspaceQueue(ProjectState projectState, String name, double defaultVolume) {
        normalizeWorkspaceOrder(projectState);
        WorkspaceQueue workspaceQueue = new WorkspaceQueue(
                UUID.randomUUID(),
                name,
                totalWorkspaceItemCount(projectState),
                defaultVolume,
                false
        );
        projectState.getWorkspaceQueues().add(workspaceQueue);
        normalizeWorkspaceOrder(projectState);
        return workspaceQueue;
    }

    public WorkspaceVirtualTile createWorkspaceVirtualTile(ProjectState projectState, VirtualTileLayout layout) {
        normalizeWorkspaceOrder(projectState);
        WorkspaceVirtualTile tile = new WorkspaceVirtualTile(UUID.randomUUID(), totalWorkspaceItemCount(projectState), layout);
        projectState.getWorkspaceVirtualTiles().add(tile);
        normalizeWorkspaceOrder(projectState);
        return tile;
    }

    public List<VirtualTileTrack> addVirtualTileTracks(ProjectState projectState, UUID tileId,
                                                       List<UUID> audioFileIds, double volume) {
        return addVirtualTileTracks(projectState, tileId, audioFileIds, volume, null, false);
    }

    public List<VirtualTileTrack> addVirtualTileTracks(ProjectState projectState, UUID tileId,
                                                       List<UUID> audioFileIds, double volume,
                                                       UUID targetTrackId, boolean placeAfter) {
        WorkspaceVirtualTile tile = findWorkspaceVirtualTile(projectState, tileId).orElse(null);
        if (tile == null || audioFileIds == null) return List.of();
        normalizeVirtualTileTracks(tile);
        int availableSlots = Math.max(0, tile.getLayout().getCapacity() - tile.getTracks().size());
        List<UUID> acceptedIds = audioFileIds.stream().filter(Objects::nonNull).limit(availableSlots).toList();
        List<VirtualTileTrack> created = new ArrayList<>();
        int insertOrder = resolveVirtualTileInsertOrder(tile, targetTrackId, placeAfter);
        for (VirtualTileTrack track : tile.getTracks()) {
            if (track.getOrder() >= insertOrder) track.setOrder(track.getOrder() + acceptedIds.size());
        }
        for (UUID audioFileId : acceptedIds) {
            created.add(new VirtualTileTrack(UUID.randomUUID(), audioFileId, insertOrder + created.size(), volume));
        }
        tile.getTracks().addAll(created);
        normalizeVirtualTileTracks(tile);
        return created;
    }

    public boolean moveWorkspaceTrackToVirtualTile(ProjectState projectState, UUID workspaceTrackId, UUID tileId,
                                                   UUID targetTrackId, boolean placeAfter) {
        WorkspaceTrack track = projectState.getWorkspaceTracks().stream()
                .filter(candidate -> candidate.getId().equals(workspaceTrackId)).findFirst().orElse(null);
        if (track == null) return false;
        List<VirtualTileTrack> created = addVirtualTileTracks(projectState, tileId,
                List.of(track.getAudioFileId()), track.getVolume(), targetTrackId, placeAfter);
        if (created.isEmpty()) return false;
        created.getFirst().setLoop(track.isLoop());
        created.getFirst().setVolume(track.getVolume());
        removeWorkspaceTrack(projectState, workspaceTrackId);
        return true;
    }

    public boolean moveVirtualTileTrack(ProjectState projectState, UUID sourceTileId, UUID targetTileId,
                                        UUID trackId, UUID targetTrackId, boolean placeAfter) {
        WorkspaceVirtualTile source = findWorkspaceVirtualTile(projectState, sourceTileId).orElse(null);
        WorkspaceVirtualTile target = findWorkspaceVirtualTile(projectState, targetTileId).orElse(null);
        if (source == null || target == null) return false;
        VirtualTileTrack track = source.getTracks().stream()
                .filter(candidate -> candidate.getId().equals(trackId)).findFirst().orElse(null);
        if (track == null) return false;
        if (sourceTileId.equals(targetTileId)) {
            List<VirtualTileTrack> tracks = new ArrayList<>(source.getTracks());
            tracks.sort(Comparator.comparingInt(VirtualTileTrack::getOrder));
            tracks.remove(track);
            int index = tracks.size();
            for (int candidateIndex = 0; candidateIndex < tracks.size(); candidateIndex++) {
                if (tracks.get(candidateIndex).getId().equals(targetTrackId)) {
                    index = placeAfter ? candidateIndex + 1 : candidateIndex;
                    break;
                }
            }
            tracks.add(index, track);
            for (int trackIndex = 0; trackIndex < tracks.size(); trackIndex++) {
                tracks.get(trackIndex).setOrder(trackIndex);
            }
            source.setTracks(tracks);
            return true;
        }
        List<VirtualTileTrack> created = addVirtualTileTracks(projectState, targetTileId,
                List.of(track.getAudioFileId()), track.getVolume(), targetTrackId, placeAfter);
        if (created.isEmpty()) return false;
        created.getFirst().setLoop(track.isLoop());
        removeVirtualTileTrack(projectState, sourceTileId, trackId);
        return true;
    }

    public WorkspaceTrack moveVirtualTileTrackToWorkspace(ProjectState projectState, UUID tileId, UUID trackId,
                                                          UUID targetWorkspaceItemId, boolean placeAfter) {
        WorkspaceVirtualTile tile = findWorkspaceVirtualTile(projectState, tileId).orElse(null);
        if (tile == null) return null;
        VirtualTileTrack track = tile.getTracks().stream()
                .filter(candidate -> candidate.getId().equals(trackId)).findFirst().orElse(null);
        if (track == null) return null;
        List<WorkspaceTrack> created = addWorkspaceTracks(projectState, List.of(track.getAudioFileId()),
                track.getVolume(), track.isLoop(), targetWorkspaceItemId, placeAfter);
        if (created.isEmpty()) return null;
        removeVirtualTileTrack(projectState, tileId, trackId);
        return created.getFirst();
    }

    public boolean moveVirtualTileTrackToQueue(ProjectState projectState, UUID tileId, UUID trackId,
                                               UUID queueId, UUID targetQueueTrackId, boolean placeAfter) {
        WorkspaceVirtualTile tile = findWorkspaceVirtualTile(projectState, tileId).orElse(null);
        if (tile == null) return false;
        VirtualTileTrack track = tile.getTracks().stream()
                .filter(candidate -> candidate.getId().equals(trackId)).findFirst().orElse(null);
        if (track == null) return false;
        List<QueueTrack> created = addQueueTracks(projectState, queueId, List.of(track.getAudioFileId()),
                targetQueueTrackId, placeAfter);
        if (created.isEmpty()) return false;
        created.getFirst().setLoop(track.isLoop());
        removeVirtualTileTrack(projectState, tileId, trackId);
        return true;
    }

    public boolean moveQueueTrackToVirtualTile(ProjectState projectState, UUID queueId, UUID queueTrackId,
                                               UUID tileId, UUID targetTrackId, boolean placeAfter) {
        QueueTrack track = findQueueTrack(projectState, queueId, queueTrackId);
        if (track == null) return false;
        List<VirtualTileTrack> created = addVirtualTileTracks(projectState, tileId,
                List.of(track.getAudioFileId()), track.getVolume(), targetTrackId, placeAfter);
        if (created.isEmpty()) return false;
        created.getFirst().setLoop(track.isLoop());
        removeQueueTrack(projectState, queueId, queueTrackId);
        return true;
    }

    public void removeVirtualTileTrack(ProjectState projectState, UUID tileId, UUID trackId) {
        findWorkspaceVirtualTile(projectState, tileId).ifPresent(tile -> {
            tile.getTracks().removeIf(track -> track.getId().equals(trackId));
            normalizeVirtualTileTracks(tile);
        });
    }

    public void removeWorkspaceVirtualTile(ProjectState projectState, UUID tileId) {
        projectState.getWorkspaceVirtualTiles().removeIf(tile -> tile.getId().equals(tileId));
        normalizeWorkspaceOrder(projectState);
    }

    public void renameWorkspaceQueue(ProjectState projectState, UUID queueId, String name) {
        findWorkspaceQueue(projectState, queueId).ifPresent(queue -> queue.setName(name));
    }

    public void removeWorkspaceTrack(ProjectState projectState, UUID workspaceTrackId) {
        projectState.getWorkspaceTracks().removeIf(track -> track.getId().equals(workspaceTrackId));
        normalizeWorkspaceOrder(projectState);
    }

    public void removeWorkspaceQueue(ProjectState projectState, UUID queueId) {
        projectState.getWorkspaceQueues().removeIf(queue -> queue.getId().equals(queueId));
        normalizeWorkspaceOrder(projectState);
    }

    public boolean moveWorkspaceTrackToQueue(
            ProjectState projectState,
            UUID workspaceTrackId,
            UUID queueId,
            UUID targetQueueTrackId,
            boolean placeAfter
    ) {
        if (workspaceTrackId == null || queueId == null) {
            return false;
        }

        WorkspaceTrack workspaceTrack = projectState.getWorkspaceTracks().stream()
                .filter(track -> track.getId().equals(workspaceTrackId))
                .findFirst()
                .orElse(null);
        if (workspaceTrack == null) {
            return false;
        }

        List<QueueTrack> createdTracks = addQueueTracks(
                projectState,
                queueId,
                List.of(workspaceTrack.getAudioFileId()),
                targetQueueTrackId,
                placeAfter
        );
        if (createdTracks.isEmpty()) {
            return false;
        }

        createdTracks.getFirst().setVolume(workspaceTrack.getVolume());

        removeWorkspaceTrack(projectState, workspaceTrackId);
        return true;
    }

    public WorkspaceTrack moveQueueTrackToWorkspace(
            ProjectState projectState,
            UUID sourceQueueId,
            UUID queueTrackId,
            double volume,
            UUID targetWorkspaceItemId,
            boolean placeAfter
    ) {
        if (sourceQueueId == null || queueTrackId == null) {
            return null;
        }

        QueueTrack queueTrack = findQueueTrack(projectState, sourceQueueId, queueTrackId);
        if (queueTrack == null) {
            return null;
        }

        List<WorkspaceTrack> createdTracks = addWorkspaceTracks(
                projectState,
                List.of(queueTrack.getAudioFileId()),
                queueTrack.getVolume(),
                queueTrack.isLoop(),
                targetWorkspaceItemId,
                placeAfter
        );
        if (createdTracks.isEmpty()) {
            return null;
        }

        removeQueueTrack(projectState, sourceQueueId, queueTrackId);
        return createdTracks.getFirst();
    }

    public boolean moveQueueTrackToQueue(
            ProjectState projectState,
            UUID sourceQueueId,
            UUID targetQueueId,
            UUID queueTrackId,
            UUID targetQueueTrackId,
            boolean placeAfter
    ) {
        if (sourceQueueId == null || targetQueueId == null || queueTrackId == null) {
            return false;
        }

        if (sourceQueueId.equals(targetQueueId)) {
            return moveQueueTrack(projectState, sourceQueueId, queueTrackId, targetQueueTrackId, placeAfter);
        }

        QueueTrack queueTrack = findQueueTrack(projectState, sourceQueueId, queueTrackId);
        if (queueTrack == null) {
            return false;
        }

        List<QueueTrack> createdTracks = addQueueTracks(
                projectState,
                targetQueueId,
                List.of(queueTrack.getAudioFileId()),
                targetQueueTrackId,
                placeAfter
        );
        if (createdTracks.isEmpty()) {
            return false;
        }

        createdTracks.getFirst().setLoop(queueTrack.isLoop());
        createdTracks.getFirst().setVolume(queueTrack.getVolume());
        removeQueueTrack(projectState, sourceQueueId, queueTrackId);
        return true;
    }

    public List<QueueTrack> addQueueTracks(ProjectState projectState, UUID queueId, List<UUID> audioFileIds) {
        return addQueueTracks(projectState, queueId, audioFileIds, null, false);
    }

    public List<QueueTrack> addQueueTracks(
            ProjectState projectState,
            UUID queueId,
            List<UUID> audioFileIds,
            UUID targetQueueTrackId,
            boolean placeAfter
    ) {
        Optional<WorkspaceQueue> optionalQueue = findWorkspaceQueue(projectState, queueId);
        if (optionalQueue.isEmpty()) {
            return List.of();
        }

        WorkspaceQueue workspaceQueue = optionalQueue.get();
        normalizeQueueTrackOrder(workspaceQueue);

        List<QueueTrack> createdTracks = new ArrayList<>();
        int startOrder = resolveQueueInsertOrder(workspaceQueue, targetQueueTrackId, placeAfter);
        for (QueueTrack existingTrack : workspaceQueue.getTracks()) {
            if (existingTrack.getOrder() >= startOrder) {
                existingTrack.setOrder(existingTrack.getOrder() + audioFileIds.size());
            }
        }
        for (UUID audioFileId : audioFileIds) {
            if (audioFileId == null) {
                continue;
            }

            QueueTrack queueTrack = new QueueTrack(
                    UUID.randomUUID(),
                    audioFileId,
                    startOrder + createdTracks.size(),
                    false
            );
            createdTracks.add(queueTrack);
        }

        workspaceQueue.getTracks().addAll(createdTracks);
        normalizeQueueTrackOrder(workspaceQueue);
        if (workspaceQueue.getSelectedTrackId() == null && !workspaceQueue.getTracks().isEmpty()) {
            workspaceQueue.setSelectedTrackId(workspaceQueue.getTracks().getFirst().getId());
        }
        return createdTracks;
    }

    public void removeQueueTrack(ProjectState projectState, UUID queueId, UUID queueTrackId) {
        findWorkspaceQueue(projectState, queueId).ifPresent(queue -> {
            queue.getTracks().removeIf(track -> track.getId().equals(queueTrackId));
            normalizeQueueTrackOrder(queue);
            if (queueTrackId.equals(queue.getSelectedTrackId())) {
                queue.setSelectedTrackId(queue.getTracks().isEmpty() ? null : queue.getTracks().getFirst().getId());
            }
        });
    }

    public boolean moveQueueTrack(ProjectState projectState, UUID queueId, UUID queueTrackId, UUID targetQueueTrackId, boolean placeAfter) {
        if (queueId == null || queueTrackId == null || targetQueueTrackId == null || queueTrackId.equals(targetQueueTrackId)) {
            return false;
        }

        Optional<WorkspaceQueue> optionalQueue = findWorkspaceQueue(projectState, queueId);
        if (optionalQueue.isEmpty()) {
            return false;
        }

        WorkspaceQueue workspaceQueue = optionalQueue.get();
        List<QueueTrack> queueTracks = new ArrayList<>(workspaceQueue.getTracks());
        queueTracks.sort(Comparator.comparingInt(QueueTrack::getOrder));

        QueueTrack sourceTrack = null;
        for (QueueTrack queueTrack : queueTracks) {
            if (queueTrack.getId().equals(queueTrackId)) {
                sourceTrack = queueTrack;
                break;
            }
        }

        if (sourceTrack == null) {
            return false;
        }

        queueTracks.removeIf(track -> track.getId().equals(queueTrackId));

        int targetIndex = -1;
        for (int index = 0; index < queueTracks.size(); index++) {
            if (queueTracks.get(index).getId().equals(targetQueueTrackId)) {
                targetIndex = index;
                break;
            }
        }

        if (targetIndex < 0) {
            return false;
        }

        queueTracks.add(placeAfter ? targetIndex + 1 : targetIndex, sourceTrack);
        for (int index = 0; index < queueTracks.size(); index++) {
            queueTracks.get(index).setOrder(index);
        }
        workspaceQueue.setTracks(queueTracks);
        return true;
    }

    public boolean moveWorkspaceItem(ProjectState projectState, UUID workspaceItemId, UUID targetWorkspaceItemId, boolean placeAfter) {
        if (workspaceItemId == null || targetWorkspaceItemId == null || workspaceItemId.equals(targetWorkspaceItemId)) {
            return false;
        }

        Optional<WorkspaceOrderEntry> sourceEntry = findWorkspaceOrderEntry(projectState, workspaceItemId);
        Optional<WorkspaceOrderEntry> targetEntry = findWorkspaceOrderEntry(projectState, targetWorkspaceItemId);
        if (sourceEntry.isEmpty() || targetEntry.isEmpty()) {
            return false;
        }

        List<WorkspaceOrderEntry> entries = collectWorkspaceOrderEntries(projectState);
        entries.sort(Comparator.comparingInt(WorkspaceOrderEntry::order));
        entries.removeIf(entry -> entry.id().equals(workspaceItemId));

        int targetIndex = -1;
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).id().equals(targetWorkspaceItemId)) {
                targetIndex = index;
                break;
            }
        }

        if (targetIndex < 0) {
            return false;
        }

        entries.add(placeAfter ? targetIndex + 1 : targetIndex, sourceEntry.get());
        applyWorkspaceOrder(projectState, entries);
        return true;
    }

    public void clearWorkspace(ProjectState projectState) {
        projectState.setWorkspaceTracks(List.of());
        projectState.setWorkspaceQueues(List.of());
        projectState.setWorkspaceVirtualTiles(List.of());
    }

    public void normalizeWorkspaceOrder(ProjectState projectState) {
        List<WorkspaceOrderEntry> entries = collectWorkspaceOrderEntries(projectState);
        entries.sort(Comparator.comparingInt(WorkspaceOrderEntry::order));
        applyWorkspaceOrder(projectState, entries);
    }

    public void normalizeQueueTrackOrder(WorkspaceQueue workspaceQueue) {
        List<QueueTrack> queueTracks = new ArrayList<>(workspaceQueue.getTracks());
        queueTracks.sort(Comparator.comparingInt(QueueTrack::getOrder));
        for (int index = 0; index < queueTracks.size(); index++) {
            queueTracks.get(index).setOrder(index);
        }
        workspaceQueue.setTracks(queueTracks);
    }

    public Optional<WorkspaceQueue> findWorkspaceQueue(ProjectState projectState, UUID queueId) {
        return projectState.getWorkspaceQueues().stream()
                .filter(queue -> queue.getId().equals(queueId))
                .findFirst();
    }

    public Optional<WorkspaceVirtualTile> findWorkspaceVirtualTile(ProjectState projectState, UUID tileId) {
        return projectState.getWorkspaceVirtualTiles().stream().filter(tile -> tile.getId().equals(tileId)).findFirst();
    }

    public Optional<AudioFile> findAudioFile(ProjectState projectState, UUID audioFileId) {
        return projectState.getAudioFiles().stream()
                .filter(audioFile -> audioFile.getId().equals(audioFileId))
                .findFirst();
    }

    private QueueTrack findQueueTrack(ProjectState projectState, UUID queueId, UUID queueTrackId) {
        Optional<WorkspaceQueue> optionalQueue = findWorkspaceQueue(projectState, queueId);
        if (optionalQueue.isEmpty()) {
            return null;
        }

        return optionalQueue.get().getTracks().stream()
                .filter(track -> track.getId().equals(queueTrackId))
                .findFirst()
                .orElse(null);
    }

    private int resolveWorkspaceInsertOrder(ProjectState projectState, UUID targetWorkspaceItemId, boolean placeAfter) {
        List<WorkspaceOrderEntry> entries = collectWorkspaceOrderEntries(projectState);
        entries.sort(Comparator.comparingInt(WorkspaceOrderEntry::order));

        if (targetWorkspaceItemId == null) {
            return entries.size();
        }

        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).id().equals(targetWorkspaceItemId)) {
                return placeAfter ? index + 1 : index;
            }
        }

        return entries.size();
    }

    private int totalWorkspaceItemCount(ProjectState projectState) {
        return projectState.getWorkspaceTracks().size() + projectState.getWorkspaceQueues().size()
                + projectState.getWorkspaceVirtualTiles().size();
    }

    private int resolveQueueInsertOrder(WorkspaceQueue workspaceQueue, UUID targetQueueTrackId, boolean placeAfter) {
        List<QueueTrack> queueTracks = new ArrayList<>(workspaceQueue.getTracks());
        queueTracks.sort(Comparator.comparingInt(QueueTrack::getOrder));

        if (targetQueueTrackId == null) {
            return queueTracks.size();
        }

        for (int index = 0; index < queueTracks.size(); index++) {
            if (queueTracks.get(index).getId().equals(targetQueueTrackId)) {
                return placeAfter ? index + 1 : index;
            }
        }

        return queueTracks.size();
    }

    private int resolveVirtualTileInsertOrder(WorkspaceVirtualTile tile, UUID targetTrackId, boolean placeAfter) {
        List<VirtualTileTrack> tracks = new ArrayList<>(tile.getTracks());
        tracks.sort(Comparator.comparingInt(VirtualTileTrack::getOrder));
        if (targetTrackId == null) return tracks.size();
        for (int index = 0; index < tracks.size(); index++) {
            if (tracks.get(index).getId().equals(targetTrackId)) return placeAfter ? index + 1 : index;
        }
        return tracks.size();
    }

    private Optional<WorkspaceOrderEntry> findWorkspaceOrderEntry(ProjectState projectState, UUID workspaceItemId) {
        return collectWorkspaceOrderEntries(projectState).stream()
                .filter(entry -> entry.id().equals(workspaceItemId))
                .findFirst();
    }

    private List<WorkspaceOrderEntry> collectWorkspaceOrderEntries(ProjectState projectState) {
        List<WorkspaceOrderEntry> entries = new ArrayList<>();
        for (WorkspaceTrack workspaceTrack : projectState.getWorkspaceTracks()) {
            entries.add(new WorkspaceOrderEntry(workspaceTrack.getId(), workspaceTrack.getOrder(), workspaceTrack::setOrder));
        }
        for (WorkspaceQueue workspaceQueue : projectState.getWorkspaceQueues()) {
            entries.add(new WorkspaceOrderEntry(workspaceQueue.getId(), workspaceQueue.getOrder(), workspaceQueue::setOrder));
        }
        for (WorkspaceVirtualTile tile : projectState.getWorkspaceVirtualTiles()) {
            entries.add(new WorkspaceOrderEntry(tile.getId(), tile.getOrder(), tile::setOrder));
        }
        return entries;
    }

    private void applyWorkspaceOrder(ProjectState projectState, List<WorkspaceOrderEntry> entries) {
        for (int index = 0; index < entries.size(); index++) {
            entries.get(index).orderSetter().accept(index);
        }

        List<WorkspaceTrack> workspaceTracks = new ArrayList<>(projectState.getWorkspaceTracks());
        workspaceTracks.sort(Comparator.comparingInt(WorkspaceTrack::getOrder));
        projectState.setWorkspaceTracks(workspaceTracks);

        List<WorkspaceQueue> workspaceQueues = new ArrayList<>(projectState.getWorkspaceQueues());
        workspaceQueues.sort(Comparator.comparingInt(WorkspaceQueue::getOrder));
        projectState.setWorkspaceQueues(workspaceQueues);

        List<WorkspaceVirtualTile> virtualTiles = new ArrayList<>(projectState.getWorkspaceVirtualTiles());
        virtualTiles.sort(Comparator.comparingInt(WorkspaceVirtualTile::getOrder));
        projectState.setWorkspaceVirtualTiles(virtualTiles);
    }

    private void normalizeVirtualTileTracks(WorkspaceVirtualTile tile) {
        List<VirtualTileTrack> tracks = new ArrayList<>(tile.getTracks());
        tracks.sort(Comparator.comparingInt(VirtualTileTrack::getOrder));
        for (int index = 0; index < tracks.size(); index++) tracks.get(index).setOrder(index);
        tile.setTracks(tracks);
    }

    private record WorkspaceOrderEntry(UUID id, int order, java.util.function.IntConsumer orderSetter) {
    }
}
