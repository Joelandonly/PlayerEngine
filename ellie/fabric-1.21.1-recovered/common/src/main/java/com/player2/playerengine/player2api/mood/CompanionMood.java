package com.player2.playerengine.player2api.mood;

import com.google.gson.JsonObject;
import com.player2.playerengine.memory.MemoryCaps;
import java.util.Locale;

public record CompanionMood(MoodLabel label, String cause, int intensity, long updatedAtEpochMs) {
   public static final int DEFAULT_INTENSITY = 3;
   public static final int MIN_INTENSITY = 1;
   public static final int MAX_INTENSITY = 5;
   public static final int CAUSE_MAX = 120;
   public static final String KEY_LABEL = "label";
   public static final String KEY_CAUSE = "cause";
   public static final String KEY_INTENSITY = "intensity";
   public static final String KEY_UPDATED_AT = "updatedAtEpochMs";

   public CompanionMood {
      if (label == null) {
         label = MoodLabel.NEUTRAL;
      }

      if (cause == null) {
         cause = "";
      }

      intensity = clampIntensity(intensity);
   }

   public static CompanionMood neutral() {
      return new CompanionMood(MoodLabel.NEUTRAL, "", 3, System.currentTimeMillis());
   }

   public static int clampIntensity(int value) {
      if (value < 1) {
         return 1;
      } else {
         return value > 5 ? 5 : value;
      }
   }

   public String toPromptString() {
      String name = this.label.name().toLowerCase(Locale.ROOT);
      String boundedCause = MemoryCaps.cap(this.cause, 120);
      StringBuilder sb = new StringBuilder();
      sb.append(name).append(" (intensity ").append(this.intensity).append(")");
      if (boundedCause != null && !boundedCause.isBlank()) {
         sb.append(": ").append(boundedCause);
      }

      return sb.toString();
   }

   public JsonObject toJson() {
      JsonObject obj = new JsonObject();
      obj.addProperty("label", this.label.name());
      obj.addProperty("cause", this.cause);
      obj.addProperty("intensity", this.intensity);
      obj.addProperty("updatedAtEpochMs", this.updatedAtEpochMs);
      return obj;
   }

   public static CompanionMood fromJson(JsonObject obj) {
      if (obj == null) {
         return neutral();
      }

      MoodLabel label = MoodLabel.NEUTRAL;

      try {
         if (obj.has("label") && obj.get("label").isJsonPrimitive()) {
            label = MoodLabel.resolve(obj.get("label").getAsString()).orElse(MoodLabel.NEUTRAL);
         }
      } catch (Exception ignored) {
         label = MoodLabel.NEUTRAL;
      }

      String cause = "";

      try {
         if (obj.has("cause") && obj.get("cause").isJsonPrimitive()) {
            cause = obj.get("cause").getAsString();
         }
      } catch (Exception ignored) {
         cause = "";
      }

      int intensity = 3;

      try {
         if (obj.has("intensity") && obj.get("intensity").isJsonPrimitive()) {
            intensity = obj.get("intensity").getAsInt();
         }
      } catch (Exception ignored) {
         intensity = 3;
      }

      long updatedAt = System.currentTimeMillis();

      try {
         if (obj.has("updatedAtEpochMs") && obj.get("updatedAtEpochMs").isJsonPrimitive()) {
            updatedAt = obj.get("updatedAtEpochMs").getAsLong();
         }
      } catch (Exception ignored) {
         updatedAt = System.currentTimeMillis();
      }

      return new CompanionMood(label, cause, intensity, updatedAt);
   }
}
