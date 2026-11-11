package com.player2.playerengine.tasks.movement;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.utils.input.Input;
import com.player2.playerengine.automaton.pathing.movement.MovementHelper;
import com.player2.playerengine.automaton.utils.BlockStateInterface;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.helpers.LookHelper;
import com.player2.playerengine.util.helpers.StorageHelper;
import com.player2.playerengine.util.time.TimerGame;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerLevel;

public class GetOutOfWaterTask extends CustomBaritoneGoalTask {
   private boolean startedShimmying = false;
   private final TimerGame shimmyTaskTimer = new TimerGame(5.0);

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      PlayerEngineController mod = this.controller;
      if (mod.getPlayer().getAirSupply() >= mod.getPlayer().getMaxAirSupply() && !mod.getPlayer().isUnderWater()) {
         boolean hasBlockBelow = false;

         for (int i = 0; i < 3; i++) {
            if (mod.getWorld().getBlockState(mod.getPlayer().getOnPos().below(i)).getBlock() != Blocks.WATER) {
               hasBlockBelow = true;
            }
         }

         boolean hasAirAbove = mod.getWorld().getBlockState(mod.getPlayer().blockPosition().above(2)).getBlock().equals(Blocks.AIR);
         if (hasAirAbove && hasBlockBelow && StorageHelper.getNumberOfThrowawayBlocks(mod) > 0) {
            mod.getInputControls().tryPress(Input.JUMP);
            if (mod.getPlayer().onGround()) {
               if (!this.startedShimmying) {
                  this.startedShimmying = true;
                  this.shimmyTaskTimer.reset();
               }

               return new SafeRandomShimmyTask();
            }

            mod.getSlotHandler().forceEquipItem(mod.getBaritoneSettings().acceptableThrowawayItems.get().toArray(new Item[0]));
            LookHelper.lookAt(mod, mod.getPlayer().getOnPos().below());
            mod.getInputControls().tryPress(Input.CLICK_RIGHT);
         }

         return super.onTick();
      } else {
         return super.onTick();
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected Goal newGoal(PlayerEngineController mod) {
      return new GetOutOfWaterTask.EscapeFromWaterGoal(mod);
   }

   @Override
   protected boolean isEqual(Task other) {
      return false;
   }

   @Override
   protected String toDebugString() {
      return "";
   }

   @Override
   public boolean isFinished() {
      return !this.controller.getPlayer().isInWater() && this.controller.getPlayer().onGround();
   }

   private class EscapeFromWaterGoal implements Goal {
      private PlayerEngineController mod;
      private final BlockStateInterface safeWaterView;
      private final ServerLevel safeWaterWorld;

      private EscapeFromWaterGoal(PlayerEngineController mod) {
         this.mod = mod;
         ServerLevel var3 = mod.getWorld();
         this.safeWaterWorld = var3;
         BlockStateInterface var4;
         if (var3 != null) {
            var4 = new BlockStateInterface(var3);
         } else {
            var4 = null;
         }

         this.safeWaterView = var4;
      }

      private boolean isWater(int x, int y, int z) {
         return this.safeWaterView != null && this.mod.getWorld() == this.safeWaterWorld && this.safeWaterView.isLoaded(x, z)
            ? MovementHelper.isWater(this.safeWaterView.get0(x, y, z))
            : true;
      }

      private boolean isWaterAdjacent(int x, int y, int z) {
         return this.isWater(x + 1, y, z)
            || this.isWater(x - 1, y, z)
            || this.isWater(x, y, z + 1)
            || this.isWater(x, y, z - 1)
            || this.isWater(x + 1, y, z - 1)
            || this.isWater(x + 1, y, z + 1)
            || this.isWater(x - 1, y, z - 1)
            || this.isWater(x - 1, y, z + 1);
      }

      @Override
      public boolean isInGoal(int x, int y, int z) {
         return !this.isWater(x, y, z) && !this.isWaterAdjacent(x, y, z);
      }

      @Override
      public double heuristic(int x, int y, int z) {
         if (this.isWater(x, y, z)) {
            return 1.0;
         } else {
            return this.isWaterAdjacent(x, y, z) ? 0.5 : 0.0;
         }
      }
   }
}
