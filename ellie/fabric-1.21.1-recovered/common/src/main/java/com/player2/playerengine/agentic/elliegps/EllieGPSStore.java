package com.player2.playerengine.agentic.elliegps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.player2api.Player2NpcPersistencePaths;
import com.player2.playerengine.retrieval.RetrievalHit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.server.MinecraftServer;

public final class EllieGPSStore {
   private static volatile EllieGPSStore INSTANCE;
   private static final int SUPPORTED_SCHEMA_VERSION = 1;
   private static final String WAYPOINTS_FILE_NAME = "waypoints.json";
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final WaypointPersistenceBackend PRODUCTION_PERSISTENCE_BACKEND = EllieGPSStore::persistAtomically;
   private final Path worldRoot;
   private final WaypointPersistenceBackend persistenceBackend;
   private final WaypointIndexBackend indexBackend;
   private Map<String, WaypointRecord> records = new LinkedHashMap<>();
   private volatile List<WaypointRecord> publishedSnapshot = List.of();
   private volatile Set<EllieGPSStore.PositionTypeKey> positionTypeIndex = Set.of();
   private volatile long mutationGeneration;
   private volatile boolean startedFromQuarantine;
   private JsonObject rootExtras;

   private EllieGPSStore(Path worldRoot) {
      this(worldRoot, PRODUCTION_PERSISTENCE_BACKEND, EllieGPSWaypointIndex.productionBackend());
   }

   EllieGPSStore(Path worldRoot, WaypointPersistenceBackend persistenceBackend, WaypointIndexBackend indexBackend) {
      this.worldRoot = Objects.requireNonNull(worldRoot, "worldRoot");
      this.persistenceBackend = Objects.requireNonNull(persistenceBackend, "persistenceBackend");
      this.indexBackend = Objects.requireNonNull(indexBackend, "indexBackend");
   }

   public static EllieGPSStore get() {
      return INSTANCE;
   }

   public static EllieGPSStore loadForServer(MinecraftServer server) {
      Path worldRoot = server.getWorldPath(LevelResource.ROOT);
      EllieGPSStore store = new EllieGPSStore(worldRoot);
      store.load();
      INSTANCE = store;
      return store;
   }

   static AutoCloseable overrideForTest(EllieGPSStore replacement) {
      Objects.requireNonNull(replacement, "replacement");
      EllieGPSStore previous = INSTANCE;
      INSTANCE = replacement;
      return () -> {
         if (INSTANCE != replacement) {
            throw new IllegalStateException("EllieGPSStore test overrides must close in LIFO order");
         }

         INSTANCE = previous;
      };
   }

   public void clear() {
      this.records = new LinkedHashMap<>();
      this.publishedSnapshot = List.of();
      this.positionTypeIndex = Set.of();
      this.mutationGeneration++;
      if (INSTANCE == this) {
         INSTANCE = null;
      }

      PlayerEngine.LOGGER.info("EllieGPS: store cleared.");
   }

   public WaypointMutationResult upsert(WaypointRecord candidate) {
      WaypointRecord safeCandidate = validatedCopy(candidate);
      WaypointRecord previous = this.records.get(safeCandidate.id);
      if (previous != null && !Objects.equals(previous.type, safeCandidate.type)) {
         return result(WaypointMutationStatus.REJECTED_TYPE_CONFLICT, previous, previous);
      }

      if (previous != null && previous.semanticallyEquals(safeCandidate)) {
         WaypointIndexUpdateStatus index = this.ensureIndexSynchronized();
         return result(
            index == WaypointIndexUpdateStatus.INDEX_COMMITTED ? WaypointMutationStatus.NO_CHANGE : WaypointMutationStatus.NO_CHANGE_INDEX_DEGRADED,
            previous,
            previous
         );
      }

      Map<String, WaypointRecord> next = new LinkedHashMap<>(this.records);
      next.put(safeCandidate.id, safeCandidate);
      if (!this.commitAndPublish(next)) {
         return result(WaypointMutationStatus.FAILED_JSON_COMMIT, previous, previous);
      }

      WaypointIndexUpdateStatus index = this.synchronizeIndex();
      WaypointRecord current = this.records.get(safeCandidate.id);
      WaypointMutationStatus status = index == WaypointIndexUpdateStatus.INDEX_COMMITTED
         ? WaypointMutationStatus.COMMITTED
         : WaypointMutationStatus.COMMITTED_INDEX_DEGRADED;
      PlayerEngine.LOGGER.info("EllieGPS: upserted waypoint id={} status={}", safeCandidate.id, status);
      return result(status, previous, current);
   }

