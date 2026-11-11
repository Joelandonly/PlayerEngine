package com.player2.playerengine.player2api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.player2.playerengine.player2api.utils.Player2HTTPUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ProfileUrlResolver {
   private static final Logger LOGGER = LogManager.getLogger();
   private static final String PROFILES_ENDPOINT = "/v1/ai_profiles";
   private static final long SUCCESS_CACHE_TTL_MS = 30000L;
   private static final long FAILURE_CACHE_TTL_MS = 5000L;
   private static final int MAX_CACHE_ENTRIES = 128;
   private static final int MAX_PROFILES_PER_BILLING = 16;
   private static final int MAX_PROFILE_NAME_LENGTH = 80;
   private static final int MAX_PROFILE_BASE_URL_LENGTH = 2048;
   private static final ConcurrentHashMap<ProfileUrlResolver.CacheKey, ProfileUrlResolver.CacheEntry> PROFILE_CACHE = new ConcurrentHashMap<>();

   private ProfileUrlResolver() {
   }

   public static Optional<String> resolve(Player2APIService apiService, String profileName) {
      if (!isValidNamedProfileName(profileName)) {
         LOGGER.warn("ProfileUrlResolver: configured profile name is not a valid named-profile identifier");
         return Optional.empty();
      }

      Map<String, String> profiles = ensureLoaded(apiService);
      String url = profiles.get(profileName);
      if (url == null) {
         LOGGER.warn("ProfileUrlResolver: configured profile was not found for the current billing identity");
      }

      return Optional.ofNullable(url);
   }

   public static Optional<String> getSoleNamedProfileBaseUrl(Player2APIService apiService) {
      Map<String, String> profiles = ensureLoaded(apiService);
      String found = null;

      for (Entry<String, String> entry : profiles.entrySet()) {
         if (!"default".equalsIgnoreCase(entry.getKey())) {
            if (found != null) {
               LOGGER.debug("ProfileUrlResolver.getSoleNamedProfileBaseUrl: >1 named profiles — returning empty");
               return Optional.empty();
            }

            found = entry.getValue();
         }
      }

      return Optional.ofNullable(found);
   }

   public static Set<String> getProfileNames(Player2APIService apiService) {
      return Collections.unmodifiableSet(ensureLoaded(apiService).keySet());
   }

   public static void invalidateCache() {
      int count = PROFILE_CACHE.size();
      PROFILE_CACHE.clear();
      LOGGER.info("ProfileUrlResolver: cache invalidated, entries={}", count);
   }

   private static Map<String, String> ensureLoaded(Player2APIService apiService) {
      if (apiService == null) {
         return Collections.emptyMap();
      }

      Player2PayerResolution.ApiBillingContext billing = apiService.billingOrFallback();
      String billingKey = billing != null ? billing.billingKey() : null;
      if (billingKey != null && !billingKey.isBlank()) {
         String clientId = apiService.getClientId() == null ? "" : apiService.getClientId();
         ProfileUrlResolver.CacheKey key = new ProfileUrlResolver.CacheKey(clientId, billingKey);
         long nowMs = System.currentTimeMillis();
         ProfileUrlResolver.CacheEntry cached = PROFILE_CACHE.get(key);
         if (cached != null && cached.isCurrent(nowMs)) {
            return cached.profiles();
         }

         pruneCache(nowMs);
         ProfileUrlResolver.CacheEntry refreshed = PROFILE_CACHE.compute(key, (ignored, existing) -> {
            long checkMs = System.currentTimeMillis();
            if (existing != null && existing.isCurrent(checkMs)) {
               return (ProfileUrlResolver.CacheEntry)existing;
            }

            ProfileUrlResolver.FetchResult fetched = fetchProfiles(apiService, billing);
            long ttlMs = fetched.successful() ? 30000L : 5000L;
            return new ProfileUrlResolver.CacheEntry(fetched.profiles(), System.currentTimeMillis() + ttlMs);
         });
         pruneCache(System.currentTimeMillis());
         return refreshed.profiles();
      } else {
         return Collections.emptyMap();
      }
   }

   private static ProfileUrlResolver.FetchResult fetchProfiles(Player2APIService apiService, Player2PayerResolution.ApiBillingContext billing) {
      try {
         ServerPlayer payer = billing.onlinePayer();
         if (payer == null) {
            LOGGER.warn("ProfileUrlResolver: no online billing player; cannot fetch /v1/ai_profiles");
            return new ProfileUrlResolver.FetchResult(Collections.emptyMap(), false);
         }

         JsonElement element = Player2HTTPUtils.sendRequestElement(payer, apiService.getClientId(), "/v1/ai_profiles", "GET", null);
         if (element != null && element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            Map<String, String> result = new HashMap<>();

            for (JsonElement entry : arr) {
               if (result.size() >= 16) {
                  break;
               }

               if (entry.isJsonObject()) {
                  JsonObject obj = entry.getAsJsonObject();
                  if (obj.has("name") && obj.has("base_url")) {
                     JsonElement nameElement = obj.get("name");
                     JsonElement baseUrlElement = obj.get("base_url");
                     if (nameElement.isJsonPrimitive()
                        && nameElement.getAsJsonPrimitive().isString()
                        && baseUrlElement.isJsonPrimitive()
                        && baseUrlElement.getAsJsonPrimitive().isString()) {
                        String name = nameElement.getAsString();
                        String baseUrl = baseUrlElement.getAsString();
                        if (isValidNamedProfileName(name) && !baseUrl.isBlank() && baseUrl.length() <= 2048) {
                           String adjustedUrl = baseUrl.endsWith("/v1") ? baseUrl.substring(0, baseUrl.length() - 3) : baseUrl;
                           if (!adjustedUrl.isBlank()) {
                              result.put(name, adjustedUrl);
                           }
                        }
                     }
                  }
               }
            }

            LOGGER.info("ProfileUrlResolver: loaded {} profiles from /v1/ai_profiles", result.size());
            return new ProfileUrlResolver.FetchResult(Collections.unmodifiableMap(result), true);
         } else {
            LOGGER.warn("ProfileUrlResolver: /v1/ai_profiles returned an unsupported response shape");
            return new ProfileUrlResolver.FetchResult(Collections.emptyMap(), false);
         }
      } catch (Exception e) {
         LOGGER.warn("ProfileUrlResolver: failed to fetch /v1/ai_profiles ({}); retrying after cache TTL", e.getClass().getSimpleName());
         return new ProfileUrlResolver.FetchResult(Collections.emptyMap(), false);
      }
   }

   private static void pruneCache(long nowMs) {
      for (Entry<ProfileUrlResolver.CacheKey, ProfileUrlResolver.CacheEntry> entry : PROFILE_CACHE.entrySet()) {
         if (!entry.getValue().isCurrent(nowMs)) {
            PROFILE_CACHE.remove(entry.getKey(), entry.getValue());
         }
      }

      int overflow = PROFILE_CACHE.size() - 128;
      if (overflow > 0) {
         for (Entry<ProfileUrlResolver.CacheKey, ProfileUrlResolver.CacheEntry> entry : PROFILE_CACHE.entrySet()) {
            if (overflow-- <= 0) {
               break;
            }

            PROFILE_CACHE.remove(entry.getKey(), entry.getValue());
         }
      }
   }

   static boolean isValidNamedProfileName(String name) {
      if (name != null && !name.isEmpty() && name.length() <= 80 && !"default".equalsIgnoreCase(name)) {
         int first = name.codePointAt(0);
         int last = name.codePointBefore(name.length());
         if (!isWhitespace(first) && !isWhitespace(last)) {
            int offset = 0;

            while (offset < name.length()) {
               int codePoint = name.codePointAt(offset);
               if (java.lang.Character.isISOControl(codePoint) || java.lang.Character.getType(codePoint) == 16) {
                  return false;
               }

               offset += java.lang.Character.charCount(codePoint);
            }

            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean isWhitespace(int codePoint) {
      return java.lang.Character.isWhitespace(codePoint) || java.lang.Character.isSpaceChar(codePoint);
   }

   private record CacheEntry(Map<String, String> profiles, long expiresAtMs) {
      boolean isCurrent(long nowMs) {
         return nowMs >= 0L && nowMs < this.expiresAtMs;
      }
   }

   private record CacheKey(String clientId, String billingKey) {
   }

   private record FetchResult(Map<String, String> profiles, boolean successful) {
   }
}
