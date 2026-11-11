package com.player2.playerengine.memory.retrieval;

public final class MemoryRetrievalConfidence {
   public static final double W_TOP = 0.5;
   public static final double W_HITS = 0.25;
   public static final double W_SEED = 0.25;
   public static final int HIT_TARGET = 5;
   public static final int SEED_TARGET = 3;
   public static final double MIN_CONFIDENCE = 0.2;

   private MemoryRetrievalConfidence() {
   }

   public static double confidence(double normTopScore, int hits, int seedNodeMatches) {
      double top = clamp01(normTopScore);
      double hitTerm = Math.min(1.0, Math.max(0, hits) / 5.0);
      double seedTerm = Math.min(1.0, Math.max(0, seedNodeMatches) / 3.0);
      return clamp01(0.5 * top + 0.25 * hitTerm + 0.25 * seedTerm);
   }

   public static MemoryRetrievalConfidence.BoundaryVerdict verdict(
      boolean storeAbsent, double confidence, int seedNodeMatches, int specificSeedMatches, double minConfidence
   ) {
      if (storeAbsent) {
         return MemoryRetrievalConfidence.BoundaryVerdict.STORE_ABSENT;
      }

      if (seedNodeMatches <= 0) {
         return MemoryRetrievalConfidence.BoundaryVerdict.NO_MEMORY;
      }

      if (specificSeedMatches <= 0) {
         return MemoryRetrievalConfidence.BoundaryVerdict.NO_MEMORY;
      }

      double floor = minConfidence > 0.0 ? minConfidence : 0.2;
      return confidence < floor ? MemoryRetrievalConfidence.BoundaryVerdict.NO_MEMORY : MemoryRetrievalConfidence.BoundaryVerdict.HAS_MEMORY;
   }

   public static MemoryRetrievalConfidence.BoundaryVerdict verdict(boolean storeAbsent, double confidence, int seedNodeMatches, double minConfidence) {
      return verdict(storeAbsent, confidence, seedNodeMatches, seedNodeMatches, minConfidence);
   }

   private static double clamp01(double v) {
      if (Double.isNaN(v)) {
         return 0.0;
      } else {
         return v < 0.0 ? 0.0 : Math.min(v, 1.0);
      }
   }

   public enum BoundaryVerdict {
      STORE_ABSENT,
      NO_MEMORY,
      HAS_MEMORY;
   }
}
