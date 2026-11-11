package com.player2.playerengine.tasks.deferred;

import com.player2.playerengine.PlayerEngineController;
import net.minecraft.core.BlockPos;

public interface DeferredProcess {
   String kind();

   BlockPos stationPos();

   long startGameTime();

   long etaGameTime();

   DeferredProgress poll(PlayerEngineController var1);

   boolean isComplete(DeferredProgress var1);

   DeferredDegradation degradationOf(DeferredProgress var1);
}
