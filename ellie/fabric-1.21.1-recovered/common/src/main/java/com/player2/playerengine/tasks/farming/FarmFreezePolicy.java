package com.player2.playerengine.tasks.farming;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;

public final class FarmFreezePolicy {
   private static final int MAX_FREEZE_BLOCK_LIGHT = 9;

   private FarmFreezePolicy() {
   }

   public static boolean wouldFreeze(FarmSiteSnapshot snapshot) {
      Objects.requireNonNull(snapshot, "snapshot");
      BlockPos center = snapshot.center();
      FarmSiteWorldView.CellObservation centerFacts = snapshot.observation(center);
      if (centerFacts != null && centerFacts.isReadable()) {
         if (!centerFacts.warmEnoughToRain() && centerFacts.blockLight() <= 9) {
            boolean surroundedByWater = true;

            for (Direction direction : Plane.HORIZONTAL) {
               FarmSiteWorldView.CellObservation neighbor = snapshot.observation(center.relative(direction));
               if (neighbor == null
                  || neighbor.fluidKind() != FarmSiteWorldView.FluidKind.STANDALONE_WATER_SOURCE
                     && neighbor.fluidKind() != FarmSiteWorldView.FluidKind.OTHER_WATER_SOURCE
                     && neighbor.fluidKind() != FarmSiteWorldView.FluidKind.FLOWING_WATER) {
                  surroundedByWater = false;
                  break;
               }
            }

            return !surroundedByWater;
         } else {
            return false;
         }
      } else {
         return true;
      }
   }
}
