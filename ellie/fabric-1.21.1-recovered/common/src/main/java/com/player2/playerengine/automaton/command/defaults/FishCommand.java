package com.player2.playerengine.automaton.command.defaults;

import com.player2.playerengine.automaton.Baritone;
import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class FishCommand extends Command {
   public FishCommand() {
      super("fish");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(0);
      ((Baritone)baritone).getFishingProcess().fish();
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Start fishing";
   }

   @Override
   public List<String> getLongDesc() {
      return Collections.singletonList("Starts fishing automatically.");
   }
}
