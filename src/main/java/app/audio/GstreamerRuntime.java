package app.audio;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.WString;
import com.sun.jna.Pointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Plugin;
import org.freedesktop.gstreamer.glib.GLib;
import org.freedesktop.gstreamer.glib.Natives;
import org.freedesktop.gstreamer.lowlevel.GstNative;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads only the explicitly selected private runtime. A system codec installation must not silently change
 * supported formats or licensing. Environment and DLL search paths are configured before GStreamer starts
 * worker threads; its registry belongs to user data, never the replaceable installation directory. Registries are
 * separated by runtime location so cached plugins from a different installed runtime cannot escape isolation. Initialization
 * is process-wide and is not undone while waveform workers or native callbacks may still reference the runtime.
 * Dependencies are preloaded in manifest order, avoiding a user-provided LD_LIBRARY_PATH on Linux. The optional
 * property is a development override; packaged runtimes live next to the application JAR on every platform.
 * Linux shares a sufficiently new system GLib with GTK: preloading an older private GLib prevents a newer GTK
 * from resolving its symbols. An older system instead uses the newer bundled GLib, initialized before GTK. The
 * system version is read from its versioned SONAME target without loading it: GLib has NODELETE and probing an old
 * version through dlopen would make replacing it unsafe. Graphics libraries explicitly listed by the native catalog
 * also share their system instance when available: an older libdrm bundled with FFmpeg can break newer GTK/Mesa.
 * Only GLib receives binding aliases; codec plugins remain private.
 * A packaged manifest explicitly selects plugin modules and separates their registry. Ordinary copy-over updates
 * leave removed files behind; scanning the installation directory would reactivate obsolete/unselected plugins.
 */
public final class GstreamerRuntime {
    private static boolean initialized;
    private static boolean closed;
    private static final List<NativeLibrary> LIBRARIES = new ArrayList<>();
    private static final Set<String> SHARED_GLIB = Set.of("libglib-2.0.so.0", "libgobject-2.0.so.0",
            "libgmodule-2.0.so.0", "libgio-2.0.so.0");
    private static Path loaderAliases;

    private GstreamerRuntime() { }

