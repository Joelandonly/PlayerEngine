package com.player2.playerengine.executor;

import com.player2.playerengine.util.Debug;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StepExecution {
   private final String stepId;
   private final String stepKind;
   private final long startTimeMs;
   private StepState state;
   private final List<String> log;

   StepExecution(String stepId, String stepKind) {
      this.stepId = stepId;
      this.stepKind = stepKind;
      this.startTimeMs = System.currentTimeMillis();
      this.state = StepState.PENDING;
      this.log = new ArrayList<>();
      Debug.logInternal("[StepExecution] " + stepId + " [" + stepKind + "] created in PENDING");
   }

   void transitionTo(StepState next, String reason) {
      if (!isValidTransition(this.state, next)) {
         throw new IllegalStateException("[StepExecution] " + this.stepId + " illegal transition " + this.state + " -> " + next + " (reason: " + reason + ")");
      }

      String entry = "[" + this.state + " -> " + next + "] " + reason;
      this.log.add(entry);
      this.state = next;
      Debug.logInternal("[StepExecution] " + this.stepId + " [" + this.stepKind + "] " + entry);
   }

   private static boolean isValidTransition(StepState from, StepState to) {
      switch (from) {
         case PENDING:
            return to == StepState.RUNNING || to == StepState.BLOCKED;
         case RUNNING:
            return to == StepState.SUCCEEDED || to == StepState.FAILED || to == StepState.ROLLED_BACK;
         case FAILED:
            return to == StepState.ROLLED_BACK;
         default:
            return false;
      }
   }

   public StepState getState() {
      return this.state;
   }

   public String getStepId() {
      return this.stepId;
   }

   public String getStepKind() {
      return this.stepKind;
   }

   public long getStartTimeMs() {
      return this.startTimeMs;
   }

   public List<String> getLog() {
      return Collections.unmodifiableList(this.log);
   }

   public String getLastLogEntry() {
      return this.log.isEmpty() ? "" : this.log.get(this.log.size() - 1);
   }

   public long getElapsedMs() {
      return System.currentTimeMillis() - this.startTimeMs;
   }

   public boolean isTerminal() {
      return this.state == StepState.SUCCEEDED || this.state == StepState.FAILED || this.state == StepState.ROLLED_BACK || this.state == StepState.BLOCKED;
   }

   @Override
   public String toString() {
      return "StepExecution{id=" + this.stepId + ", kind=" + this.stepKind + ", state=" + this.state + "}";
   }
}
