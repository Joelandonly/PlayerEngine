package com.player2.playerengine.agentic;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.containeraccess.ContainerResolver;
import com.player2.playerengine.schematic.SchematicDepotGuard;
import com.player2.playerengine.schematic.SchematicDepotJournalGuard;
import com.player2.playerengine.schematic.SchematicProjectTask;
import com.player2.playerengine.tasks.deferred.DeferredJobStore;
import com.player2.playerengine.tasks.setup.CoreGoalJournal;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;

/** A separately DESIGNATED general-purpose chest, never a schematic depot.
 * Stored per world and companion. No fallback to arbitrary nearby chests.
 */
public final class InventoryCleanupStorage {
    private static final String JOURNAL_KEY = "inventory_cleanup_storage_v1";
    private static final Map<Object, GuardTick> GUARD_CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private record GuardTick(long tick, Path root, SchematicDepotJournalGuard.Index index) {}
    private InventoryCleanupStorage() {}

    private static Path root() {
        DeferredJobStore store = DeferredJobStore.get();
        return store == null ? null : store.worldRoot();
    }

    private static String dimension(PlayerEngineController mod) {
        return mod.getWorld().dimension().location().toString();
    }

    private static CoreGoalJournal journal(PlayerEngineController mod) {
        Path path = root();
        if (path == null || mod.getAIPersistantData() == null) return CoreGoalJournal.transientJournal();
        return CoreGoalJournal.open(path, String.valueOf(mod.getAIPersistantData().getCharacterId()), JOURNAL_KEY);
    }

    public static boolean isProjectStorage(PlayerEngineController mod, BlockPos pos) {
        if (mod == null || pos == null || mod.getWorld() == null) return true;
        Path path = root();
        // Scan the journal once per server tick, not once per waypoint/deposit task.
        Object world = mod.getWorld();
        long tick = mod.getWorld().getGameTime();
        SchematicDepotJournalGuard.Index journalIndex;
        synchronized (GUARD_CACHE) {
            GuardTick cached = GUARD_CACHE.get(world);
            if (cached == null || cached.tick != tick || !java.util.Objects.equals(cached.root, path)) {
                cached = new GuardTick(tick, path, SchematicDepotJournalGuard.scan(path));
                GUARD_CACHE.put(world, cached);
            }
            journalIndex = cached.index;
        }
        if (journalIndex.isReserved(dimension(mod), pos.getX(), pos.getY(), pos.getZ())) return true;
        return SchematicProjectTask.reservedSource(mod, pos)
            || SchematicDepotGuard.protects(mod, pos)
            || SchematicProjectTask.insideProject(mod, pos);
    }

    private static boolean safeContainer(PlayerEngineController mod, BlockPos pos) {
        if (isProjectStorage(mod, pos) || !mod.getWorld().hasChunkAt(pos)) return false;
        ContainerResolver.Resolution found = ContainerResolver.resolve(mod.getWorld(), pos);
        if (!found.ok()) return false;
        BlockPos second = found.resolved().secondaryPos();
        return second == null || !isProjectStorage(mod, second);
    }

    public static String register(PlayerEngineController mod, BlockPos pos) {
        CoreGoalJournal saved = journal(mod);
        if (!saved.isPersistent()) return "Cannot persist cleanup storage: " + saved.error();
        if (!safeContainer(mod, pos)) return "Rejected: unloaded, invalid or schematic-reserved storage (including double-chest half)";
        BlockPos canonical = ContainerResolver.resolve(mod.getWorld(), pos).resolved().canonicalPos();
        if (!safeContainer(mod, canonical)) return "Rejected: canonical storage overlaps a protected project depot";
        saved.put("GENERAL_STORAGE", 0, dimension(mod), canonical.getX() + "," + canonical.getY() + "," + canonical.getZ());
        return saved.error() == null ? "General cleanup storage registered at " + canonical.toShortString() : "Failed saving cleanup storage: " + saved.error();
    }

    public static String disable(PlayerEngineController mod) {
        CoreGoalJournal saved = journal(mod);
        if (!saved.isPersistent()) return "Cannot persist cleanup storage: " + saved.error();
        saved.put("DISABLED", 0, dimension(mod), "");
        return saved.error() == null ? "Automatic inventory unloading disabled" : "Failed disabling cleanup storage: " + saved.error();
    }

    public static Optional<BlockPos> destination(PlayerEngineController mod) {
        CoreGoalJournal saved = journal(mod);
        if (!saved.isPersistent() || !"GENERAL_STORAGE".equals(saved.get("status", "")) || !dimension(mod).equals(saved.get("resource", ""))) return Optional.empty();
        String[] units = saved.get("detail", "").split(",", -1);
        if (units.length != 3) return Optional.empty();
        try {
            BlockPos pos = new BlockPos(Integer.parseInt(units[0]), Integer.parseInt(units[1]), Integer.parseInt(units[2]));
            return safeContainer(mod, pos) ? Optional.of(pos) : Optional.empty();
        } catch (NumberFormatException ex) { return Optional.empty(); }
    }

    public static String status(PlayerEngineController mod) {
        CoreGoalJournal saved = journal(mod);
        if (!saved.isPersistent()) return "Cleanup storage unavailable: " + saved.error();
        if (!"GENERAL_STORAGE".equals(saved.get("status", ""))) return "No general cleanup storage designated";
        String pos = saved.get("detail", "?");
        return destination(mod).isPresent() ? "General cleanup storage: " + pos + " (verified, schematic-safe)" : "General cleanup storage " + pos + " unavailable or project-protected; no automatic deposits permitted";
    }
}
