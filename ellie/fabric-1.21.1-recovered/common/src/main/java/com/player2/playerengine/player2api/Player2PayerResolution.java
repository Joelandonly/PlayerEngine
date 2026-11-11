package com.player2.playerengine.player2api;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.player2api.auth.TokenStorage;
import com.player2.playerengine.player2api.config.Player2PayerMode;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;
import java.util.Iterator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

public final class Player2PayerResolution {
   private Player2PayerResolution() {
   }

   public static Player2PayerResolution.ApiBillingContext resolve(PlayerEngineController mod, String chainInitiatorUsername, String clientId) {
      Player2ServerConfigHolder.get();
      Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
      MinecraftServer server = mod.getPlayer() != null ? mod.getPlayer().level().getServer() : null;
      Player owner = mod.getOwner();
      String ownerName = owner != null ? owner.getName().getString() : "";
      if (cfg.getPayerMode() == Player2PayerMode.OWNER_PAYS_ALL) {
         ServerPlayer ownerSp = owner instanceof ServerPlayer o ? o : null;
         if (ownerSp != null && server != null && server.getPlayerList().getPlayer(ownerSp.getUUID()) != null) {
            return new Player2PayerResolution.ApiBillingContext(ownerSp, null);
         } else {
            return cfg.isOwnerOfflineServerContinuation()
                  && !cfg.isDedicatedClientProxy()
                  && owner != null
                  && !TokenStorage.getToken(ownerName, clientId).isEmpty()
               ? new Player2PayerResolution.ApiBillingContext(null, ownerName)
               : new Player2PayerResolution.ApiBillingContext(null, null);
         }
      } else {
         String billName = chainInitiatorUsername != null && !chainInitiatorUsername.isBlank() ? chainInitiatorUsername : ownerName;
         ServerPlayer bill = findByName(server, billName);
         return new Player2PayerResolution.ApiBillingContext(bill, null);
      }
   }

   public static ServerPlayer findByName(MinecraftServer server, String name) {
      if (server != null && name != null) {
         for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getName().getString().equals(name)) {
               return p;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public static Player2PayerResolution.ApiBillingContext resolveForServer(MinecraftServer server, String clientId) {
      Player2ServerConfigHolder.get();
      Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
      if (server == null) {
         return new Player2PayerResolution.ApiBillingContext(null, null);
      }

      Iterator storedUser = server.getPlayerList().getPlayers().iterator();
      if (storedUser.hasNext()) {
         ServerPlayer online = (ServerPlayer)storedUser.next();
         return cfg.getPayerMode() == Player2PayerMode.OWNER_PAYS_ALL
            ? new Player2PayerResolution.ApiBillingContext(online, null)
            : new Player2PayerResolution.ApiBillingContext(online, null);
      }

      if (cfg.getPayerMode() == Player2PayerMode.OWNER_PAYS_ALL && cfg.isOwnerOfflineServerContinuation() && !cfg.isDedicatedClientProxy()) {
         String storedUserx = TokenStorage.findFirstUsernameWithToken(clientId);
         if (storedUserx != null && !TokenStorage.getToken(storedUserx, clientId).isEmpty()) {
            return new Player2PayerResolution.ApiBillingContext(null, storedUserx);
         }
      }

      return new Player2PayerResolution.ApiBillingContext(null, null);
   }

   public static boolean canBillServer(MinecraftServer server, String clientId) {
      Player2PayerResolution.ApiBillingContext ctx = resolveForServer(server, clientId);
      return ctx.billingKey() != null;
   }

   public record ApiBillingContext(ServerPlayer onlinePayer, String storedTokenUsername) {
      public boolean useStoredToken() {
         return this.onlinePayer == null && this.storedTokenUsername != null && !this.storedTokenUsername.isEmpty();
      }

      public String billingKey() {
         if (this.onlinePayer != null) {
            return this.onlinePayer.getUUID().toString();
         } else {
            return this.storedTokenUsername != null && !this.storedTokenUsername.isEmpty() ? "token:" + this.storedTokenUsername : null;
         }
      }
   }
}
