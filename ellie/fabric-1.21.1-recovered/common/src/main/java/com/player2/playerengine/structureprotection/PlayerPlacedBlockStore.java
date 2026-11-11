package com.player2.playerengine.structureprotection;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.automaton.api.BaritoneAPI;
import com.player2.playerengine.player2api.Player2NpcPersistencePaths;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.server.MinecraftServer;

public final class PlayerPlacedBlockStore {
   private static volatile PlayerPlacedBlockStore INSTANCE;
   private static final int SCHEMA_VERSION = 1;
   private static final String BLOCKS_FILE_NAME = "blocks.json";
   private static final Gson GSON = new GsonBuilder().create();
   private static final int DEFAULT_MAX_BLOCKS = 200000;
   private static final int MIN_MAX_BLOCKS = 1000;
   private static final int MAX_MAX_BLOCKS = 2000000;
   private static final long FLUSH_INTERVAL_SECONDS = 5L;
   private static final long EVICTION_WARN_INTERVAL_MS = 30000L;
   private final Path worldRoot;
   private final ConcurrentHashMap<String, PlayerPlacedBlockStore.DimBuckets> byDimension = new ConcurrentHashMap<>();
   private final ArrayDeque<PlayerPlacedBlockStore.ChunkRef> insertionOrder = new ArrayDeque<>();
   private final AtomicInteger totalBlocks = new AtomicInteger(0);
   private ScheduledExecutorService flusher;
   private long lastEvictionWarnMs = 0L;

   public static PlayerPlacedBlockStore get() {
      return INSTANCE;
   }

   private PlayerPlacedBlockStore(Path worldRoot) {
      this.worldRoot = worldRoot;
   }

   public static PlayerPlacedBlockStore loadForServer(MinecraftServer server) {
      Path worldRoot = server.getWorldPath(LevelResource.ROOT);
      PlayerPlacedBlockStore store = new PlayerPlacedBlockStore(worldRoot);
      store.load();
      store.flusher = Executors.newSingleThreadScheduledExecutor(r -> {
         Thread t = new Thread(r, "PlayerPlacedBlockStore-flush");
         t.setDaemon(true);
         return t;
      });
      store.flusher.scheduleWithFixedDelay(store::flushDirty, 5L, 5L, TimeUnit.SECONDS);
      INSTANCE = store;
      return store;
   }

   public void clear() {
      if (this.flusher != null) {
         this.flusher.shutdown();
         this.flusher = null;
      }

      this.flushDirty();
      this.byDimension.clear();
      this.insertionOrder.clear();
      this.totalBlocks.set(0);
      INSTANCE = null;
      PlayerEngine.LOGGER.info("PlayerPlacedBlockStore: store cleared.");
   }

   public void add(String dimensionId, BlockPos pos) {
      PlayerPlacedBlockStore.DimBuckets b = this.byDimension.computeIfAbsent(dimensionId, k -> new PlayerPlacedBlockStore.DimBuckets());
      long chunkKey = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
      long posKey = pos.asLong();
      LongOpenHashSet old = b.chunks.get(chunkKey);
      if (old == null || !old.contains(posKey)) {
         LongOpenHashSet copy = old == null ? new LongOpenHashSet() : new LongOpenHashSet(old);
         copy.add(posKey);
         b.chunks.put(chunkKey, copy);
         if (old == null) {
            this.insertionOrder.addLast(new PlayerPlacedBlockStore.ChunkRef(dimensionId, chunkKey));
         }

         this.totalBlocks.incrementAndGet();
         b.dirty = true;
         this.enforceCap();
      }
   }

   public boolean remove(String dimensionId, BlockPos pos) {
      PlayerPlacedBlockStore.DimBuckets b = this.byDimension.get(dimensionId);
      if (b == null) {
         return false;
      }

      long chunkKey = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
      long posKey = pos.asLong();
      LongOpenHashSet old = b.chunks.get(chunkKey);
      if (old != null && old.contains(posKey)) {
         if (old.size() == 1) {
            b.chunks.remove(chunkKey);
            this.insertionOrder.remove(new PlayerPlacedBlockStore.ChunkRef(dimensionId, chunkKey));
         } else {
            LongOpenHashSet copy = new LongOpenHashSet(old);
            copy.remove(posKey);
            b.chunks.put(chunkKey, copy);
         }

         this.totalBlocks.decrementAndGet();
         b.dirty = true;
         return true;
      } else {
         return false;
      }
   }

