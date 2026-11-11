package com.player2.playerengine.player2api.mood;

public final class MoodMemoryRule {
   private MoodMemoryRule() {
   }

   private static boolean isNegative(MoodLabel label) {
      if (label == null) {
         return false;
      }

      switch (label) {
         case SAD:
         case ANGRY:
         case AFRAID:
         case ANXIOUS:
            return true;
         default:
            return false;
      }
   }

   private static boolean isPositive(MoodLabel label) {
      if (label == null) {
         return false;
      }

      switch (label) {
         case HAPPY:
         case CONTENT:
         case EXCITED:
            return true;
         default:
            return false;
      }
   }

   public static boolean isMeaningfulTransition(CompanionMood prev, CompanionMood next, boolean memorableFlag) {
      if (!memorableFlag) {
         return false;
      }

      if (prev != null && next != null) {
         if (next.label() == prev.label()) {
            return false;
         } else {
            String cause = next.cause();
            if (cause != null && !cause.isBlank()) {
               return next.intensity() >= 4 ? true : isNegative(prev.label()) && isPositive(next.label());
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }
}
