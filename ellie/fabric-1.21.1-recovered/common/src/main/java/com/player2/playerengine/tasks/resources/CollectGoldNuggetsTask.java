package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.CraftInInventoryTask;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.movement.DefaultGoToDimensionTask;
import com.player2.playerengine.util.CraftingRecipe;
import com.player2.playerengine.util.Dimension;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.MiningRequirement;
import com.player2.playerengine.util.RecipeTarget;
import com.player2.playerengine.util.helpers.WorldHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;

/**
 * Restored from upstream 1.21.1 source, checked against exact PR29 Vineflower
 * onResourceTick bytecode dump. Preserve the original dimension/recipe behavior.
 * The intermediary names will be normalized with the rest of the project.
 */
public class CollectGoldNuggetsTask extends ResourceTask {
   private final int count;

   public CollectGoldNuggetsTask(int count) {
      super(Items.GOLD_NUGGET, count);
      this.count = count;
   }

   @Override
   protected boolean shouldAvoidPickingUp(PlayerEngineController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(PlayerEngineController mod) {
   }

   @Override
   protected Task onResourceTick(PlayerEngineController mod) {
      switch (WorldHelper.getCurrentDimension(this.controller).ordinal()) {
         case 1:
            this.setDebugState("Getting gold ingots to convert to nuggets");
            int potentialNuggies = mod.getItemStorage().getItemCount(new Item[]{Items.GOLD_NUGGET})
               + mod.getItemStorage().getItemCount(new Item[]{Items.GOLD_INGOT}) * 9;
            if (potentialNuggies >= this.count && mod.getItemStorage().hasItem(new Item[]{Items.GOLD_INGOT})) {
               return new CraftInInventoryTask(
                  new RecipeTarget(Items.GOLD_NUGGET, this.count,
                     CraftingRecipe.newShapedRecipe("golden_nuggets",
                        new ItemTarget[]{new ItemTarget(Items.GOLD_INGOT, 1), null, null, null}, 9)));
            }
            int nuggiesStillNeeded = this.count - potentialNuggies;
            return TaskCatalogue.getItemTask(Items.GOLD_INGOT, (int)Math.ceil(nuggiesStillNeeded / 9.0));
         case 2:
            this.setDebugState("Mining nuggies");
            return new MineAndCollectTask(Items.GOLD_NUGGET, this.count,
               new Block[]{Blocks.NETHER_GOLD_ORE, Blocks.GILDED_BLACKSTONE}, MiningRequirement.WOOD);
         case 3:
            this.setDebugState("Going to overworld");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
         default:
            this.setDebugState("INVALID DIMENSION??: " + WorldHelper.getCurrentDimension(this.controller));
            return null;
      }
   }

   @Override
   protected void onResourceStop(PlayerEngineController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectGoldNuggetsTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " nuggets";
   }
}
