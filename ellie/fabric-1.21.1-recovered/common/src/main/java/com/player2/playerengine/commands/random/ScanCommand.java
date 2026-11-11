package com.player2.playerengine.commands.random;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.BlockScanner;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.util.helpers.FuzzySearchHelper;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

public class ScanCommand extends Command {
   public ScanCommand() throws CommandException {
      super("scan", "Locates nearest block", new Arg<>(String.class, "block", "dirt", 0));
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      String blockStr = parser.get(String.class).toLowerCase(Locale.ROOT);
      String normalised = blockStr.contains(":") ? blockStr : "minecraft:" + blockStr;
      ResourceLocation parsed = ResourceLocation.tryParse(normalised);
      Block block = parsed != null ? (Block)BuiltInRegistries.BLOCK.getOptional(parsed).orElse(null) : null;
      if (block == null) {
         List<String> allBlockNames = BuiltInRegistries.BLOCK.keySet().stream().<String>map(ResourceLocation::toString).collect(Collectors.toList());
         String closest = FuzzySearchHelper.getClosestMatchMinecraftItems(blockStr, allBlockNames);
         String msg = "Block named: \""
            + blockStr
            + "\" not a valid block. Perhaps the user meant \""
            + closest
            + "\"?"
            + (blockStr.contains("log") ? " Can try 'log' as well" : "");
         mod.log(msg);
         this.finishWithNote(msg);
      } else {
         BlockScanner blockScanner = mod.getBlockScanner();
         Optional<BlockPos> p = blockScanner.getNearestBlock(block, mod.getPlayer().position());
         String msg;
         if (p.isPresent()) {
            BlockPos pos = p.get();
            msg = "Closest " + blockStr + ": (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
         } else {
            msg = "No blocks of type " + blockStr + " found nearby.";
         }

         mod.log(msg);
         this.finishWithNote(msg);
      }
   }
}
