package com.player2.playerengine.automaton.command.defaults;

import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.utils.accessor.ServerCommandSourceAccessor;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;

public class ClickCommand extends Command {
   public static final ResourceLocation OPEN_CLICK_SCREEN = PlayerEngine.id("open_click_screen");

   public ClickCommand() {
      super("click");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(0);

      try {
         CommandSource var6 = ((ServerCommandSourceAccessor)source).automatone$getOutput();
      } catch (Throwable var6) {
         PlayerEngine.LOGGER.error("Failed to open click screen, is this a dedicated server?", var6);
      }

      this.logDirect(source, "aight dude");
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Open click";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList("Opens click dude", "", "Usage:", "> click");
   }
}
