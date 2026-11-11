package com.player2.playerengine.agentic.elliegps;

import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.containeraccess.ItemCount;
import com.player2.playerengine.util.ItemTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

public final class EllieGPSWaypointCountingService implements EllieGPSCountingService {
   @Override
   public int estimateNearbyWaypointItems(ItemTarget target, Vec3 origin, double radiusBlocks) {
      return this.estimateNearbyWaypointItems(target, origin, radiusBlocks, null);
   }

   public int estimateNearbyWaypointItems(ItemTarget target, Vec3 origin, double radiusBlocks, String dimensionId) {
      return this.estimateNearbyWaypointItemsChecked(target, origin, radiusBlocks, dimensionId).count();
   }

   public WaypointCountResult estimateNearbyWaypointItemsChecked(ItemTarget target, Vec3 origin, double radiusBlocks, String dimensionId) {
      WaypointSearchStatus status = WaypointSearchStatus.INDEXED;

      try {
         if (dimensionId != null && !dimensionId.isBlank() && origin != null) {
            EllieGPSStore store = EllieGPSStore.get();
            if (store == null) {
               return new WaypointCountResult(0, WaypointSearchStatus.FAILED_STORE_UNAVAILABLE);
            }

            List<String> targetRegistryIds = buildRegistryIds(target);
            if (targetRegistryIds.isEmpty()) {
               return new WaypointCountResult(0, status);
            }

            String query = buildSearchQuery(targetRegistryIds);
            if (query.isEmpty()) {
               return new WaypointCountResult(0, status);
            }

            WaypointSearchResult search = WaypointSearchService.find(query, dimensionId, Set.of("inventory"), false, WaypointSearchOrder.RELEVANCE, null, 4096);
            status = search.status();
            if (status != WaypointSearchStatus.FAILED_SCAN_LIMIT
               && status != WaypointSearchStatus.FAILED_STORE_UNAVAILABLE
               && status != WaypointSearchStatus.FAILED_SEARCH_ERROR) {
               double radiusSq = radiusBlocks * radiusBlocks;
               long total = 0L;

               for (WaypointRecord record : search.records()) {
                  if ("inventory".equals(record.type) && !record.stale && dimensionId.equals(record.dimension)) {
                     InventoryWaypointData inv = record.inventoryData();
                     if (inv != null && inv.snapshot != null && withinRadius(record, origin, radiusSq) && inv.snapshot.items != null) {
                        for (ItemCount ic : inv.snapshot.items) {
                           if (ic != null && ic.count() > 0 && targetRegistryIds.contains(ic.registryId())) {
                              total = Math.min(2147483647L, total + ic.count());
                           }
                        }
                     }
                  }
               }

               return new WaypointCountResult((int)total, status);
            } else {
               return new WaypointCountResult(0, status);
            }
         } else {
            return new WaypointCountResult(0, WaypointSearchStatus.FAILED_SEARCH_ERROR);
         }
      } catch (Exception e) {
         PlayerEngine.LOGGER.warn("EllieGPS counting: estimateNearbyWaypointItems failed: {}", e.getMessage());
         return new WaypointCountResult(0, WaypointSearchStatus.FAILED_SEARCH_ERROR);
      }
   }

   private static List<String> buildRegistryIds(ItemTarget target) {
      if (target == null) {
         return List.of();
      }

      Item[] matches = target.getMatches();
      if (matches != null && matches.length != 0) {
         List<String> ids = new ArrayList<>(matches.length);

         for (Item item : matches) {
            if (item != null) {
               try {
                  ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                  if (key != null) {
                     ids.add(key.toString());
                  }
               } catch (Exception var8) {
               }
            }
         }

         return ids;
      } else {
         return List.of();
      }
   }

   private static String buildSearchQuery(List<String> registryIds) {
      List<ItemCount> asCounts = new ArrayList<>(registryIds.size());

      for (String rid : registryIds) {
         asCounts.add(new ItemCount(rid, 1));
      }

      List<String> keywords = WaypointItemCategorizer.categorize(asCounts);
      return String.join(" ", keywords).trim();
   }

   private static boolean withinRadius(WaypointRecord record, Vec3 origin, double radiusSq) {
      if (record.pos != null && record.pos.length >= 3) {
         double dx = record.pos[0] + 0.5 - origin.x;
         double dy = record.pos[1] + 0.5 - origin.y;
         double dz = record.pos[2] + 0.5 - origin.z;
         return dx * dx + dy * dy + dz * dz <= radiusSq;
      } else {
         return false;
      }
   }
}
