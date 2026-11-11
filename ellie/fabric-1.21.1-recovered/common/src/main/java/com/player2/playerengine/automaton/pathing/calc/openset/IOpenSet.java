package com.player2.playerengine.automaton.pathing.calc.openset;

import com.player2.playerengine.automaton.pathing.calc.PathNode;

public interface IOpenSet {
   void insert(PathNode var1);

   boolean isEmpty();

   PathNode removeLowest();

   void update(PathNode var1);
}
