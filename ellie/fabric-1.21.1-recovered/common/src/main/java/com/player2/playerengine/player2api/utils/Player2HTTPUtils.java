package com.player2.playerengine.player2api.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.player2.playerengine.executor.StopReason;
import com.player2.playerengine.player2api.JoulesCache;
import com.player2.playerengine.player2api.auth.AuthKey;
import com.player2.playerengine.player2api.auth.AuthenticationManager;
import com.player2.playerengine.player2api.auth.TokenStorage;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Player2HTTPUtils {
   private static final Logger LOGGER = LogManager.getLogger();
   private static final String WEB_API_URL = "https://api.player2.game";
   private static final ThreadLocal<String> PROFILE_BASE_URL_OVERRIDE = new ThreadLocal<>();
   private static final Set<AuthKey> energyRetryAttempted = ConcurrentHashMap.newKeySet();

   public static void setProfileBaseUrlOverride(String url) {
      if (url == null) {
         PROFILE_BASE_URL_OVERRIDE.remove();
      } else {
         PROFILE_BASE_URL_OVERRIDE.set(url);
      }
   }

   public static void clearProfileBaseUrlOverride() {
      PROFILE_BASE_URL_OVERRIDE.remove();
   }

   private static String getApiUrl() {
      String override = PROFILE_BASE_URL_OVERRIDE.get();
      return override != null ? override : LocalAPIDiscovery.getPreferredApiUrl("https://api.player2.game");
   }

   public static Map<String, JsonElement> sendRequest(Player player, String clientId, String endpoint, boolean postRequest, JsonObject requestBody) throws Exception {
      return sendRequest(player, clientId, endpoint, postRequest ? "POST" : "GET", requestBody);
   }

   public static Map<String, JsonElement> sendRequest(Player player, String clientId, String endpoint, String method, JsonObject requestBody) throws Exception {
      return sendRequest(player, clientId, endpoint, method, requestBody, 120000);
   }

   public static Map<String, JsonElement> sendRequest(
      Player player, String clientId, String endpoint, String method, JsonObject requestBody, int readTimeoutMs
   ) throws Exception {
      String token = awaitToken(player, clientId);
      Map<String, String> headers = getHeaders(clientId, token);

      try {
         return HTTPUtils.sendRequest(getApiUrl(), endpoint, method, requestBody, headers, readTimeoutMs);
      } catch (HttpApiException e) {
         AuthKey authKey = new AuthKey(player.getUUID(), clientId);
         if (e.getStatusCode() == 401) {
            LOGGER.warn("Received 401 Unauthorized for {}. Invalidating token.", authKey);
            AuthenticationManager.getInstance().invalidateToken(player, clientId);
            throw new Exception("Token expired, re-authentication started.", e);
         }

         if (e.getStatusCode() == 402 && !energyRetryAttempted.contains(authKey)) {
            LOGGER.warn("Received 402 insufficient credits for {}. Attempting reauth.", authKey);
            energyRetryAttempted.add(authKey);
            String oldToken = token;
            AuthenticationManager.getInstance().invalidateToken(player, clientId);
            String newToken = awaitToken(player, clientId);
            if (!oldToken.equals(newToken)) {
               LOGGER.info("Token changed after reauth for {}, retrying request.", authKey);
               Map<String, String> newHeaders = getHeaders(clientId, newToken);
               return HTTPUtils.sendRequest(getApiUrl(), endpoint, method, requestBody, newHeaders, readTimeoutMs);
            }

            LOGGER.warn("User {} is out of Joules (same account after reauth)", player.getName().getString());
            JoulesCache.invalidate(authKey.playerUuid().toString());
            if (player instanceof ServerPlayer serverPlayer) {
               serverPlayer.sendSystemMessage(Component.translatable("message.playerengine.budget.insufficient_joules").withStyle(ChatFormatting.RED));
            }

            throw new Exception(StopReason.USER_ACTION_REQUIRED.name() + ":insufficient_joules_402");
         } else {
            throw e;
         }
      }
   }

   private static Map<String, String> getHeaders(String clientId, String token) {
      Map<String, String> headers = new HashMap<>();
      headers.put("player2-game-key", clientId);
      headers.put("Authorization", "Bearer " + token);
      return headers;
   }

   public static String awaitToken(Player player, String clientId) throws ExecutionException, InterruptedException {
      return AuthenticationManager.getInstance().authenticate(player, clientId).get();
   }

   public static Map<String, JsonElement> sendRequestWithStoredToken(String username, String clientId, String endpoint, String method, JsonObject requestBody) throws Exception {
      return sendRequestWithStoredToken(username, clientId, endpoint, method, requestBody, 120000);
   }

   public static Map<String, JsonElement> sendRequestWithStoredToken(
      String username, String clientId, String endpoint, String method, JsonObject requestBody, int readTimeoutMs
   ) throws Exception {
      String token = TokenStorage.getToken(username, clientId);
      if (token != null && !token.isEmpty()) {
         Map<String, String> headers = getHeaders(clientId, token);
         return HTTPUtils.sendRequest(getApiUrl(), endpoint, method, requestBody, headers, readTimeoutMs);
      } else {
         throw new IllegalStateException("No stored Player2 token for user " + username);
      }
   }

   public static JsonElement sendRequestElement(Player player, String clientId, String endpoint, String method, JsonObject requestBody) throws Exception {
      String token = awaitToken(player, clientId);
      Map<String, String> headers = getHeaders(clientId, token);

      try {
         return HTTPUtils.sendRequestElement(getApiUrl(), endpoint, method, requestBody, headers);
      } catch (HttpApiException e) {
         AuthKey authKey = new AuthKey(player.getUUID(), clientId);
         if (e.getStatusCode() == 401) {
            LOGGER.warn("Received 401 Unauthorized for {} on element request.", authKey);
            AuthenticationManager.getInstance().invalidateToken(player, clientId);
            throw new Exception("Token expired, re-authentication started.", e);
         } else {
            throw e;
         }
      }
   }
}
