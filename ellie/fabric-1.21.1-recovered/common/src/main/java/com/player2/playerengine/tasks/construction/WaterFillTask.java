package com.player2.playerengine.tasks.construction;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.observatory.ObservatoryTelemetry;
import com.player2.playerengine.safezone.SafeZoneService;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.base.TrackedTaskOutcomeProvider;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Physical, finite water-source replacement. Prefers bottom-up, shore-accessible
 * placements so higher source blocks do not hide lower targets. As placed blocks
 * become dry footholds, previously blocked targets can become candidates again.
 * Does not cheat with setBlock/teleport or consume unspecified inventories.
 *
 * Deep/submerged cells may remain inaccessible; the command reports PARTIAL
 * rather than using unbounded movement retries or claiming every cell was filled.
 */
public final class WaterFillTask extends Task implements TrackedTaskOutcomeProvider {
    private static final int MAX_VOLUME = 8192;
    private static final int MAX_SOURCE_BLOCKS = 2048;
    private static final int MAX_PLACE_TICKS = 220;
    private static final int MAX_TOTAL_TICKS = 20 * 60 * 12;
    // WaterFill alone may use six-block interaction reach. Baritone and its
    // custom NPC interaction manager share this scoped value; ordinary Ellie
    // tasks keep the original 4.5-block reach.
    private static final double WATERFILL_REACH = 6.0;
    private static final Set<UUID> EXTENDED_REACH_NPCS = ConcurrentHashMap.newKeySet();

    public static boolean hasExtendedReach(UUID npcUuid) {
        return npcUuid != null && EXTENDED_REACH_NPCS.contains(npcUuid);
    }
    private final UUID owner;
    private final BlockPos center;
    private final int radius;
    private final int height;
    private final Block material;
    private final List<BlockPos> remaining = new ArrayList<>();
    // A failed placement is kept in remaining. Reconsider it only after nearby
    // terrain actually changes, not merely because onTick() ran again.
    private final Set<BlockPos> deferred = new HashSet<>();
    private BlockPos target;
    private PlaceBlockTask placing;
    private ServerLevel originalWorld;
    private int ticks;
    private int placementTicks;
    private int initialSources;
    private int completed;
    private int protectedSources;
    private int unreachable;
    private int stalledAttempts;
    private int changedOutside;
    private boolean terminal;
    private boolean successful;
    private String reason = "Not started";

    public WaterFillTask(UUID owner, BlockPos center, int radius, int height, Block material) {
        if (owner == null || center == null || material == null) throw new IllegalArgumentException("Missing waterfill context");
        if (radius < 0 || radius > 16 || height < 0 || height > 8) throw new IllegalArgumentException("Region too large");
        this.owner = owner;
        this.center = center.immutable();
        this.radius = radius;
        this.height = height;
        this.material = material;
        long volume = (2L * radius + 1) * (2L * radius + 1) * (2L * height + 1);
        if (volume > MAX_VOLUME) throw new IllegalArgumentException("Region exceeds " + MAX_VOLUME + " blocks");
    }

