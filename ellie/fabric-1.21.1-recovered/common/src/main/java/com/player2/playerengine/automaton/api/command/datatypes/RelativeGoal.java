package com.player2.playerengine.automaton.api.command.datatypes;

import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.pathing.goals.GoalBlock;
import com.player2.playerengine.automaton.api.pathing.goals.GoalXZ;
import com.player2.playerengine.automaton.api.pathing.goals.GoalYLevel;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import java.util.stream.Stream;

public enum RelativeGoal implements IDatatypePost<Goal, BetterBlockPos> {
   INSTANCE;

   public Goal apply(IDatatypeContext ctx, BetterBlockPos origin) throws CommandException {
      if (origin == null) {
         origin = BetterBlockPos.ORIGIN;
      }

      IArgConsumer consumer = ctx.getConsumer();
      GoalBlock goalBlock = consumer.peekDatatypePostOrNull(RelativeGoalBlock.INSTANCE, origin);
      if (goalBlock != null) {
         return goalBlock;
      }

      GoalXZ goalXZ = consumer.peekDatatypePostOrNull(RelativeGoalXZ.INSTANCE, origin);
      if (goalXZ != null) {
         return goalXZ;
      }

      GoalYLevel goalYLevel = consumer.peekDatatypePostOrNull(RelativeGoalYLevel.INSTANCE, origin);
      return goalYLevel != null ? goalYLevel : new GoalBlock(origin);
   }

   @Override
   public Stream<String> tabComplete(IDatatypeContext ctx) {
      return ctx.getConsumer().tabCompleteDatatype(RelativeCoordinate.INSTANCE);
   }
}
