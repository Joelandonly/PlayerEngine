package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.MiningRequirement;
import com.player2.playerengine.util.helpers.StorageHelper;
import java.util.Optional;
import net.minecraft.world.item.Items;

public class SatisfyMiningRequirementTask extends Task {
   private static final int MAX_FAILED_CYCLES = 3;
   private final MiningRequirement requirement;
   private Task childTask;
   private int failedCycles;
   private boolean acquisitionFailed;

   public SatisfyMiningRequirementTask(MiningRequirement requirement) {
      this.requirement = requirement;
   }

   @Override
   protected void onStart() {
      if (this.childTask != null && this.childTask.stopped()) {
         this.childTask = null;
      }
   }

   @Override
   protected Task onTick() {
      if (this.requirement == MiningRequirement.HAND) {
         return null;
      }

      if (this.childTask != null && (this.childTask.stopped() || this.childTask.isFinished())) {
         if (!StorageHelper.miningRequirementMetInventory(this.controller, this.requirement)) {
            this.failedCycles++;
            this.childTask = null;
            if (this.failedCycles >= 3) {
               this.acquisitionFailed = true;
               this.setDebugState("Could not acquire a " + this.requirement + " pickaxe; giving up.");
               this.stop();
               return null;
            }
         } else {
            this.childTask = null;
         }
      }

      if (this.childTask == null) {
         this.childTask = switch (this.requirement) {
            case WOOD -> TaskCatalogue.getItemTask(Items.WOODEN_PICKAXE, 1);
            case STONE -> TaskCatalogue.getItemTask(Items.STONE_PICKAXE, 1);
            case IRON -> TaskCatalogue.getItemTask(Items.IRON_PICKAXE, 1);
            case DIAMOND -> TaskCatalogue.getItemTask(Items.DIAMOND_PICKAXE, 1);
            default -> null;
         };
      }

      return this.childTask;
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof SatisfyMiningRequirementTask task ? task.requirement == this.requirement : false;
   }

   @Override
   protected String toDebugString() {
      return "Satisfy Mining Req: " + this.requirement;
   }

   @Override
   public boolean isFinished() {
      return StorageHelper.miningRequirementMetInventory(this.controller, this.requirement);
   }

   public boolean acquisitionFailed() {
      return this.acquisitionFailed;
   }

   public Optional<String> humanFailureReason() {
      return this.acquisitionFailed ? Optional.of("I can't make a " + this.tierWord() + " pickaxe right now — I'm missing the materials.") : Optional.empty();
   }

   public Optional<String> machineFailureReason() {
      return this.acquisitionFailed ? Optional.of("could_not_acquire_tool:" + this.requirement.name().toLowerCase()) : Optional.empty();
   }

   private String tierWord() {
      return switch (this.requirement) {
         case WOOD -> "wooden";
         case STONE -> "stone";
         case IRON -> "iron";
         case DIAMOND -> "diamond";
         default -> "suitable";
      };
   }
}
