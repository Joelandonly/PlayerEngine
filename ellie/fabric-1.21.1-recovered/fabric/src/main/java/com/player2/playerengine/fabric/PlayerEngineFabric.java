package com.player2.playerengine.fabric;

import com.player2.playerengine.PlayerEngine;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public final class PlayerEngineFabric implements ModInitializer {
   public void onInitialize() {
      PlayerEngine.onInitialize();
      CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
         EllieSlashCommands.register(dispatcher));
   }
}
