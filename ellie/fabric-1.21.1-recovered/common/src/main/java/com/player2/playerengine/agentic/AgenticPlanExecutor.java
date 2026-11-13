package com.player2.playerengine.agentic;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.agentic.elliegps.WaypointAutoRegistrar;
import com.player2.playerengine.agentic.steps.SmeltStepFactory;
import com.player2.playerengine.executor.RollbackPolicy;
import com.player2.playerengine.progression.CompanionProgression;
import com.player2.playerengine.supervisor.CoreGoalSupervisor;
import com.player2.playerengine.supervisor.GoalLifecycle;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.farming.FarmFeedback;
import com.player2.playerengine.tasks.farming.FarmTaskOutcome;
import com.player2.playerengine.tasks.farming.HarvestFarmOutcome;
import com.player2.playerengine.tasks.farming.HarvestFarmTask;
import com.player2.playerengine.tasks.farming.PlantFarmFeedback;
import com.player2.playerengine.tasks.farming.PlantFarmOutcome;
import com.player2.playerengine.tasks.farming.PlantFarmTask;
import com.player2.playerengine.tasks.farming.SetupFarmTask;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class AgenticPlanExecutor {
   private static final Logger LOGGER = LogManager.getLogger(AgenticPlanExecutor.class);
   private static final AgenticStepFactoryRegistry REGISTRY = new AgenticStepFactoryRegistry();

   private AgenticPlanExecutor() {
   }

   public static AgenticRunHandle start(
      AgenticGoalRequest request, AgenticPlan plan, PlayerEngineController controller, String planningSource, BiConsumer<Boolean, String> onTerminal
   ) {
      return startWithOutcome(request, plan, controller, planningSource,
         onTerminal == null ? null : (status, message) -> onTerminal.accept(status == GoalLifecycle.Status.SUCCEEDED, message));
   }

   public static AgenticRunHandle startWithOutcome(
      AgenticGoalRequest request, AgenticPlan plan, PlayerEngineController controller, String planningSource,
      BiConsumer<GoalLifecycle.Status, String> onTerminal
   ) {
      String runId = UUID.randomUUID().toString().substring(0, 8);
      AgenticRunRegistry.AgenticRunState state = new AgenticRunRegistry.AgenticRunState(runId, plan.goalSummary(), planningSource);
      UUID botUuid = controller.getEntity().getUUID();
      AgenticRunRegistry.register(botUuid, state);
      AgenticExecutionContext context = new AgenticExecutionContext(controller, state);
      preSeedReservations(plan, controller, context);
      runStep(0, plan, controller, context, onTerminal);
      return new AgenticRunHandle(runId, state);
   }

   private static void preSeedReservations(AgenticPlan plan, PlayerEngineController controller, AgenticExecutionContext context) {
      MaterialReservationService ledger = context.reservations();

      for (AgenticStepSpec step : plan.steps()) {
         if (step != null && step.kind() != null && "smelt_items".equals(step.kind())) {
            SmeltStepFactory.forwardReservation(step, context).ifPresent(res -> ledger.reserve(controller, res.input(), res.count()));
         }
      }
   }

   private static void releaseOwnForwardReservation(int index, AgenticPlan plan, AgenticExecutionContext context) {
      AgenticStepSpec step = plan.steps().get(index);
      if ("smelt_items".equals(step.kind())) {
         SmeltStepFactory.forwardReservation(step, context).ifPresent(res -> context.reservations().release(res.input(), res.count()));
      }
   }

   private static void runStep(int index, AgenticPlan plan, PlayerEngineController mod, AgenticExecutionContext context, BiConsumer<GoalLifecycle.Status, String> onTerminal) {
      if (index >= plan.steps().size()) {
         String summary = AgenticDegradationSummary.forModel(context.runState());
         context.runState().terminal("succeeded", "Plan complete.");
         LOGGER.info("[Agentic] run {} succeeded", context.runState().toSnapshot().runId());
         context.memory().clearMaterialReservations();
         finishOnServer(mod, context, onTerminal, GoalLifecycle.Status.SUCCEEDED, summary);
      } else {
         AgenticStepSpec step = plan.steps().get(index);
         context.runState().setActiveStep(index, step.kind());
         releaseOwnForwardReservation(index, plan, context);
         Optional<Task> taskOpt = REGISTRY.createTask(step, context);
         if (taskOpt.isEmpty()) {
            String message = "Unknown or unsupported step: " + step.kind();
            context.runState().terminal("failed", message);
            LOGGER.warn("[Agentic] unknown step kind {}", step.kind());
            context.memory().clearMaterialReservations();
            finishOnServer(mod, context, onTerminal, GoalLifecycle.Status.FAILED, message);
         } else {
            Task task = taskOpt.get();
            task.trackProgression(true);
            mod.runUserTaskTracked(step.id(), step.kind(), task, RollbackPolicy.NONE, () -> {
               // The supervisor calls back only for the current, terminal goal generation.
               GoalLifecycle.Snapshot receipt = CoreGoalSupervisor.snapshot(mod);
               GoalLifecycle.Status status = receipt == null ? GoalLifecycle.Status.FAILED : receipt.status();
               if (status == GoalLifecycle.Status.CANCELLED) {
                  String message = "Agentic goal stopped during " + step.kind() + ".";
                  context.runState().terminal("cancelled", message);
                  context.memory().clearMaterialReservations();
                  finishOnServer(mod, context, onTerminal, status, message);
                  return;
               }

               if (status == GoalLifecycle.Status.SUCCEEDED) {
                  var reward = CompanionProgression.successfulReward(task);
                  if (reward != null) context.progressionRewards.add(reward);
                  if (task instanceof SetupFarmTask farmTask) {
                     FarmTaskOutcome outcome = farmTask.outcome();
                     String modelFeedback = FarmFeedback.setupModel(outcome);
                     context.runState().setFarmProgress(modelFeedback);
                     mod.reportAgenticProgress(FarmFeedback.setupPlayer(outcome), true);
                  } else if (task instanceof HarvestFarmTask harvestTask) {
                     HarvestFarmOutcome outcome = harvestTask.outcome();
                     String modelFeedback = FarmFeedback.harvestModel(outcome);
                     context.runState().setFarmProgress(modelFeedback);
                     mod.reportAgenticProgress(FarmFeedback.harvestPlayer(outcome), true);
                  } else if (task instanceof PlantFarmTask plantTask) {
                     PlantFarmOutcome outcome = plantTask.outcome();
                     String modelFeedback = PlantFarmFeedback.model(outcome);
                     context.runState().setFarmProgress(modelFeedback);
                     mod.reportAgenticProgress(PlantFarmFeedback.player(outcome), true);
                  }

                  if ("deposit_items".equals(step.kind())) {
                     WaypointAutoRegistrar.afterDeposit(context, mod);
                  }

                  runStep(index + 1, plan, mod, context, onTerminal);
               } else {
                  String reason = receipt == null ? "" : receipt.detail().replaceFirst("^\\[[^\\]]+\\]\\s*", "");
                  if (reason.isBlank()) reason = context.runState().progressForKind(step.kind());
                  String message;
                  if (task instanceof SetupFarmTask farmTask) {
                     FarmTaskOutcome outcome = farmTask.outcome();
                     message = FarmFeedback.setupModel(outcome);
                     mod.reportAgenticProgress(FarmFeedback.setupPlayer(outcome), true);
                  } else if (task instanceof HarvestFarmTask harvestTask) {
                     HarvestFarmOutcome outcome = harvestTask.outcome();
                     message = FarmFeedback.harvestModel(outcome);
                     mod.reportAgenticProgress(FarmFeedback.harvestPlayer(outcome), true);
                  } else if (task instanceof PlantFarmTask plantTask) {
                     PlantFarmOutcome outcome = plantTask.outcome();
                     message = PlantFarmFeedback.model(outcome);
                     mod.reportAgenticProgress(PlantFarmFeedback.player(outcome), true);
                  } else {
                     message = reason != null && !reason.isBlank() ? step.kind() + ": " + reason : "Step failed: " + step.kind();
                     mod.reportAgenticProgress(message, true);
                  }

                  context.runState().terminal(status == GoalLifecycle.Status.BLOCKED ? "blocked" : "failed", message);
                  LOGGER.warn("[Agentic] step {} failed: {}", step.kind(), message);
                  context.memory().clearMaterialReservations();
                  finishOnServer(mod, context, onTerminal, status, message);
               }
            });
         }
      }
   }

   private static void finishOnServer(PlayerEngineController mod, AgenticExecutionContext context,
      BiConsumer<GoalLifecycle.Status, String> onTerminal, GoalLifecycle.Status status, String message) {
      UUID botUuid = mod.getEntity().getUUID();
      Runnable wrapped = () -> {
         if (status == GoalLifecycle.Status.SUCCEEDED && AgenticRunRegistry.get(botUuid).orElse(null) == context.runState()) {
            mod.progression().award(context.progressionRewards, "Completed plan");
         }
         AgenticRunRegistry.clear(botUuid, context.runState());
         if (onTerminal != null) {
            onTerminal.accept(status, message);
         }
      };
      if (mod.getPlayer().getServer() != null) {
         mod.getPlayer().getServer().execute(wrapped);
      } else {
         wrapped.run();
      }
   }
}
