package com.player2.playerengine.memory.retrieval;

import com.player2.playerengine.retrieval.RetrievalHit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class MemoryRetrievalResult {
   private final List<RetrievalHit> hits;
   private final double confidence;
   private final MemoryRetrievalConfidence.BoundaryVerdict verdict;
   private final Optional<String> memoryBlock;

   public MemoryRetrievalResult(List<RetrievalHit> hits, double confidence, MemoryRetrievalConfidence.BoundaryVerdict verdict, Optional<String> memoryBlock) {
      this.hits = hits == null ? List.of() : Collections.unmodifiableList(List.copyOf(hits));
      this.confidence = confidence;
      this.verdict = verdict;
      this.memoryBlock = memoryBlock == null ? Optional.empty() : memoryBlock;
   }

   public static MemoryRetrievalResult storeAbsent() {
      return new MemoryRetrievalResult(List.of(), 0.0, MemoryRetrievalConfidence.BoundaryVerdict.STORE_ABSENT, Optional.empty());
   }

   public List<RetrievalHit> hits() {
      return this.hits;
   }

   public double confidence() {
      return this.confidence;
   }

   public MemoryRetrievalConfidence.BoundaryVerdict verdict() {
      return this.verdict;
   }

   public Optional<String> memoryBlock() {
      return this.memoryBlock;
   }

   @Override
   public String toString() {
      return "MemoryRetrievalResult{verdict="
         + this.verdict
         + ", confidence="
         + String.format("%.3f", this.confidence)
         + ", hits="
         + this.hits.size()
         + ", hasBlock="
         + this.memoryBlock.isPresent()
         + "}";
   }
}
