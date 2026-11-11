package com.player2.playerengine.automaton.api.pathing.goals;

import com.player2.playerengine.automaton.api.pathing.movement.ActionCosts;
import com.player2.playerengine.automaton.api.utils.SettingsUtil;

public class GoalYLevel implements Goal, ActionCosts {
   public final int level;

   public GoalYLevel(int level) {
      this.level = level;
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      return y == this.level;
   }

   @Override
   public double heuristic(int x, int y, int z) {
      return calculate(this.level, y);
   }

   public static double calculate(int goalY, int currentY) {
      if (currentY > goalY) {
         return FALL_N_BLOCKS_COST[2] / 2.0 * (currentY - goalY);
      } else {
         return currentY < goalY ? (goalY - currentY) * JUMP_ONE_BLOCK_COST : 0.0;
      }
   }

   @Override
   public String toString() {
      return String.format("GoalYLevel{y=%s}", SettingsUtil.maybeCensor(this.level));
   }
}
