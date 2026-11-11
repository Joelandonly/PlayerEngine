package com.player2.playerengine.player2api.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Utils {
   private static final Logger LOGGER = LogManager.getLogger();
   private static final int RAW_CONTENT_LOG_LIMIT = 2000;

   public static String replacePlaceholders(String input, Map<String, String> replacements) {
      for (Entry<String, String> entry : replacements.entrySet()) {
         String placeholder = "\\{\\{" + entry.getKey() + "}}";
         input = input.replaceAll(placeholder, entry.getValue());
      }

      return input;
   }

   public static String getStringJsonSafely(JsonObject input, String fieldName) {
      return input.has(fieldName) && !input.get(fieldName).isJsonNull() ? input.get(fieldName).getAsString() : null;
   }

   public static String[] jsonArrayToStringArray(JsonArray jsonArray) {
      if (jsonArray == null) {
         return new String[0];
      }

      List<String> stringList = new ArrayList<>();

      for (JsonElement element : jsonArray) {
         if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            stringList.add(element.getAsString());
         } else {
            System.err.println("Warning: Skipping non-string element in JSON array: " + element);
         }
      }

      return stringList.toArray(new String[0]);
   }

   public static String[] getStringArrayJsonSafely(JsonObject input, String fieldName) {
      if (input.has(fieldName) && !input.get(fieldName).isJsonNull()) {
         JsonElement element = input.get(fieldName);
         if (!element.isJsonArray()) {
            System.err.println("Warning: Expected a JSON array for field '" + fieldName + "', but found a different type.");
            return null;
         } else {
            JsonArray jsonArray = element.getAsJsonArray();
            return jsonArrayToStringArray(jsonArray);
         }
      } else {
         return null;
      }
   }

   public static JsonObject parseCleanedJson(String content) throws LlmJsonParseException {
      String original = content == null ? "" : content;
      String cleaned = stripCodeFences(original);
      JsonObject obj = tryLenientParseObject(cleaned);
      if (obj != null) {
         return obj;
      }

      String extracted = extractFirstJsonObject(cleaned);
      if (extracted != null) {
         JsonObject extractedObj = tryLenientParseObject(extracted);
         if (extractedObj != null) {
            LOGGER.warn("parseCleanedJson: recovered JSON object via balanced-brace extraction from a padded model reply");
            return extractedObj;
         }

         String unwrapped = unwrapRedundantOuterBraces(extracted);
         if (unwrapped != null) {
            JsonObject unwrappedObj = tryLenientParseObject(unwrapped);
            if (unwrappedObj != null) {
               LOGGER.warn("parseCleanedJson: recovered JSON object by unwrapping redundant outer braces");
               return unwrappedObj;
            }
         }
      }

      LOGGER.warn(
         "parseCleanedJson: could not parse model reply as JSON. Raw content (truncated to {} chars): <<<{}>>>",
         2000,
         truncateForLog(original).replace('\n', ' ').replace('\r', ' ')
      );
      throw new LlmJsonParseException(truncateForLog(original), null);
   }

   private static String stripCodeFences(String content) {
      if (content == null) {
         return "";
      }

      String out = content.trim();
      out = out.replaceFirst("(?is)^```[ \\t]*[a-z0-9_-]*[ \\t]*\\r?\\n?", "");
      out = out.replaceFirst("(?s)\\r?\\n?[ \\t]*```[ \\t]*$", "");
      return out.trim();
   }

   private static JsonObject tryLenientParseObject(String text) {
      if (text != null && !text.isEmpty()) {
         try {
            JsonReader reader = new JsonReader(new StringReader(text));
            reader.setLenient(true);
            JsonElement el = JsonParser.parseReader(reader);
            if (el != null && el.isJsonObject()) {
               return el.getAsJsonObject();
            }
         } catch (JsonParseException | IllegalStateException var3) {
         }

         return null;
      } else {
         return null;
      }
   }

   private static String extractFirstJsonObject(String content) {
      if (content == null) {
         return null;
      }

      int start = content.indexOf(123);
      if (start < 0) {
         return null;
      }

      int depth = 0;
      boolean inString = false;
      boolean escaped = false;

      for (int i = start; i < content.length(); i++) {
         char c = content.charAt(i);
         if (inString) {
            if (escaped) {
               escaped = false;
            } else if (c == '\\') {
               escaped = true;
            } else if (c == '"') {
               inString = false;
            }
         } else if (c == '"') {
            inString = true;
         } else if (c == '{') {
            depth++;
         } else if (c == '}') {
            if (--depth == 0) {
               return content.substring(start, i + 1);
            }
         }
      }

      return null;
   }

   private static String unwrapRedundantOuterBraces(String content) {
      if (content == null) {
         return null;
      }

      String current = content.trim();

      while (current.startsWith("{{") && current.endsWith("}}")) {
         String inner = current.substring(1, current.length() - 1).trim();
         String balancedInner = extractFirstJsonObject(inner);
         if (balancedInner == null || balancedInner.length() != inner.length()) {
            return null;
         }

         current = inner;
      }

      return current.equals(content.trim()) ? null : current;
   }

   private static String truncateForLog(String content) {
      if (content == null) {
         return "null";
      } else {
         return content.length() <= 2000 ? content : content.substring(0, 2000) + "...[truncated " + (content.length() - 2000) + " chars]";
      }
   }

   public static String[] splitLinesToArray(String input) {
      return input != null && !input.isEmpty() ? input.split("\\R+") : new String[0];
   }

   public static JsonObject deepCopy(JsonObject original) {
      JsonParser parser = new JsonParser();
      return parser.parse(original.toString()).getAsJsonObject();
   }

   @FunctionalInterface
   public interface ThrowingFunction<T, R> {
      R apply(T var1) throws Exception;
   }
}
