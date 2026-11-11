package com.player2.playerengine.modintelligence;

import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.modintelligence.capability.CapabilityMap;
import com.player2.playerengine.modintelligence.enrich.CapabilityEnrichmentQueue;
import com.player2.playerengine.modintelligence.enrich.CapabilityEnrichmentService;
import com.player2.playerengine.modintelligence.enrich.ModIntelligenceEnrichmentClient;
import com.player2.playerengine.modintelligence.enrich.ModelBlacklist;
import com.player2.playerengine.modintelligence.ingest.CapabilityStore;
import com.player2.playerengine.modintelligence.ingest.ModMetadataIngestionPipeline;
import com.player2.playerengine.modintelligence.query.CapabilityIndex;
import com.player2.playerengine.modintelligence.query.CapabilityQueryService;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.PlayerEvent.PlayerJoin;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.Logger;

public final class ModIntelligenceService {
   private static final Logger LOGGER = PlayerEngine.LOGGER;
   private static final AtomicReference<CapabilityStore> storeRef = new AtomicReference<>(new CapabilityStore());
   private static final AtomicReference<CapabilityQueryService> queryRef = new AtomicReference<>(new CapabilityQueryService(storeRef.get()));
   private static final AtomicReference<ModIntelligenceService.OperationClaim> activeOperation = new AtomicReference<>();
   private static final AtomicLong sessionGeneration = new AtomicLong();
   private static final Object lifecycleLock = new Object();
   private static final AtomicBoolean inspecting = new AtomicBoolean(false);
   private static final AtomicBoolean enriching = new AtomicBoolean(false);
   private static final AtomicReference<ModIntelligenceStatus> cachedStatus = new AtomicReference<>(
      new ModIntelligenceStatus(true, false, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "idle", "", null)
   );
   private static final AtomicReference<ModIntelligenceService.PendingExplicitRequest> pendingExplicitRequest = new AtomicReference<>();
   private static volatile String lastError = null;
   private static volatile ModIntelligenceService.OperationStatus lastOperationStatus = ModIntelligenceService.OperationStatus.IDLE;
   private static volatile String packFingerprint = "";
   private static volatile boolean playerJoinHookRegistered = false;
   private static volatile int lastBatchValidated = 0;
   private static volatile int lastBatchFailures = 0;
   private static volatile int lastBatchRemaining = 0;

   private ModIntelligenceService() {
   }

   public static void registerEventHandlers() {
      registerPlayerJoinHook();
   }

   public static void initialize(MinecraftServer server) {
      synchronized (lifecycleLock) {
         sessionGeneration.incrementAndGet();
         resetRuntimeState();
      }

      Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
      if (!cfg.isModIntelligenceEnabled()) {
         LOGGER.info("ModIntelligence: disabled by config");
         refreshCachedStatus();
      } else {
         try {
            CapabilityStore store = new CapabilityStore();
            store.loadFromDisk();
            String expectedFp = store.getManifest() == null ? "" : store.getManifest().getPackFingerprint();
            packFingerprint = expectedFp;
            if (!CapabilityIndex.loadIfMatches(expectedFp)) {
               CapabilityIndex.rebuildFromMaps(store.getActiveMaps().values(), expectedFp);
            }

            storeRef.set(store);
            queryRef.set(new CapabilityQueryService(store));
            refreshCachedStatus();
            if (cfg.isModIntelligenceInspectOnLaunch()) {
               requestIngestion(server, false);
            } else if (cfg.isModIntelligenceEnrichmentEnabled()) {
               scheduleEnrichmentBatch(server);
            }
         } catch (Exception e) {
            lastError = e.getMessage();
            lastOperationStatus = ModIntelligenceService.OperationStatus.INGESTION_FAILED;
            LOGGER.error("ModIntelligence: initialize failed (non-fatal): {}", e.getMessage());
            refreshCachedStatus();
         }
      }
   }

   private static void resetRuntimeState() {
      pendingExplicitRequest.set(null);
      activeOperation.set(null);
      inspecting.set(false);
      enriching.set(false);
      lastOperationStatus = ModIntelligenceService.OperationStatus.IDLE;
      lastError = null;
      packFingerprint = "";
      lastBatchValidated = 0;
      lastBatchFailures = 0;
      lastBatchRemaining = 0;
      ModelBlacklist.clearBatchSnapshot();
      CapabilityStore emptyStore = new CapabilityStore();
      storeRef.set(emptyStore);
      queryRef.set(new CapabilityQueryService(emptyStore));
   }

