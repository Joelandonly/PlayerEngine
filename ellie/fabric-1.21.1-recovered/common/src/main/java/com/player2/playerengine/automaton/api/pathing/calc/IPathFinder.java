package com.player2.playerengine.automaton.api.pathing.calc;

import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.utils.PathCalculationResult;
import java.util.Optional;

public interface IPathFinder {
   Goal getGoal();

   PathCalculationResult calculate(long var1, long var3);

   boolean isFinished();

   Optional<IPath> pathToMostRecentNodeConsidered();

   Optional<IPath> bestPathSoFar();
}
