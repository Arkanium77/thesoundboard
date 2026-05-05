package app.project;

import app.model.AudioFile;
import app.model.ProjectState;
import app.model.VirtualFolder;
import app.model.WorkspaceTrack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ProjectStateEditor {
    public VirtualFolder createVirtualFolder(ProjectState projectState, UUID parentFolderId, String name) {
        VirtualFolder virtualFolder = new VirtualFolder(UUID.randomUUID(), name);
        projectState.getVirtualFolders().add(virtualFolder);

        if (parentFolderId != null) {
            findVirtualFolder(projectState, parentFolderId)
                    .ifPresent(parentFolder -> parentFolder.getChildFolderIds().add(virtualFolder.getId()));
        }

        sortVirtualFolders(projectState);
        return virtualFolder;
    }

    public void renameVirtualFolder(ProjectState projectState, UUID folderId, String newName) {
        findVirtualFolder(projectState, folderId).ifPresent(folder -> folder.setName(newName));
        sortVirtualFolders(projectState);
    }

    public void addAudioFileToVirtualFolder(ProjectState projectState, UUID folderId, UUID audioFileId) {
        findVirtualFolder(projectState, folderId).ifPresent(folder -> {
            if (!folder.getAudioFileIds().contains(audioFileId)) {
                folder.getAudioFileIds().add(audioFileId);
            }
        });
    }

    public void removeAudioFileFromVirtualFolder(ProjectState projectState, UUID folderId, UUID audioFileId) {
        findVirtualFolder(projectState, folderId)
                .ifPresent(folder -> folder.getAudioFileIds().remove(audioFileId));
    }

    public WorkspaceTrack addWorkspaceTrack(ProjectState projectState, UUID audioFileId, double volume, boolean loop) {
        return addWorkspaceTracks(projectState, List.of(audioFileId), volume, loop, null, false).getFirst();
    }

    public List<WorkspaceTrack> addWorkspaceTracks(
            ProjectState projectState,
            List<UUID> audioFileIds,
            double volume,
            boolean loop,
            UUID targetWorkspaceTrackId,
            boolean placeAfter
    ) {
        normalizeWorkspaceTrackOrder(projectState);

        List<WorkspaceTrack> workspaceTracks = new ArrayList<>(projectState.getWorkspaceTracks());
        int insertIndex = resolveInsertIndex(workspaceTracks, targetWorkspaceTrackId, placeAfter);
        List<WorkspaceTrack> createdTracks = new ArrayList<>();

        for (UUID audioFileId : new LinkedHashSet<>(audioFileIds)) {
            if (audioFileId == null) {
                continue;
            }

            WorkspaceTrack workspaceTrack = new WorkspaceTrack(
                    UUID.randomUUID(),
                    audioFileId,
                    insertIndex + createdTracks.size(),
                    volume,
                    loop
            );
            createdTracks.add(workspaceTrack);
        }

        workspaceTracks.addAll(insertIndex, createdTracks);
        for (int index = 0; index < workspaceTracks.size(); index++) {
            workspaceTracks.get(index).setOrder(index);
        }
        projectState.setWorkspaceTracks(workspaceTracks);
        return createdTracks;
    }

    public void removeWorkspaceTrack(ProjectState projectState, UUID workspaceTrackId) {
        projectState.getWorkspaceTracks().removeIf(track -> track.getId().equals(workspaceTrackId));
        normalizeWorkspaceTrackOrder(projectState);
    }

    public boolean moveWorkspaceTrack(ProjectState projectState, UUID workspaceTrackId, int direction) {
        List<WorkspaceTrack> workspaceTracks = new ArrayList<>(projectState.getWorkspaceTracks());
        workspaceTracks.sort(Comparator.comparingInt(WorkspaceTrack::getOrder));

        int currentIndex = -1;
        for (int index = 0; index < workspaceTracks.size(); index++) {
            if (workspaceTracks.get(index).getId().equals(workspaceTrackId)) {
                currentIndex = index;
                break;
            }
        }

        if (currentIndex < 0) {
            return false;
        }

        int targetIndex = currentIndex + direction;
        if (targetIndex < 0 || targetIndex >= workspaceTracks.size()) {
            return false;
        }

        WorkspaceTrack currentTrack = workspaceTracks.get(currentIndex);
        WorkspaceTrack targetTrack = workspaceTracks.get(targetIndex);
        int currentOrder = currentTrack.getOrder();
        currentTrack.setOrder(targetTrack.getOrder());
        targetTrack.setOrder(currentOrder);
        normalizeWorkspaceTrackOrder(projectState);
        return true;
    }

    public boolean moveWorkspaceTrack(ProjectState projectState, UUID workspaceTrackId, UUID targetWorkspaceTrackId, boolean placeAfter) {
        if (workspaceTrackId == null || targetWorkspaceTrackId == null || workspaceTrackId.equals(targetWorkspaceTrackId)) {
            return false;
        }

        List<WorkspaceTrack> workspaceTracks = new ArrayList<>(projectState.getWorkspaceTracks());
        workspaceTracks.sort(Comparator.comparingInt(WorkspaceTrack::getOrder));

        WorkspaceTrack sourceTrack = null;
        WorkspaceTrack targetTrack = null;
        for (WorkspaceTrack workspaceTrack : workspaceTracks) {
            if (workspaceTrack.getId().equals(workspaceTrackId)) {
                sourceTrack = workspaceTrack;
            } else if (workspaceTrack.getId().equals(targetWorkspaceTrackId)) {
                targetTrack = workspaceTrack;
            }
        }

        if (sourceTrack == null || targetTrack == null) {
            return false;
        }

        workspaceTracks.remove(sourceTrack);
        int insertIndex = resolveInsertIndex(workspaceTracks, targetWorkspaceTrackId, placeAfter);
        if (insertIndex < 0 || insertIndex > workspaceTracks.size()) {
            return false;
        }

        workspaceTracks.add(insertIndex, sourceTrack);
        for (int index = 0; index < workspaceTracks.size(); index++) {
            workspaceTracks.get(index).setOrder(index);
        }
        projectState.setWorkspaceTracks(workspaceTracks);
        return true;
    }

    public void normalizeWorkspaceTrackOrder(ProjectState projectState) {
        List<WorkspaceTrack> workspaceTracks = new ArrayList<>(projectState.getWorkspaceTracks());
        workspaceTracks.sort(Comparator.comparingInt(WorkspaceTrack::getOrder));
        for (int index = 0; index < workspaceTracks.size(); index++) {
            workspaceTracks.get(index).setOrder(index);
        }
        projectState.setWorkspaceTracks(workspaceTracks);
    }

    public Optional<VirtualFolder> findVirtualFolder(ProjectState projectState, UUID folderId) {
        return projectState.getVirtualFolders().stream()
                .filter(folder -> folder.getId().equals(folderId))
                .findFirst();
    }

    public Optional<AudioFile> findAudioFile(ProjectState projectState, UUID audioFileId) {
        return projectState.getAudioFiles().stream()
                .filter(audioFile -> audioFile.getId().equals(audioFileId))
                .findFirst();
    }

    private void sortVirtualFolders(ProjectState projectState) {
        List<VirtualFolder> virtualFolders = new ArrayList<>(projectState.getVirtualFolders());
        virtualFolders.sort(Comparator.comparing(VirtualFolder::getName, String.CASE_INSENSITIVE_ORDER));
        projectState.setVirtualFolders(virtualFolders);
    }

    private int resolveInsertIndex(List<WorkspaceTrack> workspaceTracks, UUID targetWorkspaceTrackId, boolean placeAfter) {
        if (targetWorkspaceTrackId == null) {
            return workspaceTracks.size();
        }

        for (int index = 0; index < workspaceTracks.size(); index++) {
            if (workspaceTracks.get(index).getId().equals(targetWorkspaceTrackId)) {
                return placeAfter ? index + 1 : index;
            }
        }

        return workspaceTracks.size();
    }
}
