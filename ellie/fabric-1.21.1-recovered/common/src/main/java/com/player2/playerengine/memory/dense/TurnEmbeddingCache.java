package com.player2.playerengine.memory.dense;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.memory.budget.MemoryLlmClient;
import com.player2.playerengine.player2api.Player2PayerResolution;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class TurnEmbeddingCache {
   private static final int MAX_ENTRIES = 256;
   private static final int KEY_TEXT_CAP = 512;
   private static final Map<String, float[]> CACHE = new ConcurrentHashMap<>();
   private static final ConcurrentLinkedDeque<String> LRU = new ConcurrentLinkedDeque<>();
   private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();

   private TurnEmbeddingCache() {
   }

   public static float[] lookup(String turnText) {
      String key = key(turnText);
      if (key == null) {
         return null;
      }

      float[] v = CACHE.get(key);
      if (v != null) {
         touch(key);
      }

      return v;
   }

   public static void scheduleIfAbsent(String turnText, PlayerEngineController controller, Player2PayerResolution.ApiBillingContext billing) {
      String key = key(turnText);
      if (key != null && controller != null && billing != null && billing.billingKey() != null && EmbeddingProvider.isAvailable()) {
         if (!CACHE.containsKey(key)) {
            if (IN_FLIGHT.add(key)) {
               MemoryLlmClient.MEMORY_EXECUTOR.submit(() -> {
                  try {
                     EmbeddingResult r = EmbeddingProvider.embed(List.of(turnText), controller, billing);
                     if (r.ok() && r.vectors().length > 0 && r.vectors()[0] != null) {
                        put(key, r.vectors()[0]);
                     }
                  } catch (RuntimeException var8) {
                  } finally {
                     IN_FLIGHT.remove(key);
                  }
               });
            }
         }
      }
   }

   private static void put(String key, float[] vector) {
      if (CACHE.put(key, vector) == null) {
         LRU.addLast(key);
      } else {
         touch(key);
      }

      evictIfNeeded();
   }

   private static void touch(String key) {
      LRU.remove(key);
      LRU.addLast(key);
   }

   private static void evictIfNeeded() {
      while (CACHE.size() > 256) {
         String oldest = LRU.pollFirst();
         if (oldest != null) {
            CACHE.remove(oldest);
            continue;
         }
         break;
      }
   }

   private static String key(String turnText) {
      if (turnText == null) {
         return null;
      }

      String t = turnText.trim();
      if (t.isEmpty()) {
         return null;
      }

      if (t.length() <= 512) {
         return t;
      }

      int cap = 256;
      String head = t.substring(0, cap);
      String tail = t.substring(t.length() - cap);
      return "h:" + t.length() + ":" + Long.toHexString(fnv1a64(t)) + ":" + Integer.toHexString(t.hashCode()) + ":" + head + "~" + tail;
   }

   private static long fnv1a64(String s) {
      long h = -3750763034362895579L;

      for (int i = 0; i < s.length(); i++) {
         h ^= s.charAt(i);
         h *= 1099511628211L;
      }

      return h;
   }
}
