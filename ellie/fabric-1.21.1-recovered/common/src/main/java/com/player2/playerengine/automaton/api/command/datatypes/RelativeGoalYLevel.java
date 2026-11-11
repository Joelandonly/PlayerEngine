package com.player2.playerengine.automaton.api.command.datatypes;

import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.pathing.goals.GoalYLevel;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import java.util.stream.Stream;
import net.minecraft.util.Mth;

public enum RelativeGoalYLevel implements IDatatypePost<GoalYLevel, BetterBlockPos> {
   INSTANCE;

   public GoalYLevel apply(IDatatypeContext ctx, BetterBlockPos origin) throws CommandException {
      if (origin == null) {
         origin = BetterBlockPos.ORIGIN;
      }

      return new GoalYLevel(Mth.floor(ctx.getConsumer().getDatatypePost(RelativeCoordinate.INSTANCE, (double)origin.y)));
   }

   @Override
   public Stream<String> tabComplete(IDatatypeContext ctx) {
      IArgConsumer consumer = ctx.getConsumer();
      return consumer.hasAtMost(1) ? consumer.tabCompleteDatatype(RelativeCoordinate.INSTANCE) : Stream.empty();
   }
}