   public static long currentGeneration() {
      return sessionGeneration.get();
   }

   public static boolean isCurrentGeneration(long generation) {
      return generation == sessionGeneration.get();
   }

   public static boolean isCurrentOperation(ModIntelligenceService.OperationClaim claim) {
      return claim != null && isCurrentGeneration(claim.generation) && activeOperation.get() == claim;
   }

   private static void registerPlayerJoinHook() {
      if (!playerJoinHookRegistered) {
         playerJoinHookRegistered = true;
         PlayerEvent.PLAYER_JOIN.register((PlayerJoin)player -> {
            if (player instanceof ServerPlayer sp) {
               Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
               if (cfg.isModIntelligenceEnabled() && cfg.isModIntelligenceEnrichmentEnabled()) {
                  MinecraftServer server = sp.getServer();
                  if (server != null) {
                     scheduleEnrichmentBatch(server);
                  }
               }
            }
         });
      }
   }

   public static void scheduleEnrichmentBatch(MinecraftServer server) {
      schedule(server, null, false);
   }

   public static ModIntelligenceService.ScheduleResult scheduleEnrichmentBatch(MinecraftServer server, Integer limitOverride) {
      return schedule(server, limitOverride, true);
   }

   private static ModIntelligenceService.ScheduleResult schedule(MinecraftServer server, Integer limitOverride, boolean explicit) {
      long generation = currentGeneration();
      Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
      if (!cfg.isModIntelligenceEnabled() || !cfg.isModIntelligenceEnrichmentEnabled()) {
         return ModIntelligenceService.ScheduleResult.DISABLED;
      }

      if (activeOperation.get() != null) {
         if (explicit) {
            LOGGER.info("ModIntelligence enrichment: batch in flight - stored explicit follow-up request (limit={})", describeLimit(limitOverride));
            parkExplicit(server, limitOverride, generation);
         }

         return ModIntelligenceService.ScheduleResult.ALREADY_RUNNING;
      } else {
         if (CapabilityEnrichmentQueue.countQueuedLines() == 0) {
            return ModIntelligenceService.ScheduleResult.NOTHING_QUEUED;
         }

         if (!ModIntelligenceEnrichmentClient.isBillingAvailable(server)) {
            LOGGER.debug("ModIntelligence enrichment: waiting for billing (player join or stored token)");
            return ModIntelligenceService.ScheduleResult.BILLING_UNAVAILABLE;
         }

         ThreadPoolExecutor executor = PlayerEngine.getExecutor();
         if (executor == null) {
            recordOperationStatus(generation, ModIntelligenceService.OperationStatus.ENRICHMENT_EXECUTOR_UNAVAILABLE);
            return ModIntelligenceService.ScheduleResult.EXECUTOR_UNAVAILABLE;
         }

         try {
            executor.execute(() -> CapabilityEnrichmentService.runBatch(server, limitOverride, explicit, generation));
            return ModIntelligenceService.ScheduleResult.STARTED;
         } catch (RejectedExecutionException e) {
            recordOperationStatus(generation, ModIntelligenceService.OperationStatus.ENRICHMENT_EXECUTOR_UNAVAILABLE);
            LOGGER.warn("ModIntelligence enrichment: background executor rejected batch: {}", e.getMessage());
            return ModIntelligenceService.ScheduleResult.EXECUTOR_UNAVAILABLE;
         }
      }
   }

