package com.player2.playerengine.executor;

import com.player2.playerengine.player2api.config.BudgetThresholds;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class BudgetTracker {
   private static final Logger LOGGER = LogManager.getLogger();
   private static final ConcurrentHashMap<String, BudgetTracker.WindowState> WINDOWS = new ConcurrentHashMap<>();

   private BudgetTracker() {
   }

   private static BudgetTracker.WindowState getOrCreate(String billingKey) {
      return WINDOWS.computeIfAbsent(billingKey, k -> new BudgetTracker.WindowState());
   }

   public static BudgetTracker.BudgetCheckResult checkAndRecord(String billingKey, BudgetThresholds config) {
      if (billingKey == null) {
         return BudgetTracker.BudgetCheckResult.OK;
      }

      int soft = config.getSoftBudgetCallsPerWindow();
      int hard = config.getHardBudgetCallsPerWindow();
      if (soft == 0 && hard == 0) {
         return BudgetTracker.BudgetCheckResult.OK;
      }

      long windowMs = config.getBudgetWindowMinutes() * 60000L;
      BudgetTracker.WindowState ws = getOrCreate(billingKey);
      synchronized (ws) {
         long now = System.currentTimeMillis();
         if (now > ws.windowStartMs + windowMs) {
            ws.windowStartMs = now;
            ws.callCount.set(1);
            ws.softMessagedThisWindow.set(false);
            ws.hardMessagedThisWindow.set(false);
            LOGGER.debug("BudgetTracker: window reset for billingKey={}", billingKey);
            return BudgetTracker.BudgetCheckResult.OK;
         } else {
            int count = ws.callCount.incrementAndGet();
            if (hard > 0 && count > hard) {
               return BudgetTracker.BudgetCheckResult.HARD_LIMIT;
            } else {
               return soft > 0 && count > soft ? BudgetTracker.BudgetCheckResult.SOFT_LIMIT : BudgetTracker.BudgetCheckResult.OK;
            }
         }
      }
   }

   public static BudgetTracker.BudgetCheckResult peek(String billingKey, BudgetThresholds config) {
      if (billingKey == null) {
         return BudgetTracker.BudgetCheckResult.OK;
      }

      int soft = config.getSoftBudgetCallsPerWindow();
      int hard = config.getHardBudgetCallsPerWindow();
      if (soft == 0 && hard == 0) {
         return BudgetTracker.BudgetCheckResult.OK;
      }

      long windowMs = config.getBudgetWindowMinutes() * 60000L;
      BudgetTracker.WindowState ws = WINDOWS.get(billingKey);
      if (ws == null) {
         return BudgetTracker.BudgetCheckResult.OK;
      }

      synchronized (ws) {
         long now = System.currentTimeMillis();
         if (now > ws.windowStartMs + windowMs) {
            return BudgetTracker.BudgetCheckResult.OK;
         } else {
            int nextCount = ws.callCount.get() + 1;
            if (hard > 0 && nextCount > hard) {
               return BudgetTracker.BudgetCheckResult.HARD_LIMIT;
            } else {
               return soft > 0 && nextCount > soft ? BudgetTracker.BudgetCheckResult.SOFT_LIMIT : BudgetTracker.BudgetCheckResult.OK;
            }
         }
      }
   }

   public static boolean shouldSendSoftMessage(String billingKey) {
      if (billingKey == null) {
         return false;
      }

      BudgetTracker.WindowState ws = getOrCreate(billingKey);
      return ws.softMessagedThisWindow.compareAndSet(false, true);
   }

   public static boolean shouldSendHardMessage(String billingKey) {
      if (billingKey == null) {
         return false;
      }

      BudgetTracker.WindowState ws = getOrCreate(billingKey);
      return ws.hardMessagedThisWindow.compareAndSet(false, true);
   }

   public static void reset(String billingKey) {
      if (billingKey != null) {
         WINDOWS.remove(billingKey);
         LOGGER.info("BudgetTracker: window reset (operator) for billingKey={}", billingKey);
      }
   }

   public static void resetAll() {
      int count = WINDOWS.size();
      WINDOWS.clear();
      LOGGER.info("BudgetTracker: all windows reset (operator), clearedCount={}", count);
   }

   public static Map<String, BudgetTracker.WindowSnapshot> statusSnapshot(BudgetThresholds config) {
      long windowMs = config.getBudgetWindowMinutes() * 60000L;
      Map<String, BudgetTracker.WindowSnapshot> result = new HashMap<>();

      for (Entry<String, BudgetTracker.WindowState> entry : WINDOWS.entrySet()) {
         BudgetTracker.WindowState ws = entry.getValue();
         result.put(entry.getKey(), new BudgetTracker.WindowSnapshot(ws.callCount.get(), ws.windowStartMs, ws.windowStartMs + windowMs));
      }

      return Collections.unmodifiableMap(result);
   }

   public static BudgetTracker.BudgetCheckResult stricter(BudgetTracker.BudgetCheckResult a, BudgetTracker.BudgetCheckResult b) {
      if (a == BudgetTracker.BudgetCheckResult.HARD_LIMIT || b == BudgetTracker.BudgetCheckResult.HARD_LIMIT) {
         return BudgetTracker.BudgetCheckResult.HARD_LIMIT;
      } else {
         return a != BudgetTracker.BudgetCheckResult.SOFT_LIMIT && b != BudgetTracker.BudgetCheckResult.SOFT_LIMIT
            ? BudgetTracker.BudgetCheckResult.OK
            : BudgetTracker.BudgetCheckResult.SOFT_LIMIT;
      }
   }

   public enum BudgetCheckResult {
      OK,
      SOFT_LIMIT,
      HARD_LIMIT;
   }

   public record WindowSnapshot(int callCount, long windowStartMs, long windowEndMs) {
   }

   static final class WindowState {
      final AtomicInteger callCount = new AtomicInteger(0);
      volatile long windowStartMs = System.currentTimeMillis();
      final AtomicBoolean softMessagedThisWindow = new AtomicBoolean(false);
      final AtomicBoolean hardMessagedThisWindow = new AtomicBoolean(false);
   }
}
