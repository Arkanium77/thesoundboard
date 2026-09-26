package app.packages;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Shares deterministic source traversal without imposing installation policy. Configured directory order and sorted
 * filenames define priority. Each visitor owns manifest validation and replacement decisions: skin synchronization
 * may replace a changed cache, whereas localization discovery must preserve installed edits. A broken archive or
 * unreadable directory cannot hide other sources. Single-directory callers can avoid merging an incomplete scan.
 */
public final class PackageDiscovery {
    private PackageDiscovery() { }

    public static void visit(List<Path> directories, Predicate<Path> accepts, Visitor visitor) {
        for (Path directory : directories) visit(directory, accepts, visitor);
    }

    public static boolean visit(Path directory, Predicate<Path> accepts, Visitor visitor) {
        if (!Files.isDirectory(directory)) return false;
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile).filter(accepts).sorted().toList()) {
                try { visitor.visit(file); }
                catch (IOException | IllegalArgumentException exception) {
                    // The remaining sources must still be discovered when one archive is invalid.
                }
            }
            return true;
        } catch (IOException | SecurityException exception) { return false; }
    }

    public static void addSource(Map<String, List<Path>> sources, PackageIdentity identity, Path source) {
        sources.computeIfAbsent(identity.storageName(), ignored -> new ArrayList<>()).add(source);
    }

    @FunctionalInterface
    public interface Visitor {
        void visit(Path source) throws IOException;
    }
}
