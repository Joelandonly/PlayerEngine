package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.tasks.construction.build_structure.BuildStructureTask;
import com.player2.playerengine.tasks.movement.BodyLanguageTask;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

public class BodyLanguageCommand extends Command {
   public BodyLanguageCommand() throws CommandException {
      super("bodylang", "Perform a body language gesture. Action must be one of: greeting, nod_head, shake_head, victory.", new Arg<>(String.class, "action"));
   }

   @Override
   protected void call(PlayerEngineController var1, ArgParser var2) throws CommandException {
      String var3 = var2.get(String.class);

      BodyLanguageTask.Type var4;
      try {
         var4 = BodyLanguageTask.resolveType(var3);
      } catch (IllegalArgumentException var9) {
         MinecraftServer var6 = var1.getWorld().getServer();
         if (var6 != null && var1.getOwner() != null) {
            UUID var7 = var1.getOwner().getUUID();
            ServerPlayer var8 = var6.getPlayerList().getPlayer(var7);
            if (var8 != null) {
               var8.displayClientMessage(Component.translatable("message.playerengine.bodylang.unknown_gesture", new Object[]{var3}), false);
            }
         }

         this.finishWithError("Unknown bodylang action '" + var3 + "'. Valid: greeting, nod_head, shake_head, victory.");
         return;
      }

      if (var1.getUserTaskChain().getCurrentTask() instanceof BuildStructureTask) {
         this.finish();
      } else {
         var1.runUserTask(new BodyLanguageTask(var4), () -> this.finish());
      }
   }
}
