package com.player2.playerengine.modintelligence.enrich;

import com.google.gson.JsonObject;
import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.modintelligence.ModIntelligenceService;
import com.player2.playerengine.modintelligence.capability.CapabilityGson;
import com.player2.playerengine.modintelligence.capability.CapabilityMap;
import com.player2.playerengine.modintelligence.ingest.CapabilityStore;
import com.player2.playerengine.modintelligence.ingest.CapabilityStoreWriter;
import com.player2.playerengine.modintelligence.ingest.EntryFingerprint;
import com.player2.playerengine.modintelligence.ingest.ModIntelligenceManifest;
import com.player2.playerengine.modintelligence.ingest.ModIntelligencePaths;
import com.player2.playerengine.modintelligence.query.CapabilityIndex;
import com.player2.playerengine.player2api.ConversationHistory;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.Logger;

public final class CapabilityEnrichmentService {
   private static final Logger LOGGER = PlayerEngine.LOGGER;

   private CapabilityEnrichmentService() {
   }

   public static void runBatch(MinecraftServer server) {
      runBatch(server, null, false, ModIntelligenceService.currentGeneration());
   }

   public static void runBatch(MinecraftServer server, Integer limitOverride) {
      runBatch(server, limitOverride, limitOverride != null, ModIntelligenceService.currentGeneration());
   }

   public static void runBatch(MinecraftServer server, Integer limitOverride, boolean explicit) {
      runBatch(server, limitOverride, explicit, ModIntelligenceService.currentGeneration());
   }

