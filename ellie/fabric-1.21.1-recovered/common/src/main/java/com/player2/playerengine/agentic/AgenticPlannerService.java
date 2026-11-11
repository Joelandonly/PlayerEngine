package com.player2.playerengine.agentic;
import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.PlayerEngineSettings;
import java.util.*;
import java.util.function.Consumer;
/** Explicit local resource/drop/storage planning; never requests a model, regardless of settings. */
public final class AgenticPlannerService {
 private AgenticPlannerService() {}
 public static AgenticPlannerOutcome plan(PlayerEngineController mod,AgenticGoalRequest request) {
  if(request==null||request.goalText()==null||request.goalText().isBlank())
   return new AgenticPlannerOutcome(AgenticPlanValidationResult.invalid(List.of("empty_goal")),"native",List.of(),"Goal text is required.");
  AgenticPlannerOutcome resource=ResourceAgenticIntegration.plan(mod.getModSettings(),request.goalText());
  return resource!=null?resource:tryDeterministicFallback(mod.getModSettings(),request.goalText(),List.of(),"native_unsupported");
 }
 public static void planAsync(PlayerEngineController mod,AgenticGoalRequest request,Consumer<AgenticPlannerOutcome> callback) {
  Objects.requireNonNull(callback).accept(plan(mod,request));
 }
 private static AgenticPlannerOutcome tryDeterministicFallback(PlayerEngineSettings settings,String goalText,List<String> toolIds,String reason) {
        boolean wantsLabel = settings.isAgenticEnableLabelChest() && AgenticStorageIntentDetector.looksLikeLabelGoal((String)goalText);
        AgenticPlan fallbackPlan = null;
        String source = reason;
        boolean depositGoal = AgenticStorageIntentDetector.looksLikeDepositOnlyGoal((String)goalText);
        if (depositGoal && AgenticGatherIntentDetector.looksLikeGatherDropGoal((String)goalText)) {
            fallbackPlan = AgenticFallbackPlans.gatherResolveDepositPlan((String)goalText, (PlayerEngineSettings)settings, (boolean)wantsLabel);
            source = wantsLabel ? "fallback_gather_resolve_deposit_label" : "fallback_gather_resolve_deposit";
        } else if (depositGoal) {
            fallbackPlan = AgenticFallbackPlans.resolveThenDepositPlan((String)goalText, (PlayerEngineSettings)settings, (boolean)wantsLabel);
            source = wantsLabel ? "fallback_resolve_deposit_label" : "fallback_resolve_deposit";
        } else if (AgenticStorageIntentDetector.looksLikeGatherAndStorageGoal((String)goalText)) {
            fallbackPlan = AgenticFallbackPlans.gatherThenResolvePlan((String)goalText, (PlayerEngineSettings)settings);
            source = "fallback_gather_and_storage";
        } else if (AgenticStorageIntentDetector.looksLikeStoragePrepGoal((String)goalText)) {
            fallbackPlan = AgenticFallbackPlans.resolveStorageChestPlan((String)goalText, (PlayerEngineSettings)settings);
            source = "fallback_storage";
        } else if (AgenticGatherIntentDetector.looksLikeGatherDropGoal((String)goalText)) {
            fallbackPlan = AgenticFallbackPlans.gatherLooseItemsPlan((String)goalText, (PlayerEngineSettings)settings);
            source = "fallback_gather";
        } else if (AgenticGatherIntentDetector.looksLikeMineOrGetGoal((String)goalText)) {
            return new AgenticPlannerOutcome(AgenticPlanValidationResult.invalid(List.of(reason)), reason, toolIds, AgenticPlannerService.mineGoalRedirectMessage());
        }
        if (fallbackPlan == null) {
            return new AgenticPlannerOutcome(AgenticPlanValidationResult.invalid(List.of(reason)), reason, toolIds, AgenticPlannerService.safePlanFailureMessage(goalText));
        }
        AgenticPlanValidationResult validation = AgenticPlanValidator.validate((AgenticPlan)fallbackPlan, (PlayerEngineSettings)settings);
        if (!validation.valid()) {
            return new AgenticPlannerOutcome(validation, "fallback_invalid", toolIds, AgenticPlannerService.safePlanFailureMessage(goalText));
        }
        return new AgenticPlannerOutcome(validation, source, toolIds, null);
    }

    private static String safePlanFailureMessage(String goalText) {
        return "I could not validate that command. For resources use get <item> <count> or get <count> <item>; count must be 1-"+ResourceAgenticIntegration.MAX_RESOURCE_COUNT+". Exact item names are required: stone and cobblestone differ. Loose-drop pickup and chest storage still work.";
    }

    private static String mineGoalRedirectMessage() {
        return "Resource request syntax or quantity unsupported (1-"+ResourceAgenticIntegration.MAX_RESOURCE_COUNT+"). Try 'agentic get 300 cobblestone' or 'agentic get cobblestone 300'. Use the item name, not the ore block name.";
    }
}
