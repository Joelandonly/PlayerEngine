package com.player2.playerengine.memory;

public final class MemoryCaps {
   public static final int CONTENT_MAX = 280;
   public static final int NAME_MAX = 64;
   public static final int RELATION_MAX = 48;
   public static final int ALIASES_MAX = 16;
   public static final int TAGS_MAX = 16;
   public static final int MAX_NODES = 500;
   public static final int MAX_EDGES = 2000;
   public static final int MAX_EDGES_PER_PAIR = 8;
   public static final long TICKS_PER_GAME_HOUR = 1000L;
   public static final long STALE_TICKS = 168000L;
   public static final double DECAY_BASE = 0.995;

   private MemoryCaps() {
   }

   public static String cap(String s, int max) {
      if (s == null) {
         return "";
      } else {
         return s.length() <= max ? s : s.substring(0, max);
      }
   }

   public static String capContent(String s) {
      return cap(s, 280);
   }

   public static String capName(String s) {
      return cap(s, 64);
   }

   public static String capRelation(String s) {
      return cap(s, 48);
   }
}