   public static void runBatch(MinecraftServer server, Integer limitOverride, boolean explicit, long generation) {
      if (ModIntelligenceService.isCurrentGeneration(generation)) {
         Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
         if (!cfg.isModIntelligenceEnabled() || !cfg.isModIntelligenceEnrichmentEnabled()) {
            ModIntelligenceService.recordOperationStatus(generation, ModIntelligenceService.OperationStatus.ENRICHMENT_DEFERRED);
         } else if (!ModIntelligenceEnrichmentClient.isBillingAvailable(server)) {
            ModIntelligenceService.recordOperationStatus(generation, ModIntelligenceService.OperationStatus.ENRICHMENT_DEFERRED);
            LOGGER.info("ModIntelligence enrichment: deferred - no online player or stored owner token for billing");
         } else if (ModIntelligenceEnrichmentClient.wouldExceedHardBudget(server)) {
            ModIntelligenceService.recordOperationStatus(generation, ModIntelligenceService.OperationStatus.ENRICHMENT_DEFERRED);
            LOGGER.info("ModIntelligence enrichment: deferred - budget hard limit would block calls");
         } else {
            ModIntelligenceService.OperationClaim claim = ModIntelligenceService.tryBeginEnrichment(generation);
            if (claim == null) {
               if (explicit) {
                  ModIntelligenceService.parkExplicit(server, limitOverride, generation);
                  LOGGER.info("ModIntelligence enrichment: another batch already running - explicit batch parked as pending follow-up");
               } else {
                  LOGGER.debug("ModIntelligence enrichment: another batch already running - auto batch skipped");
               }
            } else {
               int calls = 0;
               int validated = 0;
               int failures = 0;
               int remainingCount = 0;
               ModIntelligenceService.OperationStatus outcome = ModIntelligenceService.OperationStatus.ENRICHMENT_COMPLETE;
               ModelBlacklist.ModelBlacklistSnapshot blacklist = ModelBlacklist.load();
               ModelBlacklist.setBatchSnapshot(generation, blacklist);

               try {
                  try {
                     List<CapabilityMap> queue = CapabilityEnrichmentQueue.loadQueuedChecked();
                     if (queue.isEmpty()) {
                        LOGGER.info("ModIntelligence enrichment: queue empty - nothing to enrich (calls=0)");
                        outcome = ModIntelligenceService.OperationStatus.ENRICHMENT_EMPTY;
                        return;
                     }

                     Optional<ModIntelligenceSpendSafety.DeferReason> defer = ModIntelligenceSpendSafety.preflight(
                        server, queue.size(), blacklist, limitOverride != null
                     );
                     if (defer.isPresent()) {
                        ModIntelligenceSpendSafety.notifyPlayer(server, ModIntelligenceSpendSafety.messageFor(defer.get(), queue.size(), blacklist));
                        remainingCount = queue.size();
                        outcome = ModIntelligenceService.OperationStatus.ENRICHMENT_DEFERRED;
                        return;
                     }

                     int configuredMax = cfg.getModIntelligenceMaxEnrichmentCallsPerLaunch();
                     int maxCalls = limitOverride != null ? limitOverride : configuredMax;
                     boolean unlimited = maxCalls <= 0;
                     int maxFailures = cfg.getModIntelligenceMaxEnrichmentFailuresPerLaunch();
                     LOGGER.info(
                        "ModIntelligence enrichment: starting batch queued={} maxCalls={}{} maxFailures={}",
                        queue.size(),
                        unlimited ? "unlimited" : maxCalls,
                        limitOverride != null ? " (command override; config " + (configuredMax <= 0 ? "unlimited" : configuredMax) + ")" : "",
                        maxFailures
                     );
                     CapabilityStore store = ModIntelligenceService.store();
                     List<CapabilityMap> pending = new ArrayList<>(queue);

                     for (int i = 0; i < queue.size() && (unlimited || calls < maxCalls) && failures < maxFailures; i++) {
                        CapabilityMap map = queue.get(i);
                        String key = CapabilityStore.entryKey(map);

                        try {
                           ConversationHistory history = new ConversationHistory(CapabilityEnrichmentPrompt.systemPrompt());
                           JsonObject userMsg = new JsonObject();
                           userMsg.addProperty("role", "user");
                           userMsg.addProperty("content", CapabilityEnrichmentPrompt.buildUserJson(map));
                           history.addHistory(userMsg, false, null);
                           calls++;
                           String response = ModIntelligenceEnrichmentClient.complete(server, history);
                           if (!ModIntelligenceService.isCurrentOperation(claim)) {
                              return;
                           }

                           CapabilityEnrichmentResult parsed = CapabilityEnrichmentValidator.parseAndValidate(response, map.getSubjectKind());
                           CapabilityEnrichmentMerger.merge(map, parsed);
                           synchronized (store) {
                              store.getActiveMaps().put(key, map);
                           }

                           markManifestEnriched(key);
                           validated++;
                           pending.removeIf(m -> CapabilityStore.entryKey(m).equals(key));
                        } catch (Exception e) {
                           label329: {
                              if (!ModIntelligenceService.isCurrentOperation(claim)) {
                                 return;
                              }

                              String msg = e.getMessage() == null ? "" : e.getMessage();
                              if (!"billing_unavailable".equals(msg) && !"budget_hard_limit".equals(msg)) {
                                 if (!"model_blacklisted".equals(msg) && !"model_missing".equals(msg) && !"model_blacklist_invalid".equals(msg)) {
                                    if (!msg.contains("422") && !msg.contains("user input rejected")) {
                                       failures++;
                                       logFailure(map, e.getClass().getSimpleName(), msg);
                                       LOGGER.debug("ModIntelligence enrichment failed for {}: {}", map.getSubjectId(), msg);
                                       break label329;
                                    }

                                    outcome = ModIntelligenceService.OperationStatus.ENRICHMENT_PARTIAL;
                                    LOGGER.warn(
                                       "ModIntelligence enrichment: stopping batch - Player2 rejected request (check response_format / prompt). First error: {}",
                                       msg
                                    );
                                    break;
                                 }

                                 outcome = ModIntelligenceService.OperationStatus.ENRICHMENT_DEFERRED;
                                 ModIntelligenceSpendSafety.notifyBatchAbort(server, msg, null);
                                 logFailure(map, "EnrichmentAbort", msg);
                                 break;
                              }

                              outcome = ModIntelligenceService.OperationStatus.ENRICHMENT_DEFERRED;
                              LOGGER.info("ModIntelligence enrichment: stopping batch ({})", msg);
                              break;
                           }
                        }

                        persistPendingQueue(pending);
                     }

                     remainingCount = pending.size();
                     persistPendingQueue(pending);
                     if (!ModIntelligenceService.isCurrentOperation(claim)) {
                        return;
                     }

                     if (validated > 0) {
                        List<CapabilityMap> activeSnapshot;
                        String fp;
                        synchronized (store) {
                           activeSnapshot = new ArrayList<>(store.getActiveMaps().values());
                           fp = store.getManifest() == null ? "" : store.getManifest().getPackFingerprint();
                        }

                        CapabilityStoreWriter.writeJsonl(ModIntelligencePaths.capabilityMapsFile(), activeSnapshot);
                        CapabilityIndex.rebuildFromMaps(activeSnapshot, fp);
                     }

                     LOGGER.info("ModIntelligence enrichment: calls={} failures={} remaining={}", calls, failures, remainingCount);
                     if (outcome == ModIntelligenceService.OperationStatus.ENRICHMENT_COMPLETE && (failures > 0 || remainingCount > 0)) {
                        outcome = ModIntelligenceService.OperationStatus.ENRICHMENT_PARTIAL;
                        return;
                     }
                  } catch (Exception e) {
                     outcome = ModIntelligenceService.OperationStatus.ENRICHMENT_FAILED;
                     LOGGER.warn("ModIntelligence enrichment batch failed: {}", e.getMessage());
                  }
               } finally {
                  ModelBlacklist.clearBatchSnapshot(generation);
                  ModIntelligenceService.recordLastEnrichmentBatch(claim, validated, failures, remainingCount);
                  ModIntelligenceService.recordOperationStatus(claim, outcome);
                  ModIntelligenceService.finishEnrichment(server, claim);
               }
            }
         }
      }
   }

