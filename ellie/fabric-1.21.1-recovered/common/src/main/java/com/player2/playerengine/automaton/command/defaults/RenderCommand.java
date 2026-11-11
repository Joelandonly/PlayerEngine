package com.player2.playerengine.automaton.command.defaults;

import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;

public class RenderCommand extends Command {
   public RenderCommand() {
      super("render");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(0);

      try {
         Minecraft mc = Minecraft.getInstance();
         mc.execute(() -> {
            BetterBlockPos origin = baritone.getEntityContext().feetPos();
            int renderDistance = ((Integer)mc.options.renderDistance().get() + 1) * 16;
            mc.levelRenderer.setBlocksDirty(origin.x - renderDistance, 0, origin.z - renderDistance, origin.x + renderDistance, 255, origin.z + renderDistance);
            this.logDirect(source, "Done");
         });
      } catch (LinkageError t) {
         this.logDirect(source, Component.translatable("message.playerengine.commands.render_client_only"));
         PlayerEngine.LOGGER.error("Failed to render, is this a dedicated server?", t);
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Fix glitched chunks";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList("The render command fixes glitched chunk rendering without having to reload all of them.", "", "Usage:", "> render");
   }
}
