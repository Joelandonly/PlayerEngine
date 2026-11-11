package com.player2.playerengine.memory.ingest;

import com.player2.playerengine.memory.MemoryCaps;
import com.player2.playerengine.memory.MemoryNodeType;
import com.player2.playerengine.memory.MergePlan;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MemoryMergePlanBuilder {
   private static final double DEFAULT_EDGE_WEIGHT = 1.0;
   private static final String PROVENANCE_RELATION = "mentions";

   private MemoryMergePlanBuilder() {
   }

   public static MergePlan build(MemoryExtractionResponse response, long nowTick) {
      if (response != null && !response.entities().isEmpty()) {
         long nowMs = System.currentTimeMillis();
         MergePlan.Builder builder = MergePlan.builder();
         Map<String, String> nameToId = new HashMap<>();
         List<String> entityIds = new ArrayList<>();

         for (MemoryExtractionResponse.Entity e : response.entities()) {
            String name = MemoryCaps.capName(e.name);
            if (name != null && !name.isEmpty()) {
               String key = name.toLowerCase(Locale.ROOT);
               if (!nameToId.containsKey(key)) {
                  String id = nodeId(name);
                  nameToId.put(key, id);
                  entityIds.add(id);
                  String type = MemoryNodeType.isKnown(e.type) ? MemoryNodeType.fromWire(e.type).wire() : e.type;
                  builder.upsert(new MergePlan.NodeUpsert(id, MemoryCaps.capContent(e.content), type, name, e.aliases, e.tags, e.importance, nowMs, nowTick));
               }
            }
         }

         if (entityIds.isEmpty()) {
            return null;
         }

         for (MemoryExtractionResponse.Relation r : response.relations()) {
            String fromId = nameToId.get(safeLower(r.from));
            String toId = nameToId.get(safeLower(r.to));
            if (fromId != null && toId != null) {
               builder.edge(new MergePlan.EdgeUpsert(fromId, toId, MemoryCaps.capRelation(r.relation), 1.0, nowTick));
            }
         }

         String episodicId = episodicId(nowMs, entityIds);
         String episodicContent = MemoryCaps.capContent(buildEpisodicContent(response));
         builder.upsert(
            new MergePlan.NodeUpsert(
               episodicId,
               episodicContent,
               MemoryNodeType.EVENT.wire(),
               MemoryCaps.capName("conversation memory " + episodicId.substring(0, Math.min(8, episodicId.length()))),
               List.of(),
               response.keywords(),
               response.importance(),
               nowMs,
               nowTick
            )
         );

         for (String entId : entityIds) {
            builder.edge(new MergePlan.EdgeUpsert(episodicId, entId, "mentions", 1.0, nowTick));
         }

         return builder.build();
      } else {
         return null;
      }
   }

   private static String buildEpisodicContent(MemoryExtractionResponse response) {
      return response.keywords().isEmpty() ? "conversation episode" : "conversation episode: " + String.join(", ", response.keywords());
   }

   private static String nodeId(String canonicalName) {
      return "n_" + sha256Hex(canonicalName.toLowerCase(Locale.ROOT)).substring(0, 16);
   }

   private static String episodicId(long nowMs, List<String> entityIds) {
      StringBuilder sb = new StringBuilder();
      sb.append(nowMs);

      for (String id : entityIds) {
         sb.append('|').append(id);
      }

      return "e_" + sha256Hex(sb.toString()).substring(0, 16);
   }

   private static String safeLower(String s) {
      return s == null ? "" : s.toLowerCase(Locale.ROOT);
   }

   private static String sha256Hex(String s) {
      try {
         MessageDigest md = MessageDigest.getInstance("SHA-256");
         byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
         StringBuilder hex = new StringBuilder(digest.length * 2);

         for (byte b : digest) {
            hex.append(Character.forDigit(b >> 4 & 15, 16));
            hex.append(Character.forDigit(b & 15, 16));
         }

         return hex.toString();
      } catch (NoSuchAlgorithmException impossible) {
         return Integer.toHexString(s.hashCode());
      }
   }
}
