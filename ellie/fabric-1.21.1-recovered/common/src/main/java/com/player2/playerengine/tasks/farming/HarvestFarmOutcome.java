package com.player2.playerengine.tasks.farming;

import com.player2.playerengine.agentic.elliegps.WaypointMutationResult;
import java.util.Objects;
import net.minecraft.core.BlockPos;

public record HarvestFarmOutcome(
   boolean terminal,
   boolean successful,
   FarmTaskReason reason,
   FarmTaskPhase phase,
   String dimension,
   BlockPos center,
   int matureFound,
   int harvested,
   int raceSkipped,
   int denied,
   int pickupItemUnitsLeft,
   boolean pickupEntityOverflow,
   WaypointMutationResult waypointResult
) {
   public HarvestFarmOutcome {
      reason = Objects.requireNonNull(reason, "reason");
      phase = Objects.requireNonNull(phase, "phase");
      if (dimension != null && dimension.isBlank()) {
         throw new IllegalArgumentException("dimension cannot be blank");
      }

      if (center != null) {
         center = center.immutable();
      }

      requireRange(matureFound, 0, 80, "matureFound");
      requireRange(harvested, 0, 80, "harvested");
      requireRange(raceSkipped, 0, 80, "raceSkipped");
      requireRange(denied, 0, 80, "denied");
      requireRange(pickupItemUnitsLeft, 0, 999, "pickupItemUnitsLeft");
      if ((long)harvested + raceSkipped + denied > matureFound) {
         throw new IllegalArgumentException("harvested, raceSkipped, and denied cannot exceed matureFound");
      }

      if (!successful || terminal && reason == FarmTaskReason.NONE && phase == FarmTaskPhase.DONE) {
         if (!successful || dimension != null && center != null && waypointResult != null) {
            if (phase == FarmTaskPhase.HARVEST_RESOLVE || phase == FarmTaskPhase.FAILED || dimension != null && center != null) {
               if (!terminal || successful || reason != FarmTaskReason.NONE && phase != FarmTaskPhase.DONE) {
                  if (!terminal && successful) {
                     throw new IllegalArgumentException("non-terminal outcome cannot be successful");
                  }

                  if (!terminal && reason != FarmTaskReason.NONE) {
                     throw new IllegalArgumentException("non-terminal outcome cannot carry a failure reason");
                  }

                  if (phase == FarmTaskPhase.DONE && !successful) {
                     throw new IllegalArgumentException("DONE phase is reserved for successful outcomes");
                  }
               } else {
                  throw new IllegalArgumentException("failed terminal outcome needs a reason and operational phase");
               }
            } else {
               throw new IllegalArgumentException("resolved harvest phases need dimension and center");
            }
         } else {
            throw new IllegalArgumentException("successful outcome needs dimension, center, and waypoint result");
         }
      } else {
         throw new IllegalArgumentException("successful outcome must be terminal DONE with reason NONE");
      }
   }

   public static HarvestFarmOutcome initial(String dimension, BlockPos exactCenter) {
      return new HarvestFarmOutcome(false, false, FarmTaskReason.NONE, FarmTaskPhase.HARVEST_RESOLVE, dimension, exactCenter, 0, 0, 0, 0, 0, false, null);
   }

   private static void requireRange(int value, int min, int max, String field) {
      if (value < min || value > max) {
         throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
      }
   }
}
