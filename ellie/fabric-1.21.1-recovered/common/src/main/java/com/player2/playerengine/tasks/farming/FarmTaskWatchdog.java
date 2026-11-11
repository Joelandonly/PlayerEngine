package com.player2.playerengine.tasks.farming;

import java.util.Objects;

public final class FarmTaskWatchdog {
   private int wholeTicks;
   private int phaseTicks;
   private int noProgressTicks;
   private FarmTaskPhase phase = FarmTaskPhase.PRECHECK;

   public void reset() {
      this.wholeTicks = 0;
      this.restartFromCheckpoint();
   }

   public void restartFromCheckpoint() {
      this.phaseTicks = 0;
      this.noProgressTicks = 0;
      this.phase = FarmTaskPhase.PRECHECK;
   }

   public void tick() {
      this.wholeTicks++;
      this.phaseTicks++;
      this.noProgressTicks++;
   }

   public void enterPhase(FarmTaskPhase next) {
      this.phase = Objects.requireNonNull(next, "next");
      this.phaseTicks = 0;
      this.noProgressTicks = 0;
   }

   public void markProgress() {
      this.noProgressTicks = 0;
   }

   public FarmTaskPhase phase() {
      return this.phase;
   }

   public int wholeTicks() {
      return this.wholeTicks;
   }

   public int phaseTicks() {
      return this.phaseTicks;
   }

   public int noProgressTicks() {
      return this.noProgressTicks;
   }

   public boolean wholeSetupExpired() {
      return this.wholeTicks >= 12000;
   }

   public boolean phaseExpired(int limit) {
      if (limit < 1) {
         throw new IllegalArgumentException("limit must be positive");
      } else {
         return this.phaseTicks >= limit;
      }
   }

   public boolean mutationStalled() {
      return this.noProgressTicks >= 300;
   }
}
