package com.player2.playerengine.modintelligence.enrich;

import java.util.ArrayList;
import java.util.List;

public final class CapabilityEnrichmentResult {
   private int schemaVersion;
   private String shortDescription;
   private List<String> aliases = new ArrayList<>();
   private List<String> likelyUses = new ArrayList<>();
   private List<CapabilityEnrichmentResult.CapabilityHint> capabilityHints = new ArrayList<>();
   private List<String> uncertaintyNotes = new ArrayList<>();

   public int getSchemaVersion() {
      return this.schemaVersion;
   }

   public String getShortDescription() {
      return this.shortDescription;
   }

   public List<String> getAliases() {
      return this.aliases;
   }

   public List<String> getLikelyUses() {
      return this.likelyUses;
   }

   public List<CapabilityEnrichmentResult.CapabilityHint> getCapabilityHints() {
      return this.capabilityHints;
   }

   public List<String> getUncertaintyNotes() {
      return this.uncertaintyNotes;
   }

   public static final class CapabilityHint {
      private String id;
      private double confidence;
      private String reason;

      public String getId() {
         return this.id;
      }

      public double getConfidence() {
         return this.confidence;
      }

      public String getReason() {
         return this.reason;
      }
   }
}
