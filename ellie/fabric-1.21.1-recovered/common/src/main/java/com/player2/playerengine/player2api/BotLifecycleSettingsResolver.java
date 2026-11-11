package com.player2.playerengine.player2api;

import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

public final class BotLifecycleSettingsResolver {
   private BotLifecycleSettingsResolver() {
   }

   public static boolean usesServerConfig(MinecraftServer server) {
      return server != null && server.isDedicatedServer() ? Player2ServerConfigHolder.get().isServerOverridesPlayerConfig() : true;
   }

   private static boolean resolvePermadeath(Boolean chosen, boolean hardcore) {
      return chosen != null ? chosen : hardcore;
   }

   public static BotLifecycleSettings resolve(MinecraftServer server, UUID ownerUuid, boolean perPlayerAutoRespawn, Boolean perPlayerBotPermadeath) {
      Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
      boolean hardcore = server != null && server.isHardcore();
      return usesServerConfig(server)
         ? new BotLifecycleSettings(cfg.isServerAutoRespawn(), resolvePermadeath(cfg.getServerBotPermadeath(), hardcore))
         : new BotLifecycleSettings(perPlayerAutoRespawn, resolvePermadeath(perPlayerBotPermadeath, hardcore));
   }
}
