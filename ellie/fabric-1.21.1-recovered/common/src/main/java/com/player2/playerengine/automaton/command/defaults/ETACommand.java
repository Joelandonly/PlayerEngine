package com.player2.playerengine.automaton.command.defaults;

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.behavior.IPathingBehavior;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.command.exception.CommandInvalidStateException;
import com.player2.playerengine.automaton.api.pathing.calc.IPathingControlManager;
import com.player2.playerengine.automaton.api.process.IBaritoneProcess;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class ETACommand extends Command {
   public ETACommand() {
      super("eta");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(0);
      IPathingControlManager pathingControlManager = baritone.getPathingControlManager();
      IBaritoneProcess process = pathingControlManager.mostRecentInControl().orElse(null);
      if (process == null) {
         throw new CommandInvalidStateException("No process in control");
      }

      IPathingBehavior pathingBehavior = baritone.getPathingBehavior();
      this.logDirect(
         source,
         String.format(
            "Next segment: %.2f\nGoal: %.2f", pathingBehavior.ticksRemainingInSegment().orElse(-1.0), pathingBehavior.estimatedTicksToGoal().orElse(-1.0)
         )
      );
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "View the current ETA";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "The ETA command provides information about the estimated time until the next segment.",
         "and the goal",
         "",
         "Be aware that the ETA to your goal is really unprecise",
         "",
         "Usage:",
         "> eta - View ETA, if present"
      );
   }
}
