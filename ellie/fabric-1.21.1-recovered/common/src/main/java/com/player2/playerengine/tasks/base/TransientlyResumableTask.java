package com.player2.playerengine.tasks.base;

public interface TransientlyResumableTask {
   boolean prepareForTransientResume(TaskSuspensionCause var1);

   void onTransientResumeAbandoned();

   default void afterChildrenStopped() {
   }
}
