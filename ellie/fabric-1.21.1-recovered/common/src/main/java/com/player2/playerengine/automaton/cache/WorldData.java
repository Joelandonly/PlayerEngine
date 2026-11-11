package com.player2.playerengine.automaton.cache;

import com.player2.playerengine.automaton.api.cache.ICachedWorld;
import com.player2.playerengine.automaton.api.cache.IContainerMemory;
import com.player2.playerengine.automaton.api.cache.IWaypointCollection;
import com.player2.playerengine.automaton.api.cache.IWorldData;
import java.util.ArrayList;
import java.util.HashSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.HolderLookup.Provider;

public class WorldData implements IWorldData {
   private final WaypointCollection waypoints = new WaypointCollection();
   private final ContainerMemory containerMemory = new ContainerMemory();
   public final ResourceKey<Level> dimension;
   private final HashSet<Long> localChunkCache = new HashSet<>();

   WorldData(ResourceKey<Level> dimension) {
      this.dimension = dimension;
   }

   public void readFromNbt(Provider levelRegistryAccess, CompoundTag tag) {
      this.containerMemory.read(levelRegistryAccess, tag.getCompound("containers"));
      this.waypoints.readFromNbt(tag.getCompound("waypoints"));
   }

   public void writeToNbt(Provider levelRegistryAccess, CompoundTag tag) {
      tag.put("containers", this.containerMemory.toNbt(levelRegistryAccess));
      tag.put("waypoints", this.waypoints.toNbt());
   }

   @Override
   public ICachedWorld getCachedWorld() {
      return new ICachedWorld() {
         @Override
         public boolean isCached(int blockX, int blockZ) {
            int chunkX = blockX >> 4;
            int chunkZ = blockZ >> 4;
            long key = ChunkPos.asLong(chunkX, chunkZ);
            return WorldData.this.localChunkCache.contains(key);
         }

         @Override
         public ArrayList<BlockPos> getLocationsOf(String block, int maximum, int centerX, int centerZ, int maxRegionDistanceSq) {
            return new ArrayList<>();
         }
      };
   }

   @Override
   public IWaypointCollection getWaypoints() {
      return this.waypoints;
   }

   @Override
   public IContainerMemory getContainerMemory() {
      return this.containerMemory;
   }

   @Override
   public void addBlockPosToCache(int blockX, int blockZ) {
      int chunkX = blockX >> 4;
      int chunkZ = blockZ >> 4;
      this.addChunkPosToCache(chunkX, chunkZ);
   }

   public void addChunkPosToCache(int chunkX, int chunkZ) {
      long key = ChunkPos.asLong(chunkX, chunkZ);
      this.localChunkCache.add(key);
   }
}