   public static ModIntelligenceService.IngestionRequestResult requestIngestion(MinecraftServer server, boolean forceRebuild) {
      if (server != null && Player2ServerConfigHolder.get().isModIntelligenceEnabled()) {
         long generation = currentGeneration();
         ModIntelligenceService.OperationClaim claim = new ModIntelligenceService.OperationClaim(generation, ModIntelligenceService.MutationOperation.INGESTING);
         if (!activeOperation.compareAndSet(null, claim)) {
            return ModIntelligenceService.IngestionRequestResult.ALREADY_RUNNING;
         }

         synchronized (lifecycleLock) {
            if (!isCurrentOperation(claim)) {
               activeOperation.compareAndSet(claim, null);
               return ModIntelligenceService.IngestionRequestResult.ALREADY_RUNNING;
            }

            lastOperationStatus = ModIntelligenceService.OperationStatus.INGESTION_RUNNING;
            lastError = null;
            inspecting.set(true);
            publishRuntimeStatus();
         }

         ThreadPoolExecutor executor = PlayerEngine.getExecutor();
         if (executor == null) {
            synchronized (lifecycleLock) {
               if (activeOperation.compareAndSet(claim, null)) {
                  inspecting.set(false);
                  lastOperationStatus = ModIntelligenceService.OperationStatus.INGESTION_EXECUTOR_UNAVAILABLE;
                  publishRuntimeStatus();
               }
            }

            return ModIntelligenceService.IngestionRequestResult.EXECUTOR_UNAVAILABLE;
         } else {
            try {
               executor.execute(() -> runClaimedIngestion(server, forceRebuild, claim));
               return ModIntelligenceService.IngestionRequestResult.STARTED;
            } catch (RejectedExecutionException e) {
               synchronized (lifecycleLock) {
                  if (activeOperation.compareAndSet(claim, null)) {
                     inspecting.set(false);
                     lastOperationStatus = ModIntelligenceService.OperationStatus.INGESTION_EXECUTOR_UNAVAILABLE;
                     publishRuntimeStatus();
                  }
               }

               LOGGER.warn("ModIntelligence: ingestion executor unavailable: {}", e.getMessage());
               return ModIntelligenceService.IngestionRequestResult.EXECUTOR_UNAVAILABLE;
            }
         }
      } else {
         return ModIntelligenceService.IngestionRequestResult.DISABLED;
      }
   }

   public static void runIngestion(MinecraftServer server, boolean forceRebuild) {
      ModIntelligenceService.OperationClaim claim = new ModIntelligenceService.OperationClaim(
         currentGeneration(), ModIntelligenceService.MutationOperation.INGESTING
      );
      if (activeOperation.compareAndSet(null, claim)) {
         synchronized (lifecycleLock) {
            if (!isCurrentOperation(claim)) {
               activeOperation.compareAndSet(claim, null);
               return;
            }

            lastOperationStatus = ModIntelligenceService.OperationStatus.INGESTION_RUNNING;
            lastError = null;
            inspecting.set(true);
            publishRuntimeStatus();
         }

         runClaimedIngestion(server, forceRebuild, claim);
      }
   }

   private static void runClaimedIngestion(MinecraftServer server, boolean forceRebuild, ModIntelligenceService.OperationClaim claim) {
      if (!isCurrentOperation(claim)) {
         activeOperation.compareAndSet(claim, null);
      } else {
         boolean scheduleEnrichmentAfterIngestion = false;

         try {
            ModMetadataIngestionPipeline.IngestSummary summary = new ModMetadataIngestionPipeline(server).run(forceRebuild);
            if (!isCurrentOperation(claim)) {
               return;
            }

            CapabilityStore store = new CapabilityStore();
            store.loadFromDisk();
            synchronized (lifecycleLock) {
               if (isCurrentOperation(claim)) {
                  storeRef.set(store);
                  queryRef.set(new CapabilityQueryService(store));
                  packFingerprint = store.getManifest() == null ? "" : store.getManifest().getPackFingerprint();
                  scheduleEnrichmentAfterIngestion = summary.queuedEnrichment() > 0;
                  lastOperationStatus = ModIntelligenceService.OperationStatus.INGESTION_COMPLETE;
                  lastError = null;
                  return;
               }
            }
         } catch (Exception var30) {
            Exception e = var30;
            synchronized (lifecycleLock) {
               if (isCurrentOperation(claim)) {
                  lastError = e.getMessage();
                  lastOperationStatus = ModIntelligenceService.OperationStatus.INGESTION_FAILED;
                  LOGGER.error("ModIntelligence: ingestion failed: {}", e.getMessage());
               }

               return;
            }
         } finally {
            synchronized (lifecycleLock) {
               if (activeOperation.compareAndSet(claim, null)) {
                  inspecting.set(false);
                  refreshCachedStatus();
                  boolean explicitPending = pendingExplicitRequest.get() != null;
                  if (explicitPending) {
                     drainPendingExplicit(server);
                  } else if (scheduleEnrichmentAfterIngestion) {
                     scheduleEnrichmentBatch(server);
                  }
               }
            }
         }
      }
   }

   public static CapabilityQueryService queryService() {
      return queryRef.get();
   }

   public static CapabilityStore store() {
      return storeRef.get();
   }

   public static ModIntelligenceStatus status() {
      return statusFromStore(storeRef.get());
   }

   public static ModIntelligenceStatus cachedStatus() {
      return cachedStatus.get();
   }

