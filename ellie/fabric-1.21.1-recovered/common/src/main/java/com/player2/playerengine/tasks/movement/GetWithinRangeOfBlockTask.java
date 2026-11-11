package com.player2.playerengine.tasks.movement;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.pathing.goals.GoalNear;
import com.player2.playerengine.tasks.base.Task;
import net.minecraft.core.BlockPos;

public class GetWithinRangeOfBlockTask extends CustomBaritoneGoalTask {
   public final BlockPos blockPos;
   public final int range;

   public GetWithinRangeOfBlockTask(BlockPos blockPos, int range) {
      this.blockPos = blockPos;
      this.range = range;
   }

   @Override
   protected Goal newGoal(PlayerEngineController mod) {
      return new GoalNear(this.blockPos, this.range);
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof GetWithinRangeOfBlockTask task ? task.blockPos.equals(this.blockPos) && task.range == this.range : false;
   }

   @Override
   protected String toDebugString() {
      return "Getting within " + this.range + " blocks of " + this.blockPos.toShortString();
   }
}
