package com.player2.playerengine.automaton.api.process;

import com.player2.playerengine.automaton.api.pathing.goals.Goal;

public interface ICustomGoalProcess extends IBaritoneProcess {
   void setGoal(Goal var1);

   void path();

   Goal getGoal();

   default void setGoalAndPath(Goal goal) {
      this.setGoal(goal);
      this.path();
   }
}
