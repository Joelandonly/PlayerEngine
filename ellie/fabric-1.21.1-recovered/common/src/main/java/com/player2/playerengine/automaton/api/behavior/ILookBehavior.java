package com.player2.playerengine.automaton.api.behavior;

import com.player2.playerengine.automaton.api.utils.Rotation;

public interface ILookBehavior extends IBehavior {
   void updateTarget(Rotation var1, boolean var2);

   void updateSecondaryTarget(Rotation var1);
}
