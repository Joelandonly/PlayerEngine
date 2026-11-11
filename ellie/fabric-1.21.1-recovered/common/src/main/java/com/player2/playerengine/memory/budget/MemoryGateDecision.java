package com.player2.playerengine.memory.budget;

public record MemoryGateDecision(boolean allowed, MemoryGateDecision.SkipReason reason) {
   public static MemoryGateDecision allow() {
      return new MemoryGateDecision(true, MemoryGateDecision.SkipReason.NONE);
   }

   public static MemoryGateDecision skip(MemoryGateDecision.SkipReason reason) {
      return new MemoryGateDecision(false, reason);
   }

   public String playerMessage() {
      return switch (this.reason) {
         case NONE, MEMORY_DISABLED, CLIENT_PROXY_UNSUPPORTED -> "";
         case NO_BILLING, NOT_PATRON -> "Long-term memory is a Patron feature.";
         case BUDGET_HARD_SKIP, BUDGET_SOFT_SKIP, EXTRACTION_CAP_SKIP -> "Long-term memory is paused for now (budget reached).";
      };
   }

   public String modelFeedbackToken() {
      return switch (this.reason) {
         case NONE -> "";
         case MEMORY_DISABLED -> "memory_unavailable:disabled";
         case CLIENT_PROXY_UNSUPPORTED -> "memory_unavailable:proxy_mode";
         case NO_BILLING -> "memory_unavailable:no_billing";
         case NOT_PATRON -> "memory_unavailable:not_patron";
         case BUDGET_HARD_SKIP -> "memory_unavailable:budget_hard";
         case BUDGET_SOFT_SKIP -> "memory_unavailable:budget_soft";
         case EXTRACTION_CAP_SKIP -> "memory_unavailable:rate_capped";
      };
   }

   public enum SkipReason {
      NONE,
      MEMORY_DISABLED,
      CLIENT_PROXY_UNSUPPORTED,
      NO_BILLING,
      NOT_PATRON,
      BUDGET_HARD_SKIP,
      BUDGET_SOFT_SKIP,
      EXTRACTION_CAP_SKIP;
   }
}
