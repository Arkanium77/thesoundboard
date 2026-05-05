package app.audio;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.nio.file.Path;

public class JavaFxAudioEngine implements AudioEngine {
    @Override
    public PlayingTrack createTrack(Path audioPath) {
        Media media = new Media(audioPath.toUri().toString());
        MediaPlayer mediaPlayer = new MediaPlayer(media);
        return new JavaFxPlayingTrack(mediaPlayer);
    }
}
