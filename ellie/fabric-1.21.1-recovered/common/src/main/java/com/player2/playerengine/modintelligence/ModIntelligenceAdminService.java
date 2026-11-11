package com.player2.playerengine.modintelligence;

import com.player2.playerengine.modintelligence.enrich.CapabilityEnrichmentQueue;
import com.player2.playerengine.modintelligence.enrich.ModIntelligenceEnrichmentClient;
import com.player2.playerengine.modintelligence.enrich.ModIntelligenceSpendSafety;
import com.player2.playerengine.modintelligence.enrich.ModelBlacklist;
import com.player2.playerengine.modintelligence.query.CapabilityHit;
import com.player2.playerengine.modintelligence.query.CapabilityQuery;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;

public final class ModIntelligenceAdminService {
   public static final int MAX_UI_QUERY_LENGTH = 256;
   public static final int MAX_UI_QUERY_RESULTS = 20;

   private ModIntelligenceAdminService() {
   }

   public static ModIntelligenceAdminService.SettingsSnapshot settings(MinecraftServer server) {
      Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
      return new ModIntelligenceAdminService.SettingsSnapshot(
         cfg.isModIntelligenceEnabled(),
         cfg.isModIntelligenceEnrichmentEnabled(),
         Math.max(0, cfg.getModIntelligenceMaxEnrichmentCallsPerLaunch()),
         cfg.isModIntelligenceBypassLargeQueueBudgetGate(),
         server != null && ModIntelligenceSpendSafety.bypassFlagsApply(server)
      );
   }

   public static synchronized ModIntelligenceAdminService.SettingUpdateResult updateSetting(
      MinecraftServer server, ModIntelligenceAdminService.Setting setting, int value
   ) {
      if (server == null || setting == null) {
         return ModIntelligenceAdminService.SettingUpdateResult.INVALID_VALUE;
      }

      if (setting != ModIntelligenceAdminService.Setting.MAX_ENRICHMENT_CALLS && value != 0 && value != 1) {
         return ModIntelligenceAdminService.SettingUpdateResult.INVALID_VALUE;
      }

      if (setting != ModIntelligenceAdminService.Setting.MAX_ENRICHMENT_CALLS || value >= 0 && value <= 500) {
         if (setting == ModIntelligenceAdminService.Setting.BYPASS_LARGE_QUEUE_BUDGET_GATE && !ModIntelligenceSpendSafety.bypassFlagsApply(server)) {
            return ModIntelligenceAdminService.SettingUpdateResult.SINGLEPLAYER_ONLY;
         }

         Player2ServerConfigHolder.FreshLoadResult fresh = Player2ServerConfigHolder.freshLoadResult();
         if (fresh.loadFailed()) {
            return ModIntelligenceAdminService.SettingUpdateResult.LOAD_FAILED;
         }

         Player2ServerRuntimeConfig cfg = fresh.config();
         if (cfg == null) {
            return ModIntelligenceAdminService.SettingUpdateResult.SAVE_FAILED;
         }

         boolean oldEnabled = cfg.isModIntelligenceEnabled();
         boolean oldEnrichmentEnabled = cfg.isModIntelligenceEnrichmentEnabled();
         apply(cfg, setting, value);
         if (!Player2ServerConfigHolder.setAndSaveChecked(cfg)) {
            ModIntelligenceService.refreshCachedStatus();
            return ModIntelligenceAdminService.SettingUpdateResult.SAVE_FAILED;
         }

         ModIntelligenceService.refreshCachedStatus();
         boolean enabledNow = cfg.isModIntelligenceEnabled();
         boolean enrichmentEnabledNow = cfg.isModIntelligenceEnrichmentEnabled();
         if (setting == ModIntelligenceAdminService.Setting.ENABLED && !oldEnabled && enabledNow
            || setting == ModIntelligenceAdminService.Setting.ENRICHMENT_ENABLED && !oldEnrichmentEnabled && enrichmentEnabledNow && enabledNow) {
            ModIntelligenceService.requestIngestion(server, false);
         }

         return ModIntelligenceAdminService.SettingUpdateResult.UPDATED;
      } else {
         return ModIntelligenceAdminService.SettingUpdateResult.INVALID_VALUE;
      }
   }

   private static void apply(Player2ServerRuntimeConfig cfg, ModIntelligenceAdminService.Setting setting, int value) {
      switch (setting) {
         case ENABLED:
            cfg.setModIntelligenceEnabled(value == 1);
            break;
         case ENRICHMENT_ENABLED:
            cfg.setModIntelligenceEnrichmentEnabled(value == 1);
            break;
         case MAX_ENRICHMENT_CALLS:
            cfg.setModIntelligenceMaxEnrichmentCallsPerLaunch(value);
            break;
         case BYPASS_LARGE_QUEUE_BUDGET_GATE:
            cfg.setModIntelligenceBypassLargeQueueBudgetGate(value == 1);
      }
   }

   public static ModIntelligenceService.IngestionRequestResult requestRebuild(MinecraftServer server) {
      return ModIntelligenceService.requestIngestion(server, true);
   }

