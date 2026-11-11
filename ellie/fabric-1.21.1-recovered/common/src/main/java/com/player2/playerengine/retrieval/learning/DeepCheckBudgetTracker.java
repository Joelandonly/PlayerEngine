package com.player2.playerengine.retrieval.learning;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class DeepCheckBudgetTracker {
   private static final Logger LOGGER = LogManager.getLogger(DeepCheckBudgetTracker.class);
   private static final ConcurrentHashMap<String, DeepCheckBudgetTracker.WindowState> WINDOWS = new ConcurrentHashMap<>();

   private DeepCheckBudgetTracker() {
   }

   public static DeepCheckBudgetSnapshot peek(String billingKey, DeepCheckBudgetThresholds thresholds) {
      if (billingKey != null && thresholds.callsPerWindow() > 0) {
         DeepCheckBudgetTracker.WindowState ws = getOrCreate(billingKey);
         long windowMs = thresholds.windowMinutes() * 60000L;
         synchronized (ws) {
            resetIfExpired(ws, windowMs);
            return new DeepCheckBudgetSnapshot(ws.callCount.get(), ws.windowStartMs, ws.windowStartMs + windowMs);
         }
      } else {
         return new DeepCheckBudgetSnapshot(0, 0L, 0L);
      }
   }

   public static DeepCheckBudgetResult peekCap(String billingKey, DeepCheckBudgetThresholds thresholds) {
      if (billingKey != null && thresholds.callsPerWindow() > 0) {
         long windowMs = thresholds.windowMinutes() * 60000L;
         DeepCheckBudgetTracker.WindowState ws = getOrCreate(billingKey);
         synchronized (ws) {
            resetIfExpired(ws, windowMs);
            return ws.callCount.get() >= thresholds.callsPerWindow() ? DeepCheckBudgetResult.CAP_REACHED : DeepCheckBudgetResult.OK;
         }
      } else {
         return DeepCheckBudgetResult.OK;
      }
   }

   public static DeepCheckBudgetResult checkAndRecord(String billingKey, DeepCheckBudgetThresholds thresholds) {
      if (billingKey != null && thresholds.callsPerWindow() > 0) {
         long windowMs = thresholds.windowMinutes() * 60000L;
         DeepCheckBudgetTracker.WindowState ws = getOrCreate(billingKey);
         synchronized (ws) {
            resetIfExpired(ws, windowMs);
            int count = ws.callCount.incrementAndGet();
            if (count > thresholds.callsPerWindow()) {
               ws.callCount.decrementAndGet();
               LOGGER.debug("[B5] deep_check_cap_skip billingKey={} count={}", billingKey, count - 1);
               return DeepCheckBudgetResult.CAP_REACHED;
            } else {
               return DeepCheckBudgetResult.OK;
            }
         }
      } else {
         return DeepCheckBudgetResult.OK;
      }
   }

   private static DeepCheckBudgetTracker.WindowState getOrCreate(String billingKey) {
      return WINDOWS.computeIfAbsent(billingKey, k -> new DeepCheckBudgetTracker.WindowState());
   }

   private static void resetIfExpired(DeepCheckBudgetTracker.WindowState ws, long windowMs) {
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
