package app.project;

import app.model.AudioFile;
import app.model.ProjectState;
import app.model.WorkspaceTrack;
import app.scan.ScannedAudioFile;
import app.support.RelativePathUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProjectStateSynchronizer {
    private final int schemaVersion;

    public ProjectStateSynchronizer(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public ProjectState synchronize(ProjectState projectState, List<ScannedAudioFile> scannedAudioFiles) {
        Map<String, AudioFile> existingByPath = new LinkedHashMap<>();
        for (AudioFile audioFile : projectState.getAudioFiles()) {
            existingByPath.put(RelativePathUtils.normalize(audioFile.getRelativePath()), audioFile);
        }

        Map<String, AudioFile> synchronizedFiles = new LinkedHashMap<>();
        for (ScannedAudioFile scannedAudioFile : scannedAudioFiles) {
            String normalizedRelativePath = RelativePathUtils.normalize(scannedAudioFile.getRelativePath());
            AudioFile existingAudioFile = existingByPath.remove(normalizedRelativePath);
            AudioFile synchronizedAudioFile = existingAudioFile == null
                    ? new AudioFile(UUID.randomUUID(), normalizedRelativePath, scannedAudioFile.getDisplayName(), false)
                    : existingAudioFile;

            synchronizedAudioFile.setRelativePath(normalizedRelativePath);
            synchronizedAudioFile.setDisplayName(scannedAudioFile.getDisplayName());
            synchronizedAudioFile.setMissing(false);
            synchronizedFiles.put(normalizedRelativePath, synchronizedAudioFile);
        }

        for (AudioFile missingAudioFile : existingByPath.values()) {
            missingAudioFile.setRelativePath(RelativePathUtils.normalize(missingAudioFile.getRelativePath()));
            if (missingAudioFile.getDisplayName() == null || missingAudioFile.getDisplayName().isBlank()) {
                missingAudioFile.setDisplayName(RelativePathUtils.displayName(missingAudioFile.getRelativePath()));
            }
            missingAudioFile.setMissing(true);
            synchronizedFiles.put(missingAudioFile.getRelativePath(), missingAudioFile);
        }

        List<AudioFile> audioFiles = new ArrayList<>(synchronizedFiles.values());
        audioFiles.sort(Comparator.comparing(AudioFile::getRelativePath, String.CASE_INSENSITIVE_ORDER));

        projectState.setSchemaVersion(schemaVersion);
        projectState.setAudioFiles(audioFiles);
        normalizeWorkspaceTracks(projectState);
        return projectState;
    }

    private void normalizeWorkspaceTracks(ProjectState projectState) {
        List<WorkspaceTrack> workspaceTracks = new ArrayList<>(projectState.getWorkspaceTracks());
        workspaceTracks.sort(Comparator.comparingInt(WorkspaceTrack::getOrder));
        for (int index = 0; index < workspaceTracks.size(); index++) {
            workspaceTracks.get(index).setOrder(index);
        }
        projectState.setWorkspaceTracks(workspaceTracks);
    }
}
