package com.player2.playerengine.trackers;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.automaton.utils.accessor.ServerChunkManagerAccessor;
import com.player2.playerengine.util.Debug;
import dev.architectury.event.events.common.ChunkEvent;
import dev.architectury.event.events.common.ChunkEvent.LoadData;
import dev.architectury.event.events.common.ChunkEvent.SaveData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public class SimpleChunkTracker {
   private final PlayerEngineController mod;
   private final Set<ChunkPos> loaded = new HashSet<>();
   private final List<Consumer<ChunkPos>> loadCallbacks = new ArrayList<>();

   public SimpleChunkTracker(PlayerEngineController mod) {
      this.mod = mod;
      ChunkEvent.LOAD_DATA.register((LoadData)(chunk, level, data) -> this.onLoad(chunk.getPos()));
      ChunkEvent.SAVE_DATA.register((SaveData)(chunk, level, data) -> this.onUnload(chunk.getPos()));
   }

   public void addLoadCallback(Consumer<ChunkPos> callback) {
      this.loadCallbacks.add(callback);
   }

   public void removeLoadCallback(Consumer<ChunkPos> callback) {
      this.loadCallbacks.remove(callback);
   }

   private void onLoad(ChunkPos pos) {
      this.loaded.add(pos);
      if (!this.loadCallbacks.isEmpty()) {
         for (Consumer<ChunkPos> callback : List.copyOf(this.loadCallbacks)) {
            callback.accept(pos);
         }
      }
   }

   private void onUnload(ChunkPos pos) {
      this.loaded.remove(pos);
   }

   public boolean isChunkLoaded(ChunkPos pos) {
      ServerLevel world = this.mod.getWorld();
      return world != null && world.getChunkSource().hasChunk(pos.x, pos.z);
   }

   public boolean isChunkLoaded(BlockPos pos) {
      return this.isChunkLoaded(new ChunkPos(pos));
   }

   public boolean isChunkSimulated(ChunkPos pos) {
      ServerLevel world = this.mod.getWorld();
      if (world == null) {
         return false;
      } else {
         return world.getChunkSource() instanceof ServerChunkManagerAccessor accessor
            ? accessor.automatone$getChunkNow(pos.x, pos.z) != null
            : false;
      }
   }

   public boolean isChunkSimulated(BlockPos pos) {
      return this.isChunkSimulated(new ChunkPos(pos));
   }

   public List<ChunkPos> getLoadedChunks() {
      List<ChunkPos> result = new ArrayList<>(this.loaded);
      return result.stream().filter(this::isChunkLoaded).distinct().collect(Collectors.toList());
   }

   public boolean scanChunk(ChunkPos chunk, Predicate<BlockPos> onBlockStop) {
      if (!this.isChunkLoaded(chunk)) {
         return false;
      }

      int bottomY = this.mod.getWorld().getMinBuildHeight();
      int topY = this.mod.getWorld().getMaxBuildHeight();

      for (int xx = chunk.getMinBlockX(); xx <= chunk.getMaxBlockX(); xx++) {
         for (int yy = bottomY; yy <= topY; yy++) {
            for (int zz = chunk.getMinBlockZ(); zz <= chunk.getMaxBlockZ(); zz++) {
               if (onBlockStop.test(new BlockPos(xx, yy, zz))) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public void scanChunk(ChunkPos chunk, Consumer<BlockPos> onBlock) {
      this.scanChunk(chunk, block -> {
         onBlock.accept(block);
         return false;
      });
   }

   public void reset(PlayerEngineController mod) {
      Debug.logInternal("CHUNKS RESET");
      this.loaded.clear();
   }
}
