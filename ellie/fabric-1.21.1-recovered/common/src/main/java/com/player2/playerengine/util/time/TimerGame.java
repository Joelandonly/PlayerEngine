package com.player2.playerengine.util.time;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.Debug;

public class TimerGame extends BaseTimer {
   private static volatile int serverTicks = 0;

   public TimerGame(double intervalSeconds) {
      super(intervalSeconds);
   }

   public static void incrementServerTick() {
      serverTicks++;
   }

   @Override
   protected double currentTime() {
      if (!PlayerEngineController.inGame()) {
         Debug.logError("Running game timer while not in game.");
         return 0.0;
      } else {
         return serverTicks / 20.0;
      }
   }
}
