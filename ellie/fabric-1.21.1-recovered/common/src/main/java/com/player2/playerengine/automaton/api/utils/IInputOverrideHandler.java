package com.player2.playerengine.automaton.api.utils;

import com.player2.playerengine.automaton.api.behavior.IBehavior;
import com.player2.playerengine.automaton.api.utils.input.Input;

public interface IInputOverrideHandler extends IBehavior {
   boolean isInputForcedDown(Input var1);

   void setInputForceState(Input var1, boolean var2);

   void clearAllKeys();
}