   public boolean contains(String dimensionId, BlockPos pos) {
      PlayerPlacedBlockStore.DimBuckets b = this.byDimension.get(dimensionId);
      if (b == null) {
         return false;
      }

      LongOpenHashSet set = b.chunks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
      return set != null && set.contains(pos.asLong());
   }

   public int size() {
      return this.totalBlocks.get();
   }

   private static int maxBlocks() {
      try {
         int v = BaritoneAPI.getGlobalSettings().respectStructuresStoreMaxBlocks.get();
         if (v < 1000) {
            return 1000;
         } else {
            return v > 2000000 ? 2000000 : v;
         }
      } catch (Throwable t) {
         return 200000;
      }
   }

   private void enforceCap() {
      int cap = maxBlocks();
      boolean evicted = false;

      while (this.totalBlocks.get() > cap && !this.insertionOrder.isEmpty()) {
         PlayerPlacedBlockStore.ChunkRef oldest = this.insertionOrder.pollFirst();
         if (oldest == null) {
            break;
         }

         PlayerPlacedBlockStore.DimBuckets db = this.byDimension.get(oldest.dimensionId);
         if (db != null) {
            LongOpenHashSet removed = db.chunks.remove(oldest.chunkKey);
            if (removed != null) {
               this.totalBlocks.addAndGet(-removed.size());
               db.dirty = true;
               evicted = true;
            }
         }
      }

      if (evicted) {
         long now = System.currentTimeMillis();
         if (now - this.lastEvictionWarnMs > 30000L) {
            this.lastEvictionWarnMs = now;
            PlayerEngine.LOGGER.warn("PlayerPlacedBlockStore: cap {} reached — evicting oldest chunk(s); recent builds favored.", cap);
         }
      }
   }

   private void load() {
      Path root = Player2NpcPersistencePaths.playerPlacedRoot(this.worldRoot);
      if (!Files.isDirectory(root)) {
         PlayerEngine.LOGGER.info("PlayerPlacedBlockStore: no playerplaced data found — starting empty.");
      } else {
         try (Stream<Path> dirs = Files.list(root)) {
            dirs.filter(x$0 -> Files.isDirectory(x$0)).forEach(dir -> {
               Path file = dir.resolve("blocks.json");
               if (Files.exists(file)) {
                  this.loadDimensionFile(file);
               }
            });
         } catch (IOException e) {
            PlayerEngine.LOGGER.warn("PlayerPlacedBlockStore: could not scan playerplaced root: {}", e.getMessage());
         }

         PlayerEngine.LOGGER.info("PlayerPlacedBlockStore: loaded {} position(s) across {} dimension(s).", this.size(), this.byDimension.size());
      }
   }

   private void loadDimensionFile(Path file) {
      try {
         String json = Files.readString(file, StandardCharsets.UTF_8);
         JsonObject root = (JsonObject)GSON.fromJson(json, JsonObject.class);
         if (root == null) {
            this.quarantine(file, "root is null");
            return;
         }

         int version = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 0;
         if (version != 1) {
            this.quarantine(file, "schemaVersion " + version + " != supported 1");
            return;
         }

         if (!root.has("dimension") || !root.has("chunks") || !root.get("chunks").isJsonArray()) {
            this.quarantine(file, "missing dimension or chunks array");
            return;
         }

         String dimensionId = root.get("dimension").getAsString();
         PlayerPlacedBlockStore.DimBuckets b = this.byDimension.computeIfAbsent(dimensionId, k -> new PlayerPlacedBlockStore.DimBuckets());

         for (JsonElement chunkElem : root.get("chunks").getAsJsonArray()) {
            JsonObject chunkObj = chunkElem.getAsJsonObject();
            long chunkKey = chunkObj.get("c").getAsLong();
            JsonArray positions = chunkObj.get("p").getAsJsonArray();
            LongOpenHashSet set = new LongOpenHashSet(positions.size());

            for (JsonElement p : positions) {
               set.add(p.getAsLong());
            }

            if (!set.isEmpty()) {
               b.chunks.put(chunkKey, set);
               this.insertionOrder.addLast(new PlayerPlacedBlockStore.ChunkRef(dimensionId, chunkKey));
               this.totalBlocks.addAndGet(set.size());
            }
         }
      } catch (Exception e) {
         this.quarantine(file, e.getMessage());
      }
   }

