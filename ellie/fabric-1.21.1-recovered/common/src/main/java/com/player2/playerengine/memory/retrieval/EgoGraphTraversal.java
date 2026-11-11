package com.player2.playerengine.memory.retrieval;

import com.player2.playerengine.memory.MemoryEdge;
import com.player2.playerengine.memory.MemoryGraph;
import com.player2.playerengine.memory.MemoryNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EgoGraphTraversal {
   public static final int DEFAULT_MAX_HOPS = 2;
   public static final int DEFAULT_MAX_EGO_NODES = 64;
   public static final double SEED_SCORE = 1.0;

   private EgoGraphTraversal() {
   }

   public static List<EgoGraphTraversal.EgoNode> traverse(
      MemoryGraph graph, List<String> seedNodeIds, long nowTick, int maxHops, int maxEgoNodes, double decayBase, long gameTimeUnit, long deadlineNanos
   ) {
      if (graph != null && seedNodeIds != null && !seedNodeIds.isEmpty()) {
         int hops = maxHops > 0 ? maxHops : 2;
         int cap = maxEgoNodes > 0 ? maxEgoNodes : 64;
         double base = decayBase > 0.0 ? decayBase : 0.995;
         long unit = gameTimeUnit > 0L ? gameTimeUnit : 1L;
         Map<String, EgoGraphTraversal.EgoNode> reached = new LinkedHashMap<>();
         Set<String> enqueued = new HashSet<>();
         Deque<EgoGraphTraversal.Frontier> queue = new ArrayDeque<>();

         for (String seedId : seedNodeIds) {
            if (seedId != null) {
               MemoryNode n = graph.node(seedId);
               if (n != null && !enqueued.contains(seedId)) {
                  enqueued.add(seedId);
                  queue.add(new EgoGraphTraversal.Frontier(seedId, 1.0, 0));
                  reached.put(seedId, new EgoGraphTraversal.EgoNode(n, 1.0, 0, true));
               }
            }
         }

         while (!queue.isEmpty() && System.nanoTime() < deadlineNanos && reached.size() < cap) {
            EgoGraphTraversal.Frontier cur = queue.poll();
            if (cur.hop < hops) {
               for (MemoryEdge edge : graph.neighbors(cur.id)) {
                  String toId = edge.toId();
                  if (toId != null) {
                     MemoryNode neighbor = graph.node(toId);
                     if (neighbor != null) {
                        double step = edge.weight() * recencyDecay(nowTick - edge.lastSeenTick(), base, unit);
                        double childScore = cur.score * step;
                        EgoGraphTraversal.EgoNode existing = reached.get(toId);
                        if (existing == null) {
                           if (reached.size() >= cap) {
                              break;
                           }

                           reached.put(toId, new EgoGraphTraversal.EgoNode(neighbor, childScore, cur.hop + 1, false));
                        } else if (childScore > existing.relevance()) {
                           reached.put(toId, new EgoGraphTraversal.EgoNode(neighbor, childScore, Math.min(existing.hop(), cur.hop + 1), existing.isSeed()));
                        }

                        if (!enqueued.contains(toId) && cur.hop + 1 < hops) {
                           enqueued.add(toId);
                           queue.add(new EgoGraphTraversal.Frontier(toId, childScore, cur.hop + 1));
                        }
                     }
                  }
               }
            }
         }

         return new ArrayList<>(reached.values());
      } else {
         return List.of();
      }
   }

   static double recencyDecay(long deltaTicks, double decayBase, long unit) {
      long delta = Math.max(0L, deltaTicks);
      double exponent = (double)delta / (unit > 0L ? unit : 1L);
      return Math.pow(decayBase, exponent);
   }

   public static Map<String, EgoGraphTraversal.EgoNode> byId(List<EgoGraphTraversal.EgoNode> egoNodes) {
      Map<String, EgoGraphTraversal.EgoNode> map = new HashMap<>(Math.max(16, egoNodes.size() * 2));

      for (EgoGraphTraversal.EgoNode e : egoNodes) {
         map.put(e.node().id(), e);
      }

      return map;
   }

   public static final class EgoNode {
      private final MemoryNode node;
      private final double relevance;
      private final int hop;
      private final boolean seed;

      EgoNode(MemoryNode node, double relevance, int hop, boolean seed) {
         this.node = node;
         this.relevance = relevance;
         this.hop = hop;
         this.seed = seed;
      }

      public MemoryNode node() {
         return this.node;
      }

      public double relevance() {
         return this.relevance;
      }

      public int hop() {
         return this.hop;
      }

      public boolean isSeed() {
         return this.seed;
      }
   }

   private static final class Frontier {
      final String id;
      final double score;
      final int hop;

      Frontier(String id, double score, int hop) {
         this.id = id;
         this.score = score;
         this.hop = hop;
      }
   }
}
