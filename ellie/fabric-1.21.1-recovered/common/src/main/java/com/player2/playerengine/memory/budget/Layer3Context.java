package com.player2.playerengine.memory.budget;

import com.player2.playerengine.player2api.Player2PayerResolution;

public record Layer3Context(Player2PayerResolution.ApiBillingContext ownerBilling, MemoryGateDecision gateDecision, boolean layer3Enabled) {
   public static Layer3Context disabled() {
      return new Layer3Context(null, null, false);
   }

   public static Layer3Context fromGate(Player2PayerResolution.ApiBillingContext ownerBilling, MemoryGateDecision gateDecision) {
      boolean enabled = gateDecision != null && gateDecision.allowed() && ownerBilling != null && ownerBilling.billingKey() != null;
      return !enabled ? disabled() : new Layer3Context(ownerBilling, gateDecision, true);
   }
}
