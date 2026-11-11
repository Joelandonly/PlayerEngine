package com.player2.playerengine.tasks.cooking;

import com.player2.playerengine.tasks.cooking.resolver.CookingRecipeAccess;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

public final class SmeltDeferredParams {
   public final Item inputItem;
   public final int count;
   @Nullable
   public final CookingRecipeAccess.CookKind preferredKind;
   public final int stallPolls;
   public final int timeoutSeconds;
   public final boolean allowYield;

   private SmeltDeferredParams(SmeltDeferredParams.Builder b) {
      this.inputItem = b.inputItem;
      this.count = b.count;
      this.preferredKind = b.preferredKind;
      this.stallPolls = b.stallPolls;
      this.timeoutSeconds = b.timeoutSeconds;
      this.allowYield = false;
   }

   public static final class Builder {
      private final Item inputItem;
      private final int count;
      @Nullable
      private CookingRecipeAccess.CookKind preferredKind = null;
      private int stallPolls = 200;
      private int timeoutSeconds = 600;

      public Builder(Item inputItem, int count) {
         if (inputItem == null) {
            throw new IllegalArgumentException("inputItem must not be null");
         }

         if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1, got " + count);
         }

         this.inputItem = inputItem;
         this.count = count;
      }

      public SmeltDeferredParams.Builder preferredKind(@Nullable CookingRecipeAccess.CookKind kind) {
         this.preferredKind = kind;
         return this;
      }

      public SmeltDeferredParams.Builder stallPolls(int polls) {
         this.stallPolls = Math.max(20, Math.min(2000, polls));
         return this;
      }

      public SmeltDeferredParams.Builder timeoutSeconds(int seconds) {
         this.timeoutSeconds = Math.max(60, Math.min(3600, seconds));
         return this;
      }

      public SmeltDeferredParams build() {
         return new SmeltDeferredParams(this);
      }
   }
}
