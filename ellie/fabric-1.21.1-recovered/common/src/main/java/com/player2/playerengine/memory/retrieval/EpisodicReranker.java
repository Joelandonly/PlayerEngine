package com.player2.playerengine.memory.retrieval;

import com.player2.playerengine.memory.MemoryNode;
import com.player2.playerengine.memory.dense.DenseIndexState;
import com.player2.playerengine.memory.dense.EmbeddingModel;
import com.player2.playerengine.retrieval.RetrievalHit;
import com.player2.playerengine.retrieval.RrfFusion;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

public final class EpisodicReranker {
   public static final double RRF_K = 60.0;

   private EpisodicReranker() {
   }

   public static List<RetrievalHit> rerank(List<EgoGraphTraversal.EgoNode> egoNodes, long nowTick, int topK, float[] turnVector) {
      if (egoNodes != null && !egoNodes.isEmpty()) {
         double turnNorm = turnVector != null && turnVector.length == 1536 ? cosineNorm(turnVector) : -1.0;
         boolean useCosine = turnNorm > 0.0;
         List<EgoGraphTraversal.EgoNode> byRecency = new ArrayList<>(egoNodes);
         byRecency.sort(Comparator.<EgoGraphTraversal.EgoNode>comparingLong(e -> e.node().lastSeenTick()).reversed().thenComparing(e -> e.node().id()));
         List<RetrievalHit> recencyList = toHits(byRecency, e -> recencyScore(e.node(), nowTick));
         List<EgoGraphTraversal.EgoNode> byImportance = new ArrayList<>(egoNodes);
         byImportance.sort(Comparator.<EgoGraphTraversal.EgoNode>comparingInt(e -> e.node().importance()).reversed().thenComparing(e -> e.node().id()));
         List<RetrievalHit> importanceList = toHits(byImportance, e -> e.node().importance());
         ToDoubleFunction<EgoGraphTraversal.EgoNode> relevanceScorer = e -> relevanceScore(e, useCosine ? turnVector : null, turnNorm);
         List<EgoGraphTraversal.EgoNode> byRelevance = new ArrayList<>(egoNodes);
         byRelevance.sort(Comparator.comparingDouble(relevanceScorer).reversed().thenComparing(e -> e.node().id()));
         List<RetrievalHit> relevanceList = toHits(byRelevance, relevanceScorer);
         return RrfFusion.fuse(List.of(recencyList, importanceList, relevanceList), topK, 60.0);
      } else {
         return List.of();
      }
   }

   private static double relevanceScore(EgoGraphTraversal.EgoNode e, float[] turnVector, double turnNorm) {
      if (turnVector != null) {
         MemoryNode n = e.node();
         if (n != null && n.hasVector() && EmbeddingModel.vectorModelToken().equals(n.vectorModel())) {
            float[] v = n.vector();
            if (v != null && v.length == 1536) {
               return DenseIndexState.cosine(turnVector, turnNorm, v);
            }
         }
      }

      return e.relevance();
   }

   private static double cosineNorm(float[] v) {
      double s = 0.0;

      for (float x : v) {
         s += (double)x * x;
      }

      return Math.sqrt(s);
   }

   static double recencyScore(MemoryNode node, long nowTick) {
      long delta = Math.max(0L, nowTick - node.lastSeenTick());
      double hours = delta / 1000.0;
      return Math.pow(0.995, hours);
   }

   private static List<RetrievalHit> toHits(List<EgoGraphTraversal.EgoNode> sorted, ToDoubleFunction<EgoGraphTraversal.EgoNode> score) {
      List<RetrievalHit> hits = new ArrayList<>(sorted.size());

      for (EgoGraphTraversal.EgoNode e : sorted) {
         hits.add(new RetrievalHit(e.node().id(), score.applyAsDouble(e), new int[0]));
      }

      return hits;
   }
}
