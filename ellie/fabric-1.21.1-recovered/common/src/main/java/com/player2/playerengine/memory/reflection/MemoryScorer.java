package com.player2.playerengine.memory.reflection;

import com.player2.playerengine.memory.MemoryNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MemoryScorer {
   public static final double DEFAULT_HALF_LIFE_GAME_HOURS = 72.0;
   public static final int DEFAULT_TOP_K = 5;
   private static final double IMPORTANCE_RUBRIC_MAX = 10.0;

   private MemoryScorer() {
   }

   public static List<MemoryScorer.Scored> score(List<MemoryScorer.Candidate> candidates, long nowTick) {
      return score(candidates, nowTick, 72.0, 5);
   }

   public static List<MemoryScorer.Scored> score(List<MemoryScorer.Candidate> candidates, long nowTick, double halfLifeGameHours, int topK) {
      if (candidates != null && !candidates.isEmpty()) {
         double halfLife = halfLifeGameHours > 0.0 ? halfLifeGameHours : 72.0;
         int k = topK > 0 ? topK : 5;
         int n = candidates.size();
         double[] recencyRaw = new double[n];
         double[] importanceRaw = new double[n];
         double[] relevanceRaw = new double[n];

         for (int i = 0; i < n; i++) {
            MemoryScorer.Candidate c = candidates.get(i);
            MemoryNode node = c.node();
            recencyRaw[i] = recencyComponent(node, nowTick, halfLife);
            importanceRaw[i] = importanceComponent(node);
            relevanceRaw[i] = c.relevance();
         }

         minMaxNormalizeInPlace(recencyRaw);
         minMaxNormalizeInPlace(importanceRaw);
         minMaxNormalizeInPlace(relevanceRaw);
         List<MemoryScorer.Scored> scored = new ArrayList<>(n);

         for (int i = 0; i < n; i++) {
            double total = recencyRaw[i] + importanceRaw[i] + relevanceRaw[i];
            scored.add(new MemoryScorer.Scored(candidates.get(i).node(), total));
         }

         scored.sort(Comparator.comparingDouble(MemoryScorer.Scored::score).reversed().thenComparing(s -> idOf(s.node())));
         return scored.size() > k ? new ArrayList<>(scored.subList(0, k)) : scored;
      } else {
         return List.of();
      }
   }

   private static double recencyComponent(MemoryNode node, long nowTick, double halfLifeGameHours) {
      long lastRetrieved = node.lastRetrievedTick();
      if (lastRetrieved <= 0L) {
         return 0.0;
      }

      long ticksSince = Math.max(0L, nowTick - lastRetrieved);
      double hoursSince = ticksSince / 1000.0;
      return Math.exp(-hoursSince / halfLifeGameHours);
   }

   private static double importanceComponent(MemoryNode node) {
      double v = node.importance() / 10.0;
      if (v < 0.0) {
         return 0.0;
      } else {
         return v > 1.0 ? 1.0 : v;
      }
   }

   private static void minMaxNormalizeInPlace(double[] values) {
      if (values.length != 0) {
         double min = values[0];
         double max = values[0];

         for (double v : values) {
            if (v < min) {
               min = v;
            }

            if (v > max) {
               max = v;
            }
         }

         double range = max - min;
         if (range <= 0.0) {
            for (int i = 0; i < values.length; i++) {
               values[i] = 0.0;
            }
         } else {
            for (int i = 0; i < values.length; i++) {
               values[i] = (values[i] - min) / range;
            }
         }
      }
   }

   private static String idOf(MemoryNode node) {
      String id = node.id();
      return id == null ? "" : id;
   }

   public static final class Candidate {
      private final MemoryNode node;
      private final double relevance;

      public Candidate(MemoryNode node, double relevance) {
         this.node = node;
         this.relevance = relevance;
      }

      public MemoryNode node() {
         return this.node;
      }

      public double relevance() {
         return this.relevance;
      }
   }

   public static final class Scored {
      private final MemoryNode node;
      private final double score;

      Scored(MemoryNode node, double score) {
         this.node = node;
         this.score = score;
      }

      public MemoryNode node() {
         return this.node;
      }

      public double score() {
         return this.score;
      }
   }
}
