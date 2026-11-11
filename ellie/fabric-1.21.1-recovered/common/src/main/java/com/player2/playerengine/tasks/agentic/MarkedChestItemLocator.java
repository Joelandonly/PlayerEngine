package com.player2.playerengine.tasks.agentic;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.agentic.elliegps.EllieGPSStore;
import com.player2.playerengine.agentic.elliegps.InventoryWaypointData;
import com.player2.playerengine.agentic.elliegps.WaypointItemCategorizer;
import com.player2.playerengine.agentic.elliegps.WaypointRecord;
import com.player2.playerengine.agentic.elliegps.WaypointSearchDegradationReporter;
import com.player2.playerengine.agentic.elliegps.WaypointSearchOrder;
import com.player2.playerengine.agentic.elliegps.WaypointSearchResult;
import com.player2.playerengine.agentic.elliegps.WaypointSearchService;
import com.player2.playerengine.agentic.elliegps.WaypointSearchStatus;
import com.player2.playerengine.containeraccess.ItemCount;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

public final class MarkedChestItemLocator {
   private MarkedChestItemLocator() {
   }

   public static List<BlockPos> candidateCoordinates(
      PlayerEngineController controller, Item item, Vec3 origin, double radiusBlocks, String dimensionId
   ) {
      try {
         if (item != null && origin != null && validDimension(dimensionId) && Double.isFinite(radiusBlocks) && !(radiusBlocks < 0.0)) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            if (key == null) {
               WaypointSearchDegradationReporter.reportOnce(controller, WaypointSearchStatus.FAILED_SEARCH_ERROR);
               return List.of();
            }

            String registryId = key.toString();
            List<String> keywords = WaypointItemCategorizer.categorize(List.of(new ItemCount(registryId, 1)));
            String query = String.join(" ", keywords).trim();
            if (query.isEmpty()) {
               WaypointSearchDegradationReporter.reportOnce(controller, WaypointSearchStatus.FAILED_SEARCH_ERROR);
               return List.of();
            }

            WaypointSearchResult search = WaypointSearchService.find(query, dimensionId, Set.of("inventory"), false, WaypointSearchOrder.RELEVANCE, null, 4096);
            WaypointSearchDegradationReporter.reportOnce(controller, search.status());
            if (isTerminalSearchFailure(search.status())) {
               return List.of();
            }

            double radiusSq = radiusBlocks * radiusBlocks;
            LinkedHashSet<BlockPos> uniqueMatches = new LinkedHashSet<>();

            for (WaypointRecord record : search.records()) {
               if (eligibleRecord(record, registryId, dimensionId)) {
                  BlockPos pos = record.canonicalBlockPos();
                  if (pos != null && distSq(pos, origin) <= radiusSq) {
                     uniqueMatches.add(pos.immutable());
                  }
               }
            }

            ArrayList<BlockPos> matches = new ArrayList<>(uniqueMatches);
            matches.sort(positionComparator(origin));
            return List.copyOf(matches);
         } else {
            WaypointSearchDegradationReporter.reportOnce(controller, WaypointSearchStatus.FAILED_SEARCH_ERROR);
            return List.of();
         }
      } catch (RuntimeException searchFailure) {
         WaypointSearchDegradationReporter.reportOnce(controller, WaypointSearchStatus.FAILED_SEARCH_ERROR);
         return List.of();
      }
   }

   public static MarkedChestItemLocator.MarkedPositionSnapshot markedPositions(String dimensionId) {
      if (!validDimension(dimensionId)) {
         return new MarkedChestItemLocator.MarkedPositionSnapshot(false, Set.of());
      }

      EllieGPSStore store = EllieGPSStore.get();
      if (store == null) {
         return new MarkedChestItemLocator.MarkedPositionSnapshot(false, Set.of());
      }

      try {
         LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
         EllieGPSStore.BoundedSnapshot snapshot = store.boundedSnapshot(4096);
         if (snapshot.available() && snapshot.complete()) {
            for (WaypointRecord record : snapshot.records()) {
               if (record != null && "inventory".equals(record.type) && dimensionId.equals(record.dimension)) {
                  BlockPos canonical = record.canonicalBlockPos();
                  BlockPos secondary = record.secondaryBlockPos();
                  if (canonical != null) {
                     positions.add(canonical.immutable());
                  }

                  if (secondary != null) {
                     positions.add(secondary.immutable());
                  }
               }
            }

            return new MarkedChestItemLocator.MarkedPositionSnapshot(true, positions);
         } else {
            return new MarkedChestItemLocator.MarkedPositionSnapshot(false, Set.of());
         }
      } catch (RuntimeException storeFailure) {
         return new MarkedChestItemLocator.MarkedPositionSnapshot(false, Set.of());
      }
   }

   static Comparator<BlockPos> positionComparator(Vec3 origin) {
      return Comparator.<BlockPos>comparingDouble(pos -> distSq(pos, origin))
         .thenComparingInt(Vec3i::getX)
         .thenComparingInt(Vec3i::getY)
         .thenComparingInt(Vec3i::getZ);
   }

   private static boolean eligibleRecord(WaypointRecord record, String registryId, String dimensionId) {
      if (record != null && "inventory".equals(record.type) && !record.stale && dimensionId.equals(record.dimension)) {
         InventoryWaypointData inventory = record.inventoryData();
         if (inventory != null && inventory.snapshot != null && inventory.snapshot.items != null) {
            for (ItemCount count : inventory.snapshot.items) {
               if (count != null && count.count() > 0 && registryId.equals(count.registryId())) {
                  return true;
               }
            }

            return false;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean isTerminalSearchFailure(WaypointSearchStatus status) {
      return status == WaypointSearchStatus.FAILED_SCAN_LIMIT
         || status == WaypointSearchStatus.FAILED_STORE_UNAVAILABLE
         || status == WaypointSearchStatus.FAILED_SEARCH_ERROR;
   }

   private static boolean validDimension(String dimensionId) {
      return dimensionId != null && !dimensionId.isBlank();
   }

   private static double distSq(BlockPos pos, Vec3 origin) {
      double dx = pos.getX() + 0.5 - origin.x;
      double dy = pos.getY() + 0.5 - origin.y;
      double dz = pos.getZ() + 0.5 - origin.z;
      return dx * dx + dy * dy + dz * dz;
   }

   public record MarkedPositionSnapshot(boolean available, Set<BlockPos> positions) {
      public MarkedPositionSnapshot {
         positions = Set.copyOf(positions == null ? Set.of() : positions);
      }
   }
}
