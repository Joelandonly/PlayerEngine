package com.player2.playerengine.memory;

import java.util.Objects;

public final class MemoryEdge {
   private final String fromId;
   private final String toId;
   private final String relation;
   private final double weight;
   private final long lastSeenTick;
   private final int mentionCount;

   public MemoryEdge(String fromId, String toId, String relation, double weight, long lastSeenTick, int mentionCount) {
      this.fromId = fromId;
      this.toId = toId;
      this.relation = relation;
      this.weight = weight;
      this.lastSeenTick = lastSeenTick;
      this.mentionCount = mentionCount;
   }

   public String fromId() {
      return this.fromId;
   }

   public String toId() {
      return this.toId;
   }

   public String relation() {
      return this.relation;
   }

   public double weight() {
      return this.weight;
   }

   public long lastSeenTick() {
      return this.lastSeenTick;
   }

   public int mentionCount() {
      return this.mentionCount;
   }

   public String identityKey() {
      return identityKey(this.fromId, this.relation, this.toId);
   }

   public static String identityKey(String fromId, String relation, String toId) {
      return fromId + "\u0000" + relation + "\u0000" + toId;
   }

   public MemoryEdge reinforced(double addWeight, long newLastSeenTick) {
      return new MemoryEdge(this.fromId, this.toId, this.relation, this.weight + addWeight, newLastSeenTick, this.mentionCount + 1);
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else {
         return !(o instanceof MemoryEdge that)
            ? false
            : Double.compare(that.weight, this.weight) == 0
               && this.lastSeenTick == that.lastSeenTick
               && this.mentionCount == that.mentionCount
               && Objects.equals(this.fromId, that.fromId)
               && Objects.equals(this.toId, that.toId)
               && Objects.equals(this.relation, that.relation);
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.fromId, this.toId, this.relation, this.weight, this.lastSeenTick, this.mentionCount);
   }

   @Override
   public String toString() {
      return "MemoryEdge{" + this.fromId + " -[" + this.relation + " w=" + this.weight + " n=" + this.mentionCount + "]-> " + this.toId + "}";
   }
}
