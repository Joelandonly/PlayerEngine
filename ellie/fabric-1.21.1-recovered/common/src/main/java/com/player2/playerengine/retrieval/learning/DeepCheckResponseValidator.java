package com.player2.playerengine.retrieval.learning;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandExecutor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class DeepCheckResponseValidator {
   private static final Pattern KEYWORD_PATTERN = Pattern.compile("[a-z0-9][a-z0-9 -]{0,31}");
   private static final int MAX_RETRY = 5;
   private static final int MIN_RETRY_LEN = 3;
   private static final int MAX_RETRY_LEN = 80;

   private DeepCheckResponseValidator() {
   }

   public static DeepCheckAttemptResult validate(JsonObject root, CommandExecutor commandExecutor) {
      if (root == null) {
         return DeepCheckAttemptResult.skipped("malformed_json");
      }

      if (root.has("schemaVersion") && root.get("schemaVersion").getAsInt() == 1) {
         List<String> retryQueries = parseRetryQueries(root);
         if (retryQueries.isEmpty()) {
            return DeepCheckAttemptResult.skipped("no_valid_retry_queries");
         }

         Optional<AliasLearnSuggestion> learn = parseLearn(root, commandExecutor);
         return DeepCheckAttemptResult.ok(new DeepCheckResponse(1, retryQueries, learn));
      } else {
         return DeepCheckAttemptResult.skipped("invalid_schema_version");
      }
   }

   private static List<String> parseRetryQueries(JsonObject root) {
      if (root.has("retryQueries") && root.get("retryQueries").isJsonArray()) {
         Set<String> seen = new HashSet<>();
         List<String> out = new ArrayList<>();

         for (JsonElement el : root.getAsJsonArray("retryQueries")) {
            if (el.isJsonPrimitive()) {
               String q = el.getAsString().trim();
               if (q.length() >= 3 && q.length() <= 80 && !q.contains("\n") && !q.contains("\r")) {
                  String key = q.toLowerCase(Locale.ROOT);
                  if (seen.add(key)) {
                     out.add(q);
                     if (out.size() >= 5) {
                        break;
                     }
                  }
               }
            }
         }

         return out;
      } else {
         return List.of();
      }
   }

   private static Optional<AliasLearnSuggestion> parseLearn(JsonObject root, CommandExecutor commandExecutor) {
      if (root.has("learn") && root.get("learn").isJsonObject()) {
         JsonObject learn = root.getAsJsonObject("learn");
         if (!learn.has("toolId")) {
            return Optional.empty();
         }

         String toolId = learn.get("toolId").getAsString().trim().toLowerCase(Locale.ROOT);
         Command cmd = commandExecutor.get(toolId);
         if (cmd == null) {
            return Optional.empty();
         }

         List<String> keywords = parseKeywords(learn);
         List<String> examples = parseExamples(learn);
         double confidence = 0.0;
         if (learn.has("confidence")) {
            confidence = Math.min(0.7, Math.max(0.0, learn.get("confidence").getAsDouble()));
         }

         String rationale = "";
         if (learn.has("rationale")) {
            rationale = learn.get("rationale").getAsString().trim();
            if (rationale.length() > 160) {
               rationale = rationale.substring(0, 160);
            }
         }

         return keywords.isEmpty() && examples.isEmpty()
            ? Optional.empty()
            : Optional.of(new AliasLearnSuggestion(toolId, keywords, examples, confidence, rationale));
      } else {
         return Optional.empty();
      }
   }

   private static List<String> parseKeywords(JsonObject learn) {
      if (learn.has("addKeywords") && learn.get("addKeywords").isJsonArray()) {
         List<String> out = new ArrayList<>();

         for (JsonElement el : learn.getAsJsonArray("addKeywords")) {
            if (el.isJsonPrimitive()) {
               String k = el.getAsString().trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
               if (KEYWORD_PATTERN.matcher(k).matches()) {
                  out.add(k);
                  if (out.size() >= 5) {
                     break;
                  }
               }
            }
         }

         return out;
      } else {
         return List.of();
      }
   }

   private static List<String> parseExamples(JsonObject learn) {
      if (learn.has("addExamples") && learn.get("addExamples").isJsonArray()) {
         List<String> out = new ArrayList<>();

         for (JsonElement el : learn.getAsJsonArray("addExamples")) {
            if (el.isJsonPrimitive()) {
               String ex = el.getAsString().trim();
               if (ex.length() > 96) {
                  ex = ex.substring(0, 96);
               }

               if (!ex.contains("\n") && !ex.contains("{") && !ex.contains("}") && !ex.contains(";") && !ex.startsWith("@")) {
                  out.add(ex);
                  if (out.size() >= 3) {
                     break;
                  }
               }
            }
         }

         return out;
      } else {
         return List.of();
      }
   }
}
