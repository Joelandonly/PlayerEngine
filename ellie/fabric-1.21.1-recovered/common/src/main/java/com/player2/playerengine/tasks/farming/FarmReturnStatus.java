package com.player2.playerengine.tasks.farming;

public enum FarmReturnStatus {
   NOT_REQUIRED,
   RETURNED,
   TIMED_OUT,
   UNAVAILABLE;

   public boolean incomplete() {
      return this == TIMED_OUT || this == UNAVAILABLE;
   }
}
