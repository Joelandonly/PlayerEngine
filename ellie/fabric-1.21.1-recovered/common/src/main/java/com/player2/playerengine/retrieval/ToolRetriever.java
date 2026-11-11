package com.player2.playerengine.retrieval;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ToolRetriever {
   private static final Logger LOGGER = LogManager.getLogger(ToolRetriever.class);
   private static final int CANDIDATE_MULTIPLIER = 3;
   private static final double RRF_K = 60.0;
   private final ToolMetadataRegistry registry;
   private final LexicalIndex lexicalIndex;
   private final MinHashIndex minHashIndex;

   private ToolRetriever(ToolMetadataRegistry registry, LexicalIndex lexicalIndex, MinHashIndex minHashIndex) {
      this.registry = registry;
      this.lexicalIndex = lexicalIndex;
      this.minHashIndex = minHashIndex;
   }

   public static ToolRetriever buildInMemory(ToolMetadataRegistry registry) {
      long start = System.currentTimeMillis();
      LexicalIndex lex = LexicalIndex.build(registry.values());
      MinHashIndex mh = MinHashIndex.build(registry.values());
      LOGGER.debug("RAG: in-memory retriever built in {}ms ({} documents).", System.currentTimeMillis() - start, registry.documents().size());
      return new ToolRetriever(registry, lex, mh);
   }

   public static ToolRetriever loadOrRebuild(ToolMetadataRegistry registry, Path indexDir) {
      Path versionFile = indexDir.resolve("version.txt");
      Path toolsBin = indexDir.resolve("tools.bin");
      String current = registry.getVersionToken();
      if (Files.exists(versionFile) && Files.exists(toolsBin)) {
         try {
            String stored = Files.readString(versionFile, StandardCharsets.UTF_8).trim();
            if (current.equals(stored)) {
               LOGGER.info("RAG: version token matches ({}...), loading index from disk.", current.substring(0, 8));
               ToolRetriever loaded = tryLoad(registry, toolsBin);
               if (loaded != null) {
                  return loaded;
               }

               LOGGER.warn("RAG: failed to deserialize tools.bin — rebuilding.");
            } else {
               LOGGER.info(
                  "RAG: version token changed ({}... → {}...) — rebuilding.", stored.substring(0, Math.min(8, stored.length())), current.substring(0, 8)
               );
            }
         } catch (IOException e) {
            LOGGER.warn("RAG: could not read version.txt — rebuilding: {}", e.getMessage());
         }
      } else {
         LOGGER.info("RAG: no persisted index found — building for the first time.");
      }

      return buildAndPersist(registry, indexDir);
   }

   public List<RetrievalHit> retrieve(String goal, int topK) {
      return this.retrieve(goal, topK, null);
   }

   public List<RetrievalHit> retrieve(String goal, int topK, Set<String> categoryFilter) {
      return this.retrieveWithConfidence(goal, topK, categoryFilter, RetrievalConfidenceThresholds.defaults()).hits();
   }

   public RetrievalResult retrieveWithConfidence(String goal, int topK, Set<String> categoryFilter, RetrievalConfidenceThresholds thresholds) {
      int candidateK = topK * 3;
      List<RetrievalHit> bm25 = this.lexicalIndex.query(goal, candidateK);
      List<RetrievalHit> minHash = this.minHashIndex.query(goal, candidateK);
      List<RetrievalHit> fused = RrfFusion.fuse(bm25, minHash, candidateK, 60.0);
      List<RetrievalHit> hits = this.applyCategoryFilter(fused, topK, categoryFilter);
      RetrievalConfidence confidence = RetrievalConfidenceCalculator.compute(goal, hits, this.registry, thresholds);
      return new RetrievalResult(hits, confidence);
   }

   private List<RetrievalHit> applyCategoryFilter(List<RetrievalHit> fused, int topK, Set<String> categoryFilter) {
      if (categoryFilter != null && !categoryFilter.isEmpty()) {
         List<RetrievalHit> filtered = new ArrayList<>();

         for (RetrievalHit hit : fused) {
            ToolDocument doc = this.registry.getDocument(hit.toolId());
            if (doc != null && !Collections.disjoint(doc.categoryTags(), categoryFilter)) {
               filtered.add(hit);
            }

            if (filtered.size() >= topK) {
               break;
            }
         }

         return filtered;
      } else {
         return fused.size() <= topK ? fused : fused.subList(0, topK);
      }
   }

   public int documentCount() {
      return this.registry.documents().size();
   }

   public ToolMetadataRegistry getRegistry() {
      return this.registry;
   }

   private static ToolRetriever tryLoad(ToolMetadataRegistry registry, Path toolsBin) {
      try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(toolsBin))) {
         ToolRetriever.PersistedState ps = (ToolRetriever.PersistedState)ois.readObject();
         LexicalIndex lex = LexicalIndex.fromState(ps.lexState);
         MinHashIndex mh = MinHashIndex.fromState(ps.mhState);
         LOGGER.info("RAG: loaded index from disk ({} documents).", ps.lexState.docIds.length);
         return new ToolRetriever(registry, lex, mh);
      } catch (Exception e) {
         LOGGER.warn("RAG: load failed ({}): {}", e.getClass().getSimpleName(), e.getMessage());
         return null;
      }
   }

   private static ToolRetriever buildAndPersist(ToolMetadataRegistry registry, Path indexDir) {
      long start = System.currentTimeMillis();
      LexicalIndex lex = LexicalIndex.build(registry.values());
      MinHashIndex mh = MinHashIndex.build(registry.values());
      LOGGER.info("RAG: index built in {}ms ({} documents).", System.currentTimeMillis() - start, registry.documents().size());

      try {
         Files.createDirectories(indexDir);

         try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(indexDir.resolve("tools.bin")))) {
            oos.writeObject(new ToolRetriever.PersistedState(lex.getState(), mh.getState()));
         }

         Files.writeString(indexDir.resolve("version.txt"), registry.getVersionToken(), StandardCharsets.UTF_8);
         LOGGER.info("RAG: index persisted to {}.", indexDir);
      } catch (IOException e) {
         LOGGER.warn("RAG: could not persist index (will rebuild on next launch): {}", e.getMessage());
      }

      return new ToolRetriever(registry, lex, mh);
   }

   private static final class PersistedState implements Serializable {
      private static final long serialVersionUID = 1L;
      final LexicalIndex.State lexState;
      final MinHashIndex.State mhState;

      PersistedState(LexicalIndex.State lexState, MinHashIndex.State mhState) {
         this.lexState = lexState;
         this.mhState = mhState;
      }
   }
}
