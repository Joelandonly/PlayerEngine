package com.player2.playerengine.automaton.command.defaults;

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.cache.IRememberedInventory;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.command.exception.CommandInvalidStateException;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Stream;
import net.minecraft.world.item.ItemStack;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;

public class ChestsCommand extends Command {
   public ChestsCommand() {
      super("chests");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(0);
      Set<Entry<BlockPos, IRememberedInventory>> entries = baritone.getEntityContext().worldData().getContainerMemory().getRememberedInventories().entrySet();
      if (entries.isEmpty()) {
         throw new CommandInvalidStateException("No remembered inventories");
      }

      for (Entry<BlockPos, IRememberedInventory> entry : entries) {
         BetterBlockPos pos = new BetterBlockPos(entry.getKey());
         IRememberedInventory inv = entry.getValue();
         this.logDirect(source, pos.toString());

         for (ItemStack item : inv.getContents()) {
            MutableComponent component = (MutableComponent)item.getHoverName();
            component.append(String.format(" x %d", item.getCount()));
            this.logDirect(source, component);
         }
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Display remembered inventories";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList("The chests command lists remembered inventories, I guess?", "", "Usage:", "> chests");
   }
}
