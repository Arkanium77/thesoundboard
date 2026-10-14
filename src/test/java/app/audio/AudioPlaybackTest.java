package app.audio;

import app.model.PlaybackStatus;
import app.model.AudioFile;
import app.model.ProjectState;
import app.model.WorkspaceTrack;
import app.model.WorkspaceVirtualTile;
import app.model.VirtualTileTrack;
import app.model.VirtualTileLayout;
import app.config.AppConfig;
import app.config.AppConfigLoader;
import app.project.ProjectService;
import app.project.ProjectStateEditor;
import app.project.ProjectStateSynchronizer;
import app.project.ProjectSessionController;
import app.project.LastProjectPreferences;
import app.persistence.ProjectStateRepository;
import app.scan.AudioScanner;
import app.skin.SkinService;
import app.localization.LocalizationService;
import app.ui.main.MainView;
import app.ui.workspace.WorkspaceTrackItem;
import app.ui.workspace.WorkspaceVirtualTileItem;
import app.support.TestDirectorySupport;
import app.waveform.WaveformPreferences;
import app.skin.SkinRepository;
import app.skin.SkinDescriptor;
import app.ui.UiIcons;
import app.ui.UiScalePane;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.layout.Region;
import javafx.scene.image.WritableImage;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.IOException;
import javafx.stage.PopupWindow;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.geometry.Side;
import app.ui.tree.TreeNodeValue;
import app.model.QueueTrack;
import app.model.WorkspaceQueue;
import app.config.UiConfig;
import app.ui.queue.QueueView;
import app.ui.queue.QueueTrackChipView;
import app.ui.workspace.WorkspaceQueueItem;
import app.ui.waveform.WaveformSeekView;
import app.waveform.WaveformService;
import app.waveform.WaveformData;


import javafx.stage.Stage;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.TreeView;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;
import javafx.stage.Screen;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.scene.shape.SVGPath;
import javafx.application.Platform;
import javafx.util.Duration;
import javafx.css.PseudoClass;
import javafx.scene.Scene;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashSet;
import java.util.Set;
import java.nio.file.Files;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.ArrayList;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs identical workspace contracts against real native players, separately from device-free checks. */
@Tag("audio-device")
class AudioPlaybackTest {
    @ParameterizedTest
    @EnumSource(AudioEngineMode.class)
    void queueRepeatControlSurvivesSelectionAndUpdatesTheActiveBackend(AudioEngineMode mode) throws Exception {
        Path path = AudioTestSupport.fixture("wav");
        UUID audioId = UUID.randomUUID();
        AudioFile audio = new AudioFile(audioId, path.getFileName().toString(), "Tone", false);
        QueueTrack first = new QueueTrack(UUID.randomUUID(), audioId, 0, false);
        QueueTrack second = new QueueTrack(UUID.randomUUID(), audioId, 1, true);
        WorkspaceQueue queue = new WorkspaceQueue(UUID.randomUUID(), "Queue", 0, 1d, false);
        queue.setTracks(List.of(first, second));
        AudioEngine engine = AudioTestSupport.onFx(mode::createEngine);
        WorkspaceQueueItem item = AudioTestSupport.onFx(() -> new WorkspaceQueueItem(path.getParent(), queue,
                List.of(audio), engine, 0d, exception -> { throw new AssertionError(exception); }));
        try {
            AudioTestSupport.runOnFx(() -> { item.setLoopCurrentTrack(true); item.playSelectedTrack(first.getId()); });
            AudioTestSupport.await(() -> item.getCurrentTime().toMillis() > 100d);
            Thread.sleep(1200);
            AudioTestSupport.runOnFx(() -> {
                Assertions.assertThat(item.getActiveTrackId()).isEqualTo(first.getId());
                Assertions.assertThat(item.getStatus()).isEqualTo(PlaybackStatus.PLAYING);
                item.selectTrack(second.getId());
                Assertions.assertThat(item.isLoopCurrentTrack()).isTrue();
                item.setLoopCurrentTrack(false);
            });
            AudioTestSupport.await(() -> second.getId().equals(item.getActiveTrackId()) && item.getStatus() == PlaybackStatus.PLAYING);
            AudioTestSupport.runOnFx(() -> Assertions.assertThat(item.isLoopCurrentTrack()).isFalse());
        } finally {
            AudioTestSupport.runOnFx(() -> { item.dispose(); engine.shutdown(); });
        }
    }