   public WaypointMutationResult replace(String oldId, WaypointRecord candidate) {
      if (oldId != null && !oldId.isBlank()) {
         WaypointRecord safeCandidate = validatedCopy(candidate);
         WaypointRecord previous = this.records.get(oldId);
         WaypointRecord target = this.records.get(safeCandidate.id);
         if (previous == null) {
            WaypointIndexUpdateStatus index = this.ensureIndexSynchronized();
            return result(
               index == WaypointIndexUpdateStatus.INDEX_COMMITTED ? WaypointMutationStatus.NOT_FOUND : WaypointMutationStatus.NOT_FOUND_INDEX_DEGRADED,
               null,
               target
            );
         }

         if (oldId.equals(safeCandidate.id)) {
            return this.upsert(safeCandidate);
         }

         if (!Objects.equals(previous.type, safeCandidate.type)) {
            return result(WaypointMutationStatus.REJECTED_TYPE_CONFLICT, previous, target);
         }

         if (target != null) {
            WaypointMutationStatus conflict = Objects.equals(target.type, safeCandidate.type)
               ? WaypointMutationStatus.REJECTED_TARGET_CONFLICT
               : WaypointMutationStatus.REJECTED_TYPE_CONFLICT;
            return result(conflict, previous, target);
         }

         Map<String, WaypointRecord> next = new LinkedHashMap<>(this.records);
         next.remove(oldId);
         next.put(safeCandidate.id, safeCandidate);
         if (!this.commitAndPublish(next)) {
            return result(WaypointMutationStatus.FAILED_JSON_COMMIT, previous, null);
         }

         WaypointIndexUpdateStatus index = this.synchronizeIndex();
         WaypointMutationStatus status = index == WaypointIndexUpdateStatus.INDEX_COMMITTED
            ? WaypointMutationStatus.COMMITTED
            : WaypointMutationStatus.COMMITTED_INDEX_DEGRADED;
         PlayerEngine.LOGGER.info("EllieGPS: replaced waypoint oldId={} newId={} status={}", oldId, safeCandidate.id, status);
         return result(status, previous, this.records.get(safeCandidate.id));
      } else {
         throw new IllegalArgumentException("oldId is required");
      }
   }

   public WaypointMutationResult delete(String id) {
      WaypointRecord previous = id == null ? null : this.records.get(id);
      if (previous == null) {
         WaypointIndexUpdateStatus index = this.ensureIndexSynchronized();
         return result(
            index == WaypointIndexUpdateStatus.INDEX_COMMITTED ? WaypointMutationStatus.NOT_FOUND : WaypointMutationStatus.NOT_FOUND_INDEX_DEGRADED, null, null
         );
      }

      Map<String, WaypointRecord> next = new LinkedHashMap<>(this.records);
      next.remove(id);
      if (!this.commitAndPublish(next)) {
         return result(WaypointMutationStatus.FAILED_JSON_COMMIT, previous, previous);
      }

      WaypointIndexUpdateStatus index = this.synchronizeIndex();
      WaypointMutationStatus status = index == WaypointIndexUpdateStatus.INDEX_COMMITTED
         ? WaypointMutationStatus.COMMITTED
         : WaypointMutationStatus.COMMITTED_INDEX_DEGRADED;
      PlayerEngine.LOGGER.info("EllieGPS: deleted waypoint id={} status={}", id, status);
      return result(status, previous, null);
   }

