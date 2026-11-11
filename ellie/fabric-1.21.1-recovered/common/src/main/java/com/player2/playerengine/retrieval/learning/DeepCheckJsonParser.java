package com.player2.playerengine.retrieval.learning;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.player2.playerengine.player2api.utils.Utils;

final class DeepCheckJsonParser {
   private DeepCheckJsonParser() {
   }

   static JsonObject parseOrExtract(String raw) {
      if (raw != null && !raw.isBlank()) {
         String trimmed = raw.trim();

         try {
            return Utils.parseCleanedJson(trimmed);
         } catch (Exception var9) {
            int start = trimmed.indexOf(123);
            if (start < 0) {
               return null;
            }

            int depth = 0;

            for (int i = start; i < trimmed.length(); i++) {
               char c = trimmed.charAt(i);
               if (c == '{') {
                  depth++;
               } else if (c == '}') {
                  if (--depth == 0) {
                     String slice = trimmed.substring(start, i + 1);

                     try {
                        return JsonParser.parseString(slice).getAsJsonObject();
                     } catch (Exception e) {
                        return null;
                     }
                  }
               }
            }

            return null;
         }
      } else {
         return null;
      }
   }
}
