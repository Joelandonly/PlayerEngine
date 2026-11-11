package com.player2.playerengine.memory.ingest;

import com.player2.playerengine.memory.MemoryCaps;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MemoryEmotionEnricher {
   private MemoryEmotionEnricher() {
   }

   public static MemoryExtractionResponse enrich(MemoryExtractionResponse validated) {
      if (validated == null) {
         return null;
      }

      List<MemoryExtractionResponse.Relation> relations = new ArrayList<>(validated.relations().size());

      for (MemoryExtractionResponse.Relation r : validated.relations()) {
         String emotionToken = MemoryEmotionEnricher.Emotion.fromWireOrNeutral(r.emotion).wire();
         relations.add(new MemoryExtractionResponse.Relation(r.from, r.to, r.relation, emotionToken));
      }

      List<String> keywords = new ArrayList<>(validated.keywords());
      if (keywords.isEmpty()) {
         Set<String> derived = new LinkedHashSet<>();

         for (MemoryExtractionResponse.Entity e : validated.entities()) {
            String kw = MemoryCaps.capName(e.name);
            if (kw != null && !kw.isEmpty()) {
               derived.add(kw);
            }

            if (derived.size() >= 12) {
               break;
            }
         }

         keywords = new ArrayList<>(derived);
      }

      return new MemoryExtractionResponse(validated.entities(), relations, keywords, validated.importance());
   }

   public enum Emotion {
      NEUTRAL,
      JOY,
      TRUST,
      FEAR,
      SURPRISE,
      SADNESS,
      DISGUST,
      ANGER,
      ANTICIPATION;

      public String wire() {
         return this.name().toLowerCase(Locale.ROOT);
      }

      public static MemoryEmotionEnricher.Emotion fromWireOrNeutral(String raw) {
         if (raw == null) {
            return NEUTRAL;
         }

         String t = raw.trim().toUpperCase(Locale.ROOT);
         if (t.isEmpty()) {
            return NEUTRAL;
         }

         for (MemoryEmotionEnricher.Emotion e : values()) {
            if (e.name().equals(t)) {
               return e;
            }
         }

         return NEUTRAL;
      }
   }
}
