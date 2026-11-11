package com.player2.playerengine.memory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;

public final class MemoryNodeCodec {
   public static final int CONTENT_MAX = 280;
   private static final Gson GSON = new Gson();
   private static final Set<String> KNOWN_KEYS = Set.of(
      "id",
      "content",
      "type",
      "canonicalName",
      "aliases",
      "tags",
      "importance",
      "createdTick",
      "timestampMs",
      "lastSeenTick",
      "lastRetrievedTick",
      "mentionCount",
      "vector",
      "vectorModel",
      "schemaVersion"
   );

   private MemoryNodeCodec() {
   }

   public static JsonObject toJson(MemoryNode node, JsonObject extras) {
      JsonObject obj = new JsonObject();
      if (extras != null) {
         for (Entry<String, JsonElement> entry : extras.entrySet()) {
            if (!KNOWN_KEYS.contains(entry.getKey())) {
               obj.add(entry.getKey(), entry.getValue());
            }
         }
      }

      obj.addProperty("id", node.id());
      obj.addProperty("content", cap(node.content(), 280));
      if (node.type() != null) {
         obj.addProperty("type", node.type());
      }

      if (node.canonicalName() != null) {
         obj.addProperty("canonicalName", node.canonicalName());
      }

      obj.add("aliases", toStringArray(node.aliases()));
      obj.add("tags", toStringArray(node.tags()));
      obj.addProperty("importance", node.importance());
      obj.addProperty("createdTick", node.createdTick());
      obj.addProperty("timestampMs", node.timestampMs());
      obj.addProperty("lastSeenTick", node.lastSeenTick());
      obj.addProperty("lastRetrievedTick", node.lastRetrievedTick());
      obj.addProperty("mentionCount", node.mentionCount());
      float[] vec = node.vector();
      if (vec != null) {
         JsonArray va = new JsonArray(vec.length);

         for (float v : vec) {
            va.add(v);
         }

         obj.add("vector", va);
         if (node.vectorModel() != null) {
            obj.addProperty("vectorModel", node.vectorModel());
         }
      }

      obj.addProperty("schemaVersion", node.schemaVersion());
      return obj;
   }

   public static JsonObject toJson(MemoryNode node) {
      return toJson(node, null);
   }

   public static String toJsonString(MemoryNode node) {
      return GSON.toJson(toJson(node, null));
   }

   public static MemoryNode fromJson(JsonObject obj) {
      String id = optString(obj, "id", null);
      String content = cap(optString(obj, "content", ""), 280);
      String type = optString(obj, "type", null);
      String canonicalName = optString(obj, "canonicalName", null);
      String[] aliases = optStringArray(obj, "aliases");
      String[] tags = optStringArray(obj, "tags");
      int importance = optInt(obj, "importance", 0);
      long createdTick = optLong(obj, "createdTick", 0L);
      long timestampMs = optLong(obj, "timestampMs", 0L);
      long lastSeenTick = optLong(obj, "lastSeenTick", 0L);
      long lastRetrieved = optLong(obj, "lastRetrievedTick", 0L);
      int mentionCount = optInt(obj, "mentionCount", 0);
      int schemaVersion = optInt(obj, "schemaVersion", 1);
      float[] vector = null;
      String vectorModel = null;
      if (obj.has("vector") && obj.get("vector").isJsonArray()) {
         JsonArray va = obj.getAsJsonArray("vector");
         vector = new float[va.size()];

         for (int i = 0; i < va.size(); i++) {
            vector[i] = va.get(i).getAsFloat();
         }

         vectorModel = optString(obj, "vectorModel", null);
      }

      return new MemoryNode(
         id,
         content,
         type,
         canonicalName,
         aliases,
         tags,
         importance,
         createdTick,
         timestampMs,
         lastSeenTick,
         lastRetrieved,
         mentionCount,
         vector,
         vectorModel,
         schemaVersion
      );
   }

   public static JsonObject extractExtras(JsonObject obj) {
      JsonObject extras = new JsonObject();

      for (Entry<String, JsonElement> entry : obj.entrySet()) {
         if (!KNOWN_KEYS.contains(entry.getKey())) {
            extras.add(entry.getKey(), entry.getValue());
         }
      }

      return extras.size() > 0 ? extras : null;
   }

   private static String cap(String s, int max) {
      if (s == null) {
         return "";
      } else {
         return s.length() <= max ? s : s.substring(0, max);
      }
   }

   private static JsonArray toStringArray(List<String> values) {
      JsonArray arr = new JsonArray(values.size());

      for (String v : values) {
         arr.add(v);
      }

      return arr;
   }

   private static String optString(JsonObject obj, String key, String fallback) {
      return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : fallback;
   }

   private static int optInt(JsonObject obj, String key, int fallback) {
      return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsInt() : fallback;
   }

   private static long optLong(JsonObject obj, String key, long fallback) {
      return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsLong() : fallback;
   }

   private static String[] optStringArray(JsonObject obj, String key) {
      if (obj.has(key) && obj.get(key).isJsonArray()) {
         JsonArray arr = obj.getAsJsonArray(key);
         List<String> out = new ArrayList<>(arr.size());

         for (JsonElement e : arr) {
            if (e.isJsonPrimitive()) {
               out.add(e.getAsString());
            }
         }

         return out.toArray(new String[0]);
      } else {
         return new String[0];
      }
   }
}
