package com.player2.playerengine.player2api.network;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.executor.StopReason;
import com.player2.playerengine.player2api.AgentConversationData;
import com.player2.playerengine.player2api.Player2ClientApiBridge;
import com.player2.playerengine.player2api.auth.TokenStorage;
import com.player2.playerengine.player2api.config.Player2PayerMode;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;
import com.player2.playerengine.player2api.manager.ConversationManager;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Player2DisconnectHandler {
   private static final Logger LOGGER = LogManager.getLogger();

   private Player2DisconnectHandler() {
   }

   public static void onPlayerQuit(Player player) {
      if (player instanceof ServerPlayer leaving) {
         Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
         UUID leavingId = leaving.getUUID();
         TtsClientPreferenceStore.clear(leavingId);
         String leavingName = leaving.getName().getString();

         for (AgentConversationData data : ConversationManager.queueData.values()) {
            PlayerEngineController c = data.getMod();
            Player owner = c.getOwner();
            String ownerName = owner != null ? owner.getName().getString() : "";
            String initiator = data.getChainInitiatorUsername();
            boolean ownerDisconnected = owner != null && owner.getUUID().equals(leavingId);
            boolean stopForPrompter = initiator != null
               && (cfg.isDedicatedClientProxy() || cfg.getPayerMode() == Player2PayerMode.PROMPTER_PAYS)
               && initiator.equals(leavingName);
            boolean stopForOwner = cfg.getPayerMode() == Player2PayerMode.OWNER_PAYS_ALL
               && ownerDisconnected
               && (
                  !cfg.isOwnerOfflineServerContinuation()
                     || cfg.isDedicatedClientProxy()
                     || TokenStorage.getToken(ownerName, c.getPlayer2APIService().getClientId()).isEmpty()
               );
            if (stopForPrompter || stopForOwner) {
               LOGGER.info("Player2: stopping controller due to disconnect policy (entity={})", c.getEntity().getUUID());
               c.stop(StopReason.CANCELLED_DISCONNECT);
               ConversationManager.shutdownCompleterForBillingKey(leavingId.toString());
               Player2ClientApiBridge.cancelPendingForPlayer(leavingId);
            }
         }
      }
   }
}
