package com.player2.playerengine.automaton.command.defaults;

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.datatypes.BlockById;
import com.player2.playerengine.automaton.api.command.datatypes.ForBlockOptionalMeta;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.utils.BlockOptionalMeta;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class MineCommand extends Command {
   public MineCommand() {
      super("mine");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      int quantity = args.getAsOrDefault(Integer.class, 0);
      args.requireMin(1);
      List<BlockOptionalMeta> boms = new ArrayList<>();

      while (args.hasAny()) {
         boms.add(args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE));
      }

      this.logDirect(source, String.format("Mining %s", boms.toString()));
      baritone.getMineProcess().mine(quantity, boms.toArray(new BlockOptionalMeta[0]));
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return args.tabCompleteDatatype(BlockById.INSTANCE);
   }

   @Override
   public String getShortDesc() {
      return "Mine some blocks";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "The mine command allows you to tell Automatone to search for and mine individual blocks.",
         "",
         "The specified blocks can be ores (which are commonly cached), or any other block.",
         "",
         "Also see the legitMine settings (see #set l legitMine).",
         "",
         "Usage:",
         "> mine diamond_ore - Mines all diamonds it can find.",
         "> mine redstone_ore lit_redstone_ore - Mines redstone ore.",
         "> mine log:0 - Mines only oak logs."
      );
   }
}
