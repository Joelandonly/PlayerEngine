package com.player2.playerengine.memory.resolution;

import com.player2.playerengine.memory.MemoryGraph;
import com.player2.playerengine.memory.MemoryNode;
import com.player2.playerengine.memory.budget.Layer3Context;
import com.player2.playerengine.retrieval.MinHashIndex;
import com.player2.playerengine.retrieval.RetrievalHit;
import com.player2.playerengine.retrieval.ToolDocument;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;

public final class EntityResolver {
   public static final double EDIT_RATIO_THRESHOLD = 0.85;
   public static final int LAYER2_CANDIDATES = 3;
   private final Map<String, String> normalizedToId;
   private final MinHashIndex fuzzyIndex;
   private final Map<String, String> docIdToNodeId;
   private final Map<String, String> docIdToNormalized;
   private final MemoryGraph graph;
   private final int layer2MinChars;

   private EntityResolver(
      Map<String, String> normalizedToId,
      MinHashIndex fuzzyIndex,
      Map<String, String> docIdToNodeId,
      Map<String, String> docIdToNormalized,
      MemoryGraph graph,
      int layer2MinChars
   ) {
      this.normalizedToId = normalizedToId;
      this.fuzzyIndex = fuzzyIndex;
      this.docIdToNodeId = docIdToNodeId;
      this.docIdToNormalized = docIdToNormalized;
      this.graph = graph;
      this.layer2MinChars = layer2MinChars;
   }

   public static EntityResolver forGraph(MemoryGraph graph, int layer2MinChars) {
      Map<String, String> normalizedToId = new HashMap<>();
      Map<String, String> docIdToNodeId = new LinkedHashMap<>();
      Map<String, String> docIdToNormalized = new LinkedHashMap<>();
      List<ToolDocument> docs = new ArrayList<>();
      if (graph != null) {
         for (MemoryNode node : graph.nodes()) {
            indexName(node, node.canonicalName(), normalizedToId, docIdToNodeId, docIdToNormalized, docs);

            for (String alias : node.aliases()) {
               indexName(node, alias, normalizedToId, docIdToNodeId, docIdToNormalized, docs);
            }
         }
      }

      MinHashIndex fuzzyIndex = docs.isEmpty() ? null : MinHashIndex.build(docs);
      return new EntityResolver(normalizedToId, fuzzyIndex, docIdToNodeId, docIdToNormalized, graph, layer2MinChars);
   }

   private static void indexName(
      MemoryNode node,
      String name,
      Map<String, String> normalizedToId,
      Map<String, String> docIdToNodeId,
      Map<String, String> docIdToNormalized,
      List<ToolDocument> docs
   ) {
      String norm = MentionNormalizer.normalize(name);
      if (!norm.isEmpty()) {
         normalizedToId.putIfAbsent(norm, node.id());
         String docId = node.id() + "\u0000" + norm;
         if (!docIdToNodeId.containsKey(docId)) {
            docIdToNodeId.put(docId, node.id());
            docIdToNormalized.put(docId, norm);
            docs.add(new ToolDocument(docId, norm, "", "", List.of(), List.of(), List.of()));
         }
      }
   }

   public ResolutionResult resolve(MinecraftServer server, String mention, String hintType, Layer3Context ctx) {
      String normalized = MentionNormalizer.normalize(mention);
      if (normalized.isEmpty()) {
         return ResolutionResult.newNode();
      }

      String exactId = this.normalizedToId.get(normalized);
      if (exactId != null) {
         return ResolutionResult.exact(exactId, normalized);
      }

      if (normalized.length() >= this.layer2MinChars && this.fuzzyIndex != null) {
         ResolutionResult fuzzy = this.tryFuzzy(normalized);
         if (fuzzy != null) {
            return fuzzy;
         }
      }

      Layer3Context safeCtx = ctx == null ? Layer3Context.disabled() : ctx;
      if (!safeCtx.layer3Enabled()) {
         return ResolutionResult.newNode();
      }

      List<MemoryNode> candidates = this.selectCandidates(hintType);
      return candidates.isEmpty() ? ResolutionResult.newNode() : AliasDisambiguationClient.confirm(server, safeCtx, mention, normalized, hintType, candidates);
   }

   private ResolutionResult tryFuzzy(String normalized) {
      for (RetrievalHit hit : this.fuzzyIndex.query(normalized, 3)) {
         String indexed = this.docIdToNormalized.get(hit.id());
         if (indexed != null) {
            double ratio = editDistanceRatio(normalized, indexed);
            if (ratio >= 0.85) {
               String nodeId = this.docIdToNodeId.get(hit.id());
               if (nodeId != null) {
                  return ResolutionResult.fuzzy(nodeId, ratio, indexed);
               }
            }
         }
      }

      return null;
   }

   private List<MemoryNode> selectCandidates(String hintType) {
      List<MemoryNode> all = new ArrayList<>(this.graph == null ? List.of() : this.graph.nodes());
      all.sort((a, b) -> {
         boolean ah = hintType != null && hintType.equalsIgnoreCase(a.type());
         boolean bh = hintType != null && hintType.equalsIgnoreCase(b.type());
         if (ah != bh) {
            return ah ? -1 : 1;
         } else if (a.mentionCount() != b.mentionCount()) {
            return Integer.compare(b.mentionCount(), a.mentionCount());
         } else {
            return a.lastSeenTick() != b.lastSeenTick() ? Long.compare(b.lastSeenTick(), a.lastSeenTick()) : a.id().compareTo(b.id());
         }
      });
      int limit = Math.min(5, all.size());
      return new ArrayList<>(all.subList(0, limit));
   }

   static double editDistanceRatio(String a, String b) {
      if (a == null) {
         a = "";
      }

      if (b == null) {
         b = "";
      }

      int max = Math.max(a.length(), b.length());
      if (max == 0) {
         return 1.0;
      } else {
         int lev = levenshtein(a, b);
         double ratio = 1.0 - (double)lev / max;
         if (ratio < 0.0) {
            return 0.0;
         } else {
            return ratio > 1.0 ? 1.0 : ratio;
         }
      }
   }

   private static int levenshtein(String a, String b) {
      int n = a.length();
      int m = b.length();
      if (n == 0) {
         return m;
      }

      if (m == 0) {
         return n;
      }

      int[] prev = new int[m + 1];
      int[] curr = new int[m + 1];
      int j = 0;

      while (j <= m) {
         prev[j] = j++;
      }

      for (int i = 1; i <= n; i++) {
         curr[0] = i;
         char ca = a.charAt(i - 1);

         for (int jx = 1; jx <= m; jx++) {
            int cost = ca == b.charAt(jx - 1) ? 0 : 1;
            curr[jx] = Math.min(Math.min(curr[jx - 1] + 1, prev[jx] + 1), prev[jx - 1] + cost);
         }

         int[] tmp = prev;
         prev = curr;
         curr = tmp;
      }

      return prev[m];
   }
}