   public static ModIntelligenceStatus statusFromStore(CapabilityStore store) {
      Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
      int ready = 0;
      int partial = 0;
      int unknown = 0;
      int failed = 0;
      int tomb = 0;
      int enriched = 0;
      int total;
      synchronized (store) {
         for (CapabilityMap map : store.getActiveMaps().values()) {
            if (map.getEnrichment() != null) {
               enriched++;
            }

            switch (map.getStatus()) {
               case READY:
                  ready++;
                  break;
               case PARTIAL:
                  partial++;
                  break;
               case UNKNOWN:
                  unknown++;
                  break;
               case FAILED:
                  failed++;
                  break;
               case TOMBSTONED:
                  tomb++;
            }
         }

         total = store.getActiveMaps().size();
      }

      int queued = CapabilityEnrichmentQueue.countQueuedLines();
      int enrichFailures = CapabilityEnrichmentQueue.countFailureLogLines();
      return new ModIntelligenceStatus(
         cfg.isModIntelligenceEnabled(),
         inspecting.get(),
         enriching.get(),
         total,
         ready,
         partial,
         unknown,
         failed,
         tomb,
         queued,
         enriched,
         enrichFailures,
         lastBatchValidated,
         lastBatchFailures,
         lastBatchRemaining,
         lastOperationStatus.code(),
         packFingerprint,
         lastError
      );
   }

   static void refreshCachedStatus() {
      try {
         cachedStatus.set(statusFromStore(storeRef.get()));
      } catch (RuntimeException e) {
         LOGGER.debug("ModIntelligence: cached status refresh skipped: {}", e.getMessage());
         publishRuntimeStatus();
      }
   }

   private static void publishRuntimeStatus() {
      ModIntelligenceStatus previous = cachedStatus.get();
      cachedStatus.set(
         new ModIntelligenceStatus(
            Player2ServerConfigHolder.get().isModIntelligenceEnabled(),
            inspecting.get(),
            enriching.get(),
            previous.getTotalEntries(),
            previous.getReadyEntries(),
            previous.getPartialEntries(),
            previous.getUnknownEntries(),
            previous.getFailedEntries(),
            previous.getTombstonedEntries(),
            previous.getQueuedEnrichments(),
            previous.getEnrichedEntries(),
            previous.getEnrichmentFailures(),
            lastBatchValidated,
            lastBatchFailures,
            lastBatchRemaining,
            lastOperationStatus.code(),
            packFingerprint,
            lastError
         )
      );
   }

   public static void recordLastEnrichmentBatch(int validated, int failures, int remaining) {
      lastBatchValidated = validated;
      lastBatchFailures = failures;
      lastBatchRemaining = remaining;
   }

   public static void recordLastEnrichmentBatch(ModIntelligenceService.OperationClaim claim, int validated, int failures, int remaining) {
      synchronized (lifecycleLock) {
         if (isCurrentOperation(claim)) {
            recordLastEnrichmentBatch(validated, failures, remaining);
         }
      }
   }

   public static void recordOperationStatus(ModIntelligenceService.OperationStatus status) {
      lastOperationStatus = status == null ? ModIntelligenceService.OperationStatus.IDLE : status;
      refreshCachedStatus();
   }

   public static void recordOperationStatus(long generation, ModIntelligenceService.OperationStatus status) {
      synchronized (lifecycleLock) {
         if (isCurrentGeneration(generation)) {
            recordOperationStatus(status);
         }
      }
   }

   public static void recordOperationStatus(ModIntelligenceService.OperationClaim claim, ModIntelligenceService.OperationStatus status) {
      synchronized (lifecycleLock) {
         if (isCurrentOperation(claim)) {
            recordOperationStatus(status);
         }
      }
   }

   public static ModIntelligenceService.OperationClaim tryBeginEnrichment(long generation) {
      if (!isCurrentGeneration(generation)) {
         return null;
      }

      ModIntelligenceService.OperationClaim claim = new ModIntelligenceService.OperationClaim(generation, ModIntelligenceService.MutationOperation.ENRICHING);
      if (!activeOperation.compareAndSet(null, claim)) {
         return null;
      }

      synchronized (lifecycleLock) {
         if (isCurrentOperation(claim) && enriching.compareAndSet(false, true)) {
            lastOperationStatus = ModIntelligenceService.OperationStatus.ENRICHMENT_RUNNING;
            lastError = null;
            publishRuntimeStatus();
            return claim;
         } else {
            activeOperation.compareAndSet(claim, null);
            return null;
         }
      }
   }

   public static void parkExplicit(MinecraftServer server, Integer limitOverride) {
      parkExplicit(server, limitOverride, currentGeneration());
   }

