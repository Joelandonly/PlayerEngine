package com.player2.playerengine.automaton.command.defaults;

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class SchematicaCommand extends Command {
   public SchematicaCommand() {
      super("schematica");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(0);
      baritone.getBuilderProcess().buildOpenSchematic();
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Builds the loaded schematic";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList("Builds the schematica currently open in Schematica.", "", "Usage:", "> schematica");
   }
}
