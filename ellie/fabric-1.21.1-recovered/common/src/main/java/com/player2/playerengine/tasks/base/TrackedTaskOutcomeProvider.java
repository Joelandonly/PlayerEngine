package com.player2.playerengine.tasks.base;

public interface TrackedTaskOutcomeProvider {
   boolean isTerminal();

   boolean isSuccessful();

   String controlledReason();
}
