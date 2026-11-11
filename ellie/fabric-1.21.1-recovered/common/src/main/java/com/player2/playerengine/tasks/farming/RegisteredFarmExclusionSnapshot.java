package com.player2.playerengine.tasks.farming;

import com.player2.playerengine.agentic.elliegps.EllieGPSStore;
import com.player2.playerengine.agentic.elliegps.WaypointRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;

public final class RegisteredFarmExclusionSnapshot {
   private static volatile RegisteredFarmExclusionSnapshot.CachedSnapshot cached;
   private final boolean available;
   private final List<BlockPos> centers;

   private RegisteredFarmExclusionSnapshot(boolean available, List<BlockPos> centers) {
      this.available = available;
      this.centers = List.copyOf(centers);
   }

   public static RegisteredFarmExclusionSnapshot capture(String dimensionId) {
      EllieGPSStore store = EllieGPSStore.get();
      if (store == null) {
         return unavailable();
      }

      try {
         long generation = store.mutationGeneration();
         RegisteredFarmExclusionSnapshot.CachedSnapshot current = cached;
         if (current != null && current.store() == store && current.generation() == generation && Objects.equals(current.dimensionId(), dimensionId)) {
            return current.snapshot();
         }

         EllieGPSStore.BoundedSnapshot bounded = store.boundedSnapshot(4096);
         RegisteredFarmExclusionSnapshot snapshot = bounded.available() && bounded.complete() ? fromRecords(dimensionId, bounded.records()) : unavailable();
         if (store.mutationGeneration() != generation) {
            return unavailable();
         }

         cached = new RegisteredFarmExclusionSnapshot.CachedSnapshot(store, generation, dimensionId, snapshot);
         return snapshot;
      } catch (RuntimeException storeFailure) {
         return unavailable();
      }
   }

   static RegisteredFarmExclusionSnapshot fromRecords(String dimensionId, Iterable<WaypointRecord> records) {
      if (dimensionId != null && !dimensionId.isBlank() && records != null) {
         ArrayList<BlockPos> centers = new ArrayList<>();
         int inspected = 0;

         for (WaypointRecord record : records) {
            if (++inspected > 4096) {
               return unavailable();
            }

            if (record != null && "farm".equals(record.type) && dimensionId.equals(record.dimension)) {
               BlockPos center = record.canonicalBlockPos();
               if (center != null) {
                  centers.add(center.immutable());
               }
            }
         }

         centers.sort((first, second) -> {
            int x = Integer.compare(first.getX(), second.getX());
            if (x != 0) {
               return x;
            }

            int y = Integer.compare(first.getY(), second.getY());
            return y != 0 ? y : Integer.compare(first.getZ(), second.getZ());
         });
         return new RegisteredFarmExclusionSnapshot(true, centers);
      } else {
         return unavailable();
      }
   }

   public static RegisteredFarmExclusionSnapshot unavailable() {
      return new RegisteredFarmExclusionSnapshot(false, List.of());
   }

   public boolean available() {
      return this.available;
   }

   public int footprintCount() {
      return this.centers.size();
   }

   public boolean contains(BlockPos position) {
      Objects.requireNonNull(position, "position");
      if (!this.available) {
         return true;
      }

      for (BlockPos center : this.centers) {
         if (Math.abs(position.getX() - center.getX()) <= 4 && Math.abs(position.getZ() - center.getZ()) <= 4) {
            return true;
         }
      }

      return false;
   }

   List<BlockPos> centers() {
      return this.centers;
   }

   private record CachedSnapshot(EllieGPSStore store, long generation, String dimensionId, RegisteredFarmExclusionSnapshot snapshot) {
   }
}
