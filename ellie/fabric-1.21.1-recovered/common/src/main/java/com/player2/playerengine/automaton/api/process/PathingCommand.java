package com.player2.playerengine.automaton.api.process;

import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import java.util.Objects;

public class PathingCommand {
   public final Goal goal;
   public final PathingCommandType commandType;

   public PathingCommand(Goal goal, PathingCommandType commandType) {
      Objects.requireNonNull(commandType);
      this.goal = goal;
      this.commandType = commandType;
   }

   @Override
   public String toString() {
      return this.commandType + " " + this.goal;
   }
}
