package com.player2.playerengine.memory.budget;

import com.player2.playerengine.player2api.Player2PayerResolution;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;

public final class MemoryExtractionBudgetGate {
   private MemoryExtractionBudgetGate() {
   }

   public static MemoryBudgetResult peekCap(Player2PayerResolution.ApiBillingContext billing) {
      if (billing != null && billing.billingKey() != null) {
         Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
         return MemoryExtractionBudgetTracker.peekCap(billing.billingKey(), MemoryBudgetThresholds.fromConfig(cfg));
      } else {
         return MemoryBudgetResult.CAP_REACHED;
      }
   }

   public static MemoryBudgetSnapshot peek(Player2PayerResolution.ApiBillingContext billing) {
      if (billing != null && billing.billingKey() != null) {
         Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
         return MemoryExtractionBudgetTracker.peek(billing.billingKey(), MemoryBudgetThresholds.fromConfig(cfg));
      } else {
         return new MemoryBudgetSnapshot(0, 0L, 0L);
      }
   }

   public static boolean reserveSlot(Player2PayerResolution.ApiBillingContext billing) {
      if (billing != null && billing.billingKey() != null) {
         Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
         return MemoryExtractionBudgetTracker.checkAndRecord(billing.billingKey(), MemoryBudgetThresholds.fromConfig(cfg)) == MemoryBudgetResult.OK;
      } else {
         return false;
      }
   }
}
