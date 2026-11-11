package com.player2.playerengine.automaton.api.event.events;

import com.player2.playerengine.automaton.api.event.events.type.EventState;

public final class PlayerUpdateEvent {
   private final EventState state;

   public PlayerUpdateEvent(EventState state) {
      this.state = state;
   }

   public final EventState getState() {
      return this.state;
   }
}
