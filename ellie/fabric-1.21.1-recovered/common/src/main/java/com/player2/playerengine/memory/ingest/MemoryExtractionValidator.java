package com.player2.playerengine.memory.ingest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.player2.playerengine.memory.MemoryCaps;
import com.player2.playerengine.memory.MemoryDurableFactClassifier;
import com.player2.playerengine.memory.MemoryNodeType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MemoryExtractionValidator {
   public static final int MAX_ENTITIES = 12;
   public static final int MAX_RELATIONS = 16;
   public static final int MAX_KEYWORDS = 12;
   public static final int IMPORTANCE_MIN = 1;
   public static final int IMPORTANCE_MAX = 10;

   private MemoryExtractionValidator() {
   }

   public static MemoryExtractionResponse validate(String rawReply) {
      if (rawReply != null && !rawReply.isBlank()) {
         JsonObject root;
         try {
            JsonElement parsed = JsonParser.parseString(rawReply);
            if (parsed == null || !parsed.isJsonObject()) {
               return null;
            }

            root = parsed.getAsJsonObject();
         } catch (RuntimeException parseFailure) {
            return null;
         }

         List<MemoryExtractionResponse.Entity> entities = new ArrayList<>();
         Set<String> acceptedNames = new HashSet<>();
         JsonArray entityArr = optArray(root, "entities");
         if (entityArr != null) {
            for (JsonElement el : entityArr) {
               if (entities.size() >= 12) {
                  break;
               }

               if (el.isJsonObject()) {
                  MemoryExtractionResponse.Entity e = validateEntity(el.getAsJsonObject());
                  if (e != null) {
                     entities.add(e);
                     acceptedNames.add(e.name.toLowerCase(Locale.ROOT));
                  }
               }
            }
         }

         if (entities.isEmpty()) {
            return null;
         }

         List<MemoryExtractionResponse.Relation> relations = new ArrayList<>();
         JsonArray relArr = optArray(root, "relations");
         if (relArr != null) {
            for (JsonElement el : relArr) {
               if (relations.size() >= 16) {
                  break;
               }

               if (el.isJsonObject()) {
                  MemoryExtractionResponse.Relation r = validateRelation(el.getAsJsonObject(), acceptedNames);
                  if (r != null) {
                     relations.add(r);
                  }
               }
            }
         }

         List<String> keywords = capStringList(optArray(root, "keywords"), 12);
         int importance = clampImportance(optInt(root, "importance", 0));
         return new MemoryExtractionResponse(entities, relations, keywords, importance);
      } else {
         return null;
      }
   }

   private static MemoryExtractionResponse.Entity validateEntity(JsonObject o) {
      String name = MemoryCaps.capName(trimOrNull(optString(o, "name", null)));
      if (name != null && !name.isEmpty()) {
         String rawType = trimOrNull(optString(o, "type", null));
         if (rawType != null && MemoryNodeType.isKnown(rawType)) {
            String content = MemoryCaps.capContent(emptyIfNull(optString(o, "content", "")));
            List<String> aliases = capNameList(optArray(o, "aliases"), 16);
            List<String> tags = capNameList(optArray(o, "tags"), 16);
            int importance = clampImportanceOrUnscored(optInt(o, "importance", 0));
            MemoryNodeType type = MemoryDurableFactClassifier.normalizeExtractedType(MemoryNodeType.fromWire(rawType), name, content, tags);
            return type == null ? null : new MemoryExtractionResponse.Entity(name, type.wire(), content, aliases, tags, importance);
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static MemoryExtractionResponse.Relation validateRelation(JsonObject o, Set<String> accepted) {
      String from = MemoryCaps.capName(trimOrNull(optString(o, "from", null)));
      String to = MemoryCaps.capName(trimOrNull(optString(o, "to", null)));
      String relation = MemoryCaps.capRelation(trimOrNull(optString(o, "relation", null)));
      if (from == null || to == null || relation == null || from.isEmpty() || to.isEmpty() || relation.isEmpty()) {
         return null;
      }

      if (accepted.contains(from.toLowerCase(Locale.ROOT)) && accepted.contains(to.toLowerCase(Locale.ROOT))) {
         String emotion = trimOrNull(optString(o, "emotion", null));
         if (emotion != null) {
            emotion = MemoryCaps.capName(emotion);
         }

         return new MemoryExtractionResponse.Relation(from, to, relation, emotion);
      } else {
         return null;
      }
   }

   private static List<String> capNameList(JsonArray arr, int maxCount) {
      if (arr == null) {
         return List.of();
      }

      Set<String> out = new LinkedHashSet<>();

      for (JsonElement el : arr) {
         if (out.size() >= maxCount) {
            break;
         }

         if (el.isJsonPrimitive()) {
            String s = MemoryCaps.capName(trimOrNull(safeAsString(el)));
            if (s != null && !s.isEmpty()) {
               out.add(s);
            }
         }
      }

      return new ArrayList<>(out);
   }

   private static List<String> capStringList(JsonArray arr, int maxCount) {
      if (arr == null) {
         return List.of();
      }

      Set<String> out = new LinkedHashSet<>();

      for (JsonElement el : arr) {
         if (out.size() >= maxCount) {
            break;
         }

         if (el.isJsonPrimitive()) {
            String s = MemoryCaps.capName(trimOrNull(safeAsString(el)));
            if (s != null && !s.isEmpty()) {
               out.add(s);
            }
         }
      }

      return new ArrayList<>(out);
   }

   private static int clampImportance(int raw) {
      return raw < 1 ? 1 : Math.min(raw, 10);
   }

   private static int clampImportanceOrUnscored(int raw) {
      return raw <= 0 ? 0 : Math.min(raw, 10);
   }

   private static JsonArray optArray(JsonObject o, String key) {
      return o != null && o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : null;
   }

   private static String optString(JsonObject o, String key, String def) {
      if (o != null && o.has(key) && !o.get(key).isJsonNull()) {
         JsonElement el = o.get(key);
         return el.isJsonPrimitive() ? safeAsString(el) : def;
      } else {
         return def;
      }
   }

   private static int optInt(JsonObject o, String key, int def) {
      if (o != null && o.has(key) && o.get(key).isJsonPrimitive()) {
         try {
            return o.get(key).getAsInt();
         } catch (NumberFormatException nfe) {
            return def;
         }
      } else {
         return def;
      }
   }

   private static String safeAsString(JsonElement el) {
      try {
         return el.getAsString();
      } catch (RuntimeException ignored) {
         return null;
      }
   }

   private static String trimOrNull(String s) {
      if (s == null) {
         return null;
      }

      String t = s.trim();
      return t.isEmpty() ? null : t;
   }

   private static String emptyIfNull(String s) {
      return s == null ? "" : s;
   }
}
