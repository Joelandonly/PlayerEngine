package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.container.SmeltInFurnaceTask;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.SmeltTarget;
import net.minecraft.world.item.Items;

public class CollectIronIngotTask extends ResourceTask {
   private final int count;

   public CollectIronIngotTask(int count) {
      super(Items.IRON_INGOT, count);
      this.count = count;
   }

   @Override
   protected boolean shouldAvoidPickingUp(PlayerEngineController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(PlayerEngineController mod) {
      mod.getBehaviour().push();
   }

   @Override
   protected Task onResourceTick(PlayerEngineController mod) {
      String[] childReason = new String[1];
      this.thisOrChildSatisfies(task -> {
         if (task instanceof SmeltInFurnaceTask leaf && leaf.stopped()) {
            leaf.getAggregatedFailureReason().ifPresent(r -> childReason[0] = r);
            return childReason[0] != null;
         } else {
            return false;
         }
      });
      if (childReason[0] != null) {
         this.recordFailureReason(childReason[0]);
         this.stop(this);
         return null;
      } else {
         return new SmeltInFurnaceTask(new SmeltTarget(new ItemTarget(Items.IRON_INGOT, this.count), new ItemTarget(Items.RAW_IRON, this.count)));
      }
   }

   @Override
   protected void onResourceStop(PlayerEngineController mod, Task interruptTask) {
      mod.getBehaviour().pop();
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectIronIngotTask same && same.count == this.count;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " iron.";
   }
}
