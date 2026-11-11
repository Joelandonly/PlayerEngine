package com.player2.playerengine.tasks.farming;

import com.player2.playerengine.agentic.elliegps.FarmWaypointData;
import com.player2.playerengine.agentic.elliegps.WaypointRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;

public final class FarmPlantingSelector {
   private FarmPlantingSelector() {
   }

   public static FarmPlantingSelector.Selection automatic(List<WaypointRecord> records, String dimension, BlockPos anchor, List<FarmPlantingRequest> requests) {
      Objects.requireNonNull(records, "records");
      Objects.requireNonNull(dimension, "dimension");
      Objects.requireNonNull(anchor, "anchor");
      Objects.requireNonNull(requests, "requests");
      ArrayList<WaypointRecord> currentFarms = new ArrayList<>();

      for (WaypointRecord record : records) {
         if (record != null && "farm".equals(record.type) && dimension.equals(record.dimension) && !record.stale && record.canonicalBlockPos() != null) {
            currentFarms.add(record.copy());
         }
      }

      currentFarms.sort(
         Comparator.<WaypointRecord>comparingLong(recordx -> FarmPlotGeometry.horizontalDistanceSquared(anchor, recordx.canonicalBlockPos()))
            .thenComparing(recordx -> Objects.toString(recordx.id, ""))
      );
      if (currentFarms.isEmpty()) {
         return failed(PlantFarmReason.NO_RECORDED_FARMS);
      }

      int supported = 0;
      ArrayList<WaypointRecord> viable = new ArrayList<>();

      for (WaypointRecord record : currentFarms) {
         FarmWaypointData data = record.farmData();
         if (data != null && data.isSupportedVersion() && data.radius() == 4) {
            supported++;
            boolean acceptsEvery = true;

            for (FarmPlantingRequest request : requests) {
               if (!data.acceptsPlantingItem(request.plantingItemId())) {
                  acceptsEvery = false;
                  break;
               }
            }

            if (acceptsEvery) {
               viable.add(record);
            }
         }
      }

      if (!viable.isEmpty()) {
         return new FarmPlantingSelector.Selection(viable, PlantFarmReason.NONE);
      } else {
         return supported == 0 ? failed(PlantFarmReason.UNSUPPORTED_FARM_DATA) : failed(PlantFarmReason.NO_POLICY_COMPATIBLE_FARM);
      }
   }

   static PlantFarmReason exactMetadataFailure(WaypointRecord record, List<FarmPlantingRequest> requests, FarmPlantingPolicy policy) {
      if (record == null) {
         return PlantFarmReason.EXACT_FARM_NOT_FOUND;
      }

      if (!"farm".equals(record.type)) {
         return PlantFarmReason.TYPE_CONFLICT;
      }

      if (record.stale) {
         return PlantFarmReason.STALE_FARM;
      }

      FarmWaypointData data = record.farmData();
      if (data != null && data.isSupportedVersion() && data.radius() == 4) {
         if (policy.kind() == FarmPlantingPolicy.Kind.PRESERVE) {
            for (FarmPlantingRequest request : requests) {
               if (!data.acceptsPlantingItem(request.plantingItemId())) {
                  return PlantFarmReason.EXACT_FARM_POLICY_CONFLICT;
               }
            }
         }

         return PlantFarmReason.NONE;
      } else {
         return PlantFarmReason.UNSUPPORTED_FARM_DATA;
      }
   }

   private static FarmPlantingSelector.Selection failed(PlantFarmReason reason) {
      return new FarmPlantingSelector.Selection(List.of(), reason);
   }

   public record Selection(List<WaypointRecord> candidates, PlantFarmReason failure) {
      public Selection {
         candidates = candidates == null ? List.of() : candidates.stream().map(WaypointRecord::copy).toList();
         failure = Objects.requireNonNull(failure, "failure");
         if (candidates.isEmpty() == (failure == PlantFarmReason.NONE)) {
            throw new IllegalArgumentException("selection needs candidates or one terminal failure, but not both");
         }
      }

      public boolean successful() {
         return this.failure == PlantFarmReason.NONE;
      }
   }
}
