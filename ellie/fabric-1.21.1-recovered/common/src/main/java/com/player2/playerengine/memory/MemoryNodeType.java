package com.player2.playerengine.memory;

import java.util.Locale;

public enum MemoryNodeType {
   CHARACTER,
   PLACE,
   EVENT,
   FACTION,
   ITEM,
   FACT,
   PREFERENCE,
   REFLECTION;

   public String wire() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public static MemoryNodeType fromWire(String raw) {
      if (raw == null) {
         return null;
      }

      String t = raw.trim().toUpperCase(Locale.ROOT);
      if (t.isEmpty()) {
         return null;
      }

      for (MemoryNodeType v : values()) {
         if (v.name().equals(t)) {
            return v;
         }
      }

      return null;
   }

   public static boolean isKnown(String raw) {
      return fromWire(raw) != null;
   }
}