    @Test
    void addingFirstTrackToEmptyQueueSelectsChipAndLoadsWaveformWithoutPlay() throws Exception {
        Path path = AudioTestSupport.fixture("wav");
        UUID audioId = UUID.randomUUID();
        AudioFile audio = new AudioFile(audioId, path.getFileName().toString(), "Tone", false);
        WorkspaceQueue queue = new WorkspaceQueue(UUID.randomUUID(), "Queue", 0, 1d, false);
        AtomicReference<Path> extracted = new AtomicReference<>();
        WaveformData expected = new WaveformData(new double[]{0.5d}, new double[]{0.5d}, Duration.seconds(1));
        WaveformService service = new WaveformService(file -> { extracted.set(file); return expected; });
        WorkspaceQueueItem item = new WorkspaceQueueItem(path.getParent(), queue, List.of(audio),
                file -> { throw new AssertionError("Selection must not initialize playback"); }, 1d, exception -> { });
        QueueView view = AudioTestSupport.onFx(() -> new QueueView(new UiConfig(), item, service,
                () -> { }, () -> { }, (ids, target, after) -> { }, (id, target, after) -> { },
                track -> { }, (source, id, target, after) -> { }, (tile, id, target, after) -> { },
                (source, target, after) -> { }));
        try {
            QueueTrack first = new QueueTrack(UUID.randomUUID(), audioId, 0, false);
            AudioTestSupport.runOnFx(() -> { queue.setTracks(List.of(first)); item.refreshAfterMutation(); view.rebuildChips(); });
            AudioTestSupport.await(() -> extracted.get() != null);
            Assertions.assertThat(extracted.get()).isEqualTo(path.toAbsolutePath().normalize());
            AudioTestSupport.runOnFx(() -> {
                Assertions.assertThat(item.getFocusedTrack()).contains(first);
                new Scene(view);
                view.applyCss();
                QueueTrackChipView chip = (QueueTrackChipView) view.lookup(".queue-chip");
                Assertions.assertThat(chip).isNotNull();
                Assertions.assertThat(chip.getPseudoClassStates()).contains(PseudoClass.getPseudoClass("selected"));
            });
            Field waveformField = QueueView.class.getDeclaredField("waveformSeekView");
            waveformField.setAccessible(true);
            WaveformSeekView waveform = (WaveformSeekView) waveformField.get(view);
            Field dataField = WaveformSeekView.class.getDeclaredField("waveformData");
            dataField.setAccessible(true);
            AudioTestSupport.await(() -> {
                try { return dataField.get(waveform) == expected; }
                catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
            });
        } finally {
            AudioTestSupport.runOnFx(view::dispose);
            service.shutdown();
        }
    }

