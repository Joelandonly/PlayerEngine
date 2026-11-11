package com.player2.playerengine.automaton.utils.pathing;

public final class MutableMoveResult {
   public int x;
   public int y;
   public int z;
   public double cost;
   public double oxygenCost;

   public MutableMoveResult() {
      this.reset();
   }

   public final void reset() {
      this.x = 0;
      this.y = 0;
      this.z = 0;
      this.cost = 1000000.0;
      this.oxygenCost = 0.0;
   }
}
