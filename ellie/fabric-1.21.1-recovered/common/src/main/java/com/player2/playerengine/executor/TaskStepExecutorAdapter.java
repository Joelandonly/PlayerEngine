package com.player2.playerengine.executor;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.supervisor.CommandCompletion;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.base.TrackedTaskOutcomeProvider;
import com.player2.playerengine.util.Debug;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class TaskStepExecutorAdapter implements IStepExecutorAdapter {
   private final PlayerEngineController mod;
   private StepExecution activeExecution;
   private Task activeTask;
   private StepExecution lastCompletedExecution;
   private volatile String pendingFinishReason;
   private final TaskStepExecutorAdapter.CompletionTracker completionTracker = new TaskStepExecutorAdapter.CompletionTracker();

   public TaskStepExecutorAdapter(PlayerEngineController mod) {
      this.mod = mod;
   }

   public void armPendingChainCancel(StopReason reason) {
      if (reason != null) {
         if (this.pendingFinishReason == null) {
            if (this.activeExecution != null && this.activeExecution.getState() == StepState.RUNNING && this.mod.getUserTaskChain().isActive()) {
               this.pendingFinishReason = reason.name();
            }
         }
      }
   }

   @Override
   public StepExecution submit(String stepId, String stepKind, Task task, RollbackPolicy rollbackPolicy, Runnable onComplete) {
      Runnable completion = once(onComplete);
      if (this.rejectsReentrantTrackedSubmission()) {
         StepExecution rejected = sealStaleReentrantSubmission(stepId, stepKind, task, this.mod);
         this.lastCompletedExecution = rejected;
         completion.run();
         return rejected;
      }

      if (this.rejectsConfiguredIdleTrackedSubmission()) {
         StepExecution rejected = sealConfiguredIdleTrackedSubmission(stepId, stepKind, task, this.mod);
         this.lastCompletedExecution = rejected;
         completion.run();
         return rejected;
      }

      this.pendingFinishReason = null;
      Runnable supersededCompletion = null;
      if (this.activeExecution != null && !this.activeExecution.isTerminal()) {
         Optional<TaskStepExecutorAdapter.TerminalDecision> unreapedTerminal = terminalDecisionBeforeReplacement(this.activeTask);
         if (unreapedTerminal.isPresent()) {
            TaskStepExecutorAdapter.TerminalDecision decision = unreapedTerminal.get();
            Debug.logInternal(
               "[StepExecutorAdapter] Preserving terminal outcome for unreaped execution "
                  + this.activeExecution.getStepId()
                  + " before installing step "
                  + stepId
                  + "."
            );
            this.activeExecution.transitionTo(decision.state(), decision.detail());
         } else {
            Debug.logInternal(
               "[StepExecutorAdapter] Overwriting non-terminal execution "
                  + this.activeExecution.getStepId()
                  + " (state="
                  + this.activeExecution.getState()
                  + ") with new step "
                  + stepId
                  + ". Marking previous as FAILED."
            );
            this.activeExecution.transitionTo(StepState.FAILED, StopReason.CANCELLED_SUPERSEDED.name() + ":superseded_by_step=" + stepId);
         }

         this.lastCompletedExecution = this.activeExecution;
         supersededCompletion = this.completionTracker.capture(this.activeExecution);
      }

      StepExecution exec = new StepExecution(stepId, stepKind);
      this.activeExecution = exec;
      this.activeTask = task;
      this.completionTracker.install(exec, completion);
      if (!PlayerEngineController.inGame()) {
         exec.transitionTo(StepState.BLOCKED, StopReason.USER_ACTION_REQUIRED.name() + ":not_in_game");
         this.lastCompletedExecution = exec;
         if (supersededCompletion != null) {
            supersededCompletion.run();
         }

         this.finishCompletion(exec, completion);
         return exec;
      } else {
         exec.transitionTo(StepState.RUNNING, "submitted_to_chain");
         Runnable taskFinished = () -> {
            if (exec.isTerminal()) {
               this.finishCompletion(exec, completion);
            } else {
               String pending = this.pendingFinishReason;
               this.pendingFinishReason = null;
               if (pending != null) {
                  exec.transitionTo(StepState.FAILED, pending);
               } else {
                  TaskStepExecutorAdapter.TerminalDecision decision = mapTerminalOutcome(task);
                  exec.transitionTo(decision.state(), decision.detail());
                  if (decision.state() == StepState.FAILED && rollbackPolicy != RollbackPolicy.NONE) {
                     this.handleRollbackStub(exec, rollbackPolicy);
                  }
               }

               this.lastCompletedExecution = exec;
               boolean typedTerminalFailure = task instanceof TrackedTaskOutcomeProvider typed && typed.isTerminal() && !typed.isSuccessful();
               if (exec.getState() == StepState.FAILED && !typedTerminalFailure) {
                  String lastEntry = exec.getLastLogEntry();
                  if (lastEntry.contains(StopReason.FOLLOWED_TARGET_GONE.name())) {
                     if (this.mod.getOwner() instanceof ServerPlayer sp) {
                        sp.sendSystemMessage(Component.translatable("message.playerengine.follow.target_lost"));
                     }
                  } else if (!lastEntry.contains("CANCELLED_")) {
                     String msg = "[PlayerEngine] Step '" + exec.getStepKind() + "' stopped unexpectedly — " + lastEntry;
                     this.mod.log(msg);
                     if (this.mod.getOwner() instanceof ServerPlayer sp) {
                        sp.sendSystemMessage(
                           Component.translatable("message.playerengine.executor.step_stopped_unexpectedly", new Object[]{exec.getStepKind(), lastEntry})
                              .withStyle(ChatFormatting.RED)
                        );
                     }
                  }
               }

               this.finishCompletion(exec, completion);
            }
         };
         var chain = this.mod.getUserTaskChain();
         try {
            chain.runTaskReplacing(this.mod, task, taskFinished);
         } catch (RuntimeException failure) {
            if (!exec.isTerminal()) {
               exec.transitionTo(StepState.FAILED, StopReason.FATAL.name() + ":task_install_failed");
            }
            this.completionTracker.discard(exec);
            if (this.activeExecution == exec) {
               this.activeExecution = null;
               this.activeTask = null;
               this.pendingFinishReason = null;
               this.lastCompletedExecution = exec;
               if (chain.getCurrentTask() == task) {
                  try {
                     chain.cancel(this.mod);
                  } catch (RuntimeException cleanupFailure) {
                     if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
                  }
               }
            }
            if (supersededCompletion != null) {
               try {
                  supersededCompletion.run();
               } catch (RuntimeException callbackFailure) {
                  if (callbackFailure != failure) failure.addSuppressed(callbackFailure);
               }
            }
            throw failure;
         }
         Task retainedByChain = chain.getCurrentTask();
         TaskStepExecutorAdapter.RetentionDecision retention = retentionDecision(task, retainedByChain, exec.isTerminal());
         if (retention == TaskStepExecutorAdapter.RetentionDecision.DISPLACED) {
            exec.transitionTo(StepState.FAILED, StopReason.CANCELLED_SUPERSEDED.name() + ":displaced_during_install");
            this.lastCompletedExecution = exec;
            this.finishCompletion(exec, completion);
         }

         if (supersededCompletion != null) {
            supersededCompletion.run();
         }

         return exec;
      }
   }

   static StepExecution sealStaleReentrantSubmission(String stepId, String stepKind, Task task, PlayerEngineController mod) {
      Task checked = Objects.requireNonNull(task, "task");
      checked.controller = mod;
      checked.reset();
      checked.stop(null);
      StepExecution rejected = new StepExecution(stepId, stepKind);
      rejected.transitionTo(StepState.BLOCKED, StopReason.CANCELLED_SUPERSEDED.name() + ":stale_reentrant_submission");
      return rejected;
   }

   private static StepExecution sealConfiguredIdleTrackedSubmission(String stepId, String stepKind, Task task, PlayerEngineController mod) {
      Task checked = Objects.requireNonNull(task, "task");
      checked.controller = mod;
      checked.reset();
      checked.stop(null);
      StepExecution rejected = new StepExecution(stepId, stepKind);
      rejected.transitionTo(StepState.BLOCKED, StopReason.CANCELLED_SUPERSEDED.name() + ":configured_idle_preserved_active_task");
      return rejected;
   }

   boolean rejectsReentrantTrackedSubmission() {
      return this.mod.getUserTaskChain().rejectsReentrantTrackedSubmission();
   }

   boolean rejectsConfiguredIdleTrackedSubmission() {
      return this.mod.rejectsConfiguredIdleTrackedSubmission();
   }

   @Override
   public void cancel(String reason) {
      if (this.activeExecution != null && this.activeExecution.getState() == StepState.RUNNING) {
         String detail = reason != null && !reason.isBlank() ? ":" + reason : "";
         this.pendingFinishReason = StopReason.CANCELLED_OPERATOR.name() + detail;
         this.mod.getUserTaskChain().cancel(this.mod);
      }
   }

   @Override
   public Optional<StepExecution> getActiveExecution() {
      return Optional.ofNullable(this.activeExecution);
   }

   void seedActiveExecutionForSelfTest(StepExecution execution, Task task, Runnable completion) {
      if (execution != null && execution.getState() == StepState.RUNNING) {
         this.activeExecution = execution;
         this.activeTask = Objects.requireNonNull(task, "task");
         this.completionTracker.install(execution, once(completion));
      } else {
         throw new IllegalArgumentException("self-test active execution must be RUNNING");
      }
   }

   boolean ownsActiveSlotForSelfTest(StepExecution execution, Task task) {
      return this.activeExecution == execution && this.activeTask == task && this.completionTracker.isActive(execution);
   }

   void finishActiveCompletionForSelfTest(StepExecution execution) {
      Runnable completion = this.completionTracker.capture(execution);
      if (completion == null) {
         throw new IllegalStateException("self-test execution does not own a completion");
      }

      this.completionTracker.finish(execution, completion);
   }

   public Optional<StepExecution> getRunningExecution() {
      return runningExecution(this.activeExecution);
   }

   static Optional<StepExecution> runningExecution(StepExecution execution) {
      return execution != null && execution.getState() == StepState.RUNNING ? Optional.of(execution) : Optional.empty();
   }

   public Optional<StepExecution> getLastCompletedExecution() {
      return Optional.ofNullable(this.lastCompletedExecution);
   }

   static TaskStepExecutorAdapter.TerminalDecision mapTerminalOutcome(Task task) {
      CommandCompletion.TaskOutcome var10000 = CommandCompletion.taskOutcome(task);
      if (var10000 != null) {
         CommandCompletion.TaskOutcome var2 = var10000;
         return new TaskStepExecutorAdapter.TerminalDecision(var2.state(), var2.detail());
      }

      if (task instanceof TrackedTaskOutcomeProvider outcome) {
         if (!outcome.isTerminal()) {
            return new TaskStepExecutorAdapter.TerminalDecision(StepState.FAILED, StopReason.FATAL.name() + ":task_stopped_before_terminal_outcome");
         } else {
            return outcome.isSuccessful()
               ? new TaskStepExecutorAdapter.TerminalDecision(StepState.SUCCEEDED, "task_finished_successfully")
               : new TaskStepExecutorAdapter.TerminalDecision(StepState.FAILED, StopReason.FATAL.name() + ":" + boundedReason(outcome.controlledReason()));
         }
      } else {
         return task.isFinished()
            ? new TaskStepExecutorAdapter.TerminalDecision(StepState.SUCCEEDED, "task_finished_normally")
            : new TaskStepExecutorAdapter.TerminalDecision(StepState.FAILED, StopReason.FATAL.name() + ":task_stopped_without_finish");
      }
   }

   static Optional<TaskStepExecutorAdapter.TerminalDecision> terminalDecisionBeforeReplacement(Task task) {
      if (task instanceof TrackedTaskOutcomeProvider outcome && outcome.isTerminal()) {
         return Optional.of(mapTerminalOutcome(task));
      } else {
         return task != null && task.isFinished() ? Optional.of(mapTerminalOutcome(task)) : Optional.empty();
      }
   }

   private static String boundedReason(String reason) {
      if (reason != null && !reason.isBlank()) {
         String singleLine = reason.strip().replace('\n', ' ').replace('\r', ' ');
         return singleLine.length() <= 160 ? singleLine : singleLine.substring(0, 160);
      } else {
         return "internal_contract_failure";
      }
   }

   static Runnable once(Runnable delegate) {
      Runnable checked = Objects.requireNonNull(delegate, "delegate");
      AtomicBoolean fired = new AtomicBoolean();
      return () -> {
         if (fired.compareAndSet(false, true)) {
            checked.run();
         }
      };
   }

   static TaskStepExecutorAdapter.RetentionDecision retentionDecision(Task submitted, Task retainedByChain, boolean executionTerminal) {
      Task checked = Objects.requireNonNull(submitted, "submitted");
      if (executionTerminal) {
         return TaskStepExecutorAdapter.RetentionDecision.EXECUTION_TERMINAL;
      } else {
         return retainedByChain == checked
            ? TaskStepExecutorAdapter.RetentionDecision.SUBMITTED_INSTALLED
            : TaskStepExecutorAdapter.RetentionDecision.DISPLACED;
      }
   }

   private void finishCompletion(StepExecution exec, Runnable completion) {
      this.completionTracker.finish(exec, completion);
   }

   private void handleRollbackStub(StepExecution exec, RollbackPolicy policy) {
      Debug.logInternal(
         "[StepExecutorAdapter] Rollback stub: policy="
            + policy
            + " for step "
            + exec.getStepId()
            + " ["
            + exec.getStepKind()
            + "]. Full rollback deferred to Phase A3+."
      );
      exec.transitionTo(StepState.ROLLED_BACK, StopReason.COMPENSATABLE.name() + ":rollback_stub_" + policy.name().toLowerCase());
   }

   static final class CompletionTracker {
      private Object activeToken;
      private Runnable activeCompletion;

      void install(Object token, Runnable completion) {
         this.activeToken = Objects.requireNonNull(token, "token");
         this.activeCompletion = Objects.requireNonNull(completion, "completion");
      }

      Runnable capture(Object token) {
         return this.activeToken == token ? this.activeCompletion : null;
      }

      void finish(Object token, Runnable completion) {
         this.discard(token);
         Objects.requireNonNull(completion, "completion").run();
      }

      void discard(Object token) {
         if (this.activeToken == token) {
            this.activeToken = null;
            this.activeCompletion = null;
         }
      }

      boolean isActive(Object token) {
         return this.activeToken == token && this.activeCompletion != null;
      }
   }

   enum RetentionDecision {
      SUBMITTED_INSTALLED,
      DISPLACED,
      EXECUTION_TERMINAL;
   }

   record TerminalDecision(StepState state, String detail) {
   }
}
