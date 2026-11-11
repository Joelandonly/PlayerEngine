package com.player2.playerengine.automaton.command.defaults;

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.process.ICustomGoalProcess;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class PathCommand extends Command {
   public PathCommand() {
      super("path");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      ICustomGoalProcess customGoalProcess = baritone.getCustomGoalProcess();
      args.requireMax(0);
      customGoalProcess.path();
      this.logDirect(source, "Now pathing");
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Start heading towards the goal";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList("The path command makes the targeted entity head towards the current goal.", "", "Usage:", "> path - Start the pathing.");
   }
}
