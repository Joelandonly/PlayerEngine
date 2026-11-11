package com.player2.playerengine.automaton.api.pathing.path;

import com.player2.playerengine.automaton.api.pathing.calc.IPath;

public interface IPathExecutor {
   IPath getPath();

   int getPosition();
}
