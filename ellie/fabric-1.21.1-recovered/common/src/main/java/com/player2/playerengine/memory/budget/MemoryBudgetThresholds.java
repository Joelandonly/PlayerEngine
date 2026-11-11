package com.player2.playerengine.memory.budget;

import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;

public record MemoryBudgetThresholds(int callsPerWindow, int windowMinutes) {
   public static MemoryBudgetThresholds fromConfig(Player2ServerRuntimeConfig config) {
      return new MemoryBudgetThresholds(config.getMemoryCallsPerWindowClamped(), config.getMemoryWindowMinutesClamped());
   }
}
