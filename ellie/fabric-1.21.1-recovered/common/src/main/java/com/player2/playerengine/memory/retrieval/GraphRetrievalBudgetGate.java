package com.player2.playerengine.memory.retrieval;

import com.player2.playerengine.memory.budget.MemoryBudgetResult;
import com.player2.playerengine.memory.budget.MemoryExtractionBudgetGate;
import com.player2.playerengine.player2api.Player2PayerResolution;

public final class GraphRetrievalBudgetGate {
   private GraphRetrievalBudgetGate() {
   }

   public static boolean reflectionBudgetAvailable(Player2PayerResolution.ApiBillingContext ownerBilling) {
      return ownerBilling == null ? false : MemoryExtractionBudgetGate.peekCap(ownerBilling) == MemoryBudgetResult.OK;
   }
}
