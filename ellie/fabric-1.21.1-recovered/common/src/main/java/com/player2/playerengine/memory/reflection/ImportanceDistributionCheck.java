package com.player2.playerengine.memory.reflection;

import com.player2.playerengine.PlayerEngine;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class ImportanceDistributionCheck {
   public static final int SAMPLE_SIZE = 30;
   public static final double HIGH_RATIO_THRESHOLD = 0.6;
   public static final int HIGH_BAND_MIN = 7;
   private final String storeLabel;
   private final AtomicInteger sampleCount = new AtomicInteger(0);
   private final AtomicInteger highCount = new AtomicInteger(0);
   private final AtomicBoolean done = new AtomicBoolean(false);

   public ImportanceDistributionCheck(String storeLabel, boolean alreadyDone) {
      this.storeLabel = storeLabel == null ? "?" : storeLabel;
      if (alreadyDone) {
         this.done.set(true);
      }
   }

   public boolean isDone() {
      return this.done.get();
   }

   public void markDone() {
      this.done.set(true);
   }

   public boolean record(int importance) {
      if (this.done.get()) {
         return false;
      }

      if (importance >= 7 && importance <= 10) {
         this.highCount.incrementAndGet();
      }

      int n = this.sampleCount.incrementAndGet();
      if (n < 30) {
         return false;
      } else if (!this.done.compareAndSet(false, true)) {
         return false;
      } else {
         int high = this.highCount.get();
         double ratio = (double)high / n;
         if (ratio > 0.6) {
            PlayerEngine.LOGGER
               .warn(
                  "Memory importance distribution skewed for {}: {} of {} scores in 7-10 ({}%). Rubric may be clustering high; consider tuning ImportanceScorer prompt.",
                  this.storeLabel,
                  high,
                  n,
                  Math.round(ratio * 100.0)
               );
            return true;
         } else {
            return false;
         }
      }
   }
}
