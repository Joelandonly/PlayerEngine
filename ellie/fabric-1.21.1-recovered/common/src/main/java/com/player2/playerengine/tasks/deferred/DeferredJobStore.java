package com.player2.playerengine.tasks.deferred;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.player2api.Player2NpcPersistencePaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.server.MinecraftServer;

public final class DeferredJobStore {
   private static volatile DeferredJobStore INSTANCE;
   private static final int SUPPORTED_SCHEMA_VERSION = 1;
   private static final String JOBS_FILE_NAME = "jobs.json";
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private final Path worldRoot;
   private final Map<String, DeferredJobRecord> records = new LinkedHashMap<>();
   private volatile List<DeferredJobRecord> publishedSnapshot = List.of();
   private volatile boolean startedFromQuarantine = false;
   private JsonObject rootExtras;

   public static DeferredJobStore get() {
      return INSTANCE;
   }

   private DeferredJobStore(Path worldRoot) {
      this.worldRoot = worldRoot;
   }

   public static DeferredJobStore loadForServer(MinecraftServer server) {
      Path worldRoot = server.getWorldPath(LevelResource.ROOT);
      DeferredJobStore store = new DeferredJobStore(worldRoot);
      store.load();
      INSTANCE = store;
      return store;
   }

   public void clear() {
      this.records.clear();
      this.publishedSnapshot = List.of();
      INSTANCE = null;
      PlayerEngine.LOGGER.info("DeferredJobStore: store cleared.");
   }

   public void upsert(DeferredJobRecord record) {
      this.records.put(record.id, record);
      this.publishSnapshot();
      this.persist();
      PlayerEngine.LOGGER.info("DeferredJobStore: upserted job id={}", record.id);
   }

   public boolean delete(String id) {
      DeferredJobRecord removed = this.records.remove(id);
      if (removed != null) {
         this.publishSnapshot();
         this.persist();
         PlayerEngine.LOGGER.info("DeferredJobStore: deleted job id={}", id);
         return true;
      } else {
         return false;
      }
   }

   public DeferredJobRecord get(String id) {
      return this.records.get(id);
   }

   public List<DeferredJobRecord> all() {
      return List.copyOf(this.records.values());
   }

   public List<DeferredJobRecord> publishedRecords() {
      return this.publishedSnapshot;
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

   private void load() {
      Path file = Player2NpcPersistencePaths.deferredJobsFile(this.worldRoot);
      if (!Files.exists(file)) {
         PlayerEngine.LOGGER.info("DeferredJobStore: no jobs.json found — starting empty.");
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

            if (!root.has("jobs") || !root.get("jobs").isJsonArray()) {
               this.quarantine(file, "missing or invalid jobs array");
               return;
            }

            JsonArray arr = root.get("jobs").getAsJsonArray();
            JsonObject extras = root.deepCopy();
            extras.remove("schemaVersion");
            extras.remove("jobs");
            this.rootExtras = extras.size() > 0 ? extras : null;
            int loaded = 0;
            int skipped = 0;

            for (JsonElement elem : arr) {
               DeferredJobRecord r = DeferredJobRecord.fromJson(elem);
               if (r != null && r.id != null) {
                  this.records.put(r.id, r);
                  loaded++;
               } else {
                  skipped++;
               }
            }

            PlayerEngine.LOGGER.info("DeferredJobStore: loaded {} job(s), {} skipped.", loaded, skipped);
            this.publishSnapshot();
         } catch (Exception e) {
            this.quarantine(file, e.getMessage());
         }
      }
   }

   private void quarantine(Path file, String reason) {
      String quarantineName = "jobs.json.corrupt-" + System.currentTimeMillis();

      try {
         Path quarantineTarget = file.getParent().resolve(quarantineName);
         Files.move(file, quarantineTarget, StandardCopyOption.REPLACE_EXISTING);
         PlayerEngine.LOGGER.warn("DeferredJobStore: quarantined corrupt jobs.json as {} (reason: {}); starting empty.", quarantineName, reason);
      } catch (IOException ex) {
         PlayerEngine.LOGGER.warn("DeferredJobStore: could not quarantine jobs.json (reason: {}; rename error: {}); starting empty.", reason, ex.getMessage());
      }

      this.records.clear();
      this.publishedSnapshot = List.of();
      this.rootExtras = null;
      this.startedFromQuarantine = true;
   }

   private void persist() {
      Path file = Player2NpcPersistencePaths.deferredJobsFile(this.worldRoot);

      try {
         Files.createDirectories(file.getParent());
         JsonObject root = this.rootExtras != null ? this.rootExtras.deepCopy() : new JsonObject();
         root.addProperty("schemaVersion", 1);
         JsonArray arr = new JsonArray();

         for (DeferredJobRecord r : this.records.values()) {
            arr.add(r.toJson());
         }

         root.add("jobs", arr);
         String json = GSON.toJson(root);
         Path tmp = file.resolveSibling("jobs.json.tmp");
         Files.writeString(tmp, json, StandardCharsets.UTF_8);
         atomicReplace(tmp, file);
      } catch (Exception e) {
         PlayerEngine.LOGGER.warn("DeferredJobStore: failed to persist jobs.json: {}", e.getMessage());
      }
   }

   private static void atomicReplace(Path tmp, Path target) throws IOException {
      try {
         Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
         Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
         PlayerEngine.LOGGER.debug("DeferredJobStore: atomic move unavailable for {}", target.getFileName());
      }
   }

   private void publishSnapshot() {
      this.publishedSnapshot = List.copyOf(this.records.values());
   }
}
