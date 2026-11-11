package com.player2.playerengine.memory.resolution;

import com.player2.playerengine.memory.MemoryCaps;
import com.player2.playerengine.memory.MemoryEdge;
import com.player2.playerengine.memory.MemoryGraph;
import com.player2.playerengine.memory.MemoryNode;
import com.player2.playerengine.memory.MergePlan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

public final class NodeMerger {
   private NodeMerger() {
   }

   public static MergePlan merge(MemoryGraph graph, List<NodeMerger.ResolvedEntity> entities, List<NodeMerger.ResolvedRelation> relations) {
      MergePlan.Builder plan = MergePlan.builder();
      if (entities == null) {
         entities = List.of();
      }

      if (relations == null) {
         relations = List.of();
      }

      Map<String, NodeMerger.EntityAccumulator> byId = new LinkedHashMap<>();
      Map<String, String> keyToCanonical = new LinkedHashMap<>();

      for (NodeMerger.ResolvedEntity e : entities) {
         if (e != null) {
            String id = e.effectiveId();
            if (id != null) {
               NodeMerger.EntityAccumulator acc = byId.computeIfAbsent(id, k -> new NodeMerger.EntityAccumulator(id));
               acc.fold(e);
               keyToCanonical.put(id, id);
               if (e.resolvedNodeId() != null) {
                  keyToCanonical.put(e.resolvedNodeId(), id);
               }

               if (e.newNodeId() != null) {
                  keyToCanonical.put(e.newNodeId(), id);
               }
            }
         }
      }

      collapseByCanonicalName(graph, byId, keyToCanonical, plan);

      for (NodeMerger.EntityAccumulator acc : byId.values()) {
         plan.upsert(acc.toUpsert());
      }

      LinkedHashSet<String> emittedEdges = new LinkedHashSet<>();

      for (NodeMerger.ResolvedRelation r : relations) {
         if (r != null && r.relation() != null) {
            String from = keyToCanonical.getOrDefault(r.fromKey(), r.fromKey());
            String to = keyToCanonical.getOrDefault(r.toKey(), r.toKey());
            if (from != null && to != null) {
               String rel = MemoryCaps.capRelation(r.relation());
               String edgeKey = from + "\u0000" + rel + "\u0000" + to;
               if (emittedEdges.add(edgeKey)) {
                  double weight = r.addWeight() == 0.0 ? 1.0 : r.addWeight();
                  plan.edge(new MergePlan.EdgeUpsert(from, to, rel, weight, r.tick()));
               }
            }
         }
      }

      return plan.build();
   }

   private static void collapseByCanonicalName(
      MemoryGraph graph, Map<String, NodeMerger.EntityAccumulator> byId, Map<String, String> keyToCanonical, MergePlan.Builder plan
   ) {
      Map<String, String> nameToWinnerId = new LinkedHashMap<>();

      for (String id : new ArrayList<>(byId.keySet())) {
         NodeMerger.EntityAccumulator acc = byId.get(id);
         if (acc != null) {
            String nameKey = canonicalKey(acc.canonicalName);
            if (nameKey != null) {
               String winnerId = nameToWinnerId.get(nameKey);
               if (winnerId == null) {
                  nameToWinnerId.put(nameKey, id);
               } else {
                  NodeMerger.EntityAccumulator winner = byId.get(winnerId);
                  String trueWinnerId = pickCanonical(graph, winnerId, id, winner, acc);
                  String loserId = trueWinnerId.equals(winnerId) ? id : winnerId;
                  NodeMerger.EntityAccumulator keep = byId.get(trueWinnerId);
                  NodeMerger.EntityAccumulator drop = byId.get(loserId);
                  keep.absorb(drop);
                  byId.remove(loserId);
                  nameToWinnerId.put(nameKey, trueWinnerId);

                  for (Entry<String, String> en : keyToCanonical.entrySet()) {
                     if (loserId.equals(en.getValue())) {
                        en.setValue(trueWinnerId);
                     }
                  }

                  if (graph != null) {
                     for (MemoryEdge edge : graph.neighbors(loserId)) {
                        plan.edge(new MergePlan.EdgeUpsert(trueWinnerId, edge.toId(), edge.relation(), edge.weight(), edge.lastSeenTick()));
                     }

                     for (MemoryEdge edge : graph.incomingEdges(loserId)) {
                        plan.edge(new MergePlan.EdgeUpsert(edge.fromId(), trueWinnerId, edge.relation(), edge.weight(), edge.lastSeenTick()));
                     }

                     plan.remove(loserId);
                  }
               }
            }
         }
      }
   }

   private static String pickCanonical(MemoryGraph graph, String idA, String idB, NodeMerger.EntityAccumulator accA, NodeMerger.EntityAccumulator accB) {
      int mcA = effectiveMentionCount(graph, idA, accA);
      int mcB = effectiveMentionCount(graph, idB, accB);
      if (mcA != mcB) {
         return mcA > mcB ? idA : idB;
      } else {
         long createdA = createdTickOf(graph, idA);
         long createdB = createdTickOf(graph, idB);
         if (createdA != createdB) {
            return createdA < createdB ? idA : idB;
         } else {
            return idA.compareTo(idB) <= 0 ? idA : idB;
         }
      }
   }

