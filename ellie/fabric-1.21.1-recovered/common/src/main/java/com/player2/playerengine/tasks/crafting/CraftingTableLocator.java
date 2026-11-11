package com.player2.playerengine.tasks.crafting;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.construction.PlaceBlockNearbyTask;
import com.player2.playerengine.util.helpers.WorldHelper;
import java.util.Optional;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;

public final class CraftingTableLocator {
   private static final double REACH = 3.5;

   private CraftingTableLocator() {
   }

   public static Optional<CraftingTableTarget> findReachable(PlayerEngineController mod, BlockPos remembered) {
      if (remembered != null && mod.getWorld().getBlockState(remembered).is(Blocks.CRAFTING_TABLE) && isReachable(mod, remembered)) {
         Optional var7 = Optional.of(new CraftingTableTarget(remembered, false));
         return (Optional<CraftingTableTarget>)CraftTableApproachRecovery.filterReachable(mod, CraftingTableMemory.filterReachable(mod, var7));
      }

      if (mod.getModSettings().isPreferLocalCraftingTable()) {
         Optional<BlockPos> nearest = mod.getBlockScanner().getNearestBlock(Blocks.CRAFTING_TABLE);
         if (nearest.isPresent() && isReachable(mod, nearest.get())) {
            Optional var3 = Optional.of(new CraftingTableTarget(nearest.get(), false));
            return (Optional<CraftingTableTarget>)CraftTableApproachRecovery.filterReachable(mod, CraftingTableMemory.filterReachable(mod, var3));
         }
      } else {
         Optional<BlockPos> nearest = mod.getBlockScanner().getNearestBlock(Blocks.CRAFTING_TABLE);
         if (nearest.isPresent()) {
            Optional var6 = Optional.of(new CraftingTableTarget(nearest.get(), false));
            return (Optional<CraftingTableTarget>)CraftTableApproachRecovery.filterReachable(mod, CraftingTableMemory.filterReachable(mod, var6));
         }
      }

      Optional var5 = Optional.empty();
      return (Optional<CraftingTableTarget>)CraftTableApproachRecovery.filterReachable(mod, CraftingTableMemory.filterReachable(mod, var5));
   }

   public static boolean isReachable(PlayerEngineController mod, BlockPos pos) {
      boolean var2 = pos.closerToCenterThan(mod.getPlayer().position(), 3.5) && WorldHelper.canReach(mod, pos);
      return CraftingTableMemory.observeReachable(mod, pos, var2);
   }

   public static Optional<BlockPos> findReusableNearby(PlayerEngineController mod, double radius) {
      Optional<BlockPos> nearest = mod.getBlockScanner().getNearestBlock(Blocks.CRAFTING_TABLE);
      if (nearest.isEmpty()) {
         nearest = Optional.empty();
         return (Optional<BlockPos>)CraftTableApproachRecovery.filterReusable(mod, CraftingTableMemory.filterReusable(mod, radius, nearest));
      } else {
         BlockPos pos = nearest.get();
         if (!mod.getWorld().getBlockState(pos).is(Blocks.CRAFTING_TABLE)) {
            nearest = Optional.empty();
            return (Optional<BlockPos>)CraftTableApproachRecovery.filterReusable(mod, CraftingTableMemory.filterReusable(mod, radius, nearest));
         } else if (!pos.closerToCenterThan(mod.getPlayer().position(), radius)) {
            nearest = Optional.empty();
            return (Optional<BlockPos>)CraftTableApproachRecovery.filterReusable(mod, CraftingTableMemory.filterReusable(mod, radius, nearest));
         } else if (!WorldHelper.canReach(mod, pos)) {
            nearest = Optional.empty();
            return (Optional<BlockPos>)CraftTableApproachRecovery.filterReusable(mod, CraftingTableMemory.filterReusable(mod, radius, nearest));
         } else {
            nearest = Optional.of(pos);
            return (Optional<BlockPos>)CraftTableApproachRecovery.filterReusable(mod, CraftingTableMemory.filterReusable(mod, radius, nearest));
         }
      }
   }

   public static Task placeNearbyTask() {
      return new PlaceBlockNearbyTask(Blocks.CRAFTING_TABLE);
   }

   public static Task obtainTableItemTask(PlayerEngineController mod) {
      if (findReachable(mod, null).isPresent()) {
         return null;
      } else {
         return mod.getItemStorage().hasItem(Items.CRAFTING_TABLE) ? null : null;
      }
   }
}
