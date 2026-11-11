package com.player2.playerengine.memory.resolution;

import java.util.Locale;

public final class ResolutionResult {
   private final String resolvedNodeId;
   private final ResolutionResult.ResolutionLayer layer;
   private final double confidence;
   private final String matchedAlias;

   public ResolutionResult(String resolvedNodeId, ResolutionResult.ResolutionLayer layer, double confidence, String matchedAlias) {
      this.resolvedNodeId = resolvedNodeId;
      this.layer = layer;
      this.confidence = confidence;
      this.matchedAlias = matchedAlias;
   }

   public String resolvedNodeId() {
      return this.resolvedNodeId;
   }

   public ResolutionResult.ResolutionLayer layer() {
      return this.layer;
   }

   public double confidence() {
      return this.confidence;
   }

   public String matchedAlias() {
      return this.matchedAlias;
   }

   public boolean matched() {
      return this.resolvedNodeId != null;
   }

   static ResolutionResult exact(String nodeId, String matchedAlias) {
      return new ResolutionResult(nodeId, ResolutionResult.ResolutionLayer.EXACT_NORMALIZED, 1.0, matchedAlias);
   }

   static ResolutionResult fuzzy(String nodeId, double ratio, String matchedAlias) {
      return new ResolutionResult(nodeId, ResolutionResult.ResolutionLayer.MINHASH_FUZZY, ratio, matchedAlias);
   }

   static ResolutionResult llmConfirmed(String nodeId, double confidence, String matchedAlias) {
      return new ResolutionResult(nodeId, ResolutionResult.ResolutionLayer.LLM_CONFIRMED, confidence, matchedAlias);
   }

   static ResolutionResult newNode() {
      return new ResolutionResult(null, ResolutionResult.ResolutionLayer.NEW_NODE, 0.0, null);
   }

   static ResolutionResult skippedBudget() {
      return new ResolutionResult(null, ResolutionResult.ResolutionLayer.SKIPPED_BUDGET, 0.0, null);
   }

   @Override
   public String toString() {
      return "ResolutionResult{nodeId="
         + this.resolvedNodeId
         + ", layer="
         + this.layer
         + ", confidence="
         + String.format(Locale.ROOT, "%.3f", this.confidence)
         + ", matchedAlias="
         + this.matchedAlias
         + "}";
   }

   public enum ResolutionLayer {
      EXACT_NORMALIZED,
      MINHASH_FUZZY,
      LLM_CONFIRMED,
      NEW_NODE,
      SKIPPED_BUDGET;
   }
}
