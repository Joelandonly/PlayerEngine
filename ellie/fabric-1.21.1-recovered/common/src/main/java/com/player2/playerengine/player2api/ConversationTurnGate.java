package com.player2.playerengine.player2api;

import java.util.concurrent.atomic.AtomicLong;

final class ConversationTurnGate {
   private final AtomicLong generation = new AtomicLong();

   long issueTicket() {
      return this.generation.incrementAndGet();
   }

   boolean accepts(long ticket) {
      return this.generation.get() == ticket;
   }

   void invalidate() {
      this.generation.incrementAndGet();
   }
}
