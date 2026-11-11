package com.player2.playerengine.tasks.cooking;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.mixins.MixinAbstractFurnaceBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.inventory.ContainerData;

public final class FurnaceStateReader {
   public static final int SLOT_INPUT = 0;
   public static final int SLOT_FUEL = 1;
   public static final int SLOT_RESULT = 2;
   private static final int IDX_LIT_TIME = 0;
   private static final int IDX_LIT_DURATION = 1;
   private static final int IDX_COOKING_PROGRESS = 2;
   private static final int IDX_COOKING_TOTAL_TIME = 3;
   private final PlayerEngineController mod;

   public FurnaceStateReader(PlayerEngineController mod) {
      this.mod = mod;
   }

   public FurnaceStateReader.FurnaceState read(BlockPos pos) {
      Level world = this.mod.getWorld();
      if (world == null) {
         return FurnaceStateReader.FurnaceState.absent(pos, false);
      } else {
         boolean simulated = this.mod.getChunkTracker().isChunkSimulated(pos);
         if (world.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
            ItemStack var13 = furnace.getItem(0).copy();
            ItemStack fuel = furnace.getItem(1).copy();
            ItemStack output = furnace.getItem(2).copy();
            ContainerData data = ((MixinAbstractFurnaceBlockEntity)furnace).getPropertyDelegate();
            int litTime = data.get(0);
            int litDuration = data.get(1);
            int cookingProgress = data.get(2);
            int cookingTotalTime = data.get(3);
            return new FurnaceStateReader.FurnaceState(true, simulated, var13, fuel, output, litTime, litDuration, cookingProgress, cookingTotalTime);
         } else {
            return FurnaceStateReader.FurnaceState.absent(pos, simulated);
         }
      }
   }

   public record FurnaceState(
      boolean present,
      boolean simulated,
      ItemStack input,
      ItemStack fuel,
      ItemStack output,
      int litTime,
      int litDuration,
      int cookingProgress,
      int cookingTotalTime
   ) {
      public boolean isLit() {
         return this.litTime > 0;
      }

      public boolean isCookInProgress() {
         return this.cookingProgress > 0 && this.cookingTotalTime > 0 && this.cookingProgress < this.cookingTotalTime;
      }

      public boolean isStuckNotSimulated() {
         return this.present && !this.simulated;
      }

      public static FurnaceStateReader.FurnaceState absent(BlockPos pos, boolean simulated) {
         return new FurnaceStateReader.FurnaceState(false, simulated, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, 0, 0, 0, 0);
      }
   }
}
