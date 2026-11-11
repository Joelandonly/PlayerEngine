package com.player2.playerengine.tasks.smithing;

import net.minecraft.world.item.Item;

public final class SmithDeferredParams {
   public final Item outputItem;
   public final int count;
   public final int gatherBudget;
   public final int pollCap;

   private SmithDeferredParams(SmithDeferredParams.Builder b) {
      this.outputItem = b.outputItem;
      this.count = b.count;
      this.gatherBudget = b.gatherBudget;
      this.pollCap = b.pollCap;
   }

   public static final class Builder {
      private final Item outputItem;
      private final int count;
      private int gatherBudget = 400;
      private int pollCap = 12000;

      public Builder(Item outputItem, int count) {
         if (outputItem == null) {
            throw new IllegalArgumentException("outputItem must not be null");
         }

         if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1, got " + count);
         }

         this.outputItem = outputItem;
         this.count = count;
      }

      public SmithDeferredParams.Builder gatherBudget(int ticks) {
         this.gatherBudget = Math.max(20, Math.min(4000, ticks));
         return this;
      }

      public SmithDeferredParams.Builder pollCap(int ticks) {
         this.pollCap = Math.max(200, Math.min(72000, ticks));
         return this;
      }

      public SmithDeferredParams build() {
         return new SmithDeferredParams(this);
      }
   }
}
