package com.player2.playerengine.player2api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.executor.StopReason;
import dev.architectury.networking.NetworkManager;
import dev.architectury.networking.NetworkManager.Side;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Player2ClientApiBridge {
   private static final Logger LOGGER = LogManager.getLogger();
   private static final int MAX_PAYLOAD_BYTES = 1048576;
   private static final long DEFAULT_REQUEST_TIMEOUT_SECONDS = 180L;
   private static final long CHAT_COMPLETION_TIMEOUT_SECONDS = 45L;
   private static final String CHAT_COMPLETION_ENDPOINT = "/v1/chat/completions";
   private static final Map<String, Player2ClientApiBridge.PendingRequest> PENDING_REQUESTS = new ConcurrentHashMap<>();

   private Player2ClientApiBridge() {
   }

   public static long defaultTimeoutForEndpoint(String endpoint) {
      return endpoint != null && endpoint.contains("/v1/chat/completions") ? 45L : 180L;
   }

   public static Map<String, JsonElement> sendJson(ServerPlayer player, String clientId, String method, String endpoint, JsonObject requestBody) throws Exception {
      return sendJson(player, clientId, method, endpoint, requestBody, defaultTimeoutForEndpoint(endpoint));
   }

   public static Map<String, JsonElement> sendJson(
      ServerPlayer player, String clientId, String method, String endpoint, JsonObject requestBody, long timeoutSeconds
   ) throws Exception {
      JsonObject response = sendJsonObject(player, clientId, method, endpoint, requestBody, timeoutSeconds);
      return toMap(response);
   }

   public static JsonObject sendJsonObject(ServerPlayer player, String clientId, String method, String endpoint, JsonObject requestBody) throws Exception {
      return sendJsonObject(player, clientId, method, endpoint, requestBody, defaultTimeoutForEndpoint(endpoint));
   }

   public static void cancelPendingForPlayer(UUID playerId) {
      if (playerId != null) {
         Iterator<Entry<String, Player2ClientApiBridge.PendingRequest>> it = PENDING_REQUESTS.entrySet().iterator();

         while (it.hasNext()) {
            Entry<String, Player2ClientApiBridge.PendingRequest> e = it.next();
            if (playerId.equals(e.getValue().playerId())) {
               e.getValue().future().completeExceptionally(new CancellationException(StopReason.CANCELLED_DISCONNECT.name()));
               it.remove();
            }
         }
      }
   }

   public static JsonObject sendJsonObject(ServerPlayer player, String clientId, String method, String endpoint, JsonObject requestBody, long timeoutSeconds) throws Exception {
      String requestId = UUID.randomUUID().toString();
      CompletableFuture<JsonObject> future = new CompletableFuture<>();
      PENDING_REQUESTS.put(requestId, new Player2ClientApiBridge.PendingRequest(player.getUUID(), future));

      try {
         byte[] payload = requestBody == null ? new byte[0] : requestBody.toString().getBytes(StandardCharsets.UTF_8);
         if (payload.length > 1048576) {
            PENDING_REQUESTS.remove(requestId);
            throw new IllegalArgumentException("Player2 proxy request too large: " + payload.length + " bytes");
         } else {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
            buf.writeUtf(requestId);
            buf.writeUtf(clientId);
            buf.writeUtf(method == null ? "GET" : method);
            buf.writeUtf(endpoint == null ? "" : endpoint);
            buf.writeByteArray(payload);
            LOGGER.info(
               "Server: Player2 proxy {} {} {} (timeout={}s) -> client {}", requestId, method, endpoint, timeoutSeconds, player.getName().getString()
            );
            player.connection.send(NetworkManager.toPacket(Side.S2C, PlayerEngine.CLIENT_PLAYER2_PROXY_REQUEST_PACKET_ID, buf));
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
         }
      } finally {
         PENDING_REQUESTS.remove(requestId);
      }
   }

   public static void handleClientProxyResponse(RegistryFriendlyByteBuf buf, ServerPlayer player) {
      String requestId = buf.readUtf();
      boolean success = buf.readBoolean();
      byte[] payload = buf.readByteArray(1048576);
      String responseText = new String(payload, StandardCharsets.UTF_8);
      Player2ClientApiBridge.PendingRequest pending = PENDING_REQUESTS.get(requestId);
      if (pending == null) {
         LOGGER.warn("Server: Ignoring Player2 proxy response for unknown request {}", requestId);
      } else if (!pending.playerId().equals(player.getUUID())) {
         LOGGER.warn("Server: Ignoring Player2 proxy response {} from unexpected player {}", requestId, player.getName().getString());
      } else if (!success) {
         pending.future().completeExceptionally(new RuntimeException(responseText));
      } else {
         try {
            JsonObject response = JsonParser.parseString(responseText).getAsJsonObject();
            pending.future().complete(response);
         } catch (Exception e) {
            pending.future().completeExceptionally(e);
         }
      }
   }

   public static Map<String, JsonElement> toMap(JsonObject response) {
      Map<String, JsonElement> responseMap = new HashMap<>();

      for (Entry<String, JsonElement> entry : response.entrySet()) {
         responseMap.put(entry.getKey(), entry.getValue());
      }

      return responseMap;
   }

   private record PendingRequest(UUID playerId, CompletableFuture<JsonObject> future) {
   }
}
