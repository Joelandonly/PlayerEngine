package com.player2.playerengine.tasks.movement;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.pathing.goals.GoalNear;
import com.player2.playerengine.tasks.base.ITaskRequiresGrounded;
import com.player2.playerengine.tasks.base.Task;
import net.minecraft.core.BlockPos;

public class ApproachNearBlockPosTask extends CustomBaritoneGoalTask implements ITaskRequiresGrounded {
   private final BlockPos center;
   private final int maxBlocks;

   public ApproachNearBlockPosTask(BlockPos center, int maxBlocks) {
      super(false);
      this.center = center.immutable();
      this.maxBlocks = Math.max(1, maxBlocks);
   }

   @Override
   protected Goal newGoal(PlayerEngineController mod) {
      return new GoalNear(this.center, this.maxBlocks);
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof ApproachNearBlockPosTask t && t.center.equals(this.center) && t.maxBlocks == this.maxBlocks;
   }

   @Override
   protected String toDebugString() {
      return "ApproachNearBlockPosTask center=" + this.center + " maxBlocks=" + this.maxBlocks;
   }
}
