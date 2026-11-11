package com.player2.playerengine.chains;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.agentic.GatherLooseItemsTask;
import com.player2.playerengine.tasks.agentic.MineBlockTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.base.TaskRunner;
import com.player2.playerengine.tasks.base.TaskSuspensionCause;
import com.player2.playerengine.tasks.base.TransientlyResumableTask;
import com.player2.playerengine.tasks.movement.BodyLanguageTask;
import com.player2.playerengine.tasks.movement.FollowPlayerTask;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.util.time.Stopwatch;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UserTaskChain extends SingleTaskChain {
   private static final Logger LOGGER = LogManager.getLogger();
   private final Stopwatch taskStopwatch = new Stopwatch();
   private final UserTaskChain.RuntimeMode runtimeMode;
   private Runnable currentOnFinish = null;
   private boolean runningIdleTask;
   private boolean installingIdleCommandTask;
   private Task suspendedTask = null;
   private Runnable suspendedOnFinish = null;
   private boolean suspendedTaskResumePrepared = false;
   private boolean pendingGestureResumeOwnership = false;
   private boolean resumingSuspended = false;
   private boolean firingReplacedTerminal = false;

   public UserTaskChain(TaskRunner runner) {
      this(runner, UserTaskChain.RuntimeMode.LIVE);
   }

   UserTaskChain(TaskRunner runner, UserTaskChain.RuntimeMode runtimeMode) {
      super(runner);
      this.runtimeMode = Objects.requireNonNull(runtimeMode, "runtimeMode");
   }

   private static boolean isResumeSafe(Task task) {
      return task instanceof TransientlyResumableTask
         || task instanceof ResourceTask
         || task instanceof FollowPlayerTask
         || task instanceof MineBlockTask
         || task instanceof GatherLooseItemsTask;
   }

   private void abandonAndFinishSuspended(Task task, Runnable onFinish) {
      if (task != null && !task.stopped()) {
         if (task.isTransientlySuspended()) {
            task.abandonTransientResume();
         } else {
            task.stop(null);
         }
      }

      if (onFinish != null) {
         boolean alreadyFiring = this.firingReplacedTerminal;
         this.firingReplacedTerminal = true;

         try {
            onFinish.run();
         } finally {
            this.firingReplacedTerminal = alreadyFiring;
         }
      }
   }

   private static Runnable once(Runnable delegate) {
      if (delegate == null) {
         return null;
      }

      AtomicBoolean fired = new AtomicBoolean();
      return () -> {
         if (fired.compareAndSet(false, true)) {
            delegate.run();
         }
      };
   }

   private static String prettyPrintTimeDuration(double seconds) {
      int minutes = (int)(seconds / 60.0);
      int hours = minutes / 60;
      int days = hours / 24;
      String result = "";
      if (days != 0) {
         result = result + result + " days ";
      }

      if (hours != 0) {
         result = result + result + " hours ";
      }

      if (minutes != 0) {
         result = result + result + " minutes ";
      }

      if (!result.isEmpty()) {
         result = result + "and ";
      }

      return result + result;
   }

   @Override
   protected void onTick() {
      if (this.runtimeMode == UserTaskChain.RuntimeMode.DETACHED_SELF_TEST || PlayerEngineController.inGame()) {
         super.onTick();
      }
   }

   public void cancel(PlayerEngineController mod) {
      Task abandonedTask = this.suspendedTask;
      Runnable abandonedOnFinish = this.suspendedOnFinish;
      this.suspendedTask = null;
      this.suspendedOnFinish = null;
      this.suspendedTaskResumePrepared = false;
      if (this.mainTask != null) {
         this.stop();
         this.onTaskFinish(mod);
      }

      this.abandonAndFinishSuspended(abandonedTask, abandonedOnFinish);
   }

   @Override
   public float getPriority() {
      return 50.0F;
   }

   @Override
   public String getName() {
      return "User Tasks";
   }

   public void runTask(PlayerEngineController mod, Task task, Runnable onFinish) {
      if (this.installingIdleCommandTask) {
         this.runIdleTask(mod, task, onFinish);
      } else {
         this.runTaskInternal(mod, task, onFinish, false);
      }
   }

   public void runTaskReplacing(PlayerEngineController mod, Task task, Runnable onFinish) {
      if (this.installingIdleCommandTask) {
         this.runIdleTask(mod, task, onFinish);
      } else {
         this.runTaskInternal(mod, task, onFinish, true);
      }
   }

   public void runIdleTask(PlayerEngineController mod, Task task, Runnable onFinish) {
      Task incomingIdle = Objects.requireNonNull(task, "task");
      Runnable incomingOnFinish = once(onFinish);
      if ((!this.firingReplacedTerminal || this.resumingSuspended) && !this.hasProtectedNonIdleOwnership()) {
         Task replacedIdle = this.mainTask;
         Runnable replacedIdleOnFinish = replacedIdle == null ? null : this.currentOnFinish;
         this.currentOnFinish = incomingOnFinish;
         this.runningIdleTask = true;
         this.taskStopwatch.begin();
         this.replaceTask(incomingIdle);
         if (this.runtimeMode == UserTaskChain.RuntimeMode.LIVE) {
            mod.getTaskRunner().enable();
            if (mod.getModSettings().failedToLoad()) {
               Debug.logWarning("Settings file failed to load at some point. Check logs for more info, or delete the file to re-load working settings.");
            }
         }

         if (replacedIdle != null) {
            this.abandonAndFinishSuspended(replacedIdle, replacedIdleOnFinish);
         }
      } else {
         this.rejectPolicyIdleSubmission(mod, incomingIdle, incomingOnFinish);
      }
   }

   private void rejectPolicyIdleSubmission(PlayerEngineController mod, Task incomingIdle, Runnable incomingOnFinish) {
      incomingIdle.controller = mod;
      incomingIdle.reset();
      this.abandonAndFinishSuspended(incomingIdle, incomingOnFinish);
   }

   public void runIdleCommand(Runnable commandInstaller) {
      Runnable checked = Objects.requireNonNull(commandInstaller, "commandInstaller");
      boolean previous = this.installingIdleCommandTask;
      this.installingIdleCommandTask = true;

      try {
         checked.run();
      } finally {
         this.installingIdleCommandTask = previous;
      }
   }

   public boolean rejectsReentrantTrackedSubmission() {
      return this.firingReplacedTerminal && !this.resumingSuspended;
   }

   public boolean rejectsConfiguredIdleTrackedSubmission() {
      return this.installingIdleCommandTask && this.hasProtectedNonIdleOwnership();
   }

   private boolean hasProtectedNonIdleOwnership() {
      return this.pendingGestureResumeOwnership || this.suspendedTask != null || this.mainTask != null && !this.runningIdleTask;
   }

   public boolean clearPolicyIdleTask() {
      if (this.firingReplacedTerminal && !this.resumingSuspended) {
         return false;
      } else if (this.mainTask != null && this.runningIdleTask) {
         Task idleTask = this.mainTask;
         Runnable idleOnFinish = this.currentOnFinish;
         this.mainTask = null;
         this.currentOnFinish = null;
         this.runningIdleTask = false;
         this.abandonAndFinishSuspended(idleTask, idleOnFinish);
         return true;
      } else {
         return false;
      }
   }

   private void runTaskInternal(PlayerEngineController mod, Task task, Runnable onFinish, boolean forceReplace) {
      if (this.firingReplacedTerminal && !this.resumingSuspended) {
         task.controller = mod;
         task.reset();
         task.stop(null);
         Runnable rejectedOnFinish = once(onFinish);
         if (rejectedOnFinish != null) {
            rejectedOnFinish.run();
         }
      } else {
         this.runningIdleTask = false;
         Debug.logMessage("User Task Set: " + task.toString());
         boolean incomingIsFollow = task instanceof FollowPlayerTask;
         boolean incomingIsGesture = task instanceof BodyLanguageTask;
         Task replacedGestureTask = this.mainTask instanceof BodyLanguageTask && !this.mainTask.stopped() ? this.mainTask : null;
         boolean existingIsFollow = this.mainTask instanceof FollowPlayerTask && !this.mainTask.stopped();
         Task finishedExistingTask = this.mainTask != null && this.mainTask.isFinished() && !(this.mainTask instanceof BodyLanguageTask) ? this.mainTask : null;
         boolean existingIsActive = this.mainTask != null
            && (this.mainTask.isActive() || this.mainTask.isAssigned())
            && !this.mainTask.stopped()
            && !this.mainTask.isFinished()
            && !(this.mainTask instanceof BodyLanguageTask);
         Task existingTask = existingIsActive ? this.mainTask : null;
         boolean installAsTransientOverlay = false;
         Task droppedSuspendedTask = null;
         Runnable droppedSuspendedOnFinish = null;
         if (!this.resumingSuspended && !this.runningIdleTask) {
            if (incomingIsGesture && existingTask != null) {
               this.suspendedTask = existingTask;
               this.suspendedOnFinish = this.currentOnFinish;
               boolean willResume = isResumeSafe(existingTask);
               if (willResume && existingTask instanceof TransientlyResumableTask) {
                  if (existingTask.isActive()) {
                     existingTask.interrupt(task, TaskSuspensionCause.GESTURE_OVERLAY);
                     willResume = existingTask.isTransientlySuspended();
                  } else {
                     willResume = existingTask.isAssigned();
                  }

                  installAsTransientOverlay = willResume;
               } else if (willResume && existingTask.isAssigned()) {
                  installAsTransientOverlay = true;
               }

               this.suspendedTaskResumePrepared = willResume;
               if (existingIsFollow) {
                  LOGGER.info(
                     "[FollowDiag] FOLLOW-SUSPEND: active follow '{}' suspended by transient gesture '{}' (will resume on finish)",
                     existingTask.toString(),
                     task.toString()
                  );
               } else if (willResume) {
                  LOGGER.info(
                     "[FollowDiag] TASK-SUSPEND: resume-safe task '{}' suspended by transient gesture '{}' (will resume on finish)",
                     existingTask.toString(),
                     task.toString()
                  );
               } else {
                  LOGGER.info(
                     "[FollowDiag] TASK-DROP: non-resume-safe task '{}' dropped by transient gesture '{}' (will NOT resume; step resolved fail-clean on finish)",
                     existingTask.toString(),
                     task.toString()
                  );
               }
            } else if (!incomingIsGesture) {
               if (this.suspendedTask != null) {
                  if (this.suspendedTask instanceof FollowPlayerTask) {
                     LOGGER.info("[FollowDiag] FOLLOW-SUSPEND-CLEAR: genuine task '{}' replaces follow; suspended follow discarded", task.toString());
                  } else {
                     LOGGER.info(
                        "[FollowDiag] TASK-SUSPEND-CLEAR: genuine task '{}' replaces suspended task '{}'; discarded",
                        task.toString(),
                        this.suspendedTask.toString()
                     );
                  }
               }

               droppedSuspendedTask = this.suspendedTask;
               droppedSuspendedOnFinish = this.suspendedOnFinish;
               this.suspendedTask = null;
               this.suspendedOnFinish = null;
               this.suspendedTaskResumePrepared = false;
            }
         }

         Runnable replacedActiveOnFinish = null;
         if (existingTask != null && !this.runningIdleTask && !this.resumingSuspended && !incomingIsGesture && !this.firingReplacedTerminal) {
            replacedActiveOnFinish = this.currentOnFinish;
         }

         Runnable finishedExistingOnFinish = null;
         if (finishedExistingTask != null && !this.runningIdleTask && !this.resumingSuspended && !this.firingReplacedTerminal) {
            finishedExistingOnFinish = this.currentOnFinish;
         }

         Runnable replacedGestureOnFinish = null;
         if (replacedGestureTask != null && !this.runningIdleTask && !this.resumingSuspended && !this.firingReplacedTerminal) {
            replacedGestureOnFinish = this.currentOnFinish;
         }

         this.currentOnFinish = once(onFinish);
         if (existingIsFollow && !this.runningIdleTask && (!incomingIsGesture || this.suspendedTask == null)) {
            LOGGER.info(
               "[FollowDiag] FOLLOW-OVERWRITE: active follow '{}' being replaced by '{}' (incoming isFollow={})",
               this.mainTask.toString(),
               task.toString(),
               incomingIsFollow
            );
         }

         if (incomingIsFollow && !this.runningIdleTask) {
            LOGGER.info("[FollowDiag] FOLLOW-START: FollowPlayerTask assigned to UserTaskChain (existingFollowActive={})", existingIsFollow);
         }

         this.taskStopwatch.begin();
         if (installAsTransientOverlay) {
            this.replaceTaskAfterTransientSuspend(task);
         } else if (!forceReplace && replacedGestureTask == null) {
            this.setTask(task);
         } else {
            this.replaceTask(task);
         }

         if (this.runtimeMode == UserTaskChain.RuntimeMode.LIVE) {
            mod.getTaskRunner().enable();
            if (mod.getModSettings().failedToLoad()) {
               Debug.logWarning("Settings file failed to load at some point. Check logs for more info, or delete the file to re-load working settings.");
            }
         }

         if (droppedSuspendedTask != null) {
            LOGGER.info(
               "[FollowDiag] SUSPEND-DROP-FINISH: genuine task '{}' discards a suspended task; firing its stashed onFinish to resolve the orphaned step (no resume)",
               task.toString()
            );
            this.abandonAndFinishSuspended(droppedSuspendedTask, droppedSuspendedOnFinish);
         }

         if (replacedActiveOnFinish != null) {
            LOGGER.info(
               "[FollowDiag] ACTIVE-REPLACE-FINISH: genuine task '{}' directly replaces active task '{}'; firing the replaced task's onFinish to resolve its step (no resume)",
               task.toString(),
               existingTask.toString()
            );
            this.firingReplacedTerminal = true;

            try {
               replacedActiveOnFinish.run();
            } finally {
               this.firingReplacedTerminal = false;
            }
         }

         if (finishedExistingOnFinish != null) {
            LOGGER.info(
               "[FollowDiag] ACTIVE-FINISH-DURING-INSTALL: task '{}' reached terminal state before chain reap; firing its onFinish exactly once after installing '{}'",
               finishedExistingTask.toString(),
               task.toString()
            );
            this.abandonAndFinishSuspended(finishedExistingTask, finishedExistingOnFinish);
         }

         if (replacedGestureOnFinish != null) {
            LOGGER.info(
               "[FollowDiag] GESTURE-REPLACE-FINISH: gesture '{}' replaced by '{}'; firing the old gesture callback exactly once",
               replacedGestureTask.toString(),
               task.toString()
            );
            this.abandonAndFinishSuspended(replacedGestureTask, replacedGestureOnFinish);
         }
      }
   }

   @Override
   protected void onTaskFinish(PlayerEngineController mod) {
      boolean shouldIdle = this.runtimeMode == UserTaskChain.RuntimeMode.LIVE && mod.getModSettings().shouldRunIdleCommandWhenNotActive();
      double seconds = this.taskStopwatch.time();
      Task oldTask = this.mainTask;
      Task resumeTask = null;
      Runnable resumeOnFinish = null;
      boolean resumePrepared = false;
      if (oldTask instanceof BodyLanguageTask && this.suspendedTask != null && !this.runningIdleTask && !this.resumingSuspended) {
         resumeTask = this.suspendedTask;
         resumeOnFinish = this.suspendedOnFinish;
         resumePrepared = this.suspendedTaskResumePrepared;
      }

      boolean previousPendingGestureResumeOwnership = this.pendingGestureResumeOwnership;
      this.pendingGestureResumeOwnership = previousPendingGestureResumeOwnership || resumeTask != null;

      try {
         this.suspendedTask = null;
         this.suspendedOnFinish = null;
         this.suspendedTaskResumePrepared = false;
         if (oldTask instanceof FollowPlayerTask && !this.runningIdleTask) {
            LOGGER.info(
               "[FollowDiag] FOLLOW-FINISH: FollowPlayerTask ended after {}s; task.isFinished={} task.stopped={} shouldIdle={}",
               String.format("%.1f", seconds),
               oldTask.isFinished(),
               oldTask.stopped(),
               shouldIdle
            );
         }

         this.mainTask = null;
         if (this.runtimeMode == UserTaskChain.RuntimeMode.LIVE) {
            if (!shouldIdle) {
               mod.stop();
            } else {
               mod.getBaritone().getPathingBehavior().forceCancel();
               mod.getBaritone().getInputOverrideHandler().clearAllKeys();
            }
         }

         if (this.currentOnFinish != null) {
            this.currentOnFinish.run();
         }

         boolean callbackInstalledPolicyIdle = resumeTask != null && this.mainTask != null && this.runningIdleTask;
         if (resumeTask != null && (this.mainTask == null || callbackInstalledPolicyIdle)) {
            boolean readyToResume = resumePrepared && isResumeSafe(resumeTask);
            if (readyToResume) {
               Task displacedPolicyIdle = callbackInstalledPolicyIdle ? this.mainTask : null;
               Runnable displacedPolicyIdleOnFinish = callbackInstalledPolicyIdle ? this.currentOnFinish : null;
               if (resumeTask instanceof FollowPlayerTask) {
                  LOGGER.info("[FollowDiag] FOLLOW-RESUME: gesture finished, resuming suspended follow '{}'", resumeTask.toString());
               } else {
                  LOGGER.info("[FollowDiag] TASK-RESUME: gesture finished, resuming suspended task '{}'", resumeTask.toString());
               }

               this.resumingSuspended = true;

               try {
                  Runnable onFinishToRestore = resumeOnFinish != null ? resumeOnFinish : () -> {};
                  this.runTask(mod, resumeTask, onFinishToRestore);
               } finally {
                  this.resumingSuspended = false;
               }

               if (displacedPolicyIdle != null) {
                  this.abandonAndFinishSuspended(displacedPolicyIdle, displacedPolicyIdleOnFinish);
               }

               return;
            }

            LOGGER.info(
               "[FollowDiag] TASK-DROP-FINISH: gesture finished, non-resume-safe task '{}' dropped; firing its onFinish to resolve the step (no resume)",
               resumeTask.toString()
            );
            this.abandonAndFinishSuspended(resumeTask, resumeOnFinish);
         } else if (resumeTask != null) {
            LOGGER.info("[FollowDiag] TASK-SUSPEND-CLEAR: task installed during gesture completion; suspended task '{}' discarded", resumeTask.toString());
            this.abandonAndFinishSuspended(resumeTask, resumeOnFinish);
         }
      } finally {
         this.pendingGestureResumeOwnership = previousPendingGestureResumeOwnership;
      }

      boolean actuallyDone = this.mainTask == null;
      if (actuallyDone) {
         if (!this.runningIdleTask) {
            Debug.logMessage("User task FINISHED. Took %s seconds.", prettyPrintTimeDuration(seconds));
         }

         if (shouldIdle && this.runtimeMode == UserTaskChain.RuntimeMode.LIVE) {
            if (oldTask instanceof FollowPlayerTask && !this.runningIdleTask) {
               LOGGER.info(
                  "[FollowDiag] FOLLOW-IDLE-FALLBACK: follow ended, chain executing idleCommand='{}' - follow will NOT auto-resume",
                  mod.getModSettings().getIdleCommand()
               );
            }

            this.runIdleCommand(() -> this.controller.getCommandExecutor().executeWithPrefix(mod.getModSettings().getIdleCommand()));
            this.runningIdleTask = true;
         }
      }
   }

   public boolean isRunningIdleTask() {
      return this.isActive() && this.runningIdleTask;
   }

   public boolean hasActiveNonIdleUserTask() {
      return this.isActive() && !this.runningIdleTask && this.mainTask != null;
   }

   enum RuntimeMode {
      LIVE,
      DETACHED_SELF_TEST;
   }
}
