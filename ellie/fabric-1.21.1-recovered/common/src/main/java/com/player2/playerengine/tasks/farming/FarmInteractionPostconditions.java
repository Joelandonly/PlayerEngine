package com.player2.playerengine.tasks.farming;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;

public final class FarmInteractionPostconditions {
   private FarmInteractionPostconditions() {
   }

   public static boolean isStandaloneWaterSource(BlockState state) {
      return state != null && state.is(Blocks.WATER) && state.getFluidState().isSource() && state.getBlock() instanceof BucketPickup;
   }

   public static boolean waterPickupInventoryDelta(int emptyBucketsBefore, int emptyBucketsAfter, int waterBucketsBefore, int waterBucketsAfter) {
      return emptyBucketsBefore >= 1 && emptyBucketsAfter == emptyBucketsBefore - 1 && waterBucketsAfter == waterBucketsBefore + 1;
   }

   public static boolean waterPlacement(BlockState centerAfter, int emptyBucketsBefore, int emptyBucketsAfter, int waterBucketsBefore, int waterBucketsAfter) {
      return isStandaloneWaterSource(centerAfter)
         && waterBucketsBefore >= 1
         && waterBucketsAfter == waterBucketsBefore - 1
         && emptyBucketsAfter == emptyBucketsBefore + 1;
   }

   public static boolean farmlandTransition(BlockState before, BlockState after) {
      return before != null && after != null && !before.is(Blocks.FARMLAND) && after.is(Blocks.FARMLAND);
   }

   public static boolean verifiedBreakTransition(BlockState expectedBefore, BlockState observedAfter, Predicate<BlockState> allowedAfter) {
      Objects.requireNonNull(expectedBefore, "expectedBefore");
      Objects.requireNonNull(observedAfter, "observedAfter");
      Objects.requireNonNull(allowedAfter, "allowedAfter");
      return !observedAfter.equals(expectedBefore) && allowedAfter.test(observedAfter);
   }

   public static boolean exactlyOneHoeDamage(int beforeDamage, int maximumDamage, int afterDamage, boolean broke) {
      if (maximumDamage >= 1 && beforeDamage >= 0 && beforeDamage < maximumDamage) {
         int remainingBefore = maximumDamage - beforeDamage;
         return broke ? remainingBefore == 1 : afterDamage == beforeDamage + 1 && afterDamage < maximumDamage;
      } else {
         return false;
      }
   }
}
