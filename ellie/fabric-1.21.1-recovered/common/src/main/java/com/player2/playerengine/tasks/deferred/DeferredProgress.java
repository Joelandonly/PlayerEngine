package com.player2.playerengine.tasks.deferred;

public record DeferredProgress(
   boolean present,
   boolean simulated,
   int outputCount,
   int inputCount,
   int fuelCount,
   int cookingProgress,
   int cookingTotalTime,
   int litTime,
   long pollGameTime
) {
   public static DeferredProgress notLoaded(long gameTime) {
      return new DeferredProgress(false, false, 0, 0, 0, 0, 0, 0, gameTime);
   }

   public boolean isLit() {
      return this.litTime > 0;
   }

   public boolean isCooking() {
      return this.cookingTotalTime > 0 && this.cookingProgress > 0 && this.cookingProgress < this.cookingTotalTime;
   }
}
