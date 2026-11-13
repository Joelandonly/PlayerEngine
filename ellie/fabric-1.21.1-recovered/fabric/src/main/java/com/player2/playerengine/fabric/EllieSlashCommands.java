package com.player2.playerengine.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.nativecommands.NativeCommandText;
import com.player2.playerengine.player2api.SetupDirectRoute;
import java.util.Comparator;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

final class EllieSlashCommands {
   private static final Set<String> NO_ARGUMENTS = Set.of(
      "fish", "idle", "leaveboat", "level", "locate_storage", "pickup_drops",
      "reload_settings", "resetmemory", "setup", "stop"
   );
   private static final Set<String> OPTIONAL_ARGUMENTS = Set.of(
      "cleanup_storage", "deposit", "eat_food", "ellie_storage", "follow",
      "harvest_farm", "hero", "read_signs", "scan", "setup_farm"
   );

   private EllieSlashCommands() {}

   static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      var root = Commands.literal("ellie")
         .requires(source -> source.getEntity() instanceof ServerPlayer)
         .executes(context -> {
            context.getSource().sendSuccess(() -> Component.literal("Type /ellie followed by a space to see Ellie's commands."), false);
            return 1;
         });
      for (String id : NativeCommandText.IDS) {
         LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal(id);
         if (NO_ARGUMENTS.contains(id) || OPTIONAL_ARGUMENTS.contains(id)) {
            command.executes(context -> execute(context.getSource(), id));
         }
         if (!NO_ARGUMENTS.contains(id)) {
            // Preserve the existing native syntax, including quoted text and comma-separated item lists.
            command.then(Commands.argument("arguments", StringArgumentType.greedyString())
               .suggests((context, builder) -> EllieSlashSuggestions.suggest(id, context.getSource(), builder))
               .executes(context -> execute(context.getSource(), id + " " + StringArgumentType.getString(context, "arguments"))));
         }
         root.then(command);
      }
      dispatcher.register(root);
   }

   private static int execute(CommandSourceStack source, String line) throws CommandSyntaxException {
      ServerPlayer player = source.getPlayerOrException();
      PlayerEngineController controller = ownedCompanion(player);
      if (controller == null) {
         source.sendFailure(Component.literal("No loaded Ellie companion that you own in this dimension."));
         return 0;
      }
      return SetupDirectRoute.tryRoutePlayerCommand(controller, player.getUUID(), line) ? 1 : 0;
   }

   static PlayerEngineController ownedCompanion(ServerPlayer player) {
      return PlayerEngineController.staticControllers.values().stream()
         .filter(controller -> controller.getOwner() != null && controller.isOwner(player.getUUID()))
         .filter(controller -> controller.getEntity().isAlive()
            && player.serverLevel().getEntity(controller.getEntity().getUUID()) == controller.getEntity())
         .min(Comparator.comparingDouble(controller -> player.distanceToSqr(controller.getEntity())))
         .orElse(null);
   }
}