    @Override
    protected void onStart() {
        if (originalWorld != null) return; // Safe on routine resumption, do not rescan and lose receipts.
        originalWorld = controller.getWorld();
        if (originalWorld == null || !ownerHere()) {
            finish(false, "Ellie/owner unavailable or no longer in the same dimension");
            return;
        }
        for (int y = center.getY() + height; y >= center.getY() - height; --y) {
            for (int x = center.getX() - radius; x <= center.getX() + radius; ++x) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; ++z) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!originalWorld.hasChunkAt(pos)) {
                        finish(false, "Unloaded chunk in selected region at " + pos.toShortString() + "; no terrain changed");
                        return;
                    }
                    if (!sourceAt(pos)) continue;
                    ++initialSources;
                    if (initialSources > MAX_SOURCE_BLOCKS) {
                        finish(false, "More than " + MAX_SOURCE_BLOCKS + " water sources. Use smaller regions; no terrain changed");
                        return;
                    }
                    if (SafeZoneService.protectedAt(originalWorld, pos)) ++protectedSources;
                    else remaining.add(pos);
                }
            }
        }
        if (remaining.isEmpty()) {
            finish(protectedSources == 0, protectedSources == 0 ? "No water sources in this region" : "Only protected water sources were found; none edited");
            return;
        }
        EXTENDED_REACH_NPCS.add(controller.getEntity().getUUID());
        event("WATERFILL_START", "sources=" + initialSources + " protected=" + protectedSources
                + " material=" + material + " temporary-reach=" + WATERFILL_REACH);
    }

    private boolean ownerHere() {
        if (controller == null || controller.getWorld() != originalWorld || originalWorld == null) return false;
        ServerPlayer player = originalWorld.getServer().getPlayerList().getPlayer(owner);
        return player != null && player.level() == originalWorld;
    }

    private boolean sourceAt(BlockPos pos) {
        return originalWorld.getBlockState(pos).is(Blocks.WATER)
                && originalWorld.getFluidState(pos).isSource();
    }

    private boolean adjacentSupport(BlockPos pos) {
        // Place blocks against an existing dry shore or an already constructed tile.
        // No assumptions that the bot can stand on liquid or use impossible clicks.
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(dir);
            if (!originalWorld.hasChunkAt(side)) continue;
            if (originalWorld.getBlockState(side).isSolidRender(originalWorld, side)) return true;
        }
        BlockPos below = pos.below();
        return originalWorld.hasChunkAt(below) && originalWorld.getBlockState(below).isSolidRender(originalWorld, below);
    }

    /**
     * Check a real solid support face instead of guessing from a nearby shore.
     * The ray uses COLLIDER + Fluid.NONE: water itself is transparent to
     * placement aiming, but rocks, soil and completed blocks block the click.
     * A ray to the water's center alone is insufficient: building must click
     * a face on a *neighbouring solid support*.
     */
    private boolean visibleSupportFace(BlockPos water, Vec3 eye) {
        for (Direction towardSupport : Direction.values()) {
            if (towardSupport == Direction.UP) continue;
            BlockPos support = water.relative(towardSupport);
            if (!originalWorld.hasChunkAt(support)
                    || !originalWorld.getBlockState(support).isSolidRender(originalWorld, support)) continue;
            Vec3 face = Vec3.atCenterOf(support).subtract(
                    towardSupport.getStepX() * 0.49,
                    towardSupport.getStepY() * 0.49,
                    towardSupport.getStepZ() * 0.49);
            if (eye.distanceToSqr(face) > WATERFILL_REACH * WATERFILL_REACH) continue;
            BlockHitResult hit = originalWorld.clip(new ClipContext(
                    eye, face, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, controller.getEntity()));
            if (hit.getType() == HitResult.Type.BLOCK
                    && support.equals(hit.getBlockPos())
                    && hit.getDirection() == towardSupport.getOpposite()) return true;
        }
        return false;
    }

    private boolean isDryStandingCell(BlockPos feet) {
        BlockPos head = feet.above();
        BlockPos ground = feet.below();
        return originalWorld.hasChunkAt(feet) && originalWorld.hasChunkAt(head)
                && originalWorld.hasChunkAt(ground)
                && originalWorld.getBlockState(feet).isAir()
                && originalWorld.getBlockState(head).isAir()
                && originalWorld.getBlockState(ground).isSolidRender(originalWorld, ground);
    }

    /**
     * A dry foothold on the *opposite bank* is not a valid construction point
     * until Ellie can walk there. Flood-fill grounded two-high-air positions
     * reachable from her current feet. Rebuilt after every block placement.
     * This is deliberately conservative about jumps and swimming.
     */
    private Set<BlockPos> reachableDryFeet(BlockPos start) {
        Set<BlockPos> reachable = new HashSet<>();
        if (!isDryStandingCell(start)) return reachable;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        reachable.add(start);
        while (!queue.isEmpty() && reachable.size() < 1600) {
            BlockPos from = queue.removeFirst();
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                for (int climb = -1; climb <= 1; ++climb) {
                    BlockPos to = from.relative(dir).offset(0, climb, 0);
                    if (reachable.contains(to)
                            || Math.abs(to.getX() - start.getX()) > 20
                            || Math.abs(to.getZ() - start.getZ()) > 20
                            || Math.abs(to.getY() - start.getY()) > 7
                            || !isDryStandingCell(to)) continue;
                    reachable.add(to);
                    queue.addLast(to);
                }
            }
        }
        return reachable;
    }

    /**
     * Return Infinity unless a grounded, dry standing position connected to
     * Ellie can actually ray-click a solid support face within six blocks.
     * A possible Baritone route is still subject to its real navigation rules.
     */
    private double dryApproachScore(BlockPos water, BlockPos ellieFeet, Set<BlockPos> dryFeet) {
        double best = Double.POSITIVE_INFINITY;
        double eyeHeight = controller.getEntity().getEyeHeight();
        for (BlockPos feet : dryFeet) {
            if (Math.abs(feet.getX() - water.getX()) > 6
                    || Math.abs(feet.getZ() - water.getZ()) > 6
                    || Math.abs(feet.getY() - water.getY()) > 6) continue;
            Vec3 eye = new Vec3(feet.getX() + 0.5, feet.getY() + eyeHeight, feet.getZ() + 0.5);
            double dist = eye.distanceToSqr(Vec3.atCenterOf(water));
            if (dist > 49.0) continue;
            double score = dist + 0.03 * ellieFeet.distSqr(feet);
            if (score < best && visibleSupportFace(water, eye)) best = score;
        }
        return best;
    }

    private BlockPos selectNext() {
        BlockPos best = null;
        double bestShore = Double.POSITIVE_INFINITY;
        double bestDistance = Double.POSITIVE_INFINITY;
        BlockPos ellieFeet = controller.getEntity().blockPosition();
        Set<BlockPos> dryFeet = reachableDryFeet(ellieFeet);
        if (dryFeet.isEmpty()) return null;
        for (BlockPos pos : remaining) {
            if (deferred.contains(pos) || !adjacentSupport(pos)) continue;
            double shore = dryApproachScore(pos, ellieFeet, dryFeet);
            // Never try a target only visible from the far side of a pond,
            // or an underwater cell with no viable solid-face line-of-sight.
            if (!Double.isFinite(shore)) continue;
            double distance = ellieFeet.distSqr(pos);
            if (best == null || pos.getY() < best.getY()
                    || (pos.getY() == best.getY()
                    && (Double.compare(shore, bestShore) < 0
                    || (Double.compare(shore, bestShore) == 0 && distance < bestDistance)))) {
                best = pos;
                bestShore = shore;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Wake blocked targets only after construction or another real terrain edit
     * near them. Distant progress does not waste another 220 ticks on the same
     * inaccessible cell; adjacent new blocks can unlock an approach/LOS. */
    private void reconsiderNear(BlockPos changed) {
        for (Iterator<BlockPos> it = deferred.iterator(); it.hasNext();) {
            BlockPos pos = it.next();
            if (Math.abs(pos.getX() - changed.getX()) <= 4
                    && Math.abs(pos.getZ() - changed.getZ()) <= 4
                    && Math.abs(pos.getY() - changed.getY()) <= 4) {
                it.remove();
            }
        }
    }

    @Override
    protected Task onTick() {
        if (terminal) return null;
        if (!ownerHere()) { finish(false, "Owner/Ellie dimension changed, stopped safely"); return null; }
        if (++ticks > MAX_TOTAL_TICKS) { finish(false, "12 minute bounded operation timeout"); return null; }
        // Never perform remote world edits or teleport. Each successful cell must
        // be verified as physically placed by Ellie's existing builder task.
        if (target != null) {
            if (originalWorld.getBlockState(target).is(material)) {
                ++completed;
                remaining.remove(target);
                deferred.remove(target);
                reconsiderNear(target);
                event("WATERFILL_PLACED", "count=" + completed + "/" + initialSources + " at=" + target.toShortString());
                target = null;
                placing = null;
                placementTicks = 0;
            } else if (!sourceAt(target)) {
                ++changedOutside;
                remaining.remove(target);
                deferred.remove(target);
                reconsiderNear(target);
                target = null;
                placing = null;
                placementTicks = 0;
            } else if (SafeZoneService.protectedAt(originalWorld, target)) {
                ++protectedSources;
                remaining.remove(target);
                deferred.remove(target);
                target = null;
                placing = null;
                placementTicks = 0;
            } else if (placing.stopped() || ++placementTicks > MAX_PLACE_TICKS) {
                // Preserve the source; nearby successful placement may open a
                // viable approach later. Never retry unchanged terrain forever.
                ++stalledAttempts;
                deferred.add(target);
                event("WATERFILL_DEFER", "placement stalled at " + target.toShortString());
                target = null;
                placing = null;
                placementTicks = 0;
            } else {
                setDebugState("Filling source " + target.toShortString() + " (" + completed + "/" + initialSources + ")");
                return placing;
            }
        }
        for (Iterator<BlockPos> it = remaining.iterator(); it.hasNext();) {
            BlockPos pos = it.next();
            if (!sourceAt(pos)) {
                ++changedOutside;
                it.remove();
                deferred.remove(pos);
                reconsiderNear(pos);
            } else if (SafeZoneService.protectedAt(originalWorld, pos)) {
                ++protectedSources;
                it.remove();
                deferred.remove(pos);
            }
        }
        if (remaining.isEmpty()) { finish(protectedSources == 0 && unreachable == 0 && changedOutside == 0, "All reachable source cells processed"); return null; }
        int have = controller.getItemStorage().getItemCountInventoryOnly(material.asItem());
        if (have < 1) { finish(false, "Out of " + material + " in Ellie's inventory"); return null; }
        BlockPos next = selectNext();
        if (next == null) {
            unreachable = remaining.size();
            event("WATERFILL_NO_ACCESS", "remaining=" + remaining.size()
                    + " deferred=" + deferred.size()
                    + " reason=no connected dry standing point with unobstructed support-face click or remaining targets deferred");
            finish(false, "No reachable support-face clicks from dry shore; deeper water may need new footing or underwater access");
            return null;
        }
        target = next;
        placementTicks = 0;
        placing = new PlaceBlockTask(next, material);
        setDebugState("Replacing source " + next.toShortString() + " using " + material);
        return placing;
    }

    private void finish(boolean clean, String message) {
        terminal = true;
        successful = clean;
        unreachable = Math.max(unreachable, deferred.size());
        if (controller != null && controller.getEntity() != null) {
            EXTENDED_REACH_NPCS.remove(controller.getEntity().getUUID());
        }
        reason = message + "; source=" + initialSources + " placed=" + completed + " protected=" + protectedSources
                + " unreachable=" + unreachable + " stalled-attempts=" + stalledAttempts
                + " externally-changed=" + changedOutside + " remaining=" + remaining.size();
        event(clean ? "WATERFILL_DONE" : "WATERFILL_PARTIAL", reason);
    }

    private static void event(String kind, String details) {
        try { ObservatoryTelemetry.event(kind, details); }
        catch (RuntimeException | LinkageError ignored) { }
    }

    @Override protected void onStop(Task interruptTask) {
        if (!terminal) finish(false, "INTERRUPTED before verified completion");
    }
    @Override public boolean isFinished() { return terminal; }
    @Override public boolean isTerminal() { return terminal; }
    @Override public boolean isSuccessful() { return terminal && successful; }
    @Override public String controlledReason() { return reason; }
    @Override protected boolean isEqual(Task other) {
        return other instanceof WaterFillTask task && task.owner.equals(owner) && task.center.equals(center)
                && task.radius == radius && task.height == height && task.material == material;
    }
    @Override protected String toDebugString() { return "WaterFill " + center.toShortString() + " r=" + radius + " h=" + height; }
}
