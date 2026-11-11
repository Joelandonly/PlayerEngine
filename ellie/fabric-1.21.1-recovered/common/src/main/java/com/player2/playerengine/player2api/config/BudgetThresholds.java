package com.player2.playerengine.player2api.config;

public interface BudgetThresholds {
   int getSoftBudgetCallsPerWindow();

   int getHardBudgetCallsPerWindow();

   int getBudgetWindowMinutes();

   int getSoftJoulesThreshold();

   int getHardJoulesThreshold();

   int getJoulesRefreshIntervalSeconds();
}
