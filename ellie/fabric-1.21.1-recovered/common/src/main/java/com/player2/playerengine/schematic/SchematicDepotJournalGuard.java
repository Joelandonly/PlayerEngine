package com.player2.playerengine.schematic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Read-only, persistent depot protection. No active schematic task is required.
 * A failed/corrupt journal read blocks all automatic cleanup instead of permitting data loss.
 */
public final class SchematicDepotJournalGuard {
    private SchematicDepotJournalGuard() {}
    private record Pos(int x, int y, int z) {}
    /** Immutable per-scan snapshot. A single scan can serve many position queries in one server tick. */
    public static final class Index {
        private final Map<String, List<Pos>> depots;
        private final boolean uncertain;
        private Index(Map<String, List<Pos>> depots, boolean uncertain) {
            this.depots = Map.copyOf(depots);
            this.uncertain = uncertain;
        }
        public boolean isReserved(String dimension, int x, int y, int z) {
            if (uncertain || dimension == null || dimension.isBlank()) return true;
            for (Pos p : depots.getOrDefault(dimension, List.of())) {
                // Include a horizontally adjacent double-chest half.
                if (p.y == y && Math.abs((long)p.x - x) + Math.abs((long)p.z - z) <= 1L) return true;
            }
            return false;
        }
        public boolean uncertain() { return uncertain; }
    }
    public static Index scan(Path worldRoot) {
        if (worldRoot == null) return new Index(Map.of(), true);
        Path dir = worldRoot.resolve("ellie-core-goals");
        if (!Files.isDirectory(dir)) return new Index(Map.of(), Files.exists(dir));
        Map<String, List<Pos>> depots = new HashMap<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir, "goal-*.properties")) {
            for (Path file : entries) {
                Properties p = new Properties();
                try (InputStream stream = Files.newInputStream(file)) { p.load(stream); }
                if (!"SCHEMATIC_DEPOT".equals(p.getProperty("status"))) continue;
                String dimension = p.getProperty("resource");
                String[] values = p.getProperty("detail", "").split(",", -1);
                if (dimension == null || dimension.isBlank() || values.length != 3) return new Index(Map.of(), true);
                Pos pos = new Pos(Integer.parseInt(values[0].trim()), Integer.parseInt(values[1].trim()), Integer.parseInt(values[2].trim()));
                depots.computeIfAbsent(dimension, ignored -> new ArrayList<>()).add(pos);
            }
            Map<String, List<Pos>> frozen = new HashMap<>();
            depots.forEach((key, value) -> frozen.put(key, List.copyOf(value)));
            return new Index(frozen, false);
        } catch (IOException | SecurityException | IllegalArgumentException failure) {
            return new Index(Map.of(), true);
        }
    }
    public static boolean isReserved(Path worldRoot, String dimension, int x, int y, int z) {
        return scan(worldRoot).isReserved(dimension, x, y, z);
    }
}
