package com.player2.playerengine.tasks.base;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.observatory.ObservatoryTelemetry;
import com.player2.playerengine.progression.TaskWork;
import com.player2.playerengine.reliability.SharedTargetRecovery;
import com.player2.playerengine.tasks.movement.TimeoutWanderTask;
import com.player2.playerengine.util.Debug;
import java.util.Objects;
import java.util.function.Predicate;

public abstract class Task {
   public PlayerEngineController controller;
   private String oldDebugState = "";
   private String debugState = "";
   private Task sub = null;
   private boolean first = true;
   private boolean stopped = false;
   private boolean ellieStopInProgress = false;
   private boolean active = false;
   private boolean assigned = false;
   private boolean transientlySuspended = false;
   private Task sharedRecoveryAction;
   private boolean progressionStarted;
   private boolean progressionTracked;
   private boolean progressionDeferred;
   private TaskWork progressionWork;

   public void trackProgression(boolean deferred) {
      this.progressionTracked = true;
      this.progressionDeferred = deferred;
   }

   public boolean progressionTracked() {
      return this.progressionTracked;
   }

   public boolean progressionDeferred() {
      return this.progressionDeferred;
   }

   public void beginProgressionAttempt() {
      this.progressionStarted = false;
      this.progressionWork = null;
   }

   public TaskWork progressionWork() {
      if (this.progressionWork == null) this.progressionWork = new TaskWork();
      return this.progressionWork;
   }

   public TaskWork takeProgressionWork() {
      if (!this.progressionStarted) return null;
      this.progressionStarted = false;
      TaskWork work = this.progressionWork();
      this.progressionWork = null;
      return work;
   }

   public void tick(TaskChain parentChain) {
      this.controller = parentChain.controller;
      parentChain.addTaskToChain(this);
      if (!SharedTargetRecovery.beforeTask(this, parentChain)) {
         SharedTargetRecovery.enter(this);
         if (this.first) {
            Debug.logInternal("Task START: " + this);
            this.active = true;
            this.assigned = false;
            this.progressionStarted = true;
            this.onStart();
            this.first = false;
            this.stopped = false;
         }

         if (!this.stopped) {
            SharedTargetRecovery.enter(this);
            Task newSub = SharedTargetRecovery.after(this, this.onTick());
            if (!this.oldDebugState.equals(this.debugState)) {
               Debug.logInternal(this.toString());
               this.oldDebugState = this.debugState;
            }

            if (newSub != null) {
               if (this.sub == null || this.sub.stopped() || !newSub.isEqual(this.sub) && this.canBeInterrupted(this.sub, newSub)) {
                  if (this.sub != null && !this.sub.stopped()) {
                     this.sub.stop(newSub);
                  }

                  this.sub = newSub;
               }

               this.sub.tick(parentChain);
            } else if (this.sub != null && (this.sub.stopped() || this.canBeInterrupted(this.sub, null))) {
               if (!this.sub.stopped()) {
                  this.sub.stop();
               }

               this.sub = null;
            }
         }
      }
   }

   public void reset() {
      this.first = true;
      this.active = false;
      this.stopped = false;
      this.assigned = true;
      this.transientlySuspended = false;
      this.ellieStopInProgress = false;
   }

   public void stop() {
      this.stop(null);
   }

   public void stop(Task interruptTask) {
      if (!this.ellieStopInProgress) {
         this.ellieStopInProgress = true;

         try {
            com.player2.playerengine.agentic.AutoInventoryCleanup.cancel(this);
            SharedTargetRecovery.stopped(this);
            if ((this.active || this.assigned) && !this.stopped) {
               Debug.logInternal("Task STOP: " + this + ", interrupted by " + interruptTask);
               if ((this.transientlySuspended || this.assigned) && this instanceof TransientlyResumableTask resumable) {
                  resumable.onTransientResumeAbandoned();
               } else if (!this.first) {
                  this.onStop(interruptTask);
               }

               if (this.sub != null && !this.sub.stopped()) {
                  this.sub.stop(interruptTask);
               }

               if (this instanceof TransientlyResumableTask resumable) {
                  resumable.afterChildrenStopped();
               }

               this.first = true;
               this.active = false;
               this.assigned = false;
               this.stopped = true;
               this.transientlySuspended = false;
            }

            this.ellieStopInProgress = false;
         } finally {
            this.ellieStopInProgress = false;
         }
      }
   }

