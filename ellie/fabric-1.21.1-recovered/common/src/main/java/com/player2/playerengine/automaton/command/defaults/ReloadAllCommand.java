package com.player2.playerengine.automaton.command.defaults;

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class ReloadAllCommand extends Command {
   public ReloadAllCommand() {
      super("reloadall");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(0);
      throw new UnsupportedOperationException();
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Reloads Baritone's cache for this world";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList("The reloadall command reloads Baritone's world cache.", "", "Usage:", "> reloadall");
   }
}
