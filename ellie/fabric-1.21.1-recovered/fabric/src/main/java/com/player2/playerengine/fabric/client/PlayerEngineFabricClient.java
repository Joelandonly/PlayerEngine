package com.player2.playerengine.fabric.client;

import com.player2.playerengine.PlayerEngineClient;
import net.fabricmc.api.ClientModInitializer;

public final class PlayerEngineFabricClient implements ClientModInitializer {
   public void onInitializeClient() {
      PlayerEngineClient.onInitializeClient();
   }
}
