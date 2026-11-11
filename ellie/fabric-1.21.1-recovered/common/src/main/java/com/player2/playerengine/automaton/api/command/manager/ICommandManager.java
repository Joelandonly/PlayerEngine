package com.player2.playerengine.automaton.api.command.manager;

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.command.ICommand;
import com.player2.playerengine.automaton.api.command.argument.ICommandArgument;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.command.registry.Registry;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.util.Tuple;

public interface ICommandManager {
   Registry<ICommand> registry = new Registry<>();

   static ICommand getCommand(String name) {
      for (ICommand command : registry.entries) {
         if (command.getNames().contains(name.toLowerCase(Locale.ROOT))) {
            return command;
         }
      }

      return null;
   }

   IBaritone getBaritone();

   Registry<ICommand> getRegistry();

   boolean execute(CommandSourceStack var1, String var2) throws CommandException;

   boolean execute(CommandSourceStack var1, Tuple<String, List<ICommandArgument>> var2) throws CommandException;

   Stream<String> tabComplete(Tuple<String, List<ICommandArgument>> var1);

   Stream<String> tabComplete(String var1);
}
