package com.player2.playerengine.automaton.pathing.calc;

import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;

public final class PathNode {
   public final int x;
   public final int y;
   public final int z;
   public final double estimatedCostToGoal;
   public double cost;
   public double oxygenCost;
   public double combinedCost;
   public PathNode previous = null;
   public int heapPosition;

   public PathNode(int x, int y, int z, Goal goal) {
      this.cost = 1000000.0;
      this.oxygenCost = 0.0;
      this.estimatedCostToGoal = goal.heuristic(x, y, z);
      if (Double.isNaN(this.estimatedCostToGoal)) {
         throw new IllegalStateException(goal + " calculated implausible heuristic");
      }

      this.heapPosition = -1;
      this.x = x;
      this.y = y;
      this.z = z;
   }

   public boolean isOpen() {
      return this.heapPosition != -1;
   }

   @Override
   public int hashCode() {
      return (int)BetterBlockPos.longHash(this.x, this.y, this.z);
   }

   @Override
   public boolean equals(Object obj) {
      PathNode other = (PathNode)obj;
      return this.x == other.x && this.y == other.y && this.z == other.z;
   }
}
