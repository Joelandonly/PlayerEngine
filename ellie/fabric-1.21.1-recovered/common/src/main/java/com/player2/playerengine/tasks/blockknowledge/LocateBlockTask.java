package com.player2.playerengine.tasks.blockknowledge;

import com.player2.playerengine.tasks.base.Task;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.server.level.ServerLevel;

/**
 * Server-authoritative, underground-capable scan of already LOADED chunks.
 * Bounded work per server tick; no chunk generation, client x-ray or world mutation.
 * Search is centered at issuance position, independent of where Ellie wanders.
 */
public final class LocateBlockTask extends Task {
    public static final int DEFAULT_RADIUS = 64;
    public static final int MAX_RADIUS = 96;
    private static final int BLOCKS_PER_TICK = 24000;
    private final Block target;
    private final String id;
    private final int radius;
    private ServerLevel world;
    private BlockPos origin;
    private List<ChunkPos> chunks;
    private int chunkIndex;
    private int dx, dz, y;
    private int minY, maxY;
    private long visited, matching, loadedChunks;
    private LevelChunk currentChunk;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private BlockPos best;
    private double bestDist = Double.POSITIVE_INFINITY;
    private boolean completed;
    private String result;

    public LocateBlockTask(Block target, String id, int radius) {
        this.target = target; this.id = id; this.radius = Math.max(1, Math.min(MAX_RADIUS, radius));
    }

    public String result() { return result == null ? "search cancelled" : result; }
    @Override public boolean isFinished() { return completed; }

    @Override protected void onStart() {
        world = controller.getWorld();
        if (world == null || controller.getPlayer() == null) {
            result = "No active world/player";
            completed = true;
            return;
        }
        origin = controller.getPlayer().blockPosition();
        minY = world.getMinBuildHeight();
        maxY = world.getMaxBuildHeight();
        y = minY;
        int minCx = Math.floorDiv(origin.getX() - radius, 16);
        int maxCx = Math.floorDiv(origin.getX() + radius, 16);
        int minCz = Math.floorDiv(origin.getZ() - radius, 16);
        int maxCz = Math.floorDiv(origin.getZ() + radius, 16);
        ArrayList<ChunkPos> queue = new ArrayList<>();
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) queue.add(new ChunkPos(cx,cz));
        }
        queue.sort(Comparator.comparingLong(c -> {
            long cx = 16L * c.x + 8L - origin.getX();
            long cz = 16L * c.z + 8L - origin.getZ();
            return cx*cx + cz*cz;
        }));
        chunks = List.copyOf(queue);
        setDebugState("Scanning loaded chunks for " + id + " (radius " + radius + ")");
    }

    @Override protected Task onTick() {
        if (completed) return null;
        if (world != controller.getWorld()) {
            result = "World changed; search cancelled without loading chunks";
            completed = true;
            return null;
        }
        int budget = BLOCKS_PER_TICK;
        long r2 = (long)radius*radius;
        while (budget > 0 && chunkIndex < chunks.size()) {
            ChunkPos chunkPos = chunks.get(chunkIndex);
            // One loaded-chunk check per chunk, never world-generation by search.
            if (currentChunk == null) {
                if (!world.getChunkSource().hasChunk(chunkPos.x, chunkPos.z)) {
                    nextChunk();
                    continue;
                }
                currentChunk = world.getChunk(chunkPos.x, chunkPos.z);
            }
            // Each 16x16 column has a height range. Read only a bounded number per tick.
            int x = chunkPos.x * 16 + dx;
            int z = chunkPos.z * 16 + dz;
            long xDist = (long)x-origin.getX();
            long zDist = (long)z-origin.getZ();
            if (xDist*xDist + zDist*zDist > r2) {
                nextColumn();
                continue;
            }
            cursor.set(x,y,z);
            if (currentChunk.getBlockState(cursor).getBlock() == target) {
                long dy = (long)y - origin.getY();
                double distance = xDist*xDist+zDist*zDist+dy*dy;
                matching++;
                if (distance < bestDist) {bestDist = distance; best = cursor.immutable();}
            }
            visited++;
            budget--;
            if (++y >= maxY) nextColumn();
        }
        if (chunkIndex >= chunks.size()) {
            String scope = "loaded-chunk scan radius " + radius + ", checked " + visited + " blocks in " + loadedChunks + " chunks";
            if (best != null) result = "Located " + id + " at " + best.getX() + " " + best.getY() + " " + best.getZ()
                     + " (" + Math.round(Math.sqrt(bestDist)) + " blocks away, " + matching + " matches; " + scope + ")";
            else result = "No " + id + " found in " + scope + ". This does NOT prove it is absent from unloaded or unexplored chunks.";
            completed = true;
        } else {
            setDebugState("Scanning " + id + " chunks=" + chunkIndex + "/" + chunks.size() + " blocks=" + visited);
        }
        return null;
    }

    private void nextColumn() {
        y=minY;
        if (++dz == 16) {dz=0; if (++dx == 16) nextChunk();}
    }
    private void nextChunk() {
        if (currentChunk != null) loadedChunks++;
        chunkIndex++;
        dx=dz=0;
        y=minY;
        currentChunk = null;
    }

    @Override protected void onStop(Task task) { if (!completed) result = "Block location search interrupted"; completed = true; }
    @Override protected boolean isEqual(Task task) { return task instanceof LocateBlockTask t && id.equals(t.id) && radius == t.radius; }
    @Override protected String toDebugString() { return "Locate block " + id + " radius=" + radius; }
}
