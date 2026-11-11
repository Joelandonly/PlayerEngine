package com.player2.playerengine.automaton.cache;

import com.player2.playerengine.automaton.api.cache.IWorldProvider;
import net.minecraft.world.level.Level;

public class WorldProvider implements IWorldProvider {
   private final WorldData currentWorld;

   public WorldProvider(Level world) {
      this.currentWorld = new WorldData(world.dimension());
   }

   public final WorldData getCurrentWorld() {
      return this.currentWorld;
   }
}
