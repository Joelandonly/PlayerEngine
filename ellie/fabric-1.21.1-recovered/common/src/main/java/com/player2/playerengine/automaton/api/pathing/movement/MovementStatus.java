package com.player2.playerengine.automaton.api.pathing.movement;

public enum MovementStatus {
   PREPPING(false),
   WAITING(false),
   RUNNING(false),
   SUCCESS(true),
   UNREACHABLE(true),
   FAILED(true),
   CANCELED(true);

   private final boolean complete;

   MovementStatus(boolean complete) {
      this.complete = complete;
   }

   public final boolean isComplete() {
      return this.complete;
   }
}
