package com.player2.playerengine.executor;

public enum StepState {
   PENDING,
   RUNNING,
   SUCCEEDED,
   FAILED,
   ROLLED_BACK,
   BLOCKED;
}
