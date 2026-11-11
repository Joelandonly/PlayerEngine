package com.player2.playerengine.automaton.utils;

import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.process.PathingCommand;
import com.player2.playerengine.automaton.api.process.PathingCommandType;
import com.player2.playerengine.automaton.pathing.movement.CalculationContext;

public class PathingCommandContext extends PathingCommand {
   public final CalculationContext desiredCalcContext;

   public PathingCommandContext(Goal goal, PathingCommandType commandType, CalculationContext context) {
      super(goal, commandType);
      this.desiredCalcContext = context;
   }
}
