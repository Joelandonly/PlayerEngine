package com.player2.playerengine.tasks.base;

import java.util.ArrayList;
import java.util.List;

public final class TransientTaskResumeSelfTest {
   private TransientTaskResumeSelfTest() {
   }

   public static void runAll() {
      stoppedEqualChildIsReplacedAndDetached();
      transientInterruptUsesCheckpointHook();
      abandonedResumeUsesTerminalHook();
      deniedCheckpointFallsBackToTerminalStop();
      ordinaryStopNeverUsesResumeHooks();
      preStartDiscardUsesAbandonHook();
      childCleanupPrecedesRootFrameRelease();
   }

   private static void stoppedEqualChildIsReplacedAndDetached() {
      TransientTaskResumeSelfTest.RestartingEqualChildParent parent = new TransientTaskResumeSelfTest.RestartingEqualChildParent();
      TransientTaskResumeSelfTest.FakeChain chain = new TransientTaskResumeSelfTest.FakeChain();
      parent.tick(chain);
      TransientTaskResumeSelfTest.EqualForceChild first = parent.cachedChild();
      parent.stop();
      parent.reset();
      parent.tick(chain);
      TransientTaskResumeSelfTest.EqualForceChild replacement = parent.cachedChild();
      require(replacement != first, "restart creates a fresh semantically equal child");
      require(parent.frameworkChild() == replacement, "stopped equal child is replaced by the parent's fresh authoritative handle");
      require(first.startCalls == 1 && first.stopCalls == 1, "terminal child is neither restarted nor cleaned up twice");
      require(replacement.startCalls == 1, "fresh equal replacement receives its first framework tick");
      require(first.forceCalls == 0, "terminal force-capable child cannot veto its replacement");
      replacement.stop();
      parent.setReturnChild(false);
      parent.tick(chain);
      require(parent.frameworkChild() == null, "terminal child is detached when the parent no longer returns a child");
      require(replacement.forceCalls == 0, "terminal force-capable child cannot veto its detachment");
   }

   private static void transientInterruptUsesCheckpointHook() {
      TransientTaskResumeSelfTest.FakeResumableTask task = new TransientTaskResumeSelfTest.FakeResumableTask(true);
      TransientTaskResumeSelfTest.FakeChain chain = new TransientTaskResumeSelfTest.FakeChain();
      task.tick(chain);
      task.interrupt(null, TaskSuspensionCause.HIGHER_PRIORITY_CHAIN);
      require(task.prepareCalls == 1, "priority interruption prepares one checkpoint");
      require(task.stopCalls == 0 && task.abandonCalls == 0, "transient interruption avoids terminal hooks");
      require(task.isTransientlySuspended(), "task exposes prepared suspension state");
      task.reset();
      task.tick(chain);
      require(task.startCalls == 2, "same logical task restarts after checkpoint");
      require(!task.isTransientlySuspended(), "reset consumes framework suspension state");
   }

   private static void abandonedResumeUsesTerminalHook() {
      TransientTaskResumeSelfTest.FakeResumableTask task = new TransientTaskResumeSelfTest.FakeResumableTask(true);
      task.tick(new TransientTaskResumeSelfTest.FakeChain());
      task.interrupt(null, TaskSuspensionCause.GESTURE_OVERLAY);
      task.abandonTransientResume();
      require(task.abandonCalls == 1, "discarded checkpoint terminalizes exactly once");
      require(task.stopCalls == 0, "discarded checkpoint does not replay ordinary stop cleanup");
      require(task.stopped() && !task.isTransientlySuspended(), "discarded task cannot resume later");
   }

   private static void deniedCheckpointFallsBackToTerminalStop() {
      TransientTaskResumeSelfTest.FakeResumableTask task = new TransientTaskResumeSelfTest.FakeResumableTask(false);
      task.tick(new TransientTaskResumeSelfTest.FakeChain());
      task.interrupt(null, TaskSuspensionCause.HIGHER_PRIORITY_CHAIN);
      require(task.prepareCalls == 1, "denied checkpoint was consulted");
      require(task.stopCalls == 1, "denied checkpoint runs terminal stop behavior");
      require(task.stopped() && !task.isActive() && !task.isTransientlySuspended(), "denied checkpoint is sealed for chain reap instead of silently restarting");
   }

   private static void ordinaryStopNeverUsesResumeHooks() {
      TransientTaskResumeSelfTest.FakeResumableTask task = new TransientTaskResumeSelfTest.FakeResumableTask(true);
      task.tick(new TransientTaskResumeSelfTest.FakeChain());
      task.stop();
      require(task.stopCalls == 1, "ordinary stop remains terminal");
      require(task.prepareCalls == 0 && task.abandonCalls == 0, "ordinary stop never enters transient lifecycle");
   }

   private static void preStartDiscardUsesAbandonHook() {
      TransientTaskResumeSelfTest.FakeResumableTask task = new TransientTaskResumeSelfTest.FakeResumableTask(true);
      task.reset();
      require(task.isAssigned(), "reset records chain ownership before first tick");
      task.stop();
      require(task.abandonCalls == 1 && task.stopCalls == 0 && task.stopped(), "discarding an assigned resumable task yields one typed terminal hook");
   }

