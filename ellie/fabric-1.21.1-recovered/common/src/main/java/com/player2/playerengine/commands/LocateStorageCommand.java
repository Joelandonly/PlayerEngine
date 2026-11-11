package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.containeraccess.ContainerKind;
import com.player2.playerengine.containeraccess.StorageLocator;
import com.player2.playerengine.player2api.AgentConversationData;
import com.player2.playerengine.player2api.manager.ConversationManager;
import com.player2.playerengine.util.Debug;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class LocateStorageCommand extends Command {
   public LocateStorageCommand() throws CommandException {
      super(
         "locate_storage",
         "locate_storage. Instantly reports the coordinates of nearby storage containers (chest, trapped chest, barrel, shulker box): the container the prompting user is looking at, the one nearest the user, and the one nearest you. Takes no arguments, never moves you. Feed the returned coordinates to scan_storage / withdraw_from_storage / deposit_to_storage."
      );
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      ServerLevel level = mod.getWorld();
      LivingEntity bot = mod.getEntity();
      ServerPlayer user = resolvePromptingPlayer(mod, level);
      List<String> lines = new ArrayList<>();
      if (user != null) {
         StorageLocator.findLookedAtContainer(level, user, 20.0).ifPresent(located -> lines.add("chest user is looking at: " + render(located)));
         StorageLocator.findNearestContainer(mod, user.position(), 115.0).ifPresent(located -> lines.add("near user: " + render(located)));
      }

      StorageLocator.findNearestContainer(mod, bot.position(), 115.0).ifPresent(located -> lines.add("near you: " + render(located)));
      String payload;
      if (lines.isEmpty()) {
         payload = "no storage containers found within 115 blocks of you or the user";
      } else {
         payload = String.join("\n", lines);
      }

      Debug.logMessage(
         "storage-locate ok lines="
            + lines.size()
            + " user="
            + (user != null ? user.getGameProfile().getName() : "none")
            + " bot="
            + bot.getName().getString()
      );
      this.finishWithInfo(payload);
   }

   private static String render(StorageLocator.Located located) {
      String line = located.canonicalPos().getX() + " " + located.canonicalPos().getY() + " " + located.canonicalPos().getZ();
      if (located.kind() != ContainerKind.CHEST) {
         line = line + " (" + located.kind().token() + ")";
      }

      return line;
   }

   private static ServerPlayer resolvePromptingPlayer(PlayerEngineController mod, ServerLevel level) {
      if (level == null) {
         return null;
      }

      AgentConversationData data = ConversationManager.getOrCreateEventQueueData(mod);
      String initiator = data.getChainInitiatorUsername();
      if (initiator != null && !initiator.isBlank()) {
         ServerPlayer pl = level.getServer().getPlayerList().getPlayerByName(initiator);
         if (pl != null && pl.level() == level) {
            return pl;
         }
      }

      if (mod.getOwner() instanceof ServerPlayer owner && owner.level() == level) {
         return owner;
      } else {
         Optional<ServerPlayer> closest = mod.getClosestPlayer();
         return closest.filter(pl -> pl.level() == level).orElse(null);
      }
   }
}
