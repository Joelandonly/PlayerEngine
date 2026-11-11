package com.player2.playerengine.agentic;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.containeraccess.ScanReportFormatter;
import com.player2.playerengine.containeraccess.StorageItemArgs;
import com.player2.playerengine.observatory.ObservatoryTelemetry;
import com.player2.playerengine.schematic.SchematicProjectTask;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.container.BoundedContainerTransferTask;
import com.player2.playerengine.util.ItemTarget;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Optional, non-destructive pressure relief *inside* a live resource task.
 * Uses only an explicitly registered chest. Never drops items or changes a parent goal.
 */
public final class AutoInventoryCleanup {
    private AutoInventoryCleanup() {}
    private static final int START_WITH_FREE_SLOTS = 3;
    private static final long COOLDOWN_TICKS = 120;
    private static final Map<Task, Session> SESSIONS = new WeakHashMap<>();
    /** Conservative by design: no sticks, planks, string, wool, food, tools, armor, or schematic materials. */
    private static final Set<String> SURPLUS = Set.of(
        "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:gravel", "minecraft:granite",
        "minecraft:diorite", "minecraft:andesite", "minecraft:cobblestone", "minecraft:cobbled_deepslate",
        "minecraft:deepslate", "minecraft:tuff", "minecraft:stone", "minecraft:netherrack",
        "minecraft:basalt", "minecraft:blackstone", "minecraft:end_stone", "minecraft:sand",
        "minecraft:red_sand", "minecraft:rotten_flesh", "minecraft:spider_eye",
        "minecraft:bone", "minecraft:arrow"
    );
    private static final class Session {
        BoundedContainerTransferTask transfer;
        BlockPos destination;
        int freeBefore;
        long nextAttempt;
        String lastPressureReason = "";
    }

    /** Keep an already-running transfer alive through its container-close/verification phase.
     * Important for schematic preparation: free slots may appear before the transfer task finishes. */
    public static Task continuePending(Task root) {
        Session session = SESSIONS.get(root);
        return session == null || session.transfer == null ? null : choose(root, null);
    }

    /** Called from ResourceTask's existing single-threaded task-tick boundary. */
    public static Task choose(Task root, Task normalAction) {
        PlayerEngineController mod = root.controller;
        if (mod == null || mod.getWorld() == null || mod.getEntity() == null) return normalAction;
        Session session = SESSIONS.computeIfAbsent(root, unused -> new Session());
        long now = mod.getWorld().getGameTime();
        if (session.transfer != null) {
            if (session.transfer.stopped() && !session.transfer.isFinished()) {
                session.transfer = null;
                session.nextAttempt = now + COOLDOWN_TICKS;
                diagnostic(session, "Cleanup transfer was interrupted; original goal retained");
                return normalAction;
            }
            if (!session.transfer.isFinished()) {
                Optional<BlockPos> stillAllowed = InventoryCleanupStorage.destination(mod);
                if (stillAllowed.isEmpty() || !stillAllowed.get().equals(session.destination)) {
                    session.transfer.stop();
                    session.transfer = null;
                    session.nextAttempt = now + COOLDOWN_TICKS;
                    ObservatoryTelemetry.event("INVENTORY_CLEANUP_BLOCKED", "Storage no longer verified; original gathering task continues");
                    return normalAction;
                }
                return session.transfer;
            }
            int freeAfter = InventorySpace.inspect(mod).free();
            String result = String.valueOf(session.transfer.outcome());
            ObservatoryTelemetry.event("INVENTORY_CLEANUP_RESULT",
                "status=" + result + " freeBefore=" + session.freeBefore + " freeAfter=" + freeAfter + " destination=" + session.destination);
            session.transfer = null;
            // A schematic can require four free slots; one successful transfer may only free one.
            boolean schematicNeedsMore = root instanceof SchematicProjectTask && freeAfter >= 0 && freeAfter < 4 && freeAfter > session.freeBefore;
            session.nextAttempt = schematicNeedsMore ? now : now + (freeAfter > session.freeBefore ? 30 : COOLDOWN_TICKS * 5);
            if (freeAfter <= session.freeBefore)
                diagnostic(session, "Cleanup made no inventory space (outcome=" + result + "); check designated chest capacity");
            return normalAction;
        }
        if (root.isFinished()) return normalAction;
        InventorySpace.Snapshot snapshot = InventorySpace.inspect(mod);
        if (!snapshot.known() || snapshot.free() > START_WITH_FREE_SLOTS || session.nextAttempt > now) return normalAction;
        session.nextAttempt = now + COOLDOWN_TICKS;

        Optional<BlockPos> destination = InventoryCleanupStorage.destination(mod);
        if (destination.isEmpty()) {
            diagnostic(session, "No separate verified cleanup chest; inventory " + snapshot.summary());
            return normalAction;
        }
        if (mod.getEntity().distanceToSqr(destination.get().getX() + .5, destination.get().getY() + .5, destination.get().getZ() + .5) > 115.0 * 115.0) {
            diagnostic(session, "Configured cleanup chest is over 115 blocks away; inventory " + snapshot.summary());
            return normalAction;
        }
        Candidate candidate = selectSurplus(root);
        if (candidate == null) {
            diagnostic(session, "No surplus eligible for unloading; preserving target materials and gear; " + snapshot.summary());
            return normalAction;
        }
        session.lastPressureReason = "";
        session.freeBefore = snapshot.free();
        session.destination = destination.get();
        session.transfer = new BoundedContainerTransferTask(
            ScanReportFormatter.TransferDirection.DEPOSIT, destination.get(),
            List.of(new StorageItemArgs.ItemQuery(candidate.item, candidate.id, candidate.amount)));
        ObservatoryTelemetry.event("INVENTORY_CLEANUP_STARTED",
            "task=" + root.getClass().getSimpleName() + " unload=" + candidate.id + " x" + candidate.amount
                + " destination=" + destination.get().toShortString() + " freeSlots=" + snapshot.free());
        return session.transfer;
    }

    private static void diagnostic(Session session, String reason) {
        if (!reason.equals(session.lastPressureReason)) {
            session.lastPressureReason = reason;
            ObservatoryTelemetry.event("INVENTORY_CLEANUP_BLOCKED", reason);
        }
    }

    private record Candidate(Item item, String id, int amount) {}
    private static Candidate selectSurplus(Task root) {
        PlayerEngineController mod = root.controller;
        Map<Item, Integer> amounts = new HashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mod.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (!SURPLUS.contains(id) || SchematicProjectTask.isProjectMaterial(mod, id)) continue;
            if (root instanceof SchematicProjectTask project && project.reservesMaterial(id)) continue;
            boolean requiredByTask = false;
            if (root instanceof ResourceTask resource) {
                for (ItemTarget target : resource.getItemTargets()) {
                    if (target.matches(item)) { requiredByTask = true; break; }
                }
            }
            if (!requiredByTask) amounts.merge(item, stack.getCount(), Integer::sum);
        }
        Candidate best = null;
        for (var entry : amounts.entrySet()) {
            int count = Math.min(entry.getValue(), StorageItemArgs.MAX_COUNT);
            String id = BuiltInRegistries.ITEM.getKey(entry.getKey()).toString();
            if (best == null || count > best.amount) best = new Candidate(entry.getKey(), id, count);
        }
        return best;
    }
}
