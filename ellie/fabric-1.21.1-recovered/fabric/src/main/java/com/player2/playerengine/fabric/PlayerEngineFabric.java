package com.player2.playerengine.fabric;

import com.player2.playerengine.PlayerEngine;
import net.fabricmc.api.ModInitializer;

public final class PlayerEngineFabric implements ModInitializer {
   public void onInitialize() {
      PlayerEngine.onInitialize();
   }
}
