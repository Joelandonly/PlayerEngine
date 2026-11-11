package com.player2.playerengine.player2api.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.player2.playerengine.player2api.utils.HTTPUtils;
import com.player2.playerengine.player2api.utils.HttpApiException;
import com.player2.playerengine.player2api.utils.LocalAPIDiscovery;
import com.player2.playerengine.util.ExecutorShutdown;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent.Action;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AuthenticationManager {
   private static final Logger LOGGER = LogManager.getLogger();
   private static final String WEB_API_URL = "https://api.player2.game";
   private static final AuthenticationManager INSTANCE = new AuthenticationManager();
   private static volatile ExecutorService authExecutor = newAuthExecutor();
   private static volatile ScheduledExecutorService pollingExecutor = newPollingExecutor();
   private final Map<AuthKey, CompletableFuture<String>> ongoingAuths = new ConcurrentHashMap<>();
   private final Map<AuthKey, String> ongoingVerificationUrls = new ConcurrentHashMap<>();
   private final Map<AuthKey, ScheduledFuture<?>> pollingTasks = new ConcurrentHashMap<>();

   public static AuthenticationManager getInstance() {
      return INSTANCE;
   }

   public static ExecutorService getExecutor() {
      return authExecutor;
   }

   public static ExecutorService getPollingExecutor() {
      return pollingExecutor;
   }

   private static ExecutorService newAuthExecutor() {
      return Executors.newCachedThreadPool();
   }

   private static ScheduledExecutorService newPollingExecutor() {
      return Executors.newSingleThreadScheduledExecutor();
   }

   public static synchronized void shutdownAndReset() {
      INSTANCE.cancelOutstandingAuths();
      ExecutorService previousAuthExecutor = authExecutor;
      ScheduledExecutorService previousPollingExecutor = pollingExecutor;
      if (previousAuthExecutor != null && !previousAuthExecutor.isShutdown()) {
         ExecutorShutdown.shutdownNowAwait("AuthenticationManager.auth", previousAuthExecutor);
      }

      if (previousPollingExecutor != null && !previousPollingExecutor.isShutdown()) {
         ExecutorShutdown.shutdownNowAwait("AuthenticationManager.polling", previousPollingExecutor);
      }

      authExecutor = newAuthExecutor();
      pollingExecutor = newPollingExecutor();
   }

   private void cancelOutstandingAuths() {
      for (ScheduledFuture<?> task : this.pollingTasks.values()) {
         task.cancel(true);
      }

      this.pollingTasks.clear();

      for (CompletableFuture<String> future : this.ongoingAuths.values()) {
         future.cancel(true);
      }

      this.ongoingAuths.clear();
      this.ongoingVerificationUrls.clear();
   }

   public void invalidateToken(Player player, String clientId) {
      AuthKey authKey = new AuthKey(player.getUUID(), clientId);
      LOGGER.info("Invalidating token for {}", authKey);
      TokenStorage.storeToken(player.getName().getString(), clientId, "");
      this.authenticate(player, clientId);
   }

   public void checkAuth(Player player, String clientId) {
      this.tryLocalAuth(player, clientId, true);
   }

   public CompletableFuture<Void> checkAuthAsync(Player player, String clientId) {
      try {
         return CompletableFuture.runAsync(() -> this.checkAuth(player, clientId), getExecutor());
      } catch (RejectedExecutionException error) {
         return CompletableFuture.failedFuture(error);
      }
   }

   public boolean hasApiConnection(Player player, String clientId) {
      if (player != null && clientId != null && !clientId.isBlank()) {
         String username = player.getName().getString();
         return !TokenStorage.getToken(username, clientId).isEmpty() ? true : this.tryLocalAuth(player, clientId, false);
      } else {
         return false;
      }
   }

   private boolean tryLocalAuth(Player player, String clientId, boolean notifyOnFirstStore) {
      if (player != null && clientId != null && !clientId.isBlank()) {
         AuthKey authKey = new AuthKey(player.getUUID(), clientId);
         String username = player.getName().getString();

         try {
            LOGGER.info("Attempting local login check for {}", authKey);
            String p2Key = this.requestLocalAuthToken(clientId);
            if (p2Key != null) {
               LOGGER.info("Detected relogin for {}", authKey);
               if (notifyOnFirstStore && TokenStorage.getToken(username, clientId).isEmpty()) {
                  player.sendSystemMessage(Component.translatable("message.playerengine.auth.success", new Object[]{clientId}));
               }

               TokenStorage.storeToken(username, clientId, p2Key);
               return true;
            }
         } catch (Exception e) {
            LOGGER.warn("Local login check for {} failed. Error: {}", authKey, e.getMessage());
         }

         return false;
      } else {
         return false;
      }
   }

   private String requestLocalAuthToken(String clientId) throws Exception {
      String localApiUrl = LocalAPIDiscovery.getLocalApiUrl();
      if (localApiUrl == null) {
         return null;
      }

      Map<String, JsonElement> response = HTTPUtils.sendRequest(localApiUrl, "/v1/login/web/" + clientId, true, new JsonObject(), null);
      JsonElement p2Key = response.get("p2Key");
      return p2Key != null && !p2Key.isJsonNull() ? p2Key.getAsString() : null;
   }

   public CompletableFuture<String> authenticate(Player player, String clientId) {
      return this.authenticate(player, clientId, null);
   }

   public CompletableFuture<String> authenticate(Player player, String clientId, Consumer<String> verificationUrlConsumer) {
      AuthKey authKey = new AuthKey(player.getUUID(), clientId);
      String username = player.getName().getString();
      CompletableFuture<String> authFuture = this.ongoingAuths.get(authKey);
      if (authFuture != null) {
         this.notifyVerificationUrlConsumer(verificationUrlConsumer, this.ongoingVerificationUrls.get(authKey));
         return authFuture;
      } else {
         authFuture = new CompletableFuture<>();
         CompletableFuture<String> existingFuture = this.ongoingAuths.putIfAbsent(authKey, authFuture);
         if (existingFuture != null) {
            this.notifyVerificationUrlConsumer(verificationUrlConsumer, this.ongoingVerificationUrls.get(authKey));
            return existingFuture;
         } else {
            CompletableFuture<String> finalAuthFuture = authFuture;
            authExecutor.submit(
               () -> {
                  try {
                     String storedToken = TokenStorage.getToken(username, clientId);
                     if (!storedToken.isEmpty()) {
                        LOGGER.info("Found stored token for {}", authKey);
                        finalAuthFuture.complete(storedToken);
                        this.ongoingAuths.remove(authKey);
                        this.ongoingVerificationUrls.remove(authKey);
                        return;
                     }

                     try {
                        LOGGER.info("Attempting local login for {}", authKey);
                        String p2Key = this.requestLocalAuthToken(clientId);
                        if (p2Key != null) {
                           LOGGER.info("Local login successful for {}", authKey);
                           this.completeAuth(player, clientId, p2Key, finalAuthFuture);
                           return;
                        }
                     } catch (Exception e) {
                        LOGGER.warn("Local login for {} failed, proceeding to web auth. Error: {}", authKey, e.getMessage());
                     }

                     LOGGER.info("Starting web device flow for {}", authKey);
                     JsonObject deviceCodeRequestBody = new JsonObject();
                     deviceCodeRequestBody.addProperty("client_id", clientId);
                     Map<String, JsonElement> deviceCodeResponse = HTTPUtils.sendRequest(
                        "https://api.player2.game", "/v1/login/device/new", true, deviceCodeRequestBody, null
                     );
                     String deviceCode = deviceCodeResponse.get("deviceCode").getAsString();
                     String verificationUriComplete = deviceCodeResponse.get("verificationUriComplete").getAsString();
                     int interval = deviceCodeResponse.get("interval").getAsInt();
                     this.ongoingVerificationUrls.put(authKey, verificationUriComplete);
                     this.notifyVerificationUrlConsumer(verificationUrlConsumer, verificationUriComplete);
                     player.sendSystemMessage(
                        Component.translatable("message.playerengine.auth.authorize_prompt", new Object[]{clientId, verificationUriComplete})
                           .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(Action.OPEN_URL, verificationUriComplete)))
                     );
                     player.sendSystemMessage(Component.translatable("message.playerengine.auth.dedicated_hint"));
                     this.startPolling(player, clientId, deviceCode, interval, finalAuthFuture);
                  } catch (Exception e) {
                     LOGGER.error("Authentication failed for {}", authKey, e);
                     player.sendSystemMessage(Component.translatable("message.playerengine.auth.failed", new Object[]{clientId}));
                     finalAuthFuture.completeExceptionally(e);
                     this.ongoingAuths.remove(authKey);
                     this.ongoingVerificationUrls.remove(authKey);
                  }
               }
            );
            return authFuture;
         }
      }
   }

   private void notifyVerificationUrlConsumer(Consumer<String> verificationUrlConsumer, String verificationUrl) {
      if (verificationUrlConsumer != null && verificationUrl != null && !verificationUrl.isBlank()) {
         try {
            verificationUrlConsumer.accept(verificationUrl);
         } catch (Exception e) {
            LOGGER.warn("Verification URL callback failed: {}", e.getMessage());
         }
      }
   }

   private void startPolling(Player player, String clientId, String deviceCode, int interval, CompletableFuture<String> authFuture) {
      AuthKey authKey = new AuthKey(player.getUUID(), clientId);
      ScheduledFuture<?> pollingTask = pollingExecutor.scheduleAtFixedRate(() -> {
         try {
            JsonObject tokenRequestBody = new JsonObject();
            tokenRequestBody.addProperty("client_id", clientId);
            tokenRequestBody.addProperty("device_code", deviceCode);
            tokenRequestBody.addProperty("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
            Map<String, JsonElement> response = HTTPUtils.sendRequest("https://api.player2.game", "/v1/login/device/token", true, tokenRequestBody, null);
            String p2Key = response.get("p2Key").getAsString();
            if (p2Key != null) {
               LOGGER.info("Device flow polling successful for {}", authKey);
               this.completeAuth(player, clientId, p2Key, authFuture);
            }
         } catch (HttpApiException e) {
            if (!e.getMessage().contains("authorization_pending")) {
               LOGGER.error("Error during polling for {}", authKey, e);
               authFuture.completeExceptionally(e);
               this.ongoingAuths.remove(authKey);
               this.stopPolling(authKey);
            }
         } catch (Exception e) {
            LOGGER.error("Unexpected error during polling for {}", authKey, e);
            authFuture.completeExceptionally(e);
            this.ongoingAuths.remove(authKey);
            this.stopPolling(authKey);
         }
      }, 0L, interval, TimeUnit.SECONDS);
      this.pollingTasks.put(authKey, pollingTask);
   }

   private void completeAuth(Player player, String clientId, String token, CompletableFuture<String> authFuture) {
      AuthKey authKey = new AuthKey(player.getUUID(), clientId);
      String username = player.getName().getString();
      TokenStorage.storeToken(username, clientId, token);
      player.sendSystemMessage(Component.translatable("message.playerengine.auth.success", new Object[]{clientId}));
      authFuture.complete(token);
      this.stopPolling(authKey);
      this.ongoingAuths.remove(authKey);
      this.ongoingVerificationUrls.remove(authKey);
   }

   private void stopPolling(AuthKey authKey) {
      ScheduledFuture<?> task = this.pollingTasks.remove(authKey);
      if (task != null) {
         task.cancel(true);
      }

      this.ongoingVerificationUrls.remove(authKey);
   }
}
