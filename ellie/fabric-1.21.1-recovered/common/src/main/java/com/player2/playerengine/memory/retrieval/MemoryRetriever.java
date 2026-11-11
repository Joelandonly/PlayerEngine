package com.player2.playerengine.memory.retrieval;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.memory.MemoryGraph;
import com.player2.playerengine.memory.MemoryNode;
import com.player2.playerengine.memory.MemoryNodeType;
import com.player2.playerengine.memory.MemoryScope;
import com.player2.playerengine.memory.MemoryStore;
import com.player2.playerengine.memory.dense.DenseIndexState;
import com.player2.playerengine.memory.dense.EmbeddingProvider;
import com.player2.playerengine.memory.dense.TurnEmbeddingCache;
import com.player2.playerengine.player2api.Player2PayerResolution;
import com.player2.playerengine.retrieval.LexicalIndex;
import com.player2.playerengine.retrieval.MinHashIndex;
import com.player2.playerengine.retrieval.RetrievalHit;
import com.player2.playerengine.retrieval.Retriever;
import com.player2.playerengine.retrieval.RrfFusion;
import com.player2.playerengine.retrieval.ToolDocument;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MemoryRetriever implements Retriever {
   public static final long BUDGET_NANOS = 2000000L;
   static final int SEED_CANDIDATES = 16;
   public static final int DEFAULT_TOP_K = 5;
   private final MemoryStore store;
   private final MemoryRetriever.DenseContext denseContext;
   private static final double EPISODIC_LIST_COUNT = 3.0;

   public MemoryRetriever(MemoryStore store) {
      this(store, null);
   }

   public MemoryRetriever(MemoryStore store, MemoryRetriever.DenseContext denseContext) {
      this.store = store;
      this.denseContext = denseContext;
   }

   public MemoryRetrievalResult retrieve(
      String currentTurnText, UUID ownerUuid, String companionId, long currentGameTime, MemoryRetriever.Thresholds thresholds, boolean memoryEnabled
   ) {
      return this.retrieve(currentTurnText, ownerUuid, companionId, null, null, currentGameTime, thresholds, memoryEnabled);
   }

   public MemoryRetrievalResult retrieve(
      String currentTurnText,
      UUID ownerUuid,
      String companionId,
      String companionName,
      String ownerName,
      long currentGameTime,
      MemoryRetriever.Thresholds thresholds,
      boolean memoryEnabled
   ) {
      long deadline = System.nanoTime() + 2000000L;
      MemoryRetriever.Thresholds t = thresholds != null ? thresholds : MemoryRetriever.Thresholds.defaults();
      if (memoryEnabled && this.store != null) {
         MemoryStore.Snapshot snap = this.store.snapshot();
         if (snap == null) {
            return MemoryRetrievalResult.storeAbsent();
         }

         MemoryGraph graph = snap.graph();
         if (graph != null && graph.nodeCount() != 0 && currentTurnText != null && !currentTurnText.isBlank()) {
            float[] turnVector = this.embedTurnOnce(currentTurnText);
            List<String> seedIds = this.entityLinkSeeds(graph, currentTurnText, deadline, turnVector);
            int seedMatches = seedIds.size();
            int specificSeedMatches = countSpecificSeedMatches(graph, seedIds, companionName, ownerName);
            List<EgoGraphTraversal.EgoNode> ego = EgoGraphTraversal.traverse(
               graph, seedIds, currentGameTime, t.maxHops, t.maxEgoNodes, t.decayBase, t.gameTimeUnit, deadline
            );
            List<RetrievalHit> ranked = EpisodicReranker.rerank(ego, currentGameTime, t.topK, turnVector);
            double normTop = normalizedTopScore(ranked);
            double confidence = MemoryRetrievalConfidence.confidence(normTop, ranked.size(), seedMatches);
            MemoryRetrievalConfidence.BoundaryVerdict verdict = MemoryRetrievalConfidence.verdict(
               false, confidence, seedMatches, specificSeedMatches, t.minConfidence
            );
            Optional<String> block = MemoryBlockSerializer.serialize(verdict, ranked, graph, t.blockCharCap);
            if (verdict == MemoryRetrievalConfidence.BoundaryVerdict.HAS_MEMORY && block.isEmpty()) {
               return new MemoryRetrievalResult(List.of(), confidence, MemoryRetrievalConfidence.BoundaryVerdict.STORE_ABSENT, block);
            }

            List<RetrievalHit> resultHits = verdict == MemoryRetrievalConfidence.BoundaryVerdict.HAS_MEMORY ? ranked : List.of();
            return new MemoryRetrievalResult(resultHits, confidence, verdict, block);
         } else {
            return MemoryRetrievalResult.storeAbsent();
         }
      } else {
         return MemoryRetrievalResult.storeAbsent();
      }
   }

   @Override
   public List<RetrievalHit> query(String goal, int k) {
      if (this.store == null) {
         return List.of();
      } else {
         MemoryStore.Snapshot snap = this.store.snapshot();
         if (snap != null && snap.graph() != null && snap.graph().nodeCount() != 0) {
            long deadline = System.nanoTime() + 2000000L;
            MemoryRetriever.Thresholds t = MemoryRetriever.Thresholds.defaults();
            List<String> seeds = this.entityLinkSeeds(snap.graph(), goal, deadline, null);
            List<EgoGraphTraversal.EgoNode> ego = EgoGraphTraversal.traverse(
               snap.graph(), seeds, this.latestTick(), t.maxHops, t.maxEgoNodes, t.decayBase, t.gameTimeUnit, deadline
            );
            return EpisodicReranker.rerank(ego, this.latestTick(), k > 0 ? k : t.topK, null);
         } else {
            return List.of();
         }
      }
   }

   @Override
   public int slotIndex() {
      return 2;
   }

   List<String> entityLinkSeeds(MemoryGraph graph, String turnText, long deadline, float[] turnVector) {
      if (graph != null && turnText != null && !turnText.isBlank() && graph.nodeCount() != 0) {
         if (System.nanoTime() >= deadline) {
            return List.of();
         }

         String queryText = normalizeQueryText(turnText);
         List<ToolDocument> docs = new ArrayList<>(graph.nodeCount());

         for (MemoryNode n : graph.nodes()) {
            docs.add(toDocument(n));
         }

         LexicalIndex lexical = LexicalIndex.build(docs);
         MinHashIndex minHash = MinHashIndex.build(docs);
         List<RetrievalHit> bm25 = lexical.query(queryText, 16);
         List<RetrievalHit> fuzzy = minHash.query(queryText, 16);
         List<RetrievalHit> dense = this.denseCosineHits(graph, turnVector, 16);
         List<RetrievalHit> fused = RrfFusion.fuse(List.of(bm25, fuzzy, dense), 16, 60.0);
         Set<String> seeds = new LinkedHashSet<>();

         for (RetrievalHit hit : fused) {
            if (hit.id() != null) {
               seeds.add(hit.id());
            }
         }

         return new ArrayList<>(seeds);
      } else {
         return List.of();
      }
   }

   private float[] embedTurnOnce(String turnText) {
      try {
         if (this.denseContext != null && this.denseContext.usable() && turnText != null && !turnText.isBlank() && EmbeddingProvider.isAvailable()) {
            float[] cached = TurnEmbeddingCache.lookup(turnText);
            if (cached != null) {
               return cached;
            }

            TurnEmbeddingCache.scheduleIfAbsent(turnText, this.denseContext.controller(), this.denseContext.billing());
            return null;
         } else {
            return null;
         }
      } catch (RuntimeException e) {
         return null;
      }
   }

   private List<RetrievalHit> denseCosineHits(MemoryGraph graph, float[] turnVector, int topK) {
      if (graph != null && turnVector != null) {
         DenseIndexState index = this.store != null ? this.store.denseIndex() : DenseIndexState.empty();
         List<String> rankedIds = index.cosineRankedIds(graph, turnVector, topK);
         if (rankedIds.isEmpty()) {
            return List.of();
         }

         List<RetrievalHit> hits = new ArrayList<>(rankedIds.size());
         int rank = 1;

         for (String id : rankedIds) {
            hits.add(new RetrievalHit(id, 1.0 / (60.0 + rank), new int[0]));
            rank++;
         }

         return hits;
      } else {
         return List.of();
      }
   }

   static String normalizeQueryText(String turnText) {
      if (turnText != null && !turnText.isBlank()) {
         String normalized = turnText;
         normalized = normalized.replaceAll("(?i)\\bfavourite\\b", "favorite");
         normalized = normalized.replaceAll("(?i)\\bfavourites\\b", "favorites");
         normalized = normalized.replaceAll("(?i)\\bcolour\\b", "color");
         normalized = normalized.replaceAll("(?i)\\bcolours\\b", "colors");
         normalized = normalized.replaceAll("(?i)\\bvideogames\\b", "video games");
         return normalized.replaceAll("(?i)\\bvideogame\\b", "video game");
      } else {
         return "";
      }
   }

   static int countSpecificSeedMatches(MemoryGraph graph, List<String> seedIds, String companionName, String ownerName) {
      if (graph != null && seedIds != null && !seedIds.isEmpty()) {
         String companionKey = anchorKey(companionName);
         String ownerKey = anchorKey(ownerName);
         if (companionKey == null && ownerKey == null) {
            return seedIds.size();
         }

         int specific = 0;

         for (String id : seedIds) {
            MemoryNode node = graph.node(id);
            if (node != null) {
               if (MemoryNodeType.EVENT == MemoryNodeType.fromWire(node.type())) {
                  specific++;
               } else {
                  String nameKey = anchorKey(node.canonicalName());
                  boolean isAnchor = nameKey != null && (nameKey.equals(companionKey) || nameKey.equals(ownerKey));
                  if (!isAnchor) {
                     specific++;
                  }
               }
            }
         }

         return specific;
      } else {
         return 0;
      }
   }

   private static String anchorKey(String name) {
      if (name == null) {
         return null;
      }

      String t = name.trim().toLowerCase(Locale.ROOT);
      return t.isEmpty() ? null : t;
   }

   private static ToolDocument toDocument(MemoryNode n) {
      String name = safe(n.canonicalName());
      String content = safe(n.content());
      return new ToolDocument(n.id(), name, content, safe(n.type()), n.tags(), n.aliases(), List.of());
   }

   private long latestTick() {
      return 0L;
   }

   private static double normalizedTopScore(List<RetrievalHit> ranked) {
      if (ranked != null && !ranked.isEmpty()) {
         double top = ranked.get(0).score();
         if (top <= 0.0) {
            return 0.0;
         }

         double max = 0.04918032786885246;
         return max <= 0.0 ? 0.0 : Math.min(1.0, top / max);
      } else {
         return 0.0;
      }
   }

   private static String safe(String s) {
      return s == null ? "" : s;
   }

   public MemoryScope scope() {
      return this.store == null ? null : this.store.scope();
   }

   public record DenseContext(PlayerEngineController controller, Player2PayerResolution.ApiBillingContext billing) {
      public boolean usable() {
         return this.controller != null && this.billing != null && this.billing.billingKey() != null;
      }
   }

   public static final class Thresholds {
      public final int maxHops;
      public final int maxEgoNodes;
      public final int blockCharCap;
      public final double minConfidence;
      public final double decayBase;
      public final long gameTimeUnit;
      public final int topK;

      public Thresholds(int maxHops, int maxEgoNodes, int blockCharCap, double minConfidence, double decayBase, long gameTimeUnit, int topK) {
         this.maxHops = maxHops;
         this.maxEgoNodes = maxEgoNodes;
         this.blockCharCap = blockCharCap;
         this.minConfidence = minConfidence;
         this.decayBase = decayBase;
         this.gameTimeUnit = gameTimeUnit;
         this.topK = topK;
      }

      public static MemoryRetriever.Thresholds defaults() {
         return new MemoryRetriever.Thresholds(2, 64, 1600, 0.2, 0.995, 1000L, 5);
      }
   }
}
