package com.player2.playerengine.modintelligence.query;

import com.player2.playerengine.modintelligence.capability.CapabilityEvidence;
import com.player2.playerengine.modintelligence.capability.CapabilityStatus;
import com.player2.playerengine.modintelligence.capability.CapabilitySubjectKind;
import java.util.List;

public final class CapabilityHit {
   private final String documentId;
   private final String subjectId;
   private final CapabilitySubjectKind subjectKind;
   private final double score;
   private final double bestCapabilityConfidence;
   private final List<String> matchedCapabilities;
   private final List<String> matchedAliases;
   private final CapabilityStatus status;
   private final boolean hasDeterministicEvidence;
   private final List<CapabilityEvidence> topEvidence;
   private final List<String> warnings;
   private final String sourceModId;
   private final String entryFingerprint;

   public CapabilityHit(
      String documentId,
      String subjectId,
      CapabilitySubjectKind subjectKind,
      double score,
      double bestCapabilityConfidence,
      List<String> matchedCapabilities,
      List<String> matchedAliases,
      CapabilityStatus status,
      boolean hasDeterministicEvidence,
      List<CapabilityEvidence> topEvidence,
      List<String> warnings,
      String sourceModId,
      String entryFingerprint
   ) {
      this.documentId = documentId;
      this.subjectId = subjectId;
      this.subjectKind = subjectKind;
      this.score = score;
      this.bestCapabilityConfidence = bestCapabilityConfidence;
      this.matchedCapabilities = matchedCapabilities;
      this.matchedAliases = matchedAliases;
      this.status = status;
      this.hasDeterministicEvidence = hasDeterministicEvidence;
      this.topEvidence = topEvidence;
      this.warnings = warnings;
      this.sourceModId = sourceModId;
      this.entryFingerprint = entryFingerprint;
   }

   public String getDocumentId() {
      return this.documentId;
   }

   public String getSubjectId() {
      return this.subjectId;
   }

   public CapabilitySubjectKind getSubjectKind() {
      return this.subjectKind;
   }

   public double getScore() {
      return this.score;
   }

   public double getBestCapabilityConfidence() {
      return this.bestCapabilityConfidence;
   }

   public List<String> getMatchedCapabilities() {
      return this.matchedCapabilities;
   }

   public List<String> getMatchedAliases() {
      return this.matchedAliases;
   }

   public CapabilityStatus getStatus() {
      return this.status;
   }

   public boolean isHasDeterministicEvidence() {
      return this.hasDeterministicEvidence;
   }

   public List<CapabilityEvidence> getTopEvidence() {
      return this.topEvidence;
   }

   public List<String> getWarnings() {
      return this.warnings;
   }

   public String getSourceModId() {
      return this.sourceModId;
   }

   public String getEntryFingerprint() {
      return this.entryFingerprint;
   }
}
