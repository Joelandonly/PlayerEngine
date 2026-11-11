package com.player2.playerengine.tasks.farming;

import com.player2.playerengine.agentic.elliegps.WaypointMutationResult;
import java.util.Objects;
import net.minecraft.core.BlockPos;

public record FarmTaskOutcome(
   boolean terminal,
   boolean successful,
   FarmTaskReason reason,
   FarmTaskPhase phase,
   String dimension,
   BlockPos center,
   int cleared,
   int filled,
   int tilled,
   boolean sourcePlaced,
   WaypointMutationResult waypointResult,
   FarmReturnStatus returnStatus
) {
   public FarmTaskOutcome(
      boolean terminal,
      boolean successful,
      FarmTaskReason reason,
      FarmTaskPhase phase,
      String dimension,
      BlockPos center,
      int cleared,
      int filled,
      int tilled,
      boolean sourcePlaced,
      WaypointMutationResult waypointResult
   ) {
      this(terminal, successful, reason, phase, dimension, center, cleared, filled, tilled, sourcePlaced, waypointResult, FarmReturnStatus.NOT_REQUIRED);
   }

   public FarmTaskOutcome {
      reason = Objects.requireNonNull(reason, "reason");
      phase = Objects.requireNonNull(phase, "phase");
      returnStatus = Objects.requireNonNull(returnStatus, "returnStatus");
      if (dimension != null && dimension.isBlank()) {
         throw new IllegalArgumentException("dimension cannot be blank");
      }

      if (center != null) {
         center = center.immutable();
      }

      requireRange(cleared, 0, 1040, "cleared");
      requireRange(filled, 0, 64, "filled");
      requireRange(tilled, 0, 320, "tilled");
      if (!successful || terminal && reason == FarmTaskReason.NONE && phase == FarmTaskPhase.DONE) {
         if (!successful || dimension != null && center != null && waypointResult != null) {
            if (!terminal || successful || reason != FarmTaskReason.NONE && phase != FarmTaskPhase.DONE) {
               if (!terminal && successful) {
                  throw new IllegalArgumentException("non-terminal outcome cannot be successful");
               }

               if (!terminal && reason != FarmTaskReason.NONE) {
                  throw new IllegalArgumentException("non-terminal outcome cannot carry a failure reason");
               }

               if (!terminal && returnStatus != FarmReturnStatus.NOT_REQUIRED) {
                  throw new IllegalArgumentException("non-terminal outcome cannot carry a return result");
               }

               if (successful && returnStatus.incomplete()) {
                  throw new IllegalArgumentException("successful outcome cannot carry an incomplete return");
               }
            } else {
               throw new IllegalArgumentException("failed terminal outcome needs a reason and operational phase");
            }
         } else {
            throw new IllegalArgumentException("successful outcome needs dimension, center, and waypoint result");
         }
      } else {
         throw new IllegalArgumentException("successful outcome must be terminal DONE with reason NONE");
      }
   }

   public static FarmTaskOutcome initial() {
      return new FarmTaskOutcome(false, false, FarmTaskReason.NONE, FarmTaskPhase.PRECHECK, null, null, 0, 0, 0, false, null);
   }

   private static void requireRange(int value, int min, int max, String field) {
      if (value < min || value > max) {
         throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
      }
   }
}
