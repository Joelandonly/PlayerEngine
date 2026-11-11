package com.player2.playerengine.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MemoryCompactor {
   private MemoryCompactor() {
   }

   public static void compact(MemoryGraph graph, long nowTick) {
      if (graph != null) {
         edgePerPairCap(graph, nowTick);
         staleWeakEdgeEviction(graph, nowTick);
         totalEdgeCeiling(graph, nowTick);
         orphanWeakNodeEviction(graph, nowTick);
         totalNodeCeiling(graph, nowTick);
      }
   }

   private static void edgePerPairCap(MemoryGraph graph, long nowTick) {
      Map<String, List<MemoryEdge>> byPair = new HashMap<>();

      for (MemoryEdge e : new ArrayList<>(graph.edges())) {
         byPair.computeIfAbsent(e.fromId() + "\u0000" + e.toId(), k -> new ArrayList<>()).add(e);
      }

      for (List<MemoryEdge> group : byPair.values()) {
         if (group.size() > 8) {
            group.sort(edgeStrengthDesc(nowTick));

            for (int i = 8; i < group.size(); i++) {
               graph.removeEdge(group.get(i).identityKey());
            }
         }
      }
   }

   private static void staleWeakEdgeEviction(MemoryGraph graph, long nowTick) {
      for (MemoryEdge e : new ArrayList<>(graph.edges())) {
         if (e.mentionCount() == 1 && isStale(e.lastSeenTick(), nowTick)) {
            graph.removeEdge(e.identityKey());
         }
      }
   }

   private static void totalEdgeCeiling(MemoryGraph graph, long nowTick) {
      int over = graph.edgeCount() - 2000;
      if (over > 0) {
         List<MemoryEdge> all = new ArrayList<>(graph.edges());
         all.sort(edgeStrengthAsc(nowTick));

         for (int i = 0; i < over && i < all.size(); i++) {
            graph.removeEdge(all.get(i).identityKey());
         }
      }
   }

   private static void orphanWeakNodeEviction(MemoryGraph graph, long nowTick) {
      Set<String> connected = new HashSet<>();

      for (MemoryEdge e : graph.edges()) {
         connected.add(e.fromId());
         connected.add(e.toId());
      }

      for (MemoryNode n : new ArrayList<>(graph.nodes())) {
         boolean orphan = !connected.contains(n.id());
         if (orphan && n.importance() <= 0 && n.mentionCount() <= 1 && isStale(n.lastSeenTick(), nowTick)) {
            graph.removeNode(n.id());
         }
      }
   }

   private static void totalNodeCeiling(MemoryGraph graph, long nowTick) {
      int over = graph.nodeCount() - 500;
      if (over > 0) {
         List<MemoryNode> all = new ArrayList<>(graph.nodes());
         all.sort(nodeSalienceAsc(nowTick));

         for (int i = 0; i < over && i < all.size(); i++) {
            graph.removeNode(all.get(i).id());
         }
      }
   }

   private static boolean isStale(long lastSeenTick, long nowTick) {
      return nowTick - lastSeenTick > 168000L;
   }

   static double recencyDecay(long lastSeenTick, long nowTick) {
      long deltaTicks = Math.max(0L, nowTick - lastSeenTick);
      double hours = deltaTicks / 1000.0;
      return Math.pow(0.995, hours);
   }

   private static double edgeStrength(MemoryEdge e, long nowTick) {
      return e.weight() * recencyDecay(e.lastSeenTick(), nowTick);
   }

   private static double nodeSalience(MemoryNode n, long nowTick) {
      return (n.importance() + 1) * recencyDecay(n.lastSeenTick(), nowTick);
   }

   private static Comparator<MemoryEdge> edgeStrengthAsc(long nowTick) {
      return Comparator.<MemoryEdge>comparingDouble(e -> edgeStrength(e, nowTick)).thenComparing(MemoryEdge::identityKey);
   }

   private static Comparator<MemoryEdge> edgeStrengthDesc(long nowTick) {
      return Comparator.<MemoryEdge>comparingDouble(e -> edgeStrength(e, nowTick)).reversed().thenComparing(MemoryEdge::identityKey);
   }

   private static Comparator<MemoryNode> nodeSalienceAsc(long nowTick) {
      return Comparator.<MemoryNode>comparingDouble(n -> nodeSalience(n, nowTick)).thenComparing(MemoryNode::id);
   }
}
