package com.player2.playerengine.warehouse;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.containeraccess.ContainerResolver;
import com.player2.playerengine.observatory.ObservatoryTelemetry;
import com.player2.playerengine.tasks.container.BoundedContainerTransferTask;
import com.player2.playerengine.util.ItemTarget;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/** Job-local withdrawal state. The warehouse journal only authorizes locations, never stock counts. */
public final class WarehouseWithdrawal {
    private record Candidate(BlockPos pos, Item item, int count, double distance, int priority) {}
    private record Attempt(BlockPos pos, Item item) {}

    private final Set<Attempt> unavailable = new HashSet<>();
    private String dimension;
    private Candidate current;
    private BoundedContainerTransferTask transfer;
    private long nextScan;
    private List<ItemTarget> lastTargets = List.of();
    private static final int MAX_TARGET = 4096;

    public static boolean hasStock(PlayerEngineController mod, ItemTarget target) {
        return find(mod, List.of(target), Set.of()) != null;
    }

    public BoundedContainerTransferTask next(PlayerEngineController mod, List<ItemTarget> targets) {
        BoundedContainerTransferTask pending = this.pending(mod);
        if (pending != null) return pending;
        long now = mod.getWorld().getGameTime();
        if (now < this.nextScan && targets.equals(this.lastTargets)) return null;
        this.lastTargets = List.copyOf(targets);
        this.current = find(mod, targets, this.unavailable);
        if (this.current == null) {
            this.nextScan = now + 80;
            return null;
        }
        this.nextScan = 0;
        this.transfer = BoundedContainerTransferTask.withdrawUpTo(this.current.pos(), this.current.item(), this.current.count());
        ObservatoryTelemetry.event("WAREHOUSE_WITHDRAW_SELECTED", BuiltInRegistries.ITEM.getKey(this.current.item())
            + " x" + this.current.count() + " from " + this.dimension + " " + this.current.pos().toShortString());
        return this.transfer;
    }

    public BoundedContainerTransferTask pending(PlayerEngineController mod) {
        String here = WarehouseManager.dimension(mod);
        if (!here.equals(this.dimension)) {
            this.close();
            this.dimension = here;
            this.unavailable.clear();
            this.nextScan = 0;
        }

        if (this.transfer != null) {
            if (authorized(mod, this.current.pos()) && !this.transfer.stopped() && !this.transfer.isFinished()) {
                return this.transfer;
            }
            if (!this.transfer.isFinished() || this.transfer.entryOutcomes().stream().noneMatch(outcome -> outcome.moved() > 0)) {
                this.unavailable.add(new Attempt(this.current.pos(), this.current.item()));
            }
            this.close();
        }

        return null;
    }

    public void close() {
        if (this.transfer != null && !this.transfer.isFinished()) this.transfer.stop();
        this.transfer = null;
        this.current = null;
    }

    private static boolean authorized(PlayerEngineController mod, BlockPos pos) {
        try {
            return WarehouseManager.authorized(mod, WarehouseManager.ledger(mod), pos);
        } catch (IOException failure) {
            return false;
        }
    }

    private static Candidate find(PlayerEngineController mod, List<ItemTarget> targets, Set<Attempt> unavailable) {
        if (targets.isEmpty() || mod.getInventory() == null) return null;
        int[] missing = new int[targets.size()];
        boolean needed = false;
        for (int i = 0; i < targets.size(); i++) {
            ItemTarget target = targets.get(i);
            if (ItemTarget.nullOrEmpty(target) || target.getTargetCount() > MAX_TARGET) continue;
            missing[i] = target.getTargetCount() - mod.getItemStorage().getItemCountInventoryOnly(target.getMatches());
            needed |= missing[i] > 0;
        }
        if (!needed) return null;
        try {
            WarehouseLedger ledger = WarehouseManager.ledger(mod);
            if (WarehouseManager.unavailable(mod, ledger) != null) return null;
            Candidate best = null;
            for (var pair : WarehouseLayout.chests(ledger.number("requested", 4))) {
                BlockPos planned = WarehouseManager.at(ledger, pair.first());
                if (!mod.getWorld().hasChunkAt(planned)) continue;
                var resolution = ContainerResolver.resolve(mod.getWorld(), planned);
                if (!resolution.ok()) continue;
                var resolved = resolution.resolved();
                BlockPos pos = resolved.canonicalPos();
                double distance = mod.getEntity().distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
                if (distance > 115 * 115 || !WarehouseManager.authorized(mod, ledger, pos)) continue;

                for (int priority = 0; priority < targets.size(); priority++) {
                    if (missing[priority] <= 0) continue;
                    for (Item item : targets.get(priority).getMatches()) {
                        if (unavailable.contains(new Attempt(pos, item))) continue;
                        int count = Math.min(missing[priority], resolved.container().countItem(item));
                        count = Math.min(3456, Math.min(count, capacity(mod, item)));
                        if (count > 0 && (best == null || priority < best.priority()
                                || priority == best.priority() && (count > best.count()
                                || count == best.count() && distance < best.distance()))) {
                            best = new Candidate(pos, item, count, distance, priority);
                        }
                    }
                }
            }
            return best;
        } catch (IOException failure) {
            return null;
        }
    }

    private static int capacity(PlayerEngineController mod, Item item) {
        int room = 0;
        var inventory = mod.getInventory();
        int stackLimit = Math.min(inventory.getMaxStackSize(), item.getDefaultInstance().getMaxStackSize());
        for (var stack : inventory.main) {
            if (stack.isEmpty()) room += stackLimit;
            else if (stack.is(item)) room += Math.max(0, Math.min(inventory.getMaxStackSize(), stack.getMaxStackSize()) - stack.getCount());
        }
        return room;
    }
}