   private static int effectiveMentionCount(MemoryGraph graph, String id, NodeMerger.EntityAccumulator acc) {
      int existing = 0;
      if (graph != null) {
         MemoryNode n = graph.node(id);
         if (n != null) {
            existing = n.mentionCount();
         }
      }

      return existing + acc.observationCount;
   }

   private static long createdTickOf(MemoryGraph graph, String id) {
      if (graph != null) {
         MemoryNode n = graph.node(id);
         if (n != null && n.createdTick() != 0L) {
            return n.createdTick();
         }
      }

      return Long.MAX_VALUE;
   }

   private static String canonicalKey(String name) {
      if (name == null) {
         return null;
      }

      String t = name.trim().toLowerCase(Locale.ROOT);
      return t.isEmpty() ? null : t;
   }

   private static final class EntityAccumulator {
      final String id;
      String canonicalName;
      String type;
      String content;
      long latestTs = Long.MIN_VALUE;
      long tick;
      int maxImportance = 0;
      int observationCount = 0;
      final LinkedHashSet<String> aliases = new LinkedHashSet<>();
      final LinkedHashSet<String> tags = new LinkedHashSet<>();

      EntityAccumulator(String id) {
         this.id = id;
      }

      void fold(NodeMerger.ResolvedEntity e) {
         this.observationCount++;
         if (e.importance() > this.maxImportance) {
            this.maxImportance = e.importance();
         }

         if (e.timestampMs() >= this.latestTs) {
            this.latestTs = e.timestampMs();
            if (e.content() != null) {
               this.content = e.content();
            }

            if (e.type() != null && !e.type().isEmpty()) {
               this.type = e.type();
            }

            this.tick = e.tick();
         }

         if (this.canonicalName == null && e.canonicalName() != null) {
            this.canonicalName = e.canonicalName();
         }

         if (e.canonicalName() != null) {
            this.aliases.add(e.canonicalName());
         }

         if (e.aliases() != null) {
            for (String a : e.aliases()) {
               if (a != null && !a.isBlank()) {
                  this.aliases.add(a);
               }
            }
         }

         if (e.tags() != null) {
            for (String t : e.tags()) {
               if (t != null && !t.isBlank()) {
                  this.tags.add(t);
               }
            }
         }
      }

      void absorb(NodeMerger.EntityAccumulator other) {
         if (other != null) {
            this.observationCount = this.observationCount + other.observationCount;
            this.maxImportance = Math.max(this.maxImportance, other.maxImportance);
            if (other.latestTs >= this.latestTs) {
               this.latestTs = other.latestTs;
               if (other.content != null) {
                  this.content = other.content;
               }

               if (other.type != null && !other.type.isEmpty()) {
                  this.type = other.type;
               }

               this.tick = other.tick;
            }

            if (other.canonicalName != null) {
               this.aliases.add(other.canonicalName);
            }

            this.aliases.addAll(other.aliases);
            this.tags.addAll(other.tags);
         }
      }

      MergePlan.NodeUpsert toUpsert() {
         String name = MemoryCaps.capName(this.canonicalName);
         List<String> aliasList = capStrings(this.aliases, 16, 64, name);
         List<String> tagList = capStrings(this.tags, 16, 64, null);
         String cappedContent = MemoryCaps.capContent(this.content);
         long ts = this.latestTs == Long.MIN_VALUE ? 0L : this.latestTs;
         return new MergePlan.NodeUpsert(this.id, cappedContent, this.type, name, aliasList, tagList, this.maxImportance, ts, this.tick);
      }

      private static List<String> capStrings(LinkedHashSet<String> src, int maxCount, int maxLen, String excludeEqualTo) {
         List<String> out = new ArrayList<>(Math.min(src.size(), maxCount));

         for (String s : src) {
            if (out.size() >= maxCount) {
               break;
            }

            if (s != null) {
               String c = MemoryCaps.cap(s, maxLen);
               if (!c.isEmpty() && (excludeEqualTo == null || !c.equalsIgnoreCase(excludeEqualTo)) && !out.contains(c)) {
                  out.add(c);
               }
            }
         }

         return out;
      }
   }

   public record ResolvedEntity(
      String resolvedNodeId,
      String newNodeId,
      String canonicalName,
      String type,
      String content,
      List<String> aliases,
      List<String> tags,
      int importance,
      long timestampMs,
      long tick
   ) {
      public String effectiveId() {
         return this.resolvedNodeId != null ? this.resolvedNodeId : this.newNodeId;
      }
   }

   public record ResolvedRelation(String fromKey, String toKey, String relation, double addWeight, long tick) {
   }
}
