package com.player2.playerengine.memory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.memory.budget.MemoryLlmClient;
import com.player2.playerengine.memory.dense.DenseIndexState;
import com.player2.playerengine.memory.dense.EmbeddingModel;
import com.player2.playerengine.player2api.Player2NpcPersistencePaths;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MemoryStore {
   public static final int SUPPORTED_SCHEMA_VERSION = 1;
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private final MemoryScope scope;
   private final Path graphFile;
   private final MemoryGraph graph = new MemoryGraph();
   private String relationshipSummary = "";
   private long cumulativeImportanceSinceLastReflection = 0L;
   private int importanceRubricVersion = 1;
   private int summaryVersion = 0;
   private JsonObject rootExtras;
   private volatile DenseIndexState denseIndex = DenseIndexState.empty();
   private volatile boolean denseDirty = false;
   private boolean dirty = false;
   private volatile boolean startedFromQuarantine = false;
   private volatile MemoryStore.Snapshot snapshot;
   static final int RELATIONSHIP_SUMMARY_CHAR_CAP = 280;
   private static final List<String> KNOWN_ROOT_KEYS = List.of(
      "schemaVersion",
      "ownerUuid",
      "companionId",
      "relationshipSummary",
      "cumulativeImportanceSinceLastReflection",
      "importanceRubricVersion",
      "summaryVersion",
      "nodes",
      "edges"
   );

   private MemoryStore(MemoryScope scope, Path graphFile) {
      this.scope = scope;
      this.graphFile = graphFile;
   }

   public static MemoryStore loadForCompanion(Path worldRoot, MemoryScope scope) {
      Path file = scope.hasOwner()
         ? Player2NpcPersistencePaths.memoryGraphFile(worldRoot, scope.ownerUuid(), scope.companionId())
         : Player2NpcPersistencePaths.memoryGraphFileEntityFallback(worldRoot, scope.entityUuid(), scope.companionId());
      MemoryStore store = new MemoryStore(scope, file);
      store.load();
      store.loadDenseIndex();
      store.publishSnapshot();
      return store;
   }

   private void loadDenseIndex() {
      DenseIndexState loaded = DenseIndexState.load(this.denseIndexPath());
      if (loaded == null) {
         this.denseIndex = DenseIndexState.empty();
         this.denseDirty = false;
      } else {
         loaded.pruneTo(this.graph);
         this.denseIndex = loaded;
      }
   }

   private void load() {
      if (!Files.exists(this.graphFile)) {
         PlayerEngine.LOGGER.info("Memory: no graph.json for {} — starting empty.", this.scope);
      } else {
         try {
            String json = Files.readString(this.graphFile, StandardCharsets.UTF_8);
            JsonObject root = (JsonObject)GSON.fromJson(json, JsonObject.class);
            if (root == null) {
               this.quarantine("root is null");
               return;
            }

            int version = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 0;
            if (version > 1) {
               this.quarantine("schemaVersion " + version + " > supported 1");
               return;
            }

            this.relationshipSummary = optString(root, "relationshipSummary", "");
            this.cumulativeImportanceSinceLastReflection = optLong(root, "cumulativeImportanceSinceLastReflection", 0L);
            this.importanceRubricVersion = optInt(root, "importanceRubricVersion", 1);
            this.summaryVersion = optInt(root, "summaryVersion", 0);
            JsonObject extras = root.deepCopy();

            for (String known : KNOWN_ROOT_KEYS) {
               extras.remove(known);
            }

            this.rootExtras = extras.size() > 0 ? extras : null;
            int loadedNodes = this.loadNodes(root);
            int loadedEdges = this.loadEdges(root);
            PlayerEngine.LOGGER.info("Memory: loaded {} node(s), {} edge(s) for {}.", loadedNodes, loadedEdges, this.scope);
         } catch (Exception e) {
            this.quarantine("parse error: " + safeReason(e));
         }
      }
   }

   private int loadNodes(JsonObject root) {
      if (root.has("nodes") && root.get("nodes").isJsonArray()) {
         int loaded = 0;

         for (JsonElement elem : root.getAsJsonArray("nodes")) {
            if (elem.isJsonObject()) {
               MemoryNode n = MemoryNodeCodec.fromJson(elem.getAsJsonObject());
               if (n != null && n.id() != null) {
                  this.graph.mergeNode(n);
                  loaded++;
               }
            }
         }

         return loaded;
      } else {
         return 0;
      }
   }

   private int loadEdges(JsonObject root) {
      if (root.has("edges") && root.get("edges").isJsonArray()) {
         int loaded = 0;

         for (JsonElement elem : root.getAsJsonArray("edges")) {
            if (elem.isJsonObject()) {
               JsonObject o = elem.getAsJsonObject();
               String fromId = optString(o, "fromId", null);
               String toId = optString(o, "toId", null);
               if (fromId != null && toId != null) {
                  String relation = optString(o, "relation", "");
                  double weight = o.has("weight") ? o.get("weight").getAsDouble() : 1.0;
                  long lastSeen = optLong(o, "lastSeenTick", 0L);
                  int mentionCount = Math.max(1, optInt(o, "mentionCount", 1));
                  this.graph.mergeEdge(fromId, toId, relation, weight, lastSeen);

                  for (int i = 1; i < mentionCount; i++) {
                     this.graph.mergeEdge(fromId, toId, relation, 0.0, lastSeen);
                  }

                  loaded++;
               }
            }
         }

         return loaded;
      } else {
         return 0;
      }
   }

   private void quarantine(String boundedReason) {
      String quarantineName = "graph.json.corrupt-" + System.currentTimeMillis();

      try {
         Path target = this.graphFile.getParent().resolve(quarantineName);
         Files.move(this.graphFile, target, StandardCopyOption.REPLACE_EXISTING);
         PlayerEngine.LOGGER
            .warn("Memory: quarantined corrupt graph.json as {} for {} (reason: {}); starting empty.", quarantineName, this.scope, boundedReason);
      } catch (IOException ex) {
         PlayerEngine.LOGGER.warn("Memory: could not quarantine graph.json for {} (reason: {}); starting empty.", this.scope, boundedReason);
      }

      this.resetInMemory();
      this.startedFromQuarantine = true;
   }

   private void resetInMemory() {
      for (MemoryNode n : new ArrayList<>(this.graph.nodes())) {
         this.graph.removeNode(n.id());
      }

      this.relationshipSummary = "";
      this.cumulativeImportanceSinceLastReflection = 0L;
      this.importanceRubricVersion = 1;
      this.summaryVersion = 0;
      this.rootExtras = null;
   }

   public void mergeCandidates(MergePlan plan) {
      if (plan != null && !plan.isEmpty()) {
         long episodicImportance = this.newlyMintedEpisodicImportance(plan);
         this.graph.apply(plan);
         if (episodicImportance > 0L) {
            this.cumulativeImportanceSinceLastReflection += episodicImportance;
         }

         this.markDirtyAndPublish();
      }
   }

   private long newlyMintedEpisodicImportance(MergePlan plan) {
      long sum = 0L;
      Set<String> countedThisBatch = null;

      for (MergePlan.NodeUpsert u : plan.upserts()) {
         if (u != null && u.importance > 0 && MemoryNodeType.EVENT.wire().equalsIgnoreCase(u.type) && this.graph.node(u.id) == null) {
            if (countedThisBatch == null) {
               countedThisBatch = new HashSet<>();
            }

            if (countedThisBatch.add(u.id)) {
               sum += u.importance;
            }
         }
      }

      return sum;
   }

   public void recordMention(String nodeId, long tick) {
      if (this.graph.recordMention(nodeId, tick) != null) {
         this.markDirtyAndPublish();
      }
   }

   public boolean removeNode(String nodeId) {
      if (this.graph.removeNode(nodeId) != null) {
         this.markDirtyAndPublish();
         return true;
      } else {
         return false;
      }
   }

   public String relationshipSummary() {
      return this.relationshipSummary;
   }

   public void setRelationshipSummary(String summary) {
      this.relationshipSummary = MemoryCaps.cap(summary, effectiveSummaryCharCap());
      this.cumulativeImportanceSinceLastReflection = 0L;
      this.summaryVersion++;
      this.markDirtyAndPublish();
   }

   public static int effectiveSummaryCharCap() {
      int configured = Player2ServerConfigHolder.get().getRelationshipSummaryCharCapClamped();
      return Math.min(280, Math.max(0, configured));
   }

   public long cumulativeImportanceSinceLastReflection() {
      return this.cumulativeImportanceSinceLastReflection;
   }

   public void addCumulativeImportance(long delta) {
      if (delta != 0L) {
         this.cumulativeImportanceSinceLastReflection += delta;
         this.markDirtyAndPublish();
      }
   }

   public void resetReflectionCounterOnly() {
      if (this.cumulativeImportanceSinceLastReflection != 0L) {
         this.cumulativeImportanceSinceLastReflection = 0L;
         this.markDirtyAndPublish();
      }
   }

   public int importanceRubricVersion() {
      return this.importanceRubricVersion;
   }

   public void setImportanceRubricVersion(int v) {
      this.importanceRubricVersion = v;
      this.markDirtyAndPublish();
   }

   public int summaryVersion() {
      return this.summaryVersion;
   }

   public MemoryStore.Snapshot snapshot() {
      return this.snapshot;
   }

   public String versionToken() {
      return this.computeVersionToken();
   }

   public boolean startedFromQuarantine() {
      return this.startedFromQuarantine;
   }

   public boolean consumeQuarantineNote() {
      if (this.startedFromQuarantine) {
         this.startedFromQuarantine = false;
         return true;
      } else {
         return false;
      }
   }

   public MemoryScope scope() {
      return this.scope;
   }

   Path graphFile() {
      return this.graphFile;
   }

   public Path denseIndexPath() {
      return this.graphFile.resolveSibling("dense-index.bin");
   }

   public DenseIndexState denseIndex() {
      return this.denseIndex;
   }

   public boolean denseBackfillComplete() {
      return this.denseIndex.backfillComplete();
   }

   public List<String> denseBackfillCandidates(int max) {
      String frozen = EmbeddingModel.vectorModelToken();
      List<String> out = new ArrayList<>();

      for (MemoryNode n : this.graph.nodes()) {
         if (max > 0 && out.size() >= max) {
            break;
         }

         if (!n.hasVector() || !frozen.equals(n.vectorModel())) {
            out.add(n.id());
         }
      }

      return out;
   }

   public String denseEmbeddableText(String nodeId) {
      MemoryNode n = this.graph.node(nodeId);
      return n == null ? null : denseEmbeddableText(n);
   }

   public static String denseEmbeddableText(MemoryNode n) {
      if (n == null) {
         return "";
      }

      StringBuilder sb = new StringBuilder();
      if (n.canonicalName() != null && !n.canonicalName().isBlank()) {
         sb.append(n.canonicalName());
      }

      if (n.type() != null && !n.type().isBlank()) {
         sb.append(" [").append(n.type()).append(']');
      }

      if (n.content() != null && !n.content().isBlank()) {
         sb.append(": ").append(n.content());
      }

      return sb.toString().trim();
   }

   public void applyNodeVector(String nodeId, float[] vector) {
      if (nodeId != null && vector != null && vector.length == 1536) {
         String frozen = EmbeddingModel.vectorModelToken();
         if (this.graph.setNodeVector(nodeId, vector, frozen) != null) {
            this.denseIndex.put(nodeId, vector);
            this.denseDirty = true;
            this.markDirtyAndPublish();
         }
      }
   }

   public void setDenseBackfillComplete(boolean done) {
      this.denseIndex.setBackfillComplete(done);
      this.denseDirty = true;
   }

   public void flushIfDirty(long nowTick) {
      if (this.dirty) {
         this.dirty = false;

         try {
            MemoryCompactor.compact(this.graph, nowTick);
            this.denseIndex.pruneTo(this.graph);
            this.publishSnapshot();
            this.persist();
         } catch (Exception e) {
            this.dirty = true;
            PlayerEngine.LOGGER.warn("Memory: flush failed for {} (reason: {}); will retry.", this.scope, safeReason(e));
         }
      }

      this.flushDenseIndexOffTick();
   }

   private void flushDenseIndexOffTick() {
      if (this.denseDirty) {
         this.denseDirty = false;
         DenseIndexState snap = this.denseIndex.copy();
         Path path = this.denseIndexPath();

         try {
            MemoryLlmClient.MEMORY_EXECUTOR.submit(() -> {
               try {
                  snap.save(path);
               } catch (RuntimeException e) {
                  PlayerEngine.LOGGER.warn("Memory: dense-index.bin write failed ({}).", e.getClass().getSimpleName());
               }
            });
         } catch (RuntimeException e) {
            this.denseDirty = true;
         }
      }
   }

   public void clear(long nowTick) {
      try {
         if (this.dirty) {
            MemoryCompactor.compact(this.graph, nowTick);
            this.persist();
         }
      } catch (Exception e) {
         PlayerEngine.LOGGER.warn("Memory: final flush failed for {} (reason: {}).", this.scope, safeReason(e));
      }

      this.flushDenseIndexOffTick();
      this.resetInMemory();
      this.snapshot = new MemoryStore.Snapshot(new MemoryGraph(), this.computeVersionToken(), "");
      this.dirty = false;
   }

   private void persist() {
      try {
         Files.createDirectories(this.graphFile.getParent());
         JsonObject root = this.rootExtras != null ? this.rootExtras.deepCopy() : new JsonObject();
         root.addProperty("schemaVersion", 1);
         if (this.scope.hasOwner()) {
            root.addProperty("ownerUuid", this.scope.ownerUuid().toString());
         }

         root.addProperty("companionId", this.scope.companionId());
         root.addProperty("relationshipSummary", this.relationshipSummary);
         root.addProperty("cumulativeImportanceSinceLastReflection", this.cumulativeImportanceSinceLastReflection);
         root.addProperty("importanceRubricVersion", this.importanceRubricVersion);
         root.addProperty("summaryVersion", this.summaryVersion);
         JsonArray nodeArr = new JsonArray();

         for (MemoryNode n : this.graph.nodes()) {
            nodeArr.add(MemoryNodeCodec.toJson(n));
         }

         root.add("nodes", nodeArr);
         JsonArray edgeArr = new JsonArray();

         for (MemoryEdge e : this.graph.edges()) {
            edgeArr.add(edgeToJson(e));
         }

         root.add("edges", edgeArr);
         String json = GSON.toJson(root);
         Path tmp = this.graphFile.resolveSibling("graph.json.tmp");
         Files.writeString(tmp, json, StandardCharsets.UTF_8);
         atomicReplace(tmp, this.graphFile);
      } catch (Exception e) {
         PlayerEngine.LOGGER.warn("Memory: failed to persist graph.json for {} (reason: {}).", this.scope, safeReason(e));
      }
   }

   private static JsonObject edgeToJson(MemoryEdge e) {
      JsonObject o = new JsonObject();
      o.addProperty("fromId", e.fromId());
      o.addProperty("toId", e.toId());
      o.addProperty("relation", MemoryCaps.capRelation(e.relation()));
      o.addProperty("weight", e.weight());
      o.addProperty("lastSeenTick", e.lastSeenTick());
      o.addProperty("mentionCount", e.mentionCount());
      return o;
   }

   private static void atomicReplace(Path tmp, Path target) throws IOException {
      try {
         Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
         Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
      }
   }

   private void markDirtyAndPublish() {
      this.dirty = true;
      this.publishSnapshot();
   }

   private void publishSnapshot() {
      MemoryGraph copy = this.copyGraph();
      this.snapshot = new MemoryStore.Snapshot(copy, computeVersionToken(copy), this.relationshipSummary);
   }

   private MemoryGraph copyGraph() {
      MemoryGraph copy = new MemoryGraph();

      for (MemoryNode n : this.graph.nodes()) {
         copy.mergeNode(n);
      }

      for (MemoryEdge e : this.graph.edges()) {
         copy.mergeEdge(e.fromId(), e.toId(), e.relation(), e.weight(), e.lastSeenTick());

         for (int i = 1; i < e.mentionCount(); i++) {
            copy.mergeEdge(e.fromId(), e.toId(), e.relation(), 0.0, e.lastSeenTick());
         }
      }

      return copy;
   }

   private String computeVersionToken() {
      return computeVersionToken(this.graph);
   }

   private static String computeVersionToken(MemoryGraph g) {
      List<String> nodeLines = new ArrayList<>(g.nodeCount());

      for (MemoryNode n : g.nodes()) {
         nodeLines.add(
            n.id()
               + "|"
               + safe(n.canonicalName())
               + "|"
               + safe(n.type())
               + "|"
               + n.importance()
               + "|"
               + n.mentionCount()
               + "|"
               + n.lastSeenTick()
               + "|"
               + String.join(",", n.aliases())
               + "|"
               + safe(n.content())
         );
      }

      Collections.sort(nodeLines);
      List<String> edgeLines = new ArrayList<>(g.edgeCount());

      for (MemoryEdge e : g.edges()) {
         edgeLines.add(e.fromId() + "|" + e.relation() + "|" + e.toId() + "|" + e.weight() + "|" + e.mentionCount() + "|" + e.lastSeenTick());
      }

      Collections.sort(edgeLines);
      StringBuilder sb = new StringBuilder();

      for (String l : nodeLines) {
         sb.append(l).append('\n');
      }

      sb.append("--edges--\n");

      for (String l : edgeLines) {
         sb.append(l).append('\n');
      }

      try {
         MessageDigest md = MessageDigest.getInstance("SHA-256");
         byte[] hash = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
         StringBuilder hex = new StringBuilder(hash.length * 2);

         for (byte b : hash) {
            hex.append(String.format("%02x", b));
         }

         return "1:" + hex;
      } catch (Exception e) {
         return "1:unknown";
      }
   }

   private static String safe(String s) {
      return s == null ? "" : s;
   }

   private static String safeReason(Exception e) {
      return e.getClass().getSimpleName();
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

   public static final class Snapshot {
      private final MemoryGraph graph;
      private final String versionToken;
      private final String relationshipSummary;

      Snapshot(MemoryGraph graph, String versionToken, String relationshipSummary) {
         this.graph = graph;
         this.versionToken = versionToken;
         this.relationshipSummary = relationshipSummary;
      }

      public MemoryGraph graph() {
         return this.graph;
      }

      public String versionToken() {
         return this.versionToken;
      }

      public String relationshipSummary() {
         return this.relationshipSummary;
      }
   }
}
