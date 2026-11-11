package com.player2.playerengine.trackers;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.automaton.api.cache.IWorldData;
import net.minecraft.world.phys.Vec3;

public class CacheTracker {
   PlayerEngineController mod;
   private long lastProcTime = 0L;
   private final long updateTime = 1000000000L;

   public CacheTracker(PlayerEngineController mod) {
      this.mod = mod;
   }

   private void addCurrentPosToCache() {
      IWorldData worldData = this.mod.getBaritone().getWorldProvider().getCurrentWorld();
      Vec3 curPos = this.mod.getPlayer().position();
      int x = (int)curPos.x();
      int z = (int)curPos.z();
      worldData.addBlockPosToCache(x, z);
   }

   public void tick() {
      long curTime = System.nanoTime();
      if (curTime >= this.lastProcTime + 1000000000L) {
         this.lastProcTime = System.nanoTime();
         this.addCurrentPosToCache();
      }
   }
}
