package com.player2.playerengine.player2api;

import com.player2.playerengine.player2api.config.KeepInventoryOverride;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;

public final class KeepInventoryResolver {
   private KeepInventoryResolver() {
   }

   public static boolean effectiveKeep(boolean worldGameruleKeep) {
      KeepInventoryOverride ov = Player2ServerConfigHolder.get().getBotKeepInventoryOverride();

      return switch (ov) {
         case FORCE_KEEP -> true;
         case FORCE_DROP -> false;
         case FOLLOW_GAMERULE -> worldGameruleKeep;
      };
   }
}
