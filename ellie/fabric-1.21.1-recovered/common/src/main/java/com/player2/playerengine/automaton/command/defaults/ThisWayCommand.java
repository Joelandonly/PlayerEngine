package com.player2.playerengine.automaton.command.defaults;

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.pathing.goals.GoalXZ;
import com.player2.playerengine.automaton.api.utils.IEntityContext;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class ThisWayCommand extends Command {
   public ThisWayCommand() {
      super("thisway", "forward");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireExactly(1);
      IEntityContext ctx = baritone.getEntityContext();
      GoalXZ goal = GoalXZ.fromDirection(ctx.feetPosAsVec(), ctx.entity().yHeadRot, args.getAs(Double.class));
      baritone.getCustomGoalProcess().setGoal(goal);
      this.logDirect(source, String.format("Goal: %s", goal));
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Travel in your current direction";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "Creates a GoalXZ some amount of blocks in the direction you're currently looking",
         "",
         "Usage:",
         "> thisway <distance> - makes a GoalXZ distance blocks in front of you"
      );
   }
}
