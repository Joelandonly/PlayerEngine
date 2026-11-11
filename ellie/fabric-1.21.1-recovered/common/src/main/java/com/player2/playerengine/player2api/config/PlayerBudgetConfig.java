package com.player2.playerengine.player2api.config;

public class PlayerBudgetConfig implements BudgetThresholds {
   private int softBudgetCallsPerWindow = 0;
   private int hardBudgetCallsPerWindow = 0;
   private int budgetWindowMinutes = 60;
   private int softJoulesThreshold = 0;
   private int hardJoulesThreshold = 0;
   private int joulesRefreshIntervalSeconds = 300;

   @Override
   public int getSoftBudgetCallsPerWindow() {
      return this.softBudgetCallsPerWindow;
   }

   public void setSoftBudgetCallsPerWindow(int v) {
      this.softBudgetCallsPerWindow = Math.max(0, v);
   }

   @Override
   public int getHardBudgetCallsPerWindow() {
      return this.hardBudgetCallsPerWindow;
   }

   public void setHardBudgetCallsPerWindow(int v) {
      this.hardBudgetCallsPerWindow = Math.max(0, v);
   }

   @Override
   public int getBudgetWindowMinutes() {
      return this.budgetWindowMinutes;
   }

   public void setBudgetWindowMinutes(int v) {
      this.budgetWindowMinutes = Math.min(Math.max(v, 1), 1440);
   }

   @Override
   public int getSoftJoulesThreshold() {
      return this.softJoulesThreshold;
   }

   public void setSoftJoulesThreshold(int v) {
      this.softJoulesThreshold = Math.max(0, v);
   }

   @Override
   public int getHardJoulesThreshold() {
      return this.hardJoulesThreshold;
   }

   public void setHardJoulesThreshold(int v) {
      this.hardJoulesThreshold = Math.max(0, v);
   }

   @Override
   public int getJoulesRefreshIntervalSeconds() {
      return this.joulesRefreshIntervalSeconds;
   }

   public void setJoulesRefreshIntervalSeconds(int v) {
      this.joulesRefreshIntervalSeconds = Math.min(Math.max(v, 60), 86400);
   }

   public boolean isAllDisabled() {
      return this.softBudgetCallsPerWindow == 0 && this.hardBudgetCallsPerWindow == 0 && this.softJoulesThreshold == 0 && this.hardJoulesThreshold == 0;
   }
}
