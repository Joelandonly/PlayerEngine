package com.player2.playerengine.tasks.cooking;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.cooking.resolver.CookingRecipeAccess;
import com.player2.playerengine.tasks.deferred.DeferredDegradation;
import com.player2.playerengine.tasks.deferred.DeferredProcess;
import com.player2.playerengine.tasks.deferred.DeferredProgress;
import net.minecraft.world.item.Item;
import net.minecraft.core.BlockPos;

public final class DeferredCookProcess implements DeferredProcess {
   private final BlockPos stationPos;
   private final CookingRecipeAccess.CookKind cookKind;
   private final Item inputItem;
   private final int inputCount;
   private final Item outputItem;
   private final int expectedOutputCount;
   private final int cookTicksPerItem;
   private final Item fuelItem;
   private final int fuelCount;
   private final long startGameTime;
   private final int stallPolls;
   private final int timeoutSeconds;
   private final FurnaceStateReader reader;
   private int consecutiveNotSimulatedPolls = 0;

   public DeferredCookProcess(
      BlockPos stationPos,
      CookingRecipeAccess.CookKind cookKind,
      Item inputItem,
      int inputCount,
      Item outputItem,
      int expectedOutputCount,
      int cookTicksPerItem,
      Item fuelItem,
      int fuelCount,
      long startGameTime,
      int stallPolls,
      int timeoutSeconds,
      PlayerEngineController controller
   ) {
      this.stationPos = stationPos.immutable();
      this.cookKind = cookKind;
      this.inputItem = inputItem;
      this.inputCount = inputCount;
      this.outputItem = outputItem;
      this.expectedOutputCount = expectedOutputCount;
      this.cookTicksPerItem = cookTicksPerItem;
      this.fuelItem = fuelItem;
      this.fuelCount = fuelCount;
      this.startGameTime = startGameTime;
      this.stallPolls = stallPolls;
      this.timeoutSeconds = timeoutSeconds;
      this.reader = new FurnaceStateReader(controller);
   }

   @Override
   public String kind() {
      return switch (this.cookKind) {
         case SMELTING -> "smelt";
         case BLASTING -> "blast";
         case SMOKING -> "smoke";
      };
   }

   @Override
   public BlockPos stationPos() {
      return this.stationPos;
   }

   @Override
   public long startGameTime() {
      return this.startGameTime;
   }

   @Override
   public long etaGameTime() {
      return this.startGameTime + (long)this.inputCount * this.cookTicksPerItem;
   }

   @Override
   public DeferredProgress poll(PlayerEngineController controller) {
      long gameTime = controller.getWorld() != null ? controller.getWorld().getGameTime() : 0L;
      FurnaceStateReader.FurnaceState s = this.reader.read(this.stationPos);
      if (!s.present()) {
         this.consecutiveNotSimulatedPolls = 0;
         return new DeferredProgress(false, s.simulated(), 0, 0, 0, 0, 0, 0, gameTime);
      }

      DeferredProgress progress = new DeferredProgress(
         true,
         s.simulated(),
         s.output().getCount(),
         s.input().getCount(),
         s.fuel().getCount(),
         s.cookingProgress(),
         s.cookingTotalTime(),
         s.litTime(),
         gameTime
      );
      if (!progress.simulated() && !this.isComplete(progress)) {
         this.consecutiveNotSimulatedPolls++;
      } else {
         this.consecutiveNotSimulatedPolls = 0;
      }

      return progress;
   }

   @Override
   public boolean isComplete(DeferredProgress progress) {
      return progress.present() && progress.outputCount() >= this.expectedOutputCount && progress.inputCount() <= 0;
   }

   @Override
   public DeferredDegradation degradationOf(DeferredProgress progress) {
      if (this.isComplete(progress)) {
         return null;
      } else if (!progress.present()) {
         return DeferredDegradation.STATION_GONE;
      } else {
         long elapsedTicks = progress.pollGameTime() - this.startGameTime;
         if (elapsedTicks >= this.timeoutSeconds * 20L) {
            return DeferredDegradation.STALLED_TIMEOUT;
         } else if (progress.simulated() && !progress.isLit() && progress.fuelCount() <= 0 && progress.inputCount() > 0) {
            return DeferredDegradation.OUT_OF_FUEL;
         } else {
            return !progress.simulated() && this.consecutiveNotSimulatedPolls >= this.stallPolls ? DeferredDegradation.STALLED_RETRYING : null;
         }
      }
   }

   public CookingRecipeAccess.CookKind cookKind() {
      return this.cookKind;
   }

   public Item inputItem() {
      return this.inputItem;
   }

   public int inputCount() {
      return this.inputCount;
   }

   public Item outputItem() {
      return this.outputItem;
   }

   public int expectedOutputCount() {
      return this.expectedOutputCount;
   }

   public int cookTicksPerItem() {
      return this.cookTicksPerItem;
   }

   public Item fuelItem() {
      return this.fuelItem;
   }

   public int fuelCount() {
      return this.fuelCount;
   }
}
