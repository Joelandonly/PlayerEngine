package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.movement.DefaultGoToDimensionTask;
import com.player2.playerengine.util.Dimension;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.WorldHelper;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Restored from upstream 1.21.1 source, checked against exact PR29 Vineflower
 * onResourceTick bytecode dump. Resource-target arithmetic intentionally retains
 * the original implementation for parity; gameplay fixes need separate review.
 */
public class CollectMagmaCreamTask extends ResourceTask {
   private final int count;

   public CollectMagmaCreamTask(int count) {
      super(Items.MAGMA_CREAM, count);
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
      int currentCream = mod.getItemStorage().getItemCount(new Item[]{Items.MAGMA_CREAM});
      int neededCream = this.count - currentCream;
      switch (WorldHelper.getCurrentDimension(this.controller).ordinal()) {
         case 1:
            if (mod.getEntityTracker().entityFound(MagmaCube.class)) {
               this.setDebugState("Killing Magma cube");
               return new KillAndLootTask(MagmaCube.class, new ItemTarget[]{new ItemTarget(Items.MAGMA_CREAM)});
            }
            int currentBlazePowderPotential = mod.getItemStorage().getItemCount(new Item[]{Items.BLAZE_POWDER})
               + mod.getItemStorage().getItemCount(new Item[]{Items.BLAZE_ROD});
            if (neededCream > currentBlazePowderPotential) {
               this.setDebugState("Getting blaze powder");
               return TaskCatalogue.getItemTask(Items.BLAZE_POWDER, neededCream - currentCream);
            }
            this.setDebugState("Going back to overworld to kill slimes, we have enough blaze powder and no nearby magma cubes.");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
         case 2:
            int currentSlime = mod.getItemStorage().getItemCount(new Item[]{Items.SLIME_BALL});
            if (neededCream > currentSlime) {
               this.setDebugState("Getting slime balls");
               return TaskCatalogue.getItemTask(Items.SLIME_BALL, neededCream - currentCream);
            }
            this.setDebugState("Going to nether to get blaze powder and/or kill magma cubes");
            return new DefaultGoToDimensionTask(Dimension.NETHER);
         case 3:
            this.setDebugState("Going to overworld, no magma cream materials exist here.");
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
      return other instanceof CollectMagmaCreamTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " Magma cream.";
   }
}
