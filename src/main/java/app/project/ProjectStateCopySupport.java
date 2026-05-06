package app.project;

import app.model.AudioFile;
import app.model.ProjectState;
import app.model.QueueTrack;
import app.model.WorkspaceQueue;
import app.model.WorkspaceTrack;

import java.util.ArrayList;
import java.util.List;

public final class ProjectStateCopySupport {
    private ProjectStateCopySupport() {
    }

    public static ProjectState copy(ProjectState source) {
        ProjectState copy = new ProjectState(source.getSchemaVersion());
        copy.setMasterVolume(source.getMasterVolume());
        copy.setAudioFiles(copyAudioFiles(source.getAudioFiles()));
        copy.setWorkspaceTracks(copyWorkspaceTracks(source.getWorkspaceTracks()));
        copy.setWorkspaceQueues(copyWorkspaceQueues(source.getWorkspaceQueues()));
        return copy;
    }

    private static List<AudioFile> copyAudioFiles(List<AudioFile> source) {
        List<AudioFile> audioFiles = new ArrayList<>();
        for (AudioFile audioFile : source) {
            audioFiles.add(new AudioFile(
                    audioFile.getId(),
                    audioFile.getRelativePath(),
                    audioFile.getDisplayName(),
                    audioFile.isMissing()
            ));
        }
        return audioFiles;
    }

    private static List<WorkspaceTrack> copyWorkspaceTracks(List<WorkspaceTrack> source) {
        List<WorkspaceTrack> workspaceTracks = new ArrayList<>();
        for (WorkspaceTrack workspaceTrack : source) {
            workspaceTracks.add(new WorkspaceTrack(
                    workspaceTrack.getId(),
                    workspaceTrack.getAudioFileId(),
                    workspaceTrack.getOrder(),
                    workspaceTrack.getVolume(),
                    workspaceTrack.isLoop()
            ));
        }
        return workspaceTracks;
    }

    private static List<WorkspaceQueue> copyWorkspaceQueues(List<WorkspaceQueue> source) {
        List<WorkspaceQueue> workspaceQueues = new ArrayList<>();
        for (WorkspaceQueue workspaceQueue : source) {
            WorkspaceQueue copiedQueue = new WorkspaceQueue(
                    workspaceQueue.getId(),
                    workspaceQueue.getName(),
                    workspaceQueue.getOrder(),
                    workspaceQueue.getVolume(),
                    workspaceQueue.isLoopQueue()
            );
            copiedQueue.setShuffleEnabled(workspaceQueue.isShuffleEnabled());
            copiedQueue.setSelectedTrackId(workspaceQueue.getSelectedTrackId());
            copiedQueue.setTracks(copyQueueTracks(workspaceQueue.getTracks()));
            workspaceQueues.add(copiedQueue);
        }
        return workspaceQueues;
    }

    private static List<QueueTrack> copyQueueTracks(List<QueueTrack> source) {
        List<QueueTrack> queueTracks = new ArrayList<>();
        for (QueueTrack queueTrack : source) {
            QueueTrack copiedTrack = new QueueTrack(
                    queueTrack.getId(),
                    queueTrack.getAudioFileId(),
                    queueTrack.getOrder(),
                    queueTrack.isLoop()
            );
            copiedTrack.setShuffledOrder(queueTrack.getShuffledOrder());
            queueTracks.add(copiedTrack);
        }
        return queueTracks;
    }
}
