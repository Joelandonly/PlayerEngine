package com.player2.playerengine.player2api.mood;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.player2.playerengine.memory.MemoryCaps;
import java.util.Optional;

public final class MoodUpdate {
   public static final int MOOD_CAUSE_MAX = 120;
   private static final String FIELD_LABEL = "label";
   private static final String FIELD_CAUSE = "cause";
   private static final String FIELD_INTENSITY = "intensity";
   private static final String FIELD_MEMORABLE = "memorable";

   private MoodUpdate() {
   }

   public static MoodUpdate.MoodUpdateResult apply(JsonObject moodObj, CompanionMood current) {
      if (moodObj == null || moodObj.size() == 0) {
         return MoodUpdate.MoodUpdateResult.unchanged(current);
      }

      if (!moodObj.has("label")) {
         return MoodUpdate.MoodUpdateResult.unchanged(current);
      }

      String rawLabel = null;

      try {
         JsonElement labelEl = moodObj.get("label");
         if (labelEl != null && labelEl.isJsonPrimitive()) {
            rawLabel = labelEl.getAsString();
         }
      } catch (Exception var12) {
      }

      Optional<MoodLabel> resolved = MoodLabel.resolve(rawLabel);
      if (!resolved.isPresent()) {
         return MoodUpdate.MoodUpdateResult.invalidLabel(current);
      }

      MoodLabel newLabel = resolved.get();
      String cause = "";

      try {
         if (moodObj.has("cause")) {
            JsonElement causeEl = moodObj.get("cause");
            if (causeEl != null && causeEl.isJsonPrimitive()) {
               cause = causeEl.getAsString();
            }
         }
      } catch (Exception ignored) {
         cause = "";
      }

      cause = MemoryCaps.cap(cause, 120);
      int intensity = 3;

      try {
         if (moodObj.has("intensity")) {
            JsonElement intensityEl = moodObj.get("intensity");
            if (intensityEl != null && intensityEl.isJsonPrimitive()) {
               intensity = intensityEl.getAsInt();
            }
         }
      } catch (Exception ignored) {
         intensity = 3;
      }

      intensity = CompanionMood.clampIntensity(intensity);
      boolean memorable = false;

      try {
         if (moodObj.has("memorable")) {
            JsonElement memorableEl = moodObj.get("memorable");
            if (memorableEl != null && memorableEl.isJsonPrimitive()) {
               memorable = memorableEl.getAsBoolean();
            }
         }
      } catch (Exception ignored) {
         memorable = false;
      }

      CompanionMood newMood = new CompanionMood(newLabel, cause, intensity, System.currentTimeMillis());
      return MoodUpdate.MoodUpdateResult.updated(newMood, memorable);
   }

   public static final class MoodUpdateResult {
      private final CompanionMood mood;
      private final boolean invalidLabel;
      private final boolean memorable;
      private final boolean updated;

      private MoodUpdateResult(CompanionMood mood, boolean invalidLabel, boolean memorable, boolean updated) {
         this.mood = mood;
         this.invalidLabel = invalidLabel;
         this.memorable = memorable;
         this.updated = updated;
      }

      static MoodUpdate.MoodUpdateResult unchanged(CompanionMood current) {
         return new MoodUpdate.MoodUpdateResult(current, false, false, false);
      }

      static MoodUpdate.MoodUpdateResult invalidLabel(CompanionMood current) {
         return new MoodUpdate.MoodUpdateResult(current, true, false, false);
      }

      static MoodUpdate.MoodUpdateResult updated(CompanionMood newMood, boolean memorable) {
         return new MoodUpdate.MoodUpdateResult(newMood, false, memorable, true);
      }

      public CompanionMood mood() {
         return this.mood;
      }

      public boolean invalidLabel() {
         return this.invalidLabel;
      }

      public boolean memorable() {
         return this.memorable;
      }

      public boolean wasUpdated() {
         return this.updated;
      }
   }
}