   public WaypointMutationResult markStale(String id) {
      WaypointRecord previous = id == null ? null : this.records.get(id);
      if (previous == null) {
         WaypointIndexUpdateStatus index = this.ensureIndexSynchronized();
         return result(
            index == WaypointIndexUpdateStatus.INDEX_COMMITTED ? WaypointMutationStatus.NOT_FOUND : WaypointMutationStatus.NOT_FOUND_INDEX_DEGRADED, null, null
         );
      }

      WaypointRecord stale = previous.copy();
      stale.stale = true;
      if (previous.semanticallyEquals(stale)) {
         WaypointIndexUpdateStatus index = this.ensureIndexSynchronized();
         return result(
            index == WaypointIndexUpdateStatus.INDEX_COMMITTED ? WaypointMutationStatus.NO_CHANGE : WaypointMutationStatus.NO_CHANGE_INDEX_DEGRADED,
            previous,
            previous
         );
      }

      Map<String, WaypointRecord> next = new LinkedHashMap<>(this.records);
      next.put(id, stale);
      if (!this.commitAndPublish(next)) {
         return result(WaypointMutationStatus.FAILED_JSON_COMMIT, previous, previous);
      }

      WaypointIndexUpdateStatus index = this.synchronizeIndex();
      WaypointMutationStatus status = index == WaypointIndexUpdateStatus.INDEX_COMMITTED
         ? WaypointMutationStatus.COMMITTED
         : WaypointMutationStatus.COMMITTED_INDEX_DEGRADED;
      PlayerEngine.LOGGER.info("EllieGPS: marked waypoint id={} stale status={}", id, status);
      return result(status, previous, this.records.get(id));
   }

   public WaypointRecord byPosition(String dimensionId, BlockPos pos) {
      for (WaypointRecord record : this.records.values()) {
         if (Objects.equals(dimensionId, record.dimension)) {
            BlockPos canonical = record.canonicalBlockPos();
            if (canonical != null && canonical.equals(pos)) {
               return record.copy();
            }

            BlockPos secondary = record.secondaryBlockPos();
            if (secondary != null && secondary.equals(pos)) {
               return record.copy();
            }
         }
      }

      return null;
   }

   public WaypointRecord byId(String id) {
      WaypointRecord record = id == null ? null : this.records.get(id);
      return record == null ? null : record.copy();
   }

   public boolean hasTypeAtPosition(String type, String dimensionId, BlockPos pos) {
      return type != null && dimensionId != null && pos != null
         ? this.positionTypeIndex.contains(new EllieGPSStore.PositionTypeKey(type, dimensionId, pos.getX(), pos.getY(), pos.getZ()))
         : false;
   }

   public long mutationGeneration() {
      return this.mutationGeneration;
   }

   public int recordCount() {
      return this.records.size();
   }

   public EllieGPSStore.BoundedSnapshot boundedSnapshot(int maxRecords) {
      return boundedSnapshot(this.records, maxRecords);
   }

   static EllieGPSStore.BoundedSnapshot boundedSnapshot(Map<String, WaypointRecord> source, int maxRecords) {
      if (maxRecords < 0) {
         throw new IllegalArgumentException("maxRecords cannot be negative");
      }

      if (source == null) {
         return new EllieGPSStore.BoundedSnapshot(false, false, List.of());
      }

      if (source.size() > maxRecords) {
         return new EllieGPSStore.BoundedSnapshot(true, false, List.of());
      }

      try {
         return new EllieGPSStore.BoundedSnapshot(true, true, copyRecords(source.values()));
      } catch (RuntimeException copyFailure) {
         return new EllieGPSStore.BoundedSnapshot(false, false, List.of());
      }
   }

   public List<WaypointRecord> all() {
      return copyRecords(this.records.values());
   }