   private void quarantine(Path file, String reason) {
      String quarantineName = "blocks.json.corrupt-" + System.currentTimeMillis();

      try {
         Files.move(file, file.resolveSibling(quarantineName), StandardCopyOption.REPLACE_EXISTING);
         PlayerEngine.LOGGER.warn("PlayerPlacedBlockStore: quarantined corrupt {} as {} (reason: {}); skipping.", file.getFileName(), quarantineName, reason);
      } catch (IOException ex) {
         PlayerEngine.LOGGER
            .warn("PlayerPlacedBlockStore: could not quarantine {} (reason: {}; rename error: {}); skipping.", file.getFileName(), reason, ex.getMessage());
      }
   }

   private void flushDirty() {
      for (Entry<String, PlayerPlacedBlockStore.DimBuckets> entry : this.byDimension.entrySet()) {
         PlayerPlacedBlockStore.DimBuckets b = entry.getValue();
         if (b.dirty) {
            b.dirty = false;

            try {
               this.persistDimension(entry.getKey(), b);
            } catch (Exception e) {
               b.dirty = true;
               PlayerEngine.LOGGER.warn("PlayerPlacedBlockStore: failed to persist a dimension file: {}", e.getMessage());
            }
         }
      }
   }

   private void persistDimension(String dimensionId, PlayerPlacedBlockStore.DimBuckets b) throws IOException {
      Path file = Player2NpcPersistencePaths.playerPlacedFile(this.worldRoot, dimensionId);
      Files.createDirectories(file.getParent());
      JsonObject root = new JsonObject();
      root.addProperty("schemaVersion", 1);
      root.addProperty("dimension", dimensionId);
      JsonArray chunks = new JsonArray();

      for (Entry<Long, LongOpenHashSet> chunkEntry : b.chunks.entrySet()) {
         LongOpenHashSet set = chunkEntry.getValue();
         if (set != null && !set.isEmpty()) {
            JsonObject chunkObj = new JsonObject();
            chunkObj.addProperty("c", chunkEntry.getKey());
            JsonArray positions = new JsonArray();

            for (long p : set.toLongArray()) {
               positions.add(p);
            }

            chunkObj.add("p", positions);
            chunks.add(chunkObj);
         }
      }

      root.add("chunks", chunks);
      String json = GSON.toJson(root);
      Path tmp = file.resolveSibling("blocks.json.tmp");
      Files.writeString(tmp, json, StandardCharsets.UTF_8);
      atomicReplace(tmp, file);
   }

   private static void atomicReplace(Path tmp, Path target) throws IOException {
      try {
         Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
         Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
      }
   }

   private static final class ChunkRef {
      final String dimensionId;
      final long chunkKey;

      ChunkRef(String dimensionId, long chunkKey) {
         this.dimensionId = dimensionId;
         this.chunkKey = chunkKey;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else {
            return !(o instanceof PlayerPlacedBlockStore.ChunkRef other)
               ? false
               : this.chunkKey == other.chunkKey && this.dimensionId.equals(other.dimensionId);
         }
      }

      @Override
      public int hashCode() {
         return 31 * this.dimensionId.hashCode() + Long.hashCode(this.chunkKey);
      }
   }

   private static final class DimBuckets {
      final ConcurrentHashMap<Long, LongOpenHashSet> chunks = new ConcurrentHashMap<>();
      volatile boolean dirty = false;
   }
}