    public static synchronized void initialize() {
        if (closed) throw new IllegalStateException("GStreamer runtime has shut down; restart the application");
        if (initialized) return;
        String configured = System.getProperty("thesoundboard.gstreamer.path");
        Path root = configured == null || configured.isBlank() ? packagedRuntime()
                : Path.of(configured).toAbsolutePath().normalize();
        Path plugins = root.resolve("lib/gstreamer-1.0");
        if (!Files.isDirectory(plugins)) throw new IllegalStateException("Missing GStreamer plugins: " + plugins);
        byte[] manifest;
        try {
            Path path = root.resolve("runtime-manifest.json");
            manifest = Files.isRegularFile(path) ? Files.readAllBytes(path) : new byte[0];
            if (manifest.length == 0 && (configured == null || configured.isBlank())) {
                throw new IllegalStateException("Missing bundled GStreamer runtime manifest: " + path);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read GStreamer runtime manifest", exception);
        }
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");
        boolean linux = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux");
        JsonNode catalog;
        try {
            catalog = manifest.length == 0 ? null : new ObjectMapper().readTree(manifest);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot parse GStreamer runtime manifest", exception);
        }
        int minimumGlibMinor = catalog == null ? 0 : catalog.path("glib").path("minimumSystemMinor").asInt();
        Path systemGlib = linux && minimumGlibMinor > 0 ? systemGlibDirectory(minimumGlibMinor) : null;
        boolean sharedGlib = linux && (minimumGlibMinor == 0 || systemGlib != null);
        Path libraries = root.resolve(windows ? "bin" : "lib");
        if (!Files.isDirectory(libraries)) throw new IllegalStateException("Missing GStreamer libraries: " + libraries);
        String prefix = libraries.toString().replace('\\', '/').replace("%", "%%") + "/";
        String names = windows ? prefix + "%s-0.dll|" + prefix + "%s.dll|" + prefix + "lib%s-0.dll|" + prefix + "lib%s.dll"
                : System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")
                ? prefix + "lib%s.0.dylib|" + prefix + "lib%s.dylib"
                : prefix + "lib%s.so.0|" + prefix + "lib%s.so";
        if (sharedGlib) names = sharedGlibPrefix(systemGlib) + "lib%s.so.0|" + names;
        System.setProperty("gstreamer.GNative.nameFormats", names);
        System.setProperty("jna.library.path", libraries.toString());
        System.setProperty("jna.encoding", StandardCharsets.UTF_8.name());
        if (windows && !Native.load("kernel32", WindowsLoader.class).SetDllDirectoryW(
                new WString(libraries.toString()))) {
            throw new IllegalStateException("Cannot configure the GStreamer DLL directory");
        }
        Path registry = Path.of(System.getProperty("user.home"), ".thesoundboard", "gstreamer",
                "registry-" + System.getProperty("os.arch") + "-"
                        + UUID.nameUUIDFromBytes((root + "\n" + new String(manifest, StandardCharsets.UTF_8))
                        .getBytes(StandardCharsets.UTF_8)) + ".bin");
        try {
            Files.createDirectories(registry.getParent());
            Set<String> sharedLibraries = linux && catalog != null ? preloadSystemLibraries(catalog) : Set.of();
            Path order = root.resolve("native-load-order.txt");
            if (Files.isRegularFile(order)) {
                for (String entry : Files.readAllLines(order)) {
                    if (entry.isBlank()) continue;
                    Path library = root.resolve(entry).normalize();
                    if (!library.startsWith(root) || !Files.isRegularFile(library)) {
                        throw new IllegalStateException("Invalid GStreamer native manifest entry: " + entry);
                    }
                    if (sharedGlib && SHARED_GLIB.contains(library.getFileName().toString())) continue;
                    if (sharedLibraries.contains(library.getFileName().toString())) continue;
                    LIBRARIES.add(NativeLibrary.getInstance(library.toString()));
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot prepare GStreamer runtime", exception);
        }
        GLib.setEnv("GST_PLUGIN_SYSTEM_PATH_1_0", manifest.length == 0 ? plugins.toString() : "", true);
        GLib.setEnv("GST_PLUGIN_PATH_1_0", "", true);
        GLib.setEnv("GST_REGISTRY_1_0", registry.toString(), true);
        GLib.setEnv("GST_REGISTRY_FORK", "no", true);
        Gst.init("TheSoundboard");
        initialized = true;
        if (manifest.length > 0) {
            try {
                loadManifestPlugins(root, catalog);
            } catch (RuntimeException | Error exception) {
                try { shutdown(); } catch (RuntimeException failure) { exception.addSuppressed(failure); }
                throw new IllegalStateException("Cannot initialize the bundled GStreamer plugins", exception);
            }
        }
    }

    private static void loadManifestPlugins(Path root, JsonNode manifest) {
        int count = 0;
        for (JsonNode item : manifest.path("files")) {
            String filename = item.path("file").asText();
            if (!filename.startsWith("lib/gstreamer-1.0/")) continue;
            Path path = root.resolve(filename).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) {
                throw new IllegalStateException("Invalid GStreamer plugin manifest entry: " + filename);
            }
            Pointer pointer = PluginLoader.API.gst_plugin_load_file(path.toString(), null);
            if (pointer == null) throw new IllegalStateException("Cannot load GStreamer plugin: " + filename);
            // The registry retains its own reference. Release only the caller-owned load result.
            Natives.callerOwnsReturn(pointer, Plugin.class).dispose();
            count++;
        }
        if (count == 0) throw new IllegalStateException("GStreamer manifest contains no audio plugins");
    }

    private static Path systemGlibDirectory(int minimumMinor) {
        for (Path directory : List.of(Path.of("/usr/lib/x86_64-linux-gnu"), Path.of("/lib/x86_64-linux-gnu"),
                Path.of("/usr/lib64"), Path.of("/lib64"), Path.of("/usr/lib"), Path.of("/lib"))) {
            Path library = directory.resolve("libglib-2.0.so.0");
            if (!Files.isRegularFile(library)) continue;
            try {
                Path actual = library.toRealPath();
                Matcher version = Pattern.compile("libglib-2\\.0\\.so\\.0\\.(\\d+)\\.\\d+")
                        .matcher(actual.getFileName().toString());
                if (!version.matches()) throw new IllegalStateException("Cannot identify system GLib version: " + actual);
                return Integer.parseInt(version.group(1)) / 100 >= minimumMinor ? actual.getParent() : null;
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot inspect system GLib", exception);
            }
        }
        throw new IllegalStateException("Cannot locate the system GLib used by JavaFX GTK");
    }

    private static Set<String> preloadSystemLibraries(JsonNode catalog) {
        Set<String> loaded = new HashSet<>();
        for (JsonNode entry : catalog.path("sharedLibraries")) {
            String filename = entry.asText();
            if (!filename.equals(Path.of(filename).getFileName().toString())) {
                throw new IllegalStateException("Invalid system library name: " + filename);
            }
            for (Path directory : List.of(Path.of("/usr/lib/x86_64-linux-gnu"), Path.of("/lib/x86_64-linux-gnu"),
                    Path.of("/usr/lib64"), Path.of("/lib64"), Path.of("/usr/lib"), Path.of("/lib"))) {
                Path library = directory.resolve(filename);
                if (!Files.isRegularFile(library)) continue;
                LIBRARIES.add(NativeLibrary.getInstance(library.toString()));
                loaded.add(filename);
                break;
            }
        }
        return loaded;
    }

    private static String sharedGlibPrefix(Path directory) {
        try {
            Path cache = Path.of(System.getProperty("user.home"), ".thesoundboard", "gstreamer");
            Files.createDirectories(cache);
            loaderAliases = Files.createTempDirectory(cache, "glib-loader-");
            loaderAliases.toFile().deleteOnExit();
            for (String filename : SHARED_GLIB) {
                String name = filename.substring(3, filename.length() - 5);
                NativeLibrary library = NativeLibrary.getInstance(directory == null ? name : directory.resolve(filename).toString());
                LIBRARIES.add(library);
                Path source = library.getFile().toPath().toAbsolutePath();
                if (!Files.isRegularFile(source)) throw new IllegalStateException("Cannot locate system GLib: " + name);
                Path alias = loaderAliases.resolve(filename);
                Files.createSymbolicLink(alias, source);
                alias.toFile().deleteOnExit();
            }
            return loaderAliases.toString().replace("%", "%%") + "/";
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot configure shared GTK/GLib libraries", exception);
        }
    }

    private static Path packagedRuntime() {
        try {
            return Path.of(GstreamerRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath().getParent().resolve("gstreamer");
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Cannot locate the bundled GStreamer runtime", exception);
        }
    }

    /** Deinitializes only after playback disposal and waveform termination. Libraries stay mapped until process exit:
     * binding classes and delayed wrapper cleanup retain native function pointers even after Gst.deinit(). Explicit
     * dlclose would invalidate them and, on Linux, could also interfere with the GLib still used by JavaFX GTK. */
    public static synchronized void shutdown() {
        if (!initialized) return;
        Gst.deinit();
        initialized = false;
        closed = true;
        if (loaderAliases != null) {
            try {
                for (String filename : SHARED_GLIB) Files.deleteIfExists(loaderAliases.resolve(filename));
                Files.deleteIfExists(loaderAliases);
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot remove the GLib loader aliases", exception);
            }
        }
    }

    private interface WindowsLoader extends Library {
        boolean SetDllDirectoryW(WString directory);
    }

    private interface PluginLoader extends Library {
        PluginLoader API = GstNative.load(PluginLoader.class);

        Pointer gst_plugin_load_file(String filename, Pointer error);
    }
}
