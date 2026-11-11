package com.player2.playerengine.player2api.network;

import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;
import dev.architectury.networking.NetworkManager;
import dev.architectury.networking.NetworkManager.Side;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.TickTask;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Player2ServerNetworking {
   private static final Logger LOGGER = LogManager.getLogger();
   public static final ResourceLocation SYNC_SERVER_PLAYER2 = ResourceLocation.fromNamespaceAndPath("playerengine", "sync_server_player2");

   private Player2ServerNetworking() {
   }

   public static void sendConfigSync(ServerPlayer player) {
      trySendConfigSync(player);
   }

   private static boolean trySendConfigSync(ServerPlayer player) {
      if (player == null) {
         return false;
      }

      try {
         sendConfigSyncUnchecked(player);
         return true;
      } catch (RuntimeException e) {
         LOGGER.warn("Unable to send the Player2 server-config sync to one player; continuing.", e);
         return false;
      }
   }

   private static void sendConfigSyncUnchecked(ServerPlayer player) {
      Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
      RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
      buf.writeBoolean(cfg.isDedicatedClientProxy());
      buf.writeUtf(cfg.getPayerMode().name());
      buf.writeBoolean(cfg.isOwnerOfflineServerContinuation());
      buf.writeUtf(cfg.getHeartbeatClientId());
      buf.writeVarInt(cfg.getBodylangMarkerPauseMs());
      buf.writeBoolean(cfg.isBodylangGaplessPrefetch());
      player.connection.send(NetworkManager.toPacket(Side.S2C, SYNC_SERVER_PLAYER2, buf));
   }

   public static Player2ServerNetworking.ConfigSyncResult sendConfigSyncToAll(MinecraftServer server) {
      if (server == null) {
         return new Player2ServerNetworking.ConfigSyncResult(false, 0, 0, 0, 0);
      }

      List<ServerPlayer> players;
      try {
         players = List.copyOf(server.getPlayerList().getPlayers());
      } catch (RuntimeException e) {
         LOGGER.warn("Unable to snapshot connected players for a Player2 server-config sync.", e);
         return new Player2ServerNetworking.ConfigSyncResult(false, 0, 0, 0, 0);
      }

      int delivered = 0;
      int retryScheduled = 0;
      int failed = 0;

      for (ServerPlayer player : players) {
         if (trySendConfigSync(player)) {
            delivered++;
         } else {
            try {
               server.tell(new TickTask(server.getTickCount() + 1, () -> trySendConfigSync(player)));
               retryScheduled++;
            } catch (RuntimeException retryScheduleFailure) {
               LOGGER.warn("Unable to schedule one Player2 server-config sync retry.", retryScheduleFailure);
               failed++;
            }
         }
      }

      return new Player2ServerNetworking.ConfigSyncResult(true, players.size(), delivered, retryScheduled, failed);
   }

   public record ConfigSyncResult(boolean playerSnapshotSucceeded, int targetCount, int deliveredCount, int retryScheduledCount, int failedCount) {
      public boolean fullySynchronized() {
         return this.playerSnapshotSucceeded && this.deliveredCount == this.targetCount && this.retryScheduledCount == 0 && this.failedCount == 0;
      }
   }
}