   private static void childCleanupPrecedesRootFrameRelease() {
      List<String> order = new ArrayList<>();
      TransientTaskResumeSelfTest.OrderTrackingChild child = new TransientTaskResumeSelfTest.OrderTrackingChild(order);
      TransientTaskResumeSelfTest.OrderTrackingRoot root = new TransientTaskResumeSelfTest.OrderTrackingRoot(order, child);
      root.tick(new TransientTaskResumeSelfTest.FakeChain());
      root.interrupt(null, TaskSuspensionCause.HIGHER_PRIORITY_CHAIN);
      require(order.equals(List.of("prepare", "child", "root")), "nested cleanup preserves child-before-root LIFO ownership");
      require(child.stopped() && !child.isActive(), "detached child is sealed at framework level after cleanup");
   }

   private static void require(boolean condition, String message) {
      if (!condition) {
         throw new AssertionError(message);
      }
   }

   private static final class EqualForceChild extends Task implements ITaskCanForce {
      private int startCalls;
      private int stopCalls;
      private int forceCalls;

      @Override
      protected void onStart() {
         this.startCalls++;
      }

      @Override
      protected Task onTick() {
         return null;
      }

      @Override
      protected void onStop(Task interruptTask) {
         this.stopCalls++;
      }

      @Override
      public boolean shouldForce(Task interruptingCandidate) {
         this.forceCalls++;
         return true;
      }

      @Override
      protected boolean isEqual(Task other) {
         return other instanceof TransientTaskResumeSelfTest.EqualForceChild;
      }

      @Override
      protected String toDebugString() {
         return "equal force child";
      }
   }

   private static final class FakeChain extends TaskChain {
      private FakeChain() {
         super(new TaskRunner(null));
      }

      @Override
      protected void onStop() {
      }

      @Override
      public void onInterrupt(TaskChain other) {
      }

      @Override
      protected void onTick() {
      }

      @Override
      public float getPriority() {
         return 0.0F;
      }

      @Override
      public boolean isActive() {
         return true;
      }

      @Override
      public String getName() {
         return "Transient resume self-test";
      }
   }

   private static final class FakeResumableTask extends Task implements TransientlyResumableTask {
      private final boolean allowCheckpoint;
      private int startCalls;
      private int stopCalls;
      private int prepareCalls;
      private int abandonCalls;

      private FakeResumableTask(boolean allowCheckpoint) {
         this.allowCheckpoint = allowCheckpoint;
      }

      @Override
      protected void onStart() {
         this.startCalls++;
      }

      @Override
      protected Task onTick() {
         return null;
      }

      @Override
      protected void onStop(Task interruptTask) {
         this.stopCalls++;
      }

      @Override
      public boolean prepareForTransientResume(TaskSuspensionCause cause) {
         this.prepareCalls++;
         return this.allowCheckpoint;
      }

      @Override
      public void onTransientResumeAbandoned() {
         this.abandonCalls++;
      }

      @Override
      protected boolean isEqual(Task other) {
         return other == this;
      }

      @Override
      protected String toDebugString() {
         return "transient resume self-test";
      }
   }

   private static final class OrderTrackingChild extends Task {
      private final List<String> order;

      private OrderTrackingChild(List<String> order) {
         this.order = order;
      }

      @Override
      protected void onStart() {
      }

      @Override
      protected Task onTick() {
         return null;
      }

      @Override
      protected void onStop(Task interruptTask) {
         this.order.add("child");
      }

      @Override
      protected boolean isEqual(Task other) {
         return other == this;
      }

      @Override
      protected String toDebugString() {
         return "order-tracking child";
      }
   }

   private static final class OrderTrackingRoot extends Task implements TransientlyResumableTask {
      private final List<String> order;
      private final TransientTaskResumeSelfTest.OrderTrackingChild child;

      private OrderTrackingRoot(List<String> order, TransientTaskResumeSelfTest.OrderTrackingChild child) {
         this.order = order;
         this.child = child;
      }

      @Override
      protected void onStart() {
      }

      @Override
      protected Task onTick() {
         return this.child;
      }

      @Override
      protected void onStop(Task interruptTask) {
      }

      @Override
      public boolean prepareForTransientResume(TaskSuspensionCause cause) {
         this.order.add("prepare");
         return true;
      }

      @Override
      public void onTransientResumeAbandoned() {
      }

      @Override
      public void afterChildrenStopped() {
         this.order.add("root");
      }

      @Override
      protected boolean isEqual(Task other) {
         return other == this;
      }

      @Override
      protected String toDebugString() {
         return "order-tracking root";
      }
   }

   private static final class RestartingEqualChildParent extends Task {
      private TransientTaskResumeSelfTest.EqualForceChild cachedChild;
      private boolean returnChild = true;

      @Override
      protected void onStart() {
         this.cachedChild = new TransientTaskResumeSelfTest.EqualForceChild();
      }

      @Override
      protected Task onTick() {
         return this.returnChild ? this.cachedChild : null;
      }

      @Override
      protected void onStop(Task interruptTask) {
      }

      @Override
      protected boolean isEqual(Task other) {
         return other == this;
      }

      @Override
      protected String toDebugString() {
         return "restarting equal-child parent";
      }

      private TransientTaskResumeSelfTest.EqualForceChild cachedChild() {
         return this.cachedChild;
      }

      private Task frameworkChild() {
         return this.getSub();
      }

      private void setReturnChild(boolean returnChild) {
         this.returnChild = returnChild;
      }
   }
}
