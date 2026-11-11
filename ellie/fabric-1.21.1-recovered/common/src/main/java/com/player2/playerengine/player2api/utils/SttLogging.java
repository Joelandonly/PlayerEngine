package com.player2.playerengine.player2api.utils;

import com.google.gson.JsonElement;
import java.util.Map;
import java.util.stream.Collectors;

public final class SttLogging {
   private static final int PREVIEW_MAX_CHARS = 120;

   private SttLogging() {
   }

   public static String messagePreview(String message) {
      if (message == null) {
         return "<null>";
      } else {
         String trimmed = message.strip();
         if (trimmed.isEmpty()) {
            return "<blank>";
         } else {
            return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120) + "...";
         }
      }
   }

   public static String jsonResponseKeys(Map<String, JsonElement> response) {
      return response != null && !response.isEmpty() ? response.keySet().stream().sorted().collect(Collectors.joining(", ")) : "<empty>";
   }
}
