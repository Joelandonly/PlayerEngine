package com.player2.playerengine.modintelligence.capability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CapabilityMap {
   private int schemaVersion;
   private int inspectorVersion;
   private CapabilitySubjectKind subjectKind;
   private String subjectId;
   private String sourceModId;
   private String minecraftVersion;
   private String loader;
   private String entryFingerprint;
   private CapabilityStatus status;
   private List<CapabilityDecl> capabilities = new ArrayList<>();
   private List<String> tags = new ArrayList<>();
   private Map<String, String> stableProperties = new LinkedHashMap<>();
   private Map<String, String> sanitizedNbt = new LinkedHashMap<>();
   private EnrichmentSummary enrichment;
   private List<String> warnings = new ArrayList<>();
   private long updatedAtEpochMillis;

   public int getSchemaVersion() {
      return this.schemaVersion;
   }

   public void setSchemaVersion(int schemaVersion) {
      this.schemaVersion = schemaVersion;
   }

   public int getInspectorVersion() {
      return this.inspectorVersion;
   }

   public void setInspectorVersion(int inspectorVersion) {
      this.inspectorVersion = inspectorVersion;
   }

   public CapabilitySubjectKind getSubjectKind() {
      return this.subjectKind;
   }

   public void setSubjectKind(CapabilitySubjectKind subjectKind) {
      this.subjectKind = subjectKind;
   }

   public String getSubjectId() {
      return this.subjectId;
   }

   public void setSubjectId(String subjectId) {
      this.subjectId = subjectId;
   }

   public String getSourceModId() {
      return this.sourceModId;
   }

   public void setSourceModId(String sourceModId) {
      this.sourceModId = sourceModId;
   }

   public String getMinecraftVersion() {
      return this.minecraftVersion;
   }

   public void setMinecraftVersion(String minecraftVersion) {
      this.minecraftVersion = minecraftVersion;
   }

   public String getLoader() {
      return this.loader;
   }

   public void setLoader(String loader) {
      this.loader = loader;
   }

   public String getEntryFingerprint() {
      return this.entryFingerprint;
   }

   public void setEntryFingerprint(String entryFingerprint) {
      this.entryFingerprint = entryFingerprint;
   }

   public CapabilityStatus getStatus() {
      return this.status;
   }

   public void setStatus(CapabilityStatus status) {
      this.status = status;
   }

   public List<CapabilityDecl> getCapabilities() {
      return this.capabilities;
   }

   public void setCapabilities(List<CapabilityDecl> capabilities) {
      this.capabilities = capabilities == null ? new ArrayList<>() : new ArrayList<>(capabilities);
   }

   public List<String> getTags() {
      return this.tags;
   }

   public void setTags(List<String> tags) {
      this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
   }

   public Map<String, String> getStableProperties() {
      return this.stableProperties;
   }

   public void setStableProperties(Map<String, String> stableProperties) {
      this.stableProperties = stableProperties == null ? new LinkedHashMap<>() : new LinkedHashMap<>(stableProperties);
   }

   public Map<String, String> getSanitizedNbt() {
      return this.sanitizedNbt;
   }

   public void setSanitizedNbt(Map<String, String> sanitizedNbt) {
      this.sanitizedNbt = sanitizedNbt == null ? new LinkedHashMap<>() : new LinkedHashMap<>(sanitizedNbt);
   }

   public EnrichmentSummary getEnrichment() {
      return this.enrichment;
   }

   public void setEnrichment(EnrichmentSummary enrichment) {
      this.enrichment = enrichment;
   }

   public List<String> getWarnings() {
      return this.warnings;
   }

   public void setWarnings(List<String> warnings) {
      this.warnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
   }

   public long getUpdatedAtEpochMillis() {
      return this.updatedAtEpochMillis;
   }

   public void setUpdatedAtEpochMillis(long updatedAtEpochMillis) {
      this.updatedAtEpochMillis = updatedAtEpochMillis;
   }

   public String documentId() {
      return this.subjectKind.name() + " " + this.subjectId;
   }

   public double bestCapabilityConfidence() {
      double best = 0.0;

      for (CapabilityDecl c : this.capabilities) {
         if (c.getConfidence() > best) {
            best = c.getConfidence();
         }
      }

      return best;
   }

   public boolean hasDeterministicEvidence() {
      for (CapabilityDecl decl : this.capabilities) {
         for (CapabilityEvidence ev : decl.getEvidence()) {
            if (ev.getKind() != CapabilityEvidenceKind.ENRICHMENT && ev.getKind() != CapabilityEvidenceKind.HEURISTIC) {
               return true;
            }
         }
      }

      return false;
   }
}
