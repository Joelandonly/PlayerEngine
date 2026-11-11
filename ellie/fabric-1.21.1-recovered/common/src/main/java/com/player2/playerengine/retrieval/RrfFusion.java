package com.player2.playerengine.retrieval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class RrfFusion {
   private RrfFusion() {
   }

   public static List<RetrievalHit> fuse(List<RetrievalHit> listA, List<RetrievalHit> listB, int topK, double k) {
      return fuse(List.of(listA, listB), topK, k);
   }

   public static List<RetrievalHit> fuse(List<List<RetrievalHit>> lists, int topK, double k) {
      Map<String, double[]> scores = new HashMap<>();
      Map<String, int[]> ranks = new HashMap<>();

      for (int slot = 0; slot < lists.size(); slot++) {
         addList(lists.get(slot), scores, ranks, slot, k);
      }

      List<Entry<String, double[]>> entries = new ArrayList<>(scores.entrySet());
      entries.sort((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]));
      List<RetrievalHit> result = new ArrayList<>();
      int limit = Math.min(topK, entries.size());

      for (int i = 0; i < limit; i++) {
         String id = entries.get(i).getKey();
         double score = entries.get(i).getValue()[0];
         int[] r = ranks.getOrDefault(id, RetrieverRegistry.emptyRanks());
         result.add(new RetrievalHit(id, score, r));
      }

      return result;
   }

   private static void addList(List<RetrievalHit> list, Map<String, double[]> scores, Map<String, int[]> ranks, int listIdx, double k) {
      for (int i = 0; i < list.size(); i++) {
         String id = list.get(i).toolId();
         int oneIndexedRank = i + 1;
         double contribution = 1.0 / (k + oneIndexedRank);
         scores.computeIfAbsent(id, x -> new double[]{0.0})[0] += contribution;
         int[] r = ranks.computeIfAbsent(id, x -> RetrieverRegistry.emptyRanks());
         r[listIdx] = oneIndexedRank;
      }
   }
}
