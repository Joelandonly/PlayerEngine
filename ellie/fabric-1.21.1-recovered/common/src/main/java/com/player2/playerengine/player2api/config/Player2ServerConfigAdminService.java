package com.player2.playerengine.player2api.config;

import com.player2.playerengine.player2api.ProfileUrlResolver;
import com.player2.playerengine.player2api.network.Player2ServerNetworking;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Player2ServerConfigAdminService {
   private static final Logger LOGGER = LogManager.getLogger();

   private Player2ServerConfigAdminService() {
   }

   public static Map<String, String> snapshot() {
      return snapshotResult().values();
   }

   public static Player2ServerConfigAdminService.SnapshotResult snapshotResult() {
      Player2ServerConfigHolder.FreshLoadResult fresh = Player2ServerConfigHolder.freshLoadResult();
      return new Player2ServerConfigAdminService.SnapshotResult(snapshotOf(fresh.config()), fresh.loadFailed());
   }

   private static Map<String, String> snapshotOf(Player2ServerRuntimeConfig cfg) {
      LinkedHashMap<String, String> fields = new LinkedHashMap<>();
      fields.put("payerMode", cfg.getPayerMode().name());
      fields.put("ownerOfflineServerContinuation", bool(cfg.isOwnerOfflineServerContinuation()));
      fields.put("callByNameChat", bool(cfg.isCallByNameChat()));
      fields.put("maxSpawnedCompanionsPerPlayer", integer(cfg.getMaxSpawnedCompanionsPerPlayer()));
      fields.put("maxStoredCharacterIdsPerPlayer", integer(cfg.getMaxStoredCharacterIdsPerPlayer()));
      fields.put("chatCompletionMaxOutputTokens", integer(cfg.getChatCompletionMaxOutputTokensClamped()));
      fields.put("ragFallbackToFullList", bool(cfg.isRagFallbackToFullList()));
      fields.put("ragLiveEnabled", bool(cfg.isRagLiveEnabled()));
      fields.put("ragMinGoalChars", integer(cfg.getRagMinGoalCharsClamped()));
      fields.put("deepCheckMaxAttemptsPerTurn", integer(cfg.getDeepCheckMaxAttemptsPerTurnClamped()));
      fields.put("deepCheckCallsPerWindow", integer(cfg.getDeepCheckCallsPerWindowClamped()));
      fields.put("deepCheckWindowMinutes", integer(cfg.getDeepCheckWindowMinutesClamped()));
      fields.put("deepCheckWeakBelowScore", percent(cfg.getDeepCheckWeakBelowScoreClamped(), 0.015));
      fields.put("deepCheckWeakGapRatio", percent(cfg.getDeepCheckWeakGapRatioClamped(), 0.15));
      fields.put("deepCheckWeakTokenCoverage", percent(cfg.getDeepCheckWeakTokenCoverageClamped(), 0.35));
      fields.put("forceDeepCheckOnEmpty", bool(cfg.isForceDeepCheckOnEmpty()));
      fields.put("memoryWindowMinutes", integer(cfg.getMemoryWindowMinutesClamped()));
      fields.put("memoryExtractionBatchMin", integer(cfg.getMemoryExtractionBatchMin()));
      fields.put("memoryExtractionBatchMax", integer(cfg.getMemoryExtractionBatchMax()));
      fields.put("memoryExtractionLengthThreshold", integer(cfg.getMemoryExtractionLengthThreshold()));
      fields.put("memoryMaxHops", integer(cfg.getMemoryMaxHopsClamped()));
      fields.put("memoryMaxEgoNodes", integer(cfg.getMemoryMaxEgoNodesClamped()));
      fields.put("memoryBlockCharCap", integer(cfg.getMemoryBlockCharCapClamped()));
      fields.put("memoryMinConfidence", percent(cfg.getMemoryMinConfidenceClamped(), 0.2));
      fields.put("memoryDecayBase", permille(cfg.getMemoryDecayBaseClamped()));
      fields.put("memoryRetrievalTopK", integer(cfg.getMemoryRetrievalTopKClamped()));
      fields.put("reflectionImportanceThreshold", integer(cfg.getReflectionImportanceThresholdClamped()));
      fields.put("relationshipSummaryCharCap", integer(cfg.getRelationshipSummaryCharCapClamped()));
      fields.put("modIntelligenceInspectOnLaunch", bool(cfg.isModIntelligenceInspectOnLaunch()));
      fields.put("modIntelligenceMaxInspectEntriesPerLaunch", integer(cfg.getModIntelligenceMaxInspectEntriesPerLaunch()));
      fields.put("modIntelligenceMaxEnrichmentFailuresPerLaunch", integer(cfg.getModIntelligenceMaxEnrichmentFailuresPerLaunch()));
      fields.put("modIntelligenceQueryTopK", integer(cfg.getModIntelligenceQueryTopKClamped()));
      fields.put("modIntelligenceMinQueryConfidence", percent(cfg.getModIntelligenceMinQueryConfidence(), 0.5));
      fields.put("botTtsPlaybackAckEnabled", bool(cfg.isBotTtsPlaybackAckEnabled()));
      fields.put("serverOverridesPlayerConfig", bool(cfg.isServerOverridesPlayerConfig()));
      fields.put("botKeepInventoryOverride", cfg.getBotKeepInventoryOverride().name());
      return Collections.unmodifiableMap(fields);
   }

   public static synchronized Player2ServerConfigAdminService.UpdateResult update(MinecraftServer server, String key, String value) {
      if (server == null) {
         return Player2ServerConfigAdminService.UpdateResult.INVALID_VALUE;
      }

      if (key == null) {
         return Player2ServerConfigAdminService.UpdateResult.UNSUPPORTED_SETTING;
      }

      Player2ServerRuntimeConfig previousLive = Player2ServerConfigHolder.copyCurrent();
      Player2ServerConfigHolder.FreshLoadResult fresh = Player2ServerConfigHolder.freshLoadResult();
      if (fresh.loadFailed()) {
         return Player2ServerConfigAdminService.UpdateResult.LOAD_FAILED;
      }

      Player2ServerRuntimeConfig cfg = fresh.config();
      if (cfg == null) {
         return Player2ServerConfigAdminService.UpdateResult.SAVE_FAILED;
      }

      Player2ServerConfigAdminService.UpdateResult applyResult = apply(cfg, key, value);
      if (applyResult != Player2ServerConfigAdminService.UpdateResult.OK) {
         return applyResult;
      }

      if (!Player2ServerConfigHolder.hasValidBudgetThresholdPairs(cfg)) {
         return Player2ServerConfigAdminService.UpdateResult.INVALID_VALUE;
      }

      try {
         if (!Player2ServerConfigHolder.setAndSaveChecked(cfg)) {
            return Player2ServerConfigAdminService.UpdateResult.SAVE_FAILED;
         }
      } catch (RuntimeException e) {
         LOGGER.warn("Unable to persist a Player2 server-config admin update.", e);
         return Player2ServerConfigAdminService.UpdateResult.SAVE_FAILED;
      }

      Player2ServerConfigAdminService.LivePublicationResult publication = publishLiveChanges(server, previousLive, cfg);
      return publication.fullyApplied()
         ? Player2ServerConfigAdminService.UpdateResult.OK
         : Player2ServerConfigAdminService.UpdateResult.SAVED_LIVE_APPLY_FAILED;
   }

   private static Player2ServerConfigAdminService.LivePublicationResult publishLiveChanges(
      MinecraftServer server, Player2ServerRuntimeConfig previous, Player2ServerRuntimeConfig current
   ) {
      boolean cacheRequired = previous == null || previous.getPayerMode() != current.getPayerMode();
      boolean cacheSucceeded = !cacheRequired;
      if (cacheRequired) {
         try {
            ProfileUrlResolver.invalidateCache();
            cacheSucceeded = true;
         } catch (RuntimeException e) {
            LOGGER.warn("Unable to invalidate the Player2 profile cache after a config update; continuing.", e);
         }
      }

      boolean syncRequired = clientSyncFieldsChanged(previous, current);
      Player2ServerNetworking.ConfigSyncResult syncResult = null;
      if (syncRequired) {
         try {
            syncResult = Player2ServerNetworking.sendConfigSyncToAll(server);
         } catch (RuntimeException e) {
            LOGGER.warn("Unable to publish a Player2 server-config update to connected clients.", e);
         }
      }

      return new Player2ServerConfigAdminService.LivePublicationResult(cacheRequired, cacheSucceeded, syncRequired, syncResult);
   }

   private static boolean clientSyncFieldsChanged(Player2ServerRuntimeConfig previous, Player2ServerRuntimeConfig current) {
      return previous == null
         || previous.isDedicatedClientProxy() != current.isDedicatedClientProxy()
         || previous.getPayerMode() != current.getPayerMode()
         || previous.isOwnerOfflineServerContinuation() != current.isOwnerOfflineServerContinuation()
         || !Objects.equals(previous.getHeartbeatClientId(), current.getHeartbeatClientId())
         || previous.getBodylangMarkerPauseMs() != current.getBodylangMarkerPauseMs()
         || previous.isBodylangGaplessPrefetch() != current.isBodylangGaplessPrefetch();
   }

   private static Player2ServerConfigAdminService.UpdateResult apply(Player2ServerRuntimeConfig cfg, String key, String value) {
      return switch (key) {
         case "payerMode" -> setPayerMode(cfg, value);
         case "ownerOfflineServerContinuation" -> setOwnerOfflineContinuation(cfg, value);
         case "callByNameChat" -> setBoolean(value, cfg::setCallByNameChat);
         case "maxSpawnedCompanionsPerPlayer" -> setInteger(value, 1, 20, cfg::setMaxSpawnedCompanionsPerPlayer);
         case "maxStoredCharacterIdsPerPlayer" -> setInteger(value, 0, 100, cfg::setMaxStoredCharacterIdsPerPlayer);
         case "chatCompletionMaxOutputTokens" -> setInteger(value, 1, 100000, cfg::setChatCompletionMaxOutputTokens);
         case "ragFallbackToFullList" -> setBoolean(value, cfg::setRagFallbackToFullList);
         case "ragLiveEnabled" -> setBoolean(value, cfg::setRagLiveEnabled);
         case "ragMinGoalChars" -> setInteger(value, 1, 16, cfg::setRagMinGoalChars);
         case "deepCheckMaxAttemptsPerTurn" -> setInteger(value, 0, 3, cfg::setDeepCheckMaxAttemptsPerTurn);
         case "deepCheckCallsPerWindow" -> setInteger(value, 0, 100, cfg::setDeepCheckCallsPerWindow);
         case "deepCheckWindowMinutes" -> setInteger(value, 1, 1440, cfg::setDeepCheckWindowMinutes);
         case "deepCheckWeakBelowScore" -> setPercent(value, cfg::setDeepCheckWeakBelowScore);
         case "deepCheckWeakGapRatio" -> setPercent(value, cfg::setDeepCheckWeakGapRatio);
         case "deepCheckWeakTokenCoverage" -> setPercent(value, cfg::setDeepCheckWeakTokenCoverage);
         case "forceDeepCheckOnEmpty" -> setBoolean(value, cfg::setForceDeepCheckOnEmpty);
         case "memoryWindowMinutes" -> setInteger(value, 1, 1440, cfg::setMemoryWindowMinutes);
         case "memoryExtractionBatchMin" -> setMemoryBatchMin(cfg, value);
         case "memoryExtractionBatchMax" -> setMemoryBatchMax(cfg, value);
         case "memoryExtractionLengthThreshold" -> setInteger(value, 1, 32768, cfg::setMemoryExtractionLengthThreshold);
         case "memoryMaxHops" -> setInteger(value, 1, 6, cfg::setMemoryMaxHops);
         case "memoryMaxEgoNodes" -> setInteger(value, 1, 4096, cfg::setMemoryMaxEgoNodes);
         case "memoryBlockCharCap" -> setInteger(value, 0, 8000, cfg::setMemoryBlockCharCap);
         case "memoryMinConfidence" -> setPercent(value, cfg::setMemoryMinConfidence);
         case "memoryDecayBase" -> setPermille(value, cfg::setMemoryDecayBase);
         case "memoryRetrievalTopK" -> setInteger(value, 1, 50, cfg::setMemoryRetrievalTopK);
         case "reflectionImportanceThreshold" -> setInteger(value, 1, 100000, cfg::setReflectionImportanceThreshold);
         case "relationshipSummaryCharCap" -> setInteger(value, 0, 1000, cfg::setRelationshipSummaryCharCap);
         case "modIntelligenceInspectOnLaunch" -> setBoolean(value, cfg::setModIntelligenceInspectOnLaunch);
         case "modIntelligenceMaxInspectEntriesPerLaunch" -> setInteger(value, 0, 50000, cfg::setModIntelligenceMaxInspectEntriesPerLaunch);
         case "modIntelligenceMaxEnrichmentFailuresPerLaunch" -> setInteger(value, 1, 500, cfg::setModIntelligenceMaxEnrichmentFailuresPerLaunch);
         case "modIntelligenceQueryTopK" -> setInteger(value, 1, 50, cfg::setModIntelligenceQueryTopK);
         case "modIntelligenceMinQueryConfidence" -> setPercent(value, cfg::setModIntelligenceMinQueryConfidence);
         case "botTtsPlaybackAckEnabled" -> setBoolean(value, cfg::setBotTtsPlaybackAckEnabled);
         case "serverOverridesPlayerConfig" -> setBoolean(value, cfg::setServerOverridesPlayerConfig);
         case "botKeepInventoryOverride" -> setKeepInventoryOverride(cfg, value);
         default -> Player2ServerConfigAdminService.UpdateResult.UNSUPPORTED_SETTING;
      };
   }

   private static Player2ServerConfigAdminService.UpdateResult setPayerMode(Player2ServerRuntimeConfig cfg, String raw) {
      String value = normalize(raw);
      if (value == null) {
         return Player2ServerConfigAdminService.UpdateResult.INVALID_VALUE;
      }

      try {
         cfg.setPayerMode(Player2PayerMode.valueOf(value));
         return Player2ServerConfigAdminService.UpdateResult.OK;
      } catch (IllegalArgumentException ignored) {
         return Player2ServerConfigAdminService.UpdateResult.INVALID_VALUE;
      }
   }

   private static Player2ServerConfigAdminService.UpdateResult setOwnerOfflineContinuation(Player2ServerRuntimeConfig cfg, String raw) {
      Boolean value = parseBoolean(raw);
      if (value == null) {
         return Player2ServerConfigAdminService.UpdateResult.INVALID_VALUE;
      } else if (!value || cfg.getPayerMode() == Player2PayerMode.OWNER_PAYS_ALL && !cfg.isDedicatedClientProxy()) {
         cfg.setOwnerOfflineServerContinuation(value);
         return Player2ServerConfigAdminService.UpdateResult.OK;
      } else {
         return Player2ServerConfigAdminService.UpdateResult.INVALID_VALUE;
      }
   }

   private static Player2ServerConfigAdminService.UpdateResult setKeepInventoryOverride(Player2ServerRuntimeConfig cfg, String raw) {
      String value = normalize(raw);
      if (value == null) {
         return Player2ServerConfigAdminService.UpdateResult.INVALID_VALUE;
      }

      try {
         cfg.setBotKeepInventoryOverride(KeepInventoryOverride.valueOf(value));
         return Player2ServerConfigAdminService.UpdateResult.OK;
      } catch (IllegalArgumentException ignored) {
         return Player2ServerConfigAdminService.UpdateResult.INVALID_VALUE;
      }
   }

   private static Player2ServerConfigAdminService.UpdateResult setMemoryBatchMin(Player2ServerRuntimeConfig cfg, String raw) {
      Integer value = parseInteger(raw, 1, 100);
      if (value != null && value <= cfg.getMemoryExtractionBatchMax()) {
         cfg.setMemoryExtractionBatchMin(value);
         return Player2ServerConfigAdminService.UpdateResult.OK;
      } else {
         return Player2ServerConfigAdminService.UpdateResult.INVALID_VALUE;
      }
   }

   private static Player2ServerConfigAdminService.UpdateResult setMemoryBatchMax(Player2ServerRuntimeConfig cfg, String raw) {
      Integer value = parseInteger(raw, 1, 100);
      if (value != null && value >= cfg.getMemoryExtractionBatchMin()) {
         cfg.setMemoryExtractionBatchMax(value);
         return Player2ServerConfigAdminService.UpdateResult.OK;
      } else {
         return Player2ServerConfigAdminService.UpdateResult.INVALID_VALUE;
      }
   }

   private static Player2ServerConfigAdminService.UpdateResult setBoolean(String raw, Consumer<Boolean> setter) {
      Boolean value = parseBoolean(raw);
      if (value == null) {
         return Player2ServerConfigAdminService.UpdateResult.INVALID_VALUE;
      }

      setter.accept(value);
      return Player2ServerConfigAdminService.UpdateResult.OK;
   }

   private static Player2ServerConfigAdminService.UpdateResult setInteger(String raw, int min, int max, IntConsumer setter) {
      Integer value = parseInteger(raw, min, max);
      if (value == null) {
         return Player2ServerConfigAdminService.UpdateResult.INVALID_VALUE;
      }

      setter.accept(value);
      return Player2ServerConfigAdminService.UpdateResult.OK;
   }

   private static Player2ServerConfigAdminService.UpdateResult setPercent(String raw, DoubleConsumer setter) {
      Integer value = parseInteger(raw, 0, 100);
      if (value == null) {
         return Player2ServerConfigAdminService.UpdateResult.INVALID_VALUE;
      }

      setter.accept(value.intValue() / 100.0);
      return Player2ServerConfigAdminService.UpdateResult.OK;
   }

   private static Player2ServerConfigAdminService.UpdateResult setPermille(String raw, DoubleConsumer setter) {
      Integer value = parseInteger(raw, 1, 1000);
      if (value == null) {
         return Player2ServerConfigAdminService.UpdateResult.INVALID_VALUE;
      }

      setter.accept(value.intValue() / 1000.0);
      return Player2ServerConfigAdminService.UpdateResult.OK;
   }

   private static Boolean parseBoolean(String raw) {
      String value = normalize(raw);
      if ("true".equals(value)) {
         return Boolean.TRUE;
      } else {
         return "false".equals(value) ? Boolean.FALSE : null;
      }
   }

   private static Integer parseInteger(String raw, int min, int max) {
      String value = normalize(raw);
      if (value == null) {
         return null;
      }

      for (int i = 0; i < value.length(); i++) {
         char c = value.charAt(i);
         if (c < '0' || c > '9') {
            return null;
         }
      }

      try {
         int parsed = Integer.parseInt(value);
         return parsed >= min && parsed <= max ? parsed : null;
      } catch (NumberFormatException ignored) {
         return null;
      }
   }

   private static String normalize(String raw) {
      if (raw == null) {
         return null;
      }

      String value = raw.trim();
      return value.isEmpty() ? null : value;
   }

   private static String bool(boolean value) {
      return String.valueOf(value);
   }

   private static String integer(int value) {
      return String.valueOf(value);
   }

   private static String percent(double value, double fallback) {
      double safe = Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : fallback;
      return integer((int)Math.round(safe * 100.0));
   }

   private static String permille(double value) {
      double safe = Double.isFinite(value) && value > 0.0 && value <= 1.0 ? value : 0.995;
      return integer((int)Math.round(safe * 1000.0));
   }

   public record LivePublicationResult(
      boolean cacheInvalidationRequired,
      boolean cacheInvalidationSucceeded,
      boolean clientSyncRequired,
      Player2ServerNetworking.ConfigSyncResult clientSyncResult
   ) {
      public boolean fullyApplied() {
         return (!this.cacheInvalidationRequired || this.cacheInvalidationSucceeded)
            && (!this.clientSyncRequired || this.clientSyncResult != null && this.clientSyncResult.fullySynchronized());
      }
   }

   public record SnapshotResult(Map<String, String> values, boolean loadFailed) {
   }

   public enum UpdateResult {
      OK,
      INVALID_VALUE,
      UNSUPPORTED_SETTING,
      LOAD_FAILED,
      SAVED_LIVE_APPLY_FAILED,
      SAVE_FAILED;
   }
}
