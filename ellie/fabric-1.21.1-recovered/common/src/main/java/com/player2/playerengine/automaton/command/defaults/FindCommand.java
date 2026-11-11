package com.player2.playerengine.automaton.command.defaults;

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.datatypes.BlockById;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class FindCommand extends Command {
   public FindCommand() {
      super("find");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      throw new UnsupportedOperationException();
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return args.tabCompleteDatatype(BlockById.INSTANCE);
   }

   @Override
   public String getShortDesc() {
      return "Find positions of a certain block";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList("", "", "Usage:", "> ");
   }
}
