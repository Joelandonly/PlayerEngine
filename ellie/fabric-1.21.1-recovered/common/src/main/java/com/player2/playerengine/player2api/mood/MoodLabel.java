package com.player2.playerengine.player2api.mood;

import java.util.Optional;

public enum MoodLabel {
   NEUTRAL,
   HAPPY,
   CONTENT,
   EXCITED,
   CURIOUS,
   SAD,
   ANXIOUS,
   ANGRY,
   AFRAID,
   DETERMINED;

   public static Optional<MoodLabel> resolve(String raw) {
      if (raw == null) {
         return Optional.empty();
      }

      String token = raw.trim();
      if (token.isEmpty()) {
         return Optional.empty();
      }

      for (MoodLabel label : values()) {
         if (label.name().equalsIgnoreCase(token)) {
            return Optional.of(label);
         }
      }

      return Optional.empty();
   }
}