   public static void parkExplicit(MinecraftServer server, Integer limitOverride, long generation) {
      synchronized (lifecycleLock) {
         if (isCurrentGeneration(generation)) {
            pendingExplicitRequest.set(new ModIntelligenceService.PendingExplicitRequest(generation, server, limitOverride));
            if (activeOperation.get() == null) {
               drainPendingExplicit(server);
            }
         }
      }
   }

   public static void finishEnrichment(MinecraftServer server, ModIntelligenceService.OperationClaim claim) {
      synchronized (lifecycleLock) {
         if (activeOperation.compareAndSet(claim, null)) {
            enriching.set(false);
            refreshCachedStatus();
            drainPendingExplicit(server);
         }
      }
   }

   private static void drainPendingExplicit(MinecraftServer server) {
      ModIntelligenceService.PendingExplicitRequest pending = pendingExplicitRequest.get();
      if (pending != null) {
         if (!isCurrentGeneration(pending.generation()) || pending.server() != server) {
            pendingExplicitRequest.compareAndSet(pending, null);
         } else if (pendingExplicitRequest.compareAndSet(pending, null)) {
            Integer limit = pending.limitOverride();
            LOGGER.info("ModIntelligence enrichment: starting pending explicit follow-up batch (limit={})", describeLimit(limit));
            ThreadPoolExecutor executor = PlayerEngine.getExecutor();
            if (executor == null) {
               if (isCurrentGeneration(pending.generation())) {
                  pendingExplicitRequest.compareAndSet(null, pending);
                  recordOperationStatus(pending.generation(), ModIntelligenceService.OperationStatus.ENRICHMENT_EXECUTOR_UNAVAILABLE);
               }

               LOGGER.warn("ModIntelligence enrichment: pending explicit batch retained; background executor unavailable");
            } else {
               try {
                  executor.execute(() -> CapabilityEnrichmentService.runBatch(server, limit, true, pending.generation()));
               } catch (RejectedExecutionException e) {
                  if (isCurrentGeneration(pending.generation())) {
                     pendingExplicitRequest.compareAndSet(null, pending);
                     recordOperationStatus(pending.generation(), ModIntelligenceService.OperationStatus.ENRICHMENT_EXECUTOR_UNAVAILABLE);
                  }

                  LOGGER.warn("ModIntelligence enrichment: pending explicit batch retained after executor rejection: {}", e.getMessage());
               }
            }
         }
      }
   }

   private static String describeLimit(Integer limit) {
      if (limit == null) {
         return "config";
      } else {
         return limit <= 0 ? "unlimited" : String.valueOf(limit);
      }
   }

   public enum IngestionRequestResult {
      STARTED,
      ALREADY_RUNNING,
      DISABLED,
      EXECUTOR_UNAVAILABLE;
   }

   private enum MutationOperation {
      INGESTING,
      ENRICHING;
   }

   public static final class OperationClaim {
      private final long generation;
      private final ModIntelligenceService.MutationOperation operation;

      private OperationClaim(long generation, ModIntelligenceService.MutationOperation operation) {
         this.generation = generation;
         this.operation = operation;
      }
   }

   public enum OperationStatus {
      IDLE("idle"),
      INGESTION_RUNNING("ingestion_running"),
      INGESTION_COMPLETE("ingestion_complete"),
      INGESTION_FAILED("ingestion_failed"),
      INGESTION_EXECUTOR_UNAVAILABLE("ingestion_executor_unavailable"),
      ENRICHMENT_RUNNING("enrichment_running"),
      ENRICHMENT_COMPLETE("enrichment_complete"),
      ENRICHMENT_PARTIAL("enrichment_partial"),
      ENRICHMENT_DEFERRED("enrichment_deferred"),
      ENRICHMENT_EMPTY("enrichment_empty"),
      ENRICHMENT_FAILED("enrichment_failed"),
      ENRICHMENT_EXECUTOR_UNAVAILABLE("enrichment_executor_unavailable");

      private final String code;

      OperationStatus(String code) {
         this.code = code;
      }

      public String code() {
         return this.code;
      }
   }

   private record PendingExplicitRequest(long generation, MinecraftServer server, Integer limitOverride) {
   }

   public enum ScheduleResult {
      STARTED,
      ALREADY_RUNNING,
      NOTHING_QUEUED,
      DISABLED,
      BILLING_UNAVAILABLE,
      EXECUTOR_UNAVAILABLE;
   }
}
