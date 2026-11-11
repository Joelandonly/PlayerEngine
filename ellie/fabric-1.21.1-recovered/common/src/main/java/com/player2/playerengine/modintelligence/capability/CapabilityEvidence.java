package com.player2.playerengine.modintelligence.capability;

public final class CapabilityEvidence {
   private CapabilityEvidenceKind kind;
   private String source;
   private String value;
   private double confidence;
   private String note;

   public CapabilityEvidence() {
   }

   public CapabilityEvidence(CapabilityEvidenceKind kind, String source, String value, double confidence, String note) {
      this.kind = kind;
      this.source = source;
      this.value = value;
      this.confidence = confidence;
      this.note = note;
   }

   public CapabilityEvidenceKind getKind() {
      return this.kind;
   }

   public void setKind(CapabilityEvidenceKind kind) {
      this.kind = kind;
   }

   public String getSource() {
      return this.source;
   }

   public void setSource(String source) {
      this.source = source;
   }

   public String getValue() {
      return this.value;
   }

   public void setValue(String value) {
      this.value = value;
   }

   public double getConfidence() {
      return this.confidence;
   }

   public void setConfidence(double confidence) {
      this.confidence = confidence;
   }

   public String getNote() {
      return this.note;
   }

   public void setNote(String note) {
      this.note = note;
   }
}
