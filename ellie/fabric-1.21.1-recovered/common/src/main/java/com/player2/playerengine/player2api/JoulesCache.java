package com.player2.playerengine.player2api;

import com.google.gson.JsonElement;
import com.player2.playerengine.executor.BudgetTracker;
import com.player2.playerengine.player2api.config.BudgetThresholds;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class JoulesCache {
   private static final Logger LOGGER = LogManager.getLogger();
   private static final String JOULES_ENDPOINT = "/v1/joules";
   private static final ConcurrentHashMap<String, JoulesCache.JoulesSnapshot> CACHE = new ConcurrentHashMap<>();

   private JoulesCache() {
   }

   public static JoulesCache.JoulesSnapshot snapshotForProbe(long joulesDisplay, String patronTier) {
      return new JoulesCache.JoulesSnapshot(joulesDisplay, patronTier != null ? patronTier : "", "probe");
   }

   static JoulesCache.JoulesSnapshot snapshotForProbe(long joulesDisplay, String patronTier, long refreshedAtMs) {
      return new JoulesCache.JoulesSnapshot(joulesDisplay, patronTier != null ? patronTier : "", "probe", refreshedAtMs);
   }

   static boolean isFreshPatronSnapshot(JoulesCache.JoulesSnapshot snapshot, BudgetThresholds config) {
      if (snapshot != null && config != null && snapshot.isPatron()) {
         long maxAgeMs = Math.max(1L, config.getJoulesRefreshIntervalSeconds()) * 1000L;
         return snapshot.isFresh(maxAgeMs);
      } else {
         return false;
      }
   }

   public static JoulesCache.JoulesSnapshot emptyNonPatron() {
      return new JoulesCache.JoulesSnapshot(0L, "", "");
   }

   public static Optional<JoulesCache.JoulesSnapshot> get(String billingKey) {
      return billingKey == null ? Optional.empty() : Optional.ofNullable(CACHE.get(billingKey));
   }

   public static JoulesCache.JoulesSnapshot refresh(Player2APIService apiService, String billingKey) {
      try {
         Map<String, JsonElement> response = Player2ApiDispatcher.route(
            apiService.getController(), apiService.getClientId(), "GET", "/v1/joules", null, apiService.billingOrFallback()
         );
         long joulesRaw = response.containsKey("joules") ? response.get("joules").getAsLong() : 0L;
         JsonElement patronTierElement = response.get("patron_tier");
         String patronTier = patronTierElement != null && patronTierElement.isJsonPrimitive() && patronTierElement.getAsJsonPrimitive().isString()
            ? patronTierElement.getAsString()
            : "";
         JsonElement userIdElement = response.get("user_id");
         String userId = userIdElement != null && userIdElement.isJsonPrimitive() && userIdElement.getAsJsonPrimitive().isString()
            ? userIdElement.getAsString()
            : "";
         JoulesCache.JoulesSnapshot snap = new JoulesCache.JoulesSnapshot(joulesRaw, patronTier, userId);
         CACHE.put(billingKey, snap);
         LOGGER.debug("JoulesCache: refreshed billingKey={} joules={} patronTier={}", billingKey, snap.joulesDisplay(), patronTier);
         return snap;
      } catch (Exception e) {
         LOGGER.warn("JoulesCache: failed to refresh for billingKey={}: {}", billingKey, e.getMessage());
         return CACHE.get(billingKey);
      }
   }

   public static void maybeRefresh(Player2APIService apiService, String billingKey, BudgetThresholds config) {
      if (billingKey != null && apiService != null) {
         JoulesCache.JoulesSnapshot existing = CACHE.get(billingKey);
         long refreshIntervalMs = config.getJoulesRefreshIntervalSeconds() * 1000L;
         if (existing == null || System.currentTimeMillis() > existing.refreshedAtMs + refreshIntervalMs) {
            refresh(apiService, billingKey);
         }
      }
   }

   public static void invalidate(String billingKey) {
      if (billingKey != null) {
         CACHE.remove(billingKey);
         LOGGER.debug("JoulesCache: invalidated billingKey={}", billingKey);
      }
   }

   public static void invalidateAll() {
      int count = CACHE.size();
      CACHE.clear();
      LOGGER.info("JoulesCache: invalidated all entries, count={}", count);
   }

   public static BudgetTracker.BudgetCheckResult checkJoulesThreshold(JoulesCache.JoulesSnapshot snap, BudgetThresholds config) {
      int hard = config.getHardJoulesThreshold();
      int soft = config.getSoftJoulesThreshold();
      if (snap != null && (hard != 0 || soft != 0)) {
         if (hard > 0 && snap.joulesDisplay() < hard) {
            return BudgetTracker.BudgetCheckResult.HARD_LIMIT;
         } else {
            return soft > 0 && snap.joulesDisplay() < soft ? BudgetTracker.BudgetCheckResult.SOFT_LIMIT : BudgetTracker.BudgetCheckResult.OK;
         }
      } else {
         return BudgetTracker.BudgetCheckResult.OK;
      }
   }

   public static boolean shouldSendSoftMessage(String billingKey) {
      JoulesCache.JoulesSnapshot snap = CACHE.get(billingKey);
      return snap == null ? false : snap.softMessagedThisSnapshot.compareAndSet(false, true);
   }

   public static boolean shouldSendHardMessage(String billingKey) {
      JoulesCache.JoulesSnapshot snap = CACHE.get(billingKey);
      return snap == null ? false : snap.hardMessagedThisSnapshot.compareAndSet(false, true);
   }

   public static ConcurrentHashMap<String, JoulesCache.JoulesSnapshot> statusSnapshot() {
      return CACHE;
   }

   public static final class JoulesSnapshot {
      public final long joulesRaw;
      public final String patronTier;
      public final String userId;
      public final long refreshedAtMs;
      final AtomicBoolean softMessagedThisSnapshot = new AtomicBoolean(false);
      final AtomicBoolean hardMessagedThisSnapshot = new AtomicBoolean(false);

      JoulesSnapshot(long joulesRaw, String patronTier, String userId) {
         this(joulesRaw, patronTier, userId, System.currentTimeMillis());
      }

      private JoulesSnapshot(long joulesRaw, String patronTier, String userId, long refreshedAtMs) {
         this.joulesRaw = joulesRaw;
         this.patronTier = patronTier != null ? patronTier : "";
         this.userId = userId != null ? userId : "";
         this.refreshedAtMs = refreshedAtMs;
      }

      public long joulesDisplay() {
         return this.joulesRaw;
      }

      public boolean isPatron() {
         return !this.patronTier.isBlank();
      }

      boolean isFresh(long maxAgeMs) {
         long ageMs = System.currentTimeMillis() - this.refreshedAtMs;
         return maxAgeMs >= 0L && ageMs >= 0L && ageMs <= maxAgeMs;
      }
   }
}
