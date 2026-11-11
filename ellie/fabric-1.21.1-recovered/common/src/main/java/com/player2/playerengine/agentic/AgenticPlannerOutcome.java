package com.player2.playerengine.agentic;

import java.util.List;

public record AgenticPlannerOutcome(AgenticPlanValidationResult validation, String planningSource, List<String> retrievedToolIds, String failureMessage) {
   public boolean hasExecutablePlan() {
      return this.validation != null && this.validation.valid() && this.validation.sanitizedPlan() != null;
   }
}