    @ParameterizedTest
    @EnumSource(AudioEngineMode.class)
    void linkingLibraryDoesNotExtractWaveformsAndUnlinkingPreservesUnrelatedPlayback(AudioEngineMode mode) throws Exception {
        Path tone = AudioTestSupport.fixture("wav");
        Path parent = TestDirectorySupport.createTempDirectory("linked-gui-").toAbsolutePath();
        Path root = Files.createDirectory(parent.resolve("project"));
        Path library = Files.createDirectories(parent.resolve("library/nested"));
        Files.copy(tone, root.resolve("tone.wav"));
        Files.copy(tone, library.resolve("tone.wav"));
        for (int index = 0; index < 300; index++) Files.writeString(library.resolve("unused-" + index + ".wav"), "unused audio");
        AppConfig config = AppConfigLoader.load();
        ProjectService projects = new ProjectService(2, new ProjectStateRepository("state.json"),
                new AudioScanner(List.of("wav")), new ProjectStateSynchronizer(2));
        AtomicInteger extractions = new AtomicInteger();
        WaveformService waves = new WaveformService(path -> {
            extractions.incrementAndGet();
            Assertions.assertThat(path.getFileName().toString()).isEqualTo("tone.wav");
            return new WaveformData(new double[]{0.5d}, new double[]{0.5d}, Duration.seconds(1));
        });
        AudioEngine engine = AudioTestSupport.onFx(mode::createEngine);
        MainView view = AudioTestSupport.onFx(() -> {
            SkinService skins = new SkinService();
            skins.prepareUiResources();
            Stage stage = new Stage();
            Screen screen = Screen.getScreens().stream().filter(candidate -> !candidate.equals(Screen.getPrimary()))
                    .findFirst().orElse(Screen.getPrimary());
            stage.setX(screen.getVisualBounds().getMinX() + 40d);
            stage.setY(screen.getVisualBounds().getMinY() + 40d);
            return new MainView(stage, config, projects, new ProjectStateEditor(), engine, waves,
                    new SimpleDoubleProperty(1d), skins, new LocalizationService(), new LastProjectPreferences(), new WaveformPreferences());
        });
        ProjectSessionController session = (ProjectSessionController) readField(view, "session");
        try {
            var loaded = projects.loadProject(root);
            AudioTestSupport.runOnFx(() -> {
                session.completeLoad(session.beginLoad(), loaded);
                invoke(view, "rebuildTree", new Class<?>[0]);
                invoke(view, "rebuildWorkspace", new Class<?>[0]);
                invoke(view, "beginLinkFolder", new Class<?>[]{Path.class}, library.getParent());
            });
            AudioTestSupport.await(() -> !session.isLoading());
            AudioTestSupport.runOnFx(() -> {
                Assertions.assertThat(session.getState().getLinkedAudioFolders()).hasSize(1);
                Assertions.assertThat(session.getState().getAudioFiles()).hasSize(302);
                Assertions.assertThat(extractions.get()).isZero();
                TreeView<?> tree = (TreeView<?>) readField(view, "treeView");
                tree.getRoot().getChildren().forEach(item -> item.setExpanded(true));
                Assertions.assertThat(tree.getRoot().getChildren().stream().map(item -> (TreeNodeValue) item.getValue())
                        .filter(node -> node.getLinkedFolderId() != null).count()).isEqualTo(1);
                Assertions.assertThat(extractions.get()).isZero();
                Scene scene = new Scene(new UiScalePane(view, new SimpleDoubleProperty(1d)), 1100, 700);
                ((SkinService) readField(view, "skinService")).apply(scene, view);
                Stage stage = (Stage) readField(view, "stage");
                stage.setScene(scene);
                stage.show();
                SkinRepository repository = new SkinRepository();
                List<SkinDescriptor> skins = new ArrayList<>();
                skins.add(repository.findSelected(SkinRepository.DEFAULT_SKIN_UID));
                for (String name : List.of("Night Mode", "Retro Amp", "Sakura", "Tactical Codec")) {
                    skins.add(repository.loadSkinDirectory(Path.of("examples/skins", name, "source")));
                }
                for (SkinDescriptor skin : skins) {
                    UiIcons.configure(skin);
                    scene.getStylesheets().setAll(MainView.class.getResource("/skins/core.css").toExternalForm(),
                            skin.resolveResource(skin.manifest().getStylesheet()).toExternalForm());
                    tree.refresh();
                    view.applyCss();
                    view.layout();
                    Button open = (Button) readField(view, "openFolderButton");
                    MenuButton history = (MenuButton) readField(view, "recentProjectsButton");
                    Button link = (Button) readField(view, "linkFolderButton");
                    Label heading = (Label) readField(view, "projectTreeLabel");
                    Assertions.assertThat(history.getText()).isEmpty();
                    Assertions.assertThat(history.getLayoutX()).isEqualTo(open.getLayoutX() + open.getWidth());
                    Assertions.assertThat(link.getHeight()).isEqualTo(heading.getHeight() + 4d);
                    Assertions.assertThat(link.getFont()).isEqualTo(heading.getFont());
                    for (String field : List.of("settingsButton", "rescanButton", "saveButton", "clearWorkspaceButton", "rebuildStateButton")) {
                        Region button = (Region) readField(view, field);
                        Assertions.assertThat(button.getHeight()).as(skin.manifest().getName() + " " + field).isEqualTo(open.getHeight());
                    }
                    Assertions.assertThat(history.getHeight()).isEqualTo(open.getHeight());
                    if (skin.manifest().getName().contains("Tactical")) {
                        WritableImage snapshot = view.snapshot(null, null);
                        BufferedImage image = new BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < image.getHeight(); y++) {
                            for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                        }
                        Path screenshot = Path.of(".local", "validation", "compact-project-header.png");
                        try {
                            Files.createDirectories(screenshot.getParent());
                            ImageIO.write(image, "png", screenshot.toFile());
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    }
                    Assertions.assertThat(view.lookup(".linked-folder-icon")).as(skin.manifest().getName()).isNotNull();
                    SVGPath icon = (SVGPath) view.lookup(".linked-folder-icon .soundboard-icon");
                    Assertions.assertThat(icon.getContent()).isNotBlank();
                    Assertions.assertThat(icon.getFill()).isNotNull();
                    Menu nested = new Menu("Queues");
                    nested.getItems().add(new MenuItem("WAV"));
                    ContextMenu menu = new ContextMenu(new MenuItem("Unlink Folder"), nested);
                    try {
                        menu.show(tree, Side.RIGHT, 0d, 0d);
                        menu.getScene().getRoot().applyCss();
                        Assertions.assertThat(menu.getScene().getRoot().getStyleClass()).contains("soundboard-root");
                        Assertions.assertThat(menu.getScene().getRoot().getStylesheets())
                                .contains(skin.resolveResource(skin.manifest().getStylesheet()).toExternalForm());
                        Assertions.assertThat(menu.getScene().getStylesheets())
                                .contains(skin.resolveResource(skin.manifest().getStylesheet()).toExternalForm());
                        nested.show();
                        for (Window window : List.copyOf(Window.getWindows())) {
                            if (!(window instanceof PopupWindow) || window == menu) continue;
                            window.getScene().getRoot().applyCss();
                            Assertions.assertThat(window.getScene().getStylesheets())
                                    .contains(skin.resolveResource(skin.manifest().getStylesheet()).toExternalForm());
                        }
                        nested.hide();
                        if (skin.manifest().getName().contains("Tactical")) {
                            Label label = (Label) menu.getScene().getRoot().lookup(".menu-item .label");
                            Assertions.assertThat(label.getTextFill()).isIn(Color.web("#211f19"), Color.web("#f5edd2"));
                        }
                    } finally { menu.hide(); }
                }
                UUID folderId = session.getState().getLinkedAudioFolders().getFirst().getId();
                dismissUnlink(view, folderId, List.of(ButtonType.CANCEL));
                Assertions.assertThat(session.getState().getLinkedAudioFolders()).hasSize(1);
                ProjectState state = session.getState();
                AudioFile local = state.getAudioFiles().stream().filter(file -> file.getLinkedFolderId() == null).findFirst().orElseThrow();
                AudioFile external = state.getAudioFiles().stream().filter(file -> file.getLinkedFolderId() != null
                        && file.getRelativePath().equals("nested/tone.wav")).findFirst().orElseThrow();
                ProjectStateEditor editor = new ProjectStateEditor();
                editor.addWorkspaceTrack(state, local.getId(), 0d, true);
                editor.addWorkspaceTrack(state, external.getId(), 0d, true);
                WorkspaceQueue queue = new WorkspaceQueue(UUID.randomUUID(), "Mixed", 2, 0d, false);
                queue.setLoopCurrentTrack(true);
                queue.setTracks(new ArrayList<>(List.of(new QueueTrack(UUID.randomUUID(), external.getId(), 0, false),
                        new QueueTrack(UUID.randomUUID(), local.getId(), 1, false))));
                state.getWorkspaceQueues().add(queue);
                WorkspaceVirtualTile tile = new WorkspaceVirtualTile(UUID.randomUUID(), 3, VirtualTileLayout.TWO_BY_TWO);
                VirtualTileTrack virtual = new VirtualTileTrack(UUID.randomUUID(), external.getId(), 0, 0d);
                virtual.setLoop(true);
                tile.setTracks(List.of(virtual));
                state.getWorkspaceVirtualTiles().add(tile);
                invoke(view, "rebuildWorkspace", new Class<?>[0]);
            });
            Map<?, ?> tracks = (Map<?, ?>) readField(view, "workspaceTrackItems");
            WorkspaceTrackItem localPlayer = (WorkspaceTrackItem) tracks.get(session.getState().getWorkspaceTracks().getFirst().getId());
            WorkspaceTrackItem externalPlayer = (WorkspaceTrackItem) tracks.get(session.getState().getWorkspaceTracks().getLast().getId());
            WorkspaceQueueItem queuePlayer = (WorkspaceQueueItem) ((Map<?, ?>) readField(view, "workspaceQueueItems")).values().iterator().next();
            WorkspaceVirtualTileItem virtualPlayer = (WorkspaceVirtualTileItem) ((Map<?, ?>) readField(view, "workspaceVirtualTileItems")).values().iterator().next();
            AudioTestSupport.runOnFx(() -> {
                localPlayer.togglePlayPause();
                externalPlayer.togglePlayPause();
                queuePlayer.togglePlayPause();
                virtualPlayer.getTrackItems().getFirst().togglePlayPause();
            });
            AudioTestSupport.await(() -> localPlayer.getCurrentTime().toMillis() > 100d
                    && externalPlayer.getCurrentTime().toMillis() > 100d && queuePlayer.getCurrentTime().toMillis() > 100d);
            AudioTestSupport.runOnFx(() -> {
                dismissUnlink(view, session.getState().getLinkedAudioFolders().getFirst().getId(), List.of(ButtonType.OK));
                Assertions.assertThat(session.getState().getLinkedAudioFolders()).isEmpty();
                Assertions.assertThat(session.getState().getAudioFiles()).hasSize(1);
                Assertions.assertThat(session.getState().getWorkspaceTracks()).hasSize(1);
                Assertions.assertThat(localPlayer.getStatus()).isEqualTo(PlaybackStatus.PLAYING);
                Assertions.assertThat(externalPlayer.getStatus()).isEqualTo(PlaybackStatus.READY);
                Assertions.assertThat(queuePlayer.getStatus()).isEqualTo(PlaybackStatus.READY);
                Assertions.assertThat(queuePlayer.getTracks()).hasSize(1);
                Assertions.assertThat(virtualPlayer.getTrackItems()).isEmpty();
            });
            Assertions.assertThat(library.resolve("tone.wav")).exists();
        } finally {
            AudioTestSupport.runOnFx(() -> { view.shutdown(); ((Stage) readField(view, "stage")).close(); engine.shutdown(); });
        }
    }

