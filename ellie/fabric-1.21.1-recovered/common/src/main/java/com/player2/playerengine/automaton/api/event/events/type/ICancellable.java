package com.player2.playerengine.automaton.api.event.events.type;

public interface ICancellable {
   void cancel();

   boolean isCancelled();
}
