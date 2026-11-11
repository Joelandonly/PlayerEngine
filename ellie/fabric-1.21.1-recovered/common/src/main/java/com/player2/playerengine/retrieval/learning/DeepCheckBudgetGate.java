package com.player2.playerengine.retrieval.learning;

import com.player2.playerengine.executor.BudgetTracker;
import com.player2.playerengine.player2api.BudgetThresholdsResolver;
import com.player2.playerengine.player2api.JoulesCache;
import com.player2.playerengine.player2api.Player2PayerResolution;
import com.player2.playerengine.player2api.config.BudgetThresholds;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class DeepCheckBudgetGate {
   private static final Logger LOGGER = LogManager.getLogger(DeepCheckBudgetGate.class);

   private DeepCheckBudgetGate() {
   }

   public static DeepCheckBudgetGate.SkipReason preflight(Player2PayerResolution.ApiBillingContext billing) {
      Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
      if (!cfg.isEnableDeepCheckRephrase()) {
         return DeepCheckBudgetGate.SkipReason.DEEP_CHECK_DISABLED;
      }

      if (billing != null && billing.billingKey() != null) {
         BudgetThresholds thresholds = thresholdsFor(billing);
         BudgetTracker.BudgetCheckResult callPeek = BudgetTracker.peek(billing.billingKey(), thresholds);
         JoulesCache.JoulesSnapshot joulesSnap = JoulesCache.get(billing.billingKey()).orElse(null);
         BudgetTracker.BudgetCheckResult joulesPeek = JoulesCache.checkJoulesThreshold(joulesSnap, thresholds);
         BudgetTracker.BudgetCheckResult combined = BudgetTracker.stricter(callPeek, joulesPeek);
         if (combined == BudgetTracker.BudgetCheckResult.HARD_LIMIT) {
            LOGGER.debug("[B5] budget_hard_skip billingKey={}", billing.billingKey());
            return DeepCheckBudgetGate.SkipReason.BUDGET_HARD_SKIP;
         } else if (combined == BudgetTracker.BudgetCheckResult.SOFT_LIMIT) {
            LOGGER.debug("[B5] budget_soft_skip billingKey={}", billing.billingKey());
            return DeepCheckBudgetGate.SkipReason.BUDGET_SOFT_SKIP;
         } else {
            DeepCheckBudgetThresholds deepThresholds = DeepCheckBudgetThresholds.fromConfig(cfg);
            return DeepCheckBudgetTracker.peekCap(billing.billingKey(), deepThresholds) == DeepCheckBudgetResult.CAP_REACHED
               ? DeepCheckBudgetGate.SkipReason.DEEP_CHECK_CAP_SKIP
               : DeepCheckBudgetGate.SkipReason.NONE;
         }
      } else {
         return DeepCheckBudgetGate.SkipReason.BUDGET_HARD_SKIP;
      }
   }

   public static boolean reserveDeepCheckSlot(Player2PayerResolution.ApiBillingContext billing) {
      Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
      return billing != null && billing.billingKey() != null
         ? DeepCheckBudgetTracker.checkAndRecord(billing.billingKey(), DeepCheckBudgetThresholds.fromConfig(cfg)) == DeepCheckBudgetResult.OK
         : false;
   }

   public static DeepCheckBudgetGate.SkipReason peekA4Only(Player2PayerResolution.ApiBillingContext billing) {
      if (billing != null && billing.billingKey() != null) {
         BudgetThresholds thresholds = thresholdsFor(billing);
         BudgetTracker.BudgetCheckResult callPeek = BudgetTracker.peek(billing.billingKey(), thresholds);
         JoulesCache.JoulesSnapshot joulesSnap = JoulesCache.get(billing.billingKey()).orElse(null);
         BudgetTracker.BudgetCheckResult joulesPeek = JoulesCache.checkJoulesThreshold(joulesSnap, thresholds);
         BudgetTracker.BudgetCheckResult combined = BudgetTracker.stricter(callPeek, joulesPeek);
         if (combined == BudgetTracker.BudgetCheckResult.HARD_LIMIT) {
            return DeepCheckBudgetGate.SkipReason.BUDGET_HARD_SKIP;
         } else {
            return combined == BudgetTracker.BudgetCheckResult.SOFT_LIMIT
               ? DeepCheckBudgetGate.SkipReason.BUDGET_SOFT_SKIP
               : DeepCheckBudgetGate.SkipReason.NONE;
         }
      } else {
         return DeepCheckBudgetGate.SkipReason.BUDGET_HARD_SKIP;
      }
   }

   private static BudgetThresholds thresholdsFor(Player2PayerResolution.ApiBillingContext billing) {
      ServerPlayer payer = billing != null ? billing.onlinePayer() : null;
      MinecraftServer server = payer != null ? payer.getServer() : null;
      return BudgetThresholdsResolver.resolve(server, billing);
   }

   public enum SkipReason {
      NONE,
      DEEP_CHECK_DISABLED,
      BUDGET_SOFT_SKIP,
      BUDGET_HARD_SKIP,
      DEEP_CHECK_CAP_SKIP;
   }
}