   public List<WaypointRecord> publishedRecords() {
      return copyRecords(this.publishedSnapshot);
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

   public Path worldRoot() {
      return this.worldRoot;
   }

   public String versionToken() {
      ArrayList<WaypointRecord> indexable = new ArrayList<>();

      for (WaypointRecord record : this.records.values()) {
         if (isIndexable(record)) {
            indexable.add(record);
         }
      }

      indexable.sort((left, right) -> left.id.compareTo(right.id));
      StringBuilder material = new StringBuilder();

      for (WaypointRecord record : indexable) {
         material.append(record.toJson()).append('\n');
      }

      try {
         MessageDigest digest = MessageDigest.getInstance("SHA-256");
         byte[] hash = digest.digest(material.toString().getBytes(StandardCharsets.UTF_8));
         StringBuilder hex = new StringBuilder(hash.length * 2);

         for (byte value : hash) {
            hex.append(String.format("%02x", value));
         }

         return "2:" + hex;
      } catch (Exception e) {
         PlayerEngine.LOGGER.warn("EllieGPS: SHA-256 unavailable for version token: {}", e.getMessage());
         return "2:unknown";
      }
   }

   boolean isIndexHealthy() {
      try {
         return this.indexBackend.isHealthyFor(this.versionToken());
      } catch (RuntimeException e) {
         PlayerEngine.LOGGER.warn("EllieGPS index health check failed: {}", e.getMessage());
         return false;
      }
   }

   WaypointIndexUpdateStatus synchronizeIndex() {
      try {
         WaypointIndexUpdateStatus status = this.indexBackend.synchronize(this);
         return status == null ? WaypointIndexUpdateStatus.INDEX_FAILED : status;
      } catch (RuntimeException e) {
         PlayerEngine.LOGGER.warn("EllieGPS index synchronization failed: {}", e.getMessage());
         return WaypointIndexUpdateStatus.INDEX_FAILED;
      }
   }

   List<RetrievalHit> queryIndex(String query, int limit) throws Exception {
      return this.indexBackend.query(query, limit);
   }

   private WaypointIndexUpdateStatus ensureIndexSynchronized() {
      return this.isIndexHealthy() ? WaypointIndexUpdateStatus.INDEX_COMMITTED : this.synchronizeIndex();
   }

   private boolean commitAndPublish(Map<String, WaypointRecord> next) {
      List<WaypointRecord> nextSnapshot;
      Set<EllieGPSStore.PositionTypeKey> nextPositionTypeIndex;
      String json;
      try {
         nextSnapshot = copyRecords(next.values());
         nextPositionTypeIndex = buildPositionTypeIndex(next.values());
         json = this.serialize(next);
      } catch (RuntimeException e) {
         PlayerEngine.LOGGER.warn("EllieGPS: candidate waypoint set was invalid: {}", e.getMessage());
         return false;
      }

      Path file = Player2NpcPersistencePaths.ellieGpsWaypointsFile(this.worldRoot);

      try {
         this.persistenceBackend.persist(file, json);
      } catch (Exception e) {
         PlayerEngine.LOGGER.warn("EllieGPS: failed to commit waypoints.json: {}", e.getMessage());
         return false;
      }

      this.records = next;
      this.publishedSnapshot = nextSnapshot;
      this.positionTypeIndex = nextPositionTypeIndex;
      this.mutationGeneration++;
      return true;
   }

   private String serialize(Map<String, WaypointRecord> source) {
      JsonObject root = this.rootExtras == null ? new JsonObject() : this.rootExtras.deepCopy();
      root.addProperty("schemaVersion", 1);
      JsonArray array = new JsonArray();

      for (WaypointRecord record : source.values()) {
         array.add(record.toJson());
      }

      root.add("waypoints", array);
      return GSON.toJson(root);
   }

   private static WaypointRecord validatedCopy(WaypointRecord candidate) {
      if (candidate == null) {
         throw new IllegalArgumentException("waypoint candidate is required");
      }

      WaypointRecord copy = candidate.copy();
      if (copy.schemaVersion == 1
         && copy.id != null
         && !copy.id.isBlank()
         && copy.type != null
         && !copy.type.isBlank()
         && copy.dimension != null
         && !copy.dimension.isBlank()
         && copy.pos != null
         && copy.pos.length == 3) {
         BlockPos canonical = copy.canonicalBlockPos();
         if (canonical != null && copy.id.equals(WaypointRecord.idFor(copy.dimension, canonical))) {
            if ("farm".equals(copy.type)) {
               FarmWaypointData farm = copy.farmData();
               if (farm == null || !farm.isSupportedVersion()) {
                  throw new IllegalArgumentException("cannot mutate an unsupported farm payload");
               }
            }

            return copy;
         } else {
            throw new IllegalArgumentException("waypoint candidate id does not match dimension and position");
         }
      } else {
         throw new IllegalArgumentException("waypoint candidate has an invalid schema-v1 envelope");
      }
   }

   private static boolean isIndexable(WaypointRecord record) {
      if ("inventory".equals(record.type)) {
         return record.inventoryData() != null;
      }

      if (!"farm".equals(record.type)) {
         return false;
      }

      FarmWaypointData farm = record.farmData();
      return farm != null && farm.isSupportedVersion();
   }

   private static WaypointMutationResult result(WaypointMutationStatus status, WaypointRecord previous, WaypointRecord current) {
      return new WaypointMutationResult(status, previous == null ? null : previous.copy(), current == null ? null : current.copy());
   }

   private static List<WaypointRecord> copyRecords(Iterable<WaypointRecord> source) {
      ArrayList<WaypointRecord> copies = new ArrayList<>();

      for (WaypointRecord record : source) {
         copies.add(record.copy());
      }

      return List.copyOf(copies);
   }

   private void load() {
      Path file = Player2NpcPersistencePaths.ellieGpsWaypointsFile(this.worldRoot);
      if (!Files.exists(file)) {
         PlayerEngine.LOGGER.info("EllieGPS: no waypoints.json found; starting empty.");
         this.publishCurrentSnapshot();
      } else {
         try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = (JsonObject)GSON.fromJson(json, JsonObject.class);
            if (root == null) {
               this.quarantine(file, "root is null");
               return;
            }

            int version = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 0;
            if (version > 1) {
               this.quarantine(file, "schemaVersion " + version + " > supported 1");
               return;
            }

            if (!root.has("waypoints") || !root.get("waypoints").isJsonArray()) {
               this.quarantine(file, "missing or invalid waypoints array");
               return;
            }

            JsonObject extras = root.deepCopy();
            extras.remove("schemaVersion");
            extras.remove("waypoints");
            this.rootExtras = extras.size() == 0 ? null : extras;
            LinkedHashMap<String, WaypointRecord> loadedRecords = new LinkedHashMap<>();
            int loaded = 0;
            int skipped = 0;

            for (JsonElement element : root.getAsJsonArray("waypoints")) {
               WaypointRecord record = WaypointRecord.fromJson(element);
               if (record != null && record.id != null) {
                  loadedRecords.put(record.id, record);
                  loaded++;
               } else {
                  skipped++;
               }
            }

            this.records = loadedRecords;
            this.publishCurrentSnapshot();
            PlayerEngine.LOGGER.info("EllieGPS: loaded {} waypoint(s), {} skipped.", loaded, skipped);
         } catch (Exception e) {
            this.quarantine(file, e.getMessage());
         }
      }
   }

