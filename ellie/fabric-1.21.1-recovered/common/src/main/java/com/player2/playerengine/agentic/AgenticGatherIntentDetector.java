package com.player2.playerengine.agentic;

import java.util.Locale;

public final class AgenticGatherIntentDetector {
   private static final String[] KEYWORDS = new String[]{
      "pick up",
      "pickup",
      "pick-up",
      "collect",
      "grab",
      "loot",
      "gather",
      "nearby drop",
      "nearby item",
      "item drop",
      "dropped item",
      "floor item",
      "ground item",
      "loose item",
      "drops around",
      "drops nearby",
      "pick up drop"
   };
   private static final String[] MINE_GET_KEYWORDS = new String[]{"find", "mine", "dig", "get", "fetch", "acquire", "obtain", "bring", "retrieve"};

   private AgenticGatherIntentDetector() {
   }

   public static boolean looksLikeGatherDropGoal(String goalText) {
      if (goalText != null && !goalText.isBlank()) {
         String lower = goalText.toLowerCase(Locale.ROOT);

         for (String keyword : KEYWORDS) {
            if (lower.contains(keyword)) {
               return true;
            }
         }

         return lower.contains("drop") && (lower.contains("near") || lower.contains("around") || lower.contains("here"));
      } else {
         return false;
      }
   }

   public static boolean looksLikeMineOrGetGoal(String goalText) {
      if (goalText != null && !goalText.isBlank()) {
         String lower = goalText.toLowerCase(Locale.ROOT);

         for (String keyword : MINE_GET_KEYWORDS) {
            if (lower.contains(keyword)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }
}
