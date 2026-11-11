package com.player2.playerengine.automaton.api.entity;

import com.player2.playerengine.automaton.api.IBaritone;

public interface IAutomatone {
   default IBaritone getBaritone() {
      return IBaritone.KEY.get(this);
   }
}
