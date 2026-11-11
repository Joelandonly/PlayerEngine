package com.player2.playerengine.automaton.command.defaults;

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.command.argument.ArgConsumer;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class CommandAlias extends Command {
   private final String shortDesc;
   public final String target;

   public CommandAlias(List<String> names, String shortDesc, String target) {
      super(names.toArray(new String[0]));
      this.shortDesc = shortDesc;
      this.target = target;
   }

   public CommandAlias(String name, String shortDesc, String target) {
      super(name);
      this.shortDesc = shortDesc;
      this.target = target;
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      baritone.getCommandManager().execute(source, String.format("%s %s", this.target, args.rawRest()));
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return ((ArgConsumer)args).getBaritone().getCommandManager().tabComplete(String.format("%s %s", this.target, args.rawRest()));
   }

   @Override
   public String getShortDesc() {
      return this.shortDesc;
   }

   @Override
   public List<String> getLongDesc() {
      return Collections.singletonList(String.format("This command is an alias, for: %s ...", this.target));
   }
}