   public static ModIntelligenceAdminService.EnrichmentRequestResult requestEnrichment(MinecraftServer server) {
      Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
      if (server != null && cfg.isModIntelligenceEnabled() && cfg.isModIntelligenceEnrichmentEnabled()) {
         int queued;
         try {
            queued = CapabilityEnrichmentQueue.countQueuedLinesChecked();
         } catch (IOException e) {
            ModIntelligenceService.recordOperationStatus(ModIntelligenceService.OperationStatus.ENRICHMENT_FAILED);
            return ModIntelligenceAdminService.EnrichmentRequestResult.QUEUE_UNAVAILABLE;
         }

         if (queued <= 0) {
            return ModIntelligenceAdminService.EnrichmentRequestResult.NOTHING_QUEUED;
         }

         if (!ModIntelligenceEnrichmentClient.isBillingAvailable(server)) {
            return ModIntelligenceAdminService.EnrichmentRequestResult.BILLING_UNAVAILABLE;
         }

         if (ModIntelligenceEnrichmentClient.wouldExceedHardBudget(server)) {
            return ModIntelligenceAdminService.EnrichmentRequestResult.HARD_BUDGET_LIMIT;
         }

         ModelBlacklist.ModelBlacklistSnapshot blacklist = ModelBlacklist.load();
         Optional<ModIntelligenceSpendSafety.DeferReason> deferred = ModIntelligenceSpendSafety.preflight(server, queued, blacklist, false);
         if (deferred.isPresent()) {
            return deferred.get() == ModIntelligenceSpendSafety.DeferReason.BLACKLIST_INVALID
               ? ModIntelligenceAdminService.EnrichmentRequestResult.MODEL_BLACKLIST_INVALID
               : ModIntelligenceAdminService.EnrichmentRequestResult.LARGE_QUEUE_BUDGET_REQUIRED;
         }

         return switch (ModIntelligenceService.scheduleEnrichmentBatch(server, null)) {
            case STARTED -> ModIntelligenceAdminService.EnrichmentRequestResult.STARTED;
            case ALREADY_RUNNING -> ModIntelligenceAdminService.EnrichmentRequestResult.QUEUED_AFTER_CURRENT;
            case NOTHING_QUEUED -> ModIntelligenceAdminService.EnrichmentRequestResult.NOTHING_QUEUED;
            case DISABLED -> ModIntelligenceAdminService.EnrichmentRequestResult.DISABLED;
            case BILLING_UNAVAILABLE -> ModIntelligenceAdminService.EnrichmentRequestResult.BILLING_UNAVAILABLE;
            case EXECUTOR_UNAVAILABLE -> ModIntelligenceAdminService.EnrichmentRequestResult.EXECUTOR_UNAVAILABLE;
         };
      } else {
         return ModIntelligenceAdminService.EnrichmentRequestResult.DISABLED;
      }
   }

   public static List<CapabilityHit> query(String rawQuery, int requestedLimit) {
      String query = normalizeQuery(rawQuery);
      if (query.isEmpty()) {
         throw new IllegalArgumentException("query is blank");
      }

      int limit = Math.max(1, Math.min(20, requestedLimit));
      double minConfidence = Player2ServerConfigHolder.get().getModIntelligenceMinQueryConfidence();
      List<CapabilityHit> hits = ModIntelligenceService.queryService().query(CapabilityQuery.defaults().setText(query).setMinConfidence(minConfidence), limit);
      return List.copyOf(hits);
   }

   public static String normalizeQuery(String rawQuery) {
      if (rawQuery == null) {
         return "";
      }

      String query = rawQuery.replace('\r', ' ').replace('\n', ' ').trim();

      for (int i = 0; i < query.length(); i++) {
         if (Character.isISOControl(query.charAt(i))) {
            throw new IllegalArgumentException("query contains control characters");
         }
      }

      if (query.length() > 256) {
         throw new IllegalArgumentException("query is too long");
      } else {
         return query;
      }
   }

   public enum EnrichmentRequestResult {
      STARTED,
      QUEUED_AFTER_CURRENT,
      NOTHING_QUEUED,
      QUEUE_UNAVAILABLE,
      DISABLED,
      BILLING_UNAVAILABLE,
      EXECUTOR_UNAVAILABLE,
      HARD_BUDGET_LIMIT,
      MODEL_BLACKLIST_INVALID,
      LARGE_QUEUE_BUDGET_REQUIRED;
   }

   public enum Setting {
      ENABLED,
      ENRICHMENT_ENABLED,
      MAX_ENRICHMENT_CALLS,
      BYPASS_LARGE_QUEUE_BUDGET_GATE;
   }

   public enum SettingUpdateResult {
      UPDATED,
      INVALID_VALUE,
      SINGLEPLAYER_ONLY,
      LOAD_FAILED,
      SAVE_FAILED;
   }

   public record SettingsSnapshot(
      boolean enabled, boolean enrichmentEnabled, int maxEnrichmentCalls, boolean bypassLargeQueueBudgetGate, boolean bypassSupported
   ) {
   }
}
