package com.player2.playerengine.memory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class MemoryGraph {
   private final Map<String, MemoryNode> nodes = new LinkedHashMap<>();
   private final Map<String, MemoryEdge> edges = new LinkedHashMap<>();
   private final Map<String, List<String>> outgoing = new LinkedHashMap<>();
   private final Map<String, List<String>> incoming = new LinkedHashMap<>();
   private final Map<String, String> canonicalNameToId = new LinkedHashMap<>();

   public MemoryNode mergeNode(MemoryNode incoming) {
      if (incoming == null) {
         return null;
      }

      String canon = normalizeDurableCanonicalName(MemoryCaps.capName(incoming.canonicalName()), incoming);
      String key = canonicalKey(canon);
      String existingId = key != null ? this.canonicalNameToId.get(key) : null;
      if (existingId == null && this.nodes.containsKey(incoming.id())) {
         existingId = incoming.id();
      }

      if (existingId == null) {
         MemoryNode capped = capNode(rewriteCanonical(incoming, canon));
         this.nodes.put(capped.id(), capped);
         if (key != null) {
            this.canonicalNameToId.put(key, capped.id());
         }

         return capped;
      } else {
         MemoryNode existing = this.nodes.get(existingId);
         if (existing == null) {
            MemoryNode capped = capNode(rewriteId(rewriteCanonical(incoming, canon), existingId));
            this.nodes.put(existingId, capped);
            if (key != null) {
               this.canonicalNameToId.put(key, existingId);
            }

            return capped;
         } else {
            MemoryNode merged = unionMerge(existing, incoming, canon);
            this.nodes.put(existingId, merged);
            if (key != null) {
               this.canonicalNameToId.put(key, existingId);
            }

            return merged;
         }
      }
   }

   public MemoryEdge mergeEdge(String fromId, String toId, String relation, double addWeight, long tick) {
      if (fromId != null && toId != null) {
         String rel = MemoryCaps.capRelation(relation);
         String key = MemoryEdge.identityKey(fromId, rel, toId);
         MemoryEdge existing = this.edges.get(key);
         if (existing == null) {
            MemoryEdge fresh = new MemoryEdge(fromId, toId, rel, addWeight, tick, 1);
            this.edges.put(key, fresh);
            this.outgoing.computeIfAbsent(fromId, k -> new ArrayList<>()).add(key);
            this.incoming.computeIfAbsent(toId, k -> new ArrayList<>()).add(key);
            return fresh;
         } else {
            MemoryEdge reinforced = existing.reinforced(addWeight, Math.max(existing.lastSeenTick(), tick));
            this.edges.put(key, reinforced);
            return reinforced;
         }
      } else {
         return null;
      }
   }

   public MemoryNode recordMention(String nodeId, long tick) {
      MemoryNode n = this.nodes.get(nodeId);
      if (n == null) {
         return null;
      }

      MemoryNode updated = new MemoryNode(
         n.id(),
         n.content(),
         n.type(),
         n.canonicalName(),
         n.aliases().toArray(new String[0]),
         n.tags().toArray(new String[0]),
         n.importance(),
         n.createdTick(),
         n.timestampMs(),
         Math.max(n.lastSeenTick(), tick),
         n.lastRetrievedTick(),
         n.mentionCount() + 1,
         n.vector(),
         n.vectorModel(),
         n.schemaVersion()
      );
      this.nodes.put(nodeId, updated);
      return updated;
   }

   public MemoryNode setNodeVector(String nodeId, float[] vector, String vectorModel) {
      MemoryNode n = this.nodes.get(nodeId);
      if (n == null) {
         return null;
      }

      MemoryNode updated = new MemoryNode(
         n.id(),
         n.content(),
         n.type(),
         n.canonicalName(),
         n.aliases().toArray(new String[0]),
         n.tags().toArray(new String[0]),
         n.importance(),
         n.createdTick(),
         n.timestampMs(),
         n.lastSeenTick(),
         n.lastRetrievedTick(),
         n.mentionCount(),
         vector,
         vectorModel,
         n.schemaVersion()
      );
      this.nodes.put(nodeId, updated);
      return updated;
   }

   public MemoryNode removeNode(String nodeId) {
      MemoryNode removed = this.nodes.remove(nodeId);
      if (removed == null) {
         return null;
      }

      if (removed.canonicalName() != null) {
         String key = canonicalKey(removed.canonicalName());
         if (key != null && nodeId.equals(this.canonicalNameToId.get(key))) {
            this.canonicalNameToId.remove(key);
         }
      }

      List<String> out = this.outgoing.remove(nodeId);
      if (out != null) {
         for (String ek : out) {
            this.dropEdgeKey(ek);
         }
      }

      List<String> in = this.incoming.remove(nodeId);
      if (in != null) {
         for (String ek : in) {
            this.dropEdgeKey(ek);
         }
      }

      return removed;
   }

   public boolean removeEdge(String identityKey) {
      MemoryEdge e = this.edges.remove(identityKey);
      if (e == null) {
         return false;
      }

      List<String> out = this.outgoing.get(e.fromId());
      if (out != null) {
         out.remove(identityKey);
      }

      List<String> in = this.incoming.get(e.toId());
      if (in != null) {
         in.remove(identityKey);
      }

      return true;
   }

   public void apply(MergePlan plan) {
      if (plan != null && !plan.isEmpty()) {
         for (MergePlan.NodeUpsert u : plan.upserts()) {
            MemoryNode incoming = new MemoryNode(
               u.id,
               u.content,
               u.type,
               u.canonicalName,
               u.aliases.toArray(new String[0]),
               u.tags.toArray(new String[0]),
               u.importance,
               u.tick,
               u.timestampMs,
               u.tick,
               0L,
               1,
               null,
               null,
               1
            );
            this.mergeNode(incoming);
         }

         for (MergePlan.EdgeUpsert e : plan.edges()) {
            this.mergeEdge(e.fromId, e.toId, e.relation, e.addWeight, e.tick);
         }

         for (String id : plan.removals()) {
            this.removeNode(id);
         }
      }
   }

   public MemoryNode node(String id) {
      return this.nodes.get(id);
   }

   public MemoryNode findByCanonicalName(String name) {
      String key = canonicalKey(name);
      if (key == null) {
         return null;
      }

      String id = this.canonicalNameToId.get(key);
      return id == null ? null : this.nodes.get(id);
   }

   public List<MemoryEdge> neighbors(String nodeId) {
      List<String> keys = this.outgoing.get(nodeId);
      if (keys != null && !keys.isEmpty()) {
         List<MemoryEdge> out = new ArrayList<>(keys.size());

         for (String k : keys) {
            MemoryEdge e = this.edges.get(k);
            if (e != null) {
               out.add(e);
            }
         }

         return Collections.unmodifiableList(out);
      } else {
         return List.of();
      }
   }

   public List<MemoryEdge> incomingEdges(String nodeId) {
      List<String> keys = this.incoming.get(nodeId);
      if (keys != null && !keys.isEmpty()) {
         List<MemoryEdge> out = new ArrayList<>(keys.size());

         for (String k : keys) {
            MemoryEdge e = this.edges.get(k);
            if (e != null) {
               out.add(e);
            }
         }

         return Collections.unmodifiableList(out);
      } else {
         return List.of();
      }
   }

   public int nodeCount() {
      return this.nodes.size();
   }

   public int edgeCount() {
      return this.edges.size();
   }

   public Collection<MemoryNode> nodes() {
      return Collections.unmodifiableCollection(this.nodes.values());
   }

   public Collection<MemoryEdge> edges() {
      return Collections.unmodifiableCollection(this.edges.values());
   }

   private static String canonicalKey(String name) {
      if (name == null) {
         return null;
      }

      String t = name.trim().toLowerCase(Locale.ROOT);
      return t.isEmpty() ? null : t;
   }

   private static String normalizeDurableCanonicalName(String canon, MemoryNode incoming) {
      MemoryNodeType type = incoming == null ? null : MemoryNodeType.fromWire(incoming.type());
      if (MemoryDurableFactClassifier.isDurableFactType(type) && canon != null && !canon.isBlank()) {
         String normalized = canon;
         normalized = normalized.replaceAll("(?i)\\bfavourite\\b", "favorite");
         normalized = normalized.replaceAll("(?i)\\bfavourites\\b", "favorites");
         normalized = normalized.replaceAll("(?i)\\bcolour\\b", "color");
         normalized = normalized.replaceAll("(?i)\\bcolours\\b", "colors");
         normalized = normalized.replaceAll("(?i)\\bvideogames\\b", "video games");
         normalized = normalized.replaceAll("(?i)\\bvideogame\\b", "video game");
         normalized = normalized.replaceAll("(?i)\\bvideo games\\b", "video game");
         String lower = normalized.toLowerCase(Locale.ROOT);
         String legacyColorSuffix = " color preference";
         if (lower.endsWith(legacyColorSuffix)) {
            normalized = normalized.substring(0, normalized.length() - legacyColorSuffix.length()) + " favorite color";
         }

         return MemoryCaps.capName(normalized);
      } else {
         return canon;
      }
   }

   private void dropEdgeKey(String edgeKey) {
      MemoryEdge e = this.edges.remove(edgeKey);
      if (e != null) {
         List<String> out = this.outgoing.get(e.fromId());
         if (out != null) {
            out.remove(edgeKey);
         }

         List<String> in = this.incoming.get(e.toId());
         if (in != null) {
            in.remove(edgeKey);
         }
      }
   }

   private static MemoryNode capNode(MemoryNode n) {
      String content = MemoryCaps.capContent(n.content());
      String canon = MemoryCaps.capName(n.canonicalName());
      String[] aliases = capList(n.aliases(), 16, 64);
      String[] tags = capList(n.tags(), 16, 64);
      return new MemoryNode(
         n.id(),
         content,
         n.type(),
         canon,
         aliases,
         tags,
         n.importance(),
         n.createdTick(),
         n.timestampMs(),
         n.lastSeenTick(),
         n.lastRetrievedTick(),
         n.mentionCount(),
         n.vector(),
         n.vectorModel(),
         n.schemaVersion()
      );
   }

   private static MemoryNode rewriteCanonical(MemoryNode n, String canon) {
      return Objects.equals(n.canonicalName(), canon)
         ? n
         : new MemoryNode(
            n.id(),
            n.content(),
            n.type(),
            canon,
            n.aliases().toArray(new String[0]),
            n.tags().toArray(new String[0]),
            n.importance(),
            n.createdTick(),
            n.timestampMs(),
            n.lastSeenTick(),
            n.lastRetrievedTick(),
            n.mentionCount(),
            n.vector(),
            n.vectorModel(),
            n.schemaVersion()
         );
   }

   private static MemoryNode rewriteId(MemoryNode n, String id) {
      return new MemoryNode(
         id,
         n.content(),
         n.type(),
         n.canonicalName(),
         n.aliases().toArray(new String[0]),
         n.tags().toArray(new String[0]),
         n.importance(),
         n.createdTick(),
         n.timestampMs(),
         n.lastSeenTick(),
         n.lastRetrievedTick(),
         n.mentionCount(),
         n.vector(),
         n.vectorModel(),
         n.schemaVersion()
      );
   }

   private static MemoryNode unionMerge(MemoryNode existing, MemoryNode incoming, String canon) {
      if (isDurableFactMerge(existing, incoming)) {
         return durableFactMerge(existing, incoming, canon);
      }

      LinkedHashSet<String> aliasSet = new LinkedHashSet<>(existing.aliases());
      aliasSet.addAll(incoming.aliases());
      if (incoming.canonicalName() != null && !incoming.canonicalName().equalsIgnoreCase(existing.canonicalName())) {
         aliasSet.add(incoming.canonicalName());
      }

      String[] aliases = capList(new ArrayList<>(aliasSet), 16, 64);
      LinkedHashSet<String> tagSet = new LinkedHashSet<>(existing.tags());
      tagSet.addAll(incoming.tags());
      String[] tags = capList(new ArrayList<>(tagSet), 16, 64);
      int importance = Math.max(existing.importance(), incoming.importance());
      int mentionCount = existing.mentionCount() + incoming.mentionCount();
      long lastSeen = Math.max(existing.lastSeenTick(), incoming.lastSeenTick());
      long lastRetrieved = Math.max(existing.lastRetrievedTick(), incoming.lastRetrievedTick());
      boolean incNewer = incoming.timestampMs() >= existing.timestampMs();
      String content = MemoryCaps.capContent(incNewer ? incoming.content() : existing.content());
      String type = incNewer && incoming.type() != null ? incoming.type() : existing.type();
      long timestampMs = Math.max(existing.timestampMs(), incoming.timestampMs());
      long createdTick = Math.min(
         existing.createdTick() == 0L ? Long.MAX_VALUE : existing.createdTick(), incoming.createdTick() == 0L ? Long.MAX_VALUE : incoming.createdTick()
      );
      if (createdTick == Long.MAX_VALUE) {
         createdTick = existing.createdTick();
      }

      float[] vector = existing.hasVector() ? existing.vector() : incoming.vector();
      String vectorModel = existing.hasVector() ? existing.vectorModel() : incoming.vectorModel();
      return new MemoryNode(
         existing.id(),
         content,
         type,
         MemoryCaps.capName(canon),
         aliases,
         tags,
         importance,
         createdTick,
         timestampMs,
         lastSeen,
         lastRetrieved,
         mentionCount,
         vector,
         vectorModel,
         existing.schemaVersion()
      );
   }

   private static boolean isDurableFactMerge(MemoryNode existing, MemoryNode incoming) {
      MemoryNodeType existingType = MemoryNodeType.fromWire(existing.type());
      MemoryNodeType incomingType = MemoryNodeType.fromWire(incoming.type());
      return MemoryDurableFactClassifier.isDurableFactType(existingType) || MemoryDurableFactClassifier.isDurableFactType(incomingType);
   }

   private static MemoryNode durableFactMerge(MemoryNode existing, MemoryNode incoming, String canon) {
      MemoryNodeType incomingType = MemoryNodeType.fromWire(incoming.type());
      boolean incomingDurable = MemoryDurableFactClassifier.isDurableFactType(incomingType);
      boolean incNewer = incoming.timestampMs() >= existing.timestampMs();
      boolean replaceAtomicValue = incomingDurable && incNewer;
      String content = MemoryCaps.capContent(replaceAtomicValue ? incoming.content() : existing.content());
      String type = replaceAtomicValue && incomingType != null ? incomingType.wire() : existing.type();
      String[] aliases = replaceAtomicValue ? capList(incoming.aliases(), 16, 64) : capList(existing.aliases(), 16, 64);
      String[] tags = replaceAtomicValue ? capList(incoming.tags(), 16, 64) : capList(existing.tags(), 16, 64);
      int importance = Math.max(existing.importance(), incoming.importance());
      int mentionCount = existing.mentionCount() + incoming.mentionCount();
      long lastSeen = Math.max(existing.lastSeenTick(), incoming.lastSeenTick());
      long lastRetrieved = Math.max(existing.lastRetrievedTick(), incoming.lastRetrievedTick());
      long timestampMs = Math.max(existing.timestampMs(), incoming.timestampMs());
      long createdTick = mergedCreatedTick(existing, incoming);
      boolean changed = replaceAtomicValue
         && (
            !Objects.equals(content, existing.content())
               || !Objects.equals(type, existing.type())
               || !Arrays.equals(aliases, existing.aliases().toArray(new String[0]))
               || !Arrays.equals(tags, existing.tags().toArray(new String[0]))
         );
      float[] vector = changed ? null : existing.vector();
      String vectorModel = changed ? null : existing.vectorModel();
      return new MemoryNode(
         existing.id(),
         content,
         type,
         MemoryCaps.capName(canon),
         aliases,
         tags,
         importance,
         createdTick,
         timestampMs,
         lastSeen,
         lastRetrieved,
         mentionCount,
         vector,
         vectorModel,
         existing.schemaVersion()
      );
   }

   private static long mergedCreatedTick(MemoryNode existing, MemoryNode incoming) {
      long createdTick = Math.min(
         existing.createdTick() == 0L ? Long.MAX_VALUE : existing.createdTick(), incoming.createdTick() == 0L ? Long.MAX_VALUE : incoming.createdTick()
      );
      return createdTick == Long.MAX_VALUE ? existing.createdTick() : createdTick;
   }

   private static String[] capList(List<String> values, int maxCount, int maxLen) {
      List<String> out = new ArrayList<>(Math.min(values.size(), maxCount));

      for (String v : values) {
         if (out.size() >= maxCount) {
            break;
         }

         if (v != null) {
            String c = MemoryCaps.cap(v, maxLen);
            if (!c.isEmpty()) {
               out.add(c);
            }
         }
      }

      return out.toArray(new String[0]);
   }
}
