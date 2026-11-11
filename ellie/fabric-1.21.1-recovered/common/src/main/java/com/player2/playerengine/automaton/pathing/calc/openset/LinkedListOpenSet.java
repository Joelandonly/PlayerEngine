package com.player2.playerengine.automaton.pathing.calc.openset;

import com.player2.playerengine.automaton.pathing.calc.PathNode;

class LinkedListOpenSet implements IOpenSet {
   private LinkedListOpenSet.Node first = null;

   @Override
   public boolean isEmpty() {
      return this.first == null;
   }

   @Override
   public void insert(PathNode pathNode) {
      LinkedListOpenSet.Node node = new LinkedListOpenSet.Node();
      node.val = pathNode;
      node.nextOpen = this.first;
      this.first = node;
   }

   @Override
   public void update(PathNode node) {
   }

   @Override
   public PathNode removeLowest() {
      if (this.first == null) {
         return null;
      }

      LinkedListOpenSet.Node current = this.first.nextOpen;
      if (current == null) {
         LinkedListOpenSet.Node n = this.first;
         this.first = null;
         return n.val;
      }

      LinkedListOpenSet.Node previous = this.first;
      double bestValue = this.first.val.combinedCost;
      LinkedListOpenSet.Node bestNode = this.first;
      LinkedListOpenSet.Node beforeBest = null;

      while (current != null) {
         double comp = current.val.combinedCost;
         if (comp < bestValue) {
            bestValue = comp;
            bestNode = current;
            beforeBest = previous;
         }

         previous = current;
         current = current.nextOpen;
      }

      if (beforeBest == null) {
         this.first = this.first.nextOpen;
         bestNode.nextOpen = null;
         return bestNode.val;
      } else {
         beforeBest.nextOpen = bestNode.nextOpen;
         bestNode.nextOpen = null;
         return bestNode.val;
      }
   }

   public static class Node {
      private LinkedListOpenSet.Node nextOpen;
      private PathNode val;
   }
}
