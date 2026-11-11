package com.player2.playerengine.trackers.storage;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.OptionalInt;
import net.minecraft.world.item.Item;

public final class SurvivalConsumptionLedger {
   private static final int DEFAULT_MAX_RETAINED_EVENTS = 256;
   private final int maxRetainedEvents;
   private final Deque<SurvivalConsumptionLedger.ConsumptionEvent> events = new ArrayDeque<>();
   private long epoch;
   private long sequence;

   public SurvivalConsumptionLedger() {
      this(256);
   }

   SurvivalConsumptionLedger(int maxRetainedEvents) {
      if (maxRetainedEvents < 1) {
         throw new IllegalArgumentException("maxRetainedEvents must be positive");
      }

      this.maxRetainedEvents = maxRetainedEvents;
   }

   public synchronized SurvivalConsumptionLedger.Snapshot snapshot() {
      return new SurvivalConsumptionLedger.Snapshot(this.epoch, this.sequence);
   }

   public synchronized void recordConsumption(Item item) {
      Objects.requireNonNull(item, "item");
      if (this.sequence == Long.MAX_VALUE) {
         this.epoch = this.epoch == Long.MAX_VALUE ? 0L : this.epoch + 1L;
         this.sequence = 0L;
         this.events.clear();
      }

      this.sequence++;
      this.events.addLast(new SurvivalConsumptionLedger.ConsumptionEvent(this.sequence, item));

      while (this.events.size() > this.maxRetainedEvents) {
         this.events.removeFirst();
      }
   }

   public synchronized OptionalInt consumedSince(Item item, SurvivalConsumptionLedger.Snapshot baseline) {
      Objects.requireNonNull(item, "item");
      if (baseline != null && baseline.epoch() == this.epoch && baseline.sequence() >= 0L && baseline.sequence() <= this.sequence) {
         SurvivalConsumptionLedger.ConsumptionEvent earliest = this.events.peekFirst();
         if (earliest != null && baseline.sequence() < earliest.sequence() - 1L) {
            return OptionalInt.empty();
         }

         int count = 0;

         for (SurvivalConsumptionLedger.ConsumptionEvent event : this.events) {
            if (event.sequence() > baseline.sequence() && event.item() == item) {
               count++;
            }
         }

         return OptionalInt.of(count);
      } else {
         return OptionalInt.empty();
      }
   }

   private record ConsumptionEvent(long sequence, Item item) {
   }

   public record Snapshot(long epoch, long sequence) {
   }
}
