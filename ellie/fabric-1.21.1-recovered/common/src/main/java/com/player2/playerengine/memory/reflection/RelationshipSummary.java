package com.player2.playerengine.memory.reflection;

import com.player2.playerengine.memory.MemoryCaps;

public final class RelationshipSummary {
   public static final int CHAR_CAP = 280;
   public static final String LABEL = "[Relationship]";
   private final String text;

   private RelationshipSummary(String text) {
      this.text = text;
   }

   public static RelationshipSummary of(String raw) {
      return new RelationshipSummary(normalizeAndCap(raw));
   }

   public static RelationshipSummary empty() {
      return new RelationshipSummary("");
   }

   public String text() {
      return this.text;
   }

   public boolean isEmpty() {
      return this.text.isEmpty();
   }

   public String systemBlockSuffix() {
      return this.text.isEmpty() ? "" : "\n\n[Relationship] " + this.text;
   }

   public static String suffixFor(String rawStoredSummary) {
      return of(rawStoredSummary).systemBlockSuffix();
   }

   private static String normalizeAndCap(String raw) {
      if (raw == null) {
         return "";
      }

      String collapsed = raw.trim().replaceAll("\\s+", " ");
      return MemoryCaps.cap(collapsed, 280);
   }
}
