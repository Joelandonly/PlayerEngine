package com.player2.playerengine.memory.budget;

import com.player2.playerengine.executor.BudgetTracker;
import com.player2.playerengine.player2api.BudgetThresholdsResolver;
import com.player2.playerengine.player2api.JoulesCache;
import com.player2.playerengine.player2api.Player2PayerResolution;
import com.player2.playerengine.player2api.config.BudgetThresholds;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;
import net.minecraft.server.MinecraftServer;

public final class MemoryGate {
   private MemoryGate() {
   }

   public static MemoryGateDecision preflight(MinecraftServer server, Player2PayerResolution.ApiBillingContext ownerBilling) {
      Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
      if (!cfg.isEnableGraphRagMemory()) {
         return MemoryGateDecision.skip(MemoryGateDecision.SkipReason.MEMORY_DISABLED);
      }

      if (cfg.isDedicatedClientProxy()) {
         return MemoryGateDecision.skip(MemoryGateDecision.SkipReason.CLIENT_PROXY_UNSUPPORTED);
      }

      if (ownerBilling != null && ownerBilling.billingKey() != null) {
         String ownerBillingKey = ownerBilling.billingKey();
         JoulesCache.JoulesSnapshot ownerSnapshot = JoulesCache.get(ownerBillingKey).orElse(null);
         if (ownerSnapshot == null) {
            ownerSnapshot = JoulesCache.emptyNonPatron();
         }

         BudgetThresholds thresholds = BudgetThresholdsResolver.resolve(server, ownerBilling);
         BudgetTracker.BudgetCheckResult callPeek = BudgetTracker.peek(ownerBillingKey, thresholds);
         BudgetTracker.BudgetCheckResult joulesPeek = JoulesCache.checkJoulesThreshold(ownerSnapshot, thresholds);
         BudgetTracker.BudgetCheckResult combined = BudgetTracker.stricter(callPeek, joulesPeek);
         if (combined == BudgetTracker.BudgetCheckResult.HARD_LIMIT) {
            return MemoryGateDecision.skip(MemoryGateDecision.SkipReason.BUDGET_HARD_SKIP);
         } else if (combined == BudgetTracker.BudgetCheckResult.SOFT_LIMIT) {
            return MemoryGateDecision.skip(MemoryGateDecision.SkipReason.BUDGET_SOFT_SKIP);
         } else {
            return MemoryExtractionBudgetGate.peekCap(ownerBilling) == MemoryBudgetResult.CAP_REACHED
               ? MemoryGateDecision.skip(MemoryGateDecision.SkipReason.EXTRACTION_CAP_SKIP)
               : MemoryGateDecision.allow();
         }
      } else {
         return MemoryGateDecision.skip(MemoryGateDecision.SkipReason.NO_BILLING);
      }
   }

   public static boolean reserveSlot(Player2PayerResolution.ApiBillingContext ownerBilling) {
      return MemoryExtractionBudgetGate.reserveSlot(ownerBilling);
   }

   public static boolean isConfirmedPatron(JoulesCache.JoulesSnapshot snapshot) {
      return snapshot != null && snapshot.isPatron();
   }
}