   public void fail(String reason) {
      ObservatoryTelemetry.taskFailure(this, reason);
      this.stop();
      Debug.logMessage("Task FAILED: " + reason);
   }

   public void interrupt(Task interruptTask) {
      this.interrupt(interruptTask, TaskSuspensionCause.HIGHER_PRIORITY_CHAIN);
   }

   public void interrupt(Task interruptTask, TaskSuspensionCause cause) {
      if (this.active) {
         boolean checkpointDeclined = false;
         boolean resumableCandidate = false;
         if (!this.first) {
            boolean prepared = false;
            resumableCandidate = !this.isFinished() && this instanceof TransientlyResumableTask;
            if (resumableCandidate) {
               TransientlyResumableTask resumable = (TransientlyResumableTask)this;
               prepared = resumable.prepareForTransientResume(Objects.requireNonNull(cause, "cause"));
            }

            this.transientlySuspended = prepared && !this.isFinished();
            if (!this.transientlySuspended && !resumableCandidate) {
               this.onStop(interruptTask);
            }

            checkpointDeclined = resumableCandidate && !this.transientlySuspended;
         }

         if (this.sub != null && !this.sub.stopped()) {
            this.sub.interrupt(interruptTask, cause);
         }

         if (this.transientlySuspended || checkpointDeclined) {
            if (this.sub != null && !this.sub.stopped()) {
               this.sub.stop(interruptTask);
            }

            this.sub = null;
         }

         if (checkpointDeclined) {
            this.onStop(interruptTask);
         }

         if (this instanceof TransientlyResumableTask resumable) {
            resumable.afterChildrenStopped();
         }

         this.first = true;
         if (checkpointDeclined) {
            this.active = false;
            this.assigned = false;
            this.stopped = true;
         }
      }
   }

   public boolean isTransientlySuspended() {
      return this.transientlySuspended;
   }

   public void abandonTransientResume() {
      if (this.transientlySuspended) {
         this.stop(null);
      }
   }

   protected void setDebugState(String state) {
      ObservatoryTelemetry.taskState(this, state);
      if (state == null) {
         state = "";
      }

      this.debugState = state;
   }

   public boolean isFinished() {
      return false;
   }

   public boolean isActive() {
      return this.active;
   }

   public boolean isAssigned() {
      return this.assigned;
   }

   public boolean stopped() {
      return this.stopped;
   }

   protected Task getSub() {
      return this.sub;
   }

   protected abstract void onStart();

   protected abstract Task onTick();

   protected abstract void onStop(Task var1);

   protected abstract boolean isEqual(Task var1);

   protected abstract String toDebugString();

   @Override
   public String toString() {
      return "<" + this.toDebugString() + "> " + this.debugState;
   }

   @Override
   public boolean equals(Object obj) {
      return obj instanceof Task task ? this.isEqual(task) : false;
   }

   public boolean thisOrChildSatisfies(Predicate<Task> pred) {
      for (Task t = this; t != null; t = t.sub) {
         if (pred.test(t)) {
            return true;
         }
      }

      return false;
   }

   public boolean containsTask(Predicate<Task> pred) {
      return this.thisOrChildSatisfies(pred);
   }

   public boolean thisOrChildAreTimedOut() {
      return this.thisOrChildSatisfies(task -> task instanceof TimeoutWanderTask);
   }

   private boolean canBeInterrupted(Task subTask, Task toInterruptWith) {
      return subTask == null ? true : subTask.thisOrChildSatisfies(task -> {
         if (task instanceof ITaskCanForce canForce) {
            if (toInterruptWith != null && toInterruptWith.controller == null) {
               toInterruptWith.controller = this.controller;
            }

            return !canForce.shouldForce(toInterruptWith);
         } else {
            return true;
         }
      });
   }

   public String getTaskTree() {
      StringBuilder builder = new StringBuilder("Main task:\n");
      Task cur = this;

      while (cur != null) {
         builder.append(cur.toDebugString());
         cur = cur.sub;
         if (cur != null) {
            builder.append("\nFor that doing:\n");
         }
      }

      return builder.toString();
   }
}