    /** Exercise the actual modal sequence with deterministic button presses; the owner is placed on the second
     * monitor when present. No operating-system automation or user input is needed to finish these checks. */
    private static void dismissUnlink(MainView view, UUID folderId, List<ButtonType> responses) {
        AtomicInteger step = new AtomicInteger();
        Set<Window> handled = new HashSet<>();
        Timeline dismiss = new Timeline(new KeyFrame(Duration.millis(50), event -> {
            for (Window window : new ArrayList<>(Window.getWindows())) {
                if (!window.isShowing() || handled.contains(window) || window.getScene() == null
                        || !(window.getScene().getRoot() instanceof DialogPane pane)) continue;
                handled.add(window);
                Assertions.assertThat(pane.getStyleClass()).contains("settings-dialog", "settings-confirmation-dialog");
                pane.applyCss();
                Assertions.assertThat(pane.getGraphic()).isNull();
                Assertions.assertThat(pane.getButtonTypes()).containsExactly(ButtonType.CANCEL, ButtonType.OK);
                ((Button) pane.lookupButton(responses.get(step.getAndIncrement()))).fire();
            }
        }));
        dismiss.setCycleCount(Timeline.INDEFINITE);
        dismiss.play();
        try {
            invoke(view, "confirmUnlinkFolder", new Class<?>[]{UUID.class}, folderId);
            Assertions.assertThat(step.get()).isEqualTo(responses.size());
        } finally { dismiss.stop(); }
    }