   private static void persistPendingQueue(List<CapabilityMap> pending) throws IOException {
      CapabilityEnrichmentQueue.saveQueued(pending);
   }

   private static void markManifestEnriched(String entryKey) {
      try {
         if (!ModIntelligencePaths.manifestFile().exists()) {
            return;
         }

         FileReader reader = new FileReader(ModIntelligencePaths.manifestFile(), StandardCharsets.UTF_8);

         ModIntelligenceManifest manifest;
         try {
            manifest = (ModIntelligenceManifest)CapabilityGson.instance().fromJson(reader, ModIntelligenceManifest.class);
         } catch (Throwable var6) {
            try {
               reader.close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }

            throw var6;
         }

         reader.close();
         if (manifest == null || manifest.getEntries() == null) {
            return;
         }

         EntryFingerprint fp = manifest.getEntries().get(entryKey);
         if (fp != null) {
            fp.setEnriched(true);
            CapabilityStoreWriter.writeJson(ModIntelligencePaths.manifestFile(), manifest);
         }
      } catch (Exception e) {
         LOGGER.debug("ModIntelligence: could not update manifest enriched flag for {}: {}", entryKey, e.getMessage());
      }
   }

   private static void logFailure(CapabilityMap map, String failureClass, String message) {
      try {
         JsonObject o = new JsonObject();
         o.addProperty("timestamp", System.currentTimeMillis());
         o.addProperty("subjectKind", map.getSubjectKind().name());
         o.addProperty("subjectId", map.getSubjectId());
         o.addProperty("failureClass", failureClass);
         o.addProperty("message", message == null ? "" : message.substring(0, Math.min(200, message.length())));

         try (BufferedWriter w = new BufferedWriter(new FileWriter(ModIntelligencePaths.enrichmentFailuresFile(), StandardCharsets.UTF_8, true))) {
            w.write(CapabilityGson.instance().toJson(o));
            w.newLine();
         }
      } catch (Exception var9) {
      }
   }
}
