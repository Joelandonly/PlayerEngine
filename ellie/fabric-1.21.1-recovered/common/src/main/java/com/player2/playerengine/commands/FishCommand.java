package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.supervisor.ProjectTaskGateway;
import com.player2.playerengine.tasks.misc.FishTask;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class FishCommand extends Command {
   public FishCommand() throws CommandException {
      super("fish", "Starts fishing automatically.  Example: `fish` to start fishing. NEEDS FISHING ROD");
   }

   @Override
   protected void call(PlayerEngineController controller, ArgParser parser) throws CommandException {
      Consumer<String> onCannotStart = reason -> {
         ServerPlayer target;
         if (controller.getOwner() instanceof ServerPlayer sp) {
            target = sp;
         } else {
            target = controller.getClosestPlayer().orElse(null);
         }

         if (target != null) {
            target.displayClientMessage(Component.translatable("message.playerengine.fish.no_rod"), false);
         }

         this.finishWithError("Cannot fish: no fishing rod in inventory. The bot did not fish. Give the bot a fishing rod first.");
      };
      ProjectTaskGateway.run(controller, new FishTask(onCannotStart), () -> this.finish());
   }
}
