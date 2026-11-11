package com.player2.playerengine.player2api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;
import com.player2.playerengine.player2api.utils.Player2HTTPUtils;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

public final class Player2ApiDispatcher {
   private Player2ApiDispatcher() {
   }

   public static Map<String, JsonElement> route(
      PlayerEngineController controller, String clientId, String method, String endpoint, JsonObject body, Player2PayerResolution.ApiBillingContext billing
   ) throws Exception {
      Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
      if (billing == null) {
         billing = Player2PayerResolution.resolve(controller, null, clientId);
      }

      if (cfg.isDedicatedClientProxy()) {
         if (billing.onlinePayer() == null) {
            throw new IllegalStateException("Dedicated client-proxy mode requires the billing player to be online.");
         } else {
            return Player2ClientApiBridge.sendJson(billing.onlinePayer(), clientId, method, endpoint, body);
         }
      } else if (billing.useStoredToken() && billing.storedTokenUsername() != null) {
         return Player2HTTPUtils.sendRequestWithStoredToken(billing.storedTokenUsername(), clientId, endpoint, method, body);
      } else if (billing.onlinePayer() != null) {
         return Player2HTTPUtils.sendRequest(billing.onlinePayer(), clientId, endpoint, method, body);
      } else {
         throw new IllegalStateException("No billing player available for Player2 API call (offline or missing token).");
      }
   }

   public static Map<String, JsonElement> routeSimple(ServerPlayer player, String clientId, String method, String endpoint, JsonObject body) throws Exception {
      Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
      return cfg.isDedicatedClientProxy()
         ? Player2ClientApiBridge.sendJson(player, clientId, method, endpoint, body)
         : Player2HTTPUtils.sendRequest(player, clientId, endpoint, method, body);
   }
}
