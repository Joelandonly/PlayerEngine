package com.player2.playerengine.player2api;

import com.player2.playerengine.player2api.config.BudgetThresholds;
import com.player2.playerengine.player2api.config.Player2PayerMode;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

public final class BudgetThresholdsResolver {
   private BudgetThresholdsResolver() {
   }

   public static boolean useServerConfigForBudget(MinecraftServer server) {
      return server != null && !server.isDedicatedServer();
   }

   public static boolean usesServerBudgetStore(MinecraftServer server) {
      return server == null || useServerConfigForBudget(server) || Player2ServerConfigHolder.get().getPayerMode() != Player2PayerMode.PROMPTER_PAYS;
   }

   public static BudgetThresholds resolve(MinecraftServer server, Player2PayerResolution.ApiBillingContext billing) {
      Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
      if (usesServerBudgetStore(server)) {
         return cfg;
      }

      ServerPlayer payer = billing != null ? billing.onlinePayer() : null;
      return payer != null ? PlayerBudgetConfigHolder.load(server, payer.getUUID()) : cfg;
   }
}
