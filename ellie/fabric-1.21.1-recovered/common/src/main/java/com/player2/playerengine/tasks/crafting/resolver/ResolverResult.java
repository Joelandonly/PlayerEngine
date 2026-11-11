package com.player2.playerengine.tasks.crafting.resolver;

import com.player2.playerengine.tasks.crafting.CraftMacroStep;
import com.player2.playerengine.util.ItemTarget;
import java.util.List;

public record ResolverResult(
   ResolverResult.Status status, List<CraftMacroStep> remainingSteps, List<ItemTarget> externalNeeded, int remainingDeficit, String failureReason
) {
   public enum Status {
      READY,
      NEEDS_WORK,
      UNOBTAINABLE;
   }
}
