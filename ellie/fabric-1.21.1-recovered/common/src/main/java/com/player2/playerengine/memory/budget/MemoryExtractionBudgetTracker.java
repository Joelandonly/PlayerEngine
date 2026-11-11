package com.player2.playerengine.memory.budget;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MemoryExtractionBudgetTracker {
   private static final Logger LOGGER = LogManager.getLogger(MemoryExtractionBudgetTracker.class);
   private static final ConcurrentHashMap<String, MemoryExtractionBudgetTracker.WindowState> WINDOWS = new ConcurrentHashMap<>();

   private MemoryExtractionBudgetTracker() {
   }

   public static MemoryBudgetSnapshot peek(String billingKey, MemoryBudgetThresholds thresholds) {
      if (billingKey != null && thresholds.callsPerWindow() > 0) {
         MemoryExtractionBudgetTracker.WindowState ws = getOrCreate(billingKey);
         long windowMs = thresholds.windowMinutes() * 60000L;
         synchronized (ws) {
            resetIfExpired(ws, windowMs);
            return new MemoryBudgetSnapshot(ws.callCount.get(), ws.windowStartMs, ws.windowStartMs + windowMs);
         }
      } else {
         return new MemoryBudgetSnapshot(0, 0L, 0L);
      }
   }

   public static MemoryBudgetResult peekCap(String billingKey, MemoryBudgetThresholds thresholds) {
      if (billingKey != null && thresholds.callsPerWindow() > 0) {
         long windowMs = thresholds.windowMinutes() * 60000L;
         MemoryExtractionBudgetTracker.WindowState ws = getOrCreate(billingKey);
         synchronized (ws) {
            resetIfExpired(ws, windowMs);
            return ws.callCount.get() >= thresholds.callsPerWindow() ? MemoryBudgetResult.CAP_REACHED : MemoryBudgetResult.OK;
         }
      } else {
         return MemoryBudgetResult.OK;
      }
   }

   public static MemoryBudgetResult checkAndRecord(String billingKey, MemoryBudgetThresholds thresholds) {
      if (billingKey != null && thresholds.callsPerWindow() > 0) {
         long windowMs = thresholds.windowMinutes() * 60000L;
         MemoryExtractionBudgetTracker.WindowState ws = getOrCreate(billingKey);
         synchronized (ws) {
            resetIfExpired(ws, windowMs);
            int count = ws.callCount.incrementAndGet();
            if (count > thresholds.callsPerWindow()) {
               ws.callCount.decrementAndGet();
               LOGGER.debug("[memory] extraction_cap_skip billingKey={} count={}", billingKey, count - 1);
               return MemoryBudgetResult.CAP_REACHED;
            } else {
               return MemoryBudgetResult.OK;
            }
         }
      } else {
         return MemoryBudgetResult.OK;
      }
   }

   private static MemoryExtractionBudgetTracker.WindowState getOrCreate(String billingKey) {
      return WINDOWS.computeIfAbsent(billingKey, k -> new MemoryExtractionBudgetTracker.WindowState());
   }

   private static void resetIfExpired(MemoryExtractionBudgetTracker.WindowState ws, long windowMs) {
      long now = System.currentTimeMillis();
      if (now > ws.windowStartMs + windowMs) {
         ws.windowStartMs = now;
         ws.callCount.set(0);
      }
   }

   private static final class WindowState {
      final AtomicInteger callCount = new AtomicInteger(0);
      volatile long windowStartMs = System.currentTimeMillis();
   }
}
