package com.player2.playerengine.automaton.command.defaults;

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.pathing.goals.GoalAxis;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class AxisCommand extends Command {
   public AxisCommand() {
      super("axis", "highway");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(0);
      Goal goal = new GoalAxis(baritone.settings().axisHeight.get());
      baritone.getCustomGoalProcess().setGoal(goal);
      this.logDirect(source, String.format("Goal: %s", goal.toString()));
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Set a goal to the axes";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList("The axis command sets a goal that makes an entity head towards the nearest axis. That is, X=0 or Z=0.", "", "Usage:", "> axis");
   }
}