   private void quarantine(Path file, String reason) {
      String quarantineName = "waypoints.json.corrupt-" + System.currentTimeMillis();

      try {
         Path target = file.getParent().resolve(quarantineName);
         Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
         PlayerEngine.LOGGER.warn("EllieGPS: quarantined corrupt waypoints.json as {} (reason: {}); starting empty.", quarantineName, reason);
      } catch (IOException e) {
         PlayerEngine.LOGGER.warn("EllieGPS: could not quarantine waypoints.json (reason: {}; rename error: {}); starting empty.", reason, e.getMessage());
      }

      this.records = new LinkedHashMap<>();
      this.publishedSnapshot = List.of();
      this.positionTypeIndex = Set.of();
      this.mutationGeneration++;
      this.rootExtras = null;
      this.startedFromQuarantine = true;
   }

   private void publishCurrentSnapshot() {
      this.publishedSnapshot = copyRecords(this.records.values());
      this.positionTypeIndex = buildPositionTypeIndex(this.records.values());
      this.mutationGeneration++;
   }

   private static Set<EllieGPSStore.PositionTypeKey> buildPositionTypeIndex(Iterable<WaypointRecord> source) {
      HashSet<EllieGPSStore.PositionTypeKey> result = new HashSet<>();

      for (WaypointRecord record : source) {
         BlockPos pos = record == null ? null : record.canonicalBlockPos();
         if (pos != null && record.type != null && record.dimension != null) {
            result.add(new EllieGPSStore.PositionTypeKey(record.type, record.dimension, pos.getX(), pos.getY(), pos.getZ()));
         }
      }

      return Set.copyOf(result);
   }

   private static void persistAtomically(Path file, String serializedStore) throws IOException {
      Files.createDirectories(file.getParent());
      Path temporary = file.resolveSibling("waypoints.json.tmp");
      Files.writeString(temporary, serializedStore, StandardCharsets.UTF_8);
      atomicReplace(temporary, file);
   }

   private static void atomicReplace(Path temporary, Path target) throws IOException {
      try {
         Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
         Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
   }

   public record BoundedSnapshot(boolean available, boolean complete, List<WaypointRecord> records) {
      public BoundedSnapshot {
         records = records == null ? List.of() : List.copyOf(records);
         if ((!available || !complete) && !records.isEmpty()) {
            throw new IllegalArgumentException("incomplete bounded snapshots cannot expose records");
         }
      }
   }

   private record PositionTypeKey(String type, String dimension, int x, int y, int z) {
   }
}
