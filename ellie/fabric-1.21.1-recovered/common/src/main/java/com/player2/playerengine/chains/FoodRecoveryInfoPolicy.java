package com.player2.playerengine.chains;

final class FoodRecoveryInfoPolicy {
   static final int STARVING_FOOD_LEVEL = 6;

   private FoodRecoveryInfoPolicy() {
   }

   static FoodRecoveryInfoPolicy.State advance(
      boolean episodeActive, boolean recoveryInfoPending, boolean isStarving, int foodLevel, boolean isTryingToEat, boolean requestFillup
   ) {
      boolean notifyPlayer = false;
      boolean deferModelInfo = false;
      if (isStarving) {
         if (!episodeActive) {
            notifyPlayer = true;
         }

         episodeActive = true;
         recoveryInfoPending = false;
      } else if (episodeActive) {
         recoveryInfoPending = true;
         if (foodLevel > 6 && !isTryingToEat && !requestFillup) {
            episodeActive = false;
            recoveryInfoPending = false;
            deferModelInfo = true;
         }
      } else {
         recoveryInfoPending = false;
      }

      return new FoodRecoveryInfoPolicy.State(episodeActive, recoveryInfoPending, notifyPlayer, deferModelInfo);
   }

   record State(boolean episodeActive, boolean recoveryInfoPending, boolean notifyPlayer, boolean deferModelInfo) {
   }
}