    private static Object readField(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    private static void invoke(Object target, String name, Class<?>[] types, Object... arguments) {
        try {
            Method method = target.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(target, arguments);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    @BeforeAll
    static void startJavaFx() throws Exception {
        AudioTestSupport.startJavaFx();
    }

    @ParameterizedTest
    @EnumSource(AudioEngineMode.class)
    void playsPausesRestoresAndResetsBothCommonFormats(AudioEngineMode mode) throws Exception {
        AudioEngine engine = AudioTestSupport.onFx(mode::createEngine);
        try {
            for (String extension : List.of("wav", "mp3")) {
                Path path = AudioTestSupport.fixture(extension);
                PlayingTrack track = AudioTestSupport.onFx(() -> engine.createTrack(path));
                try {
                    AudioTestSupport.runOnFx(() -> track.setVolume(0d));
                    AudioTestSupport.await(() -> track.getTotalDuration().toSeconds() > 0.8d);
                    AudioTestSupport.runOnFx(track::play);
                    AudioTestSupport.await(() -> track.getCurrentTime().toMillis() > 100d);
                    AudioTestSupport.runOnFx(track::pause);
                    AudioTestSupport.await(() -> track.getStatus() == PlaybackStatus.PAUSED);
                    double paused = AudioTestSupport.onFx(() -> track.getCurrentTime().toMillis());
                    Thread.sleep(100);
                    Assertions.assertThat(AudioTestSupport.onFx(() -> track.getCurrentTime().toMillis()))
                            .isBetween(paused - 30d, paused + 80d);
                    AudioTestSupport.runOnFx(() -> track.restorePlayback(Duration.millis(400), true));
                    AudioTestSupport.await(() -> track.getStatus() == PlaybackStatus.PAUSED
                            && track.getCurrentTime().toMillis() > 300d);
                    AudioTestSupport.runOnFx(track::stop);
                    AudioTestSupport.await(() -> track.getStatus() == PlaybackStatus.STOPPED);
                    AudioTestSupport.runOnFx(() -> track.restorePlayback(Duration.millis(500), false));
                    AudioTestSupport.await(() -> track.getStatus() == PlaybackStatus.PLAYING
                            && track.getCurrentTime().toMillis() > 400d);
                    AudioTestSupport.runOnFx(track::stop);
                    AudioTestSupport.runOnFx(track::play);
                    AudioTestSupport.await(() -> track.getStatus() == PlaybackStatus.PLAYING);
                    Assertions.assertThat(AudioTestSupport.onFx(() -> track.getCurrentTime().toMillis())).isLessThan(350d);
                } finally {
                    AudioTestSupport.runOnFx(track::dispose);
                }
            }
        } finally {
            AudioTestSupport.runOnFx(engine::shutdown);
        }
    }

    @ParameterizedTest
    @EnumSource(AudioEngineMode.class)
    void preservesLoopAfterStopAndDeliversCompletionOnFx(AudioEngineMode mode) throws Exception {
        Path path = AudioTestSupport.fixture("wav");
        AudioEngine engine = AudioTestSupport.onFx(mode::createEngine);
        PlayingTrack track = AudioTestSupport.onFx(() -> engine.createTrack(path));
        AtomicBoolean callbackOnFx = new AtomicBoolean(true);
        AtomicInteger finished = new AtomicInteger();
        try {
            AudioTestSupport.await(() -> track.getTotalDuration().toSeconds() > 0.8d);
            AudioTestSupport.runOnFx(() -> {
                track.setVolume(0d);
                track.setOnStatusChanged(() -> {
                    if (!Platform.isFxApplicationThread()) callbackOnFx.set(false);
                    if (track.getStatus() == PlaybackStatus.FINISHED) finished.incrementAndGet();
                });
                track.setLoop(true);
                track.stop();
                track.restorePlayback(Duration.millis(850), false);
            });
            Thread.sleep(500);
            Assertions.assertThat(AudioTestSupport.onFx(track::getStatus)).isEqualTo(PlaybackStatus.PLAYING);
            Assertions.assertThat(finished.get()).isZero();
            AudioTestSupport.runOnFx(() -> {
                track.setLoop(false);
                track.seek(Duration.millis(850));
            });
            AudioTestSupport.await(() -> finished.get() == 1);
            Assertions.assertThat(callbackOnFx.get()).isTrue();
            AudioTestSupport.runOnFx(track::play);
            AudioTestSupport.await(() -> track.getStatus() == PlaybackStatus.PLAYING
                    && track.getCurrentTime().toMillis() > 50d);
        } finally {
            AudioTestSupport.runOnFx(track::dispose);
            AudioTestSupport.runOnFx(engine::shutdown);
        }
    }
}
