package com.player2.playerengine.modintelligence;

public final class ModIntelligenceStatus {
   private final boolean enabled;
   private final boolean inspecting;
   private final boolean enriching;
   private final int totalEntries;
   private final int readyEntries;
   private final int partialEntries;
   private final int unknownEntries;
   private final int failedEntries;
   private final int tombstonedEntries;
   private final int queuedEnrichments;
   private final int enrichedEntries;
   private final int enrichmentFailures;
   private final int lastBatchValidated;
   private final int lastBatchFailures;
   private final int lastBatchRemaining;
   private final String lastOperationCode;
   private final String activePackFingerprint;
   private final String lastError;

   public ModIntelligenceStatus(
      boolean enabled,
      boolean inspecting,
      boolean enriching,
      int totalEntries,
      int readyEntries,
      int partialEntries,
      int unknownEntries,
      int failedEntries,
      int tombstonedEntries,
      int queuedEnrichments,
      int enrichedEntries,
      int enrichmentFailures,
      int lastBatchValidated,
      int lastBatchFailures,
      int lastBatchRemaining,
      String lastOperationCode,
      String activePackFingerprint,
      String lastError
   ) {
      this.enabled = enabled;
      this.inspecting = inspecting;
      this.enriching = enriching;
      this.totalEntries = totalEntries;
      this.readyEntries = readyEntries;
      this.partialEntries = partialEntries;
      this.unknownEntries = unknownEntries;
      this.failedEntries = failedEntries;
      this.tombstonedEntries = tombstonedEntries;
      this.queuedEnrichments = queuedEnrichments;
      this.enrichedEntries = enrichedEntries;
      this.enrichmentFailures = enrichmentFailures;
      this.lastBatchValidated = lastBatchValidated;
      this.lastBatchFailures = lastBatchFailures;
      this.lastBatchRemaining = lastBatchRemaining;
      this.lastOperationCode = lastOperationCode;
      this.activePackFingerprint = activePackFingerprint;
      this.lastError = lastError;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public boolean isInspecting() {
      return this.inspecting;
   }

   public boolean isEnriching() {
      return this.enriching;
   }

   public int getTotalEntries() {
      return this.totalEntries;
   }

   public int getReadyEntries() {
      return this.readyEntries;
   }

   public int getPartialEntries() {
      return this.partialEntries;
   }

   public int getUnknownEntries() {
      return this.unknownEntries;
   }

   public int getFailedEntries() {
      return this.failedEntries;
   }

   public int getTombstonedEntries() {
      return this.tombstonedEntries;
   }

   public int getQueuedEnrichments() {
      return this.queuedEnrichments;
   }

   public int getEnrichedEntries() {
      return this.enrichedEntries;
   }

   public int getEnrichmentFailures() {
      return this.enrichmentFailures;
   }

   public int getLastBatchValidated() {
      return this.lastBatchValidated;
   }

   public int getLastBatchFailures() {
      return this.lastBatchFailures;
   }

   public int getLastBatchRemaining() {
      return this.lastBatchRemaining;
   }

   public String getLastOperationCode() {
      return this.lastOperationCode;
   }

   public String getActivePackFingerprint() {
      return this.activePackFingerprint;
   }

   public String getLastError() {
      return this.lastError;
   }
}
