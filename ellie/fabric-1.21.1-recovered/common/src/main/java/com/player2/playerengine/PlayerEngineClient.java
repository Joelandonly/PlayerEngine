package com.player2.playerengine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.player2.playerengine.automaton.KeepName;
import com.player2.playerengine.automaton.client.CustomFishingBobberRenderer;
import com.player2.playerengine.client.PlayerEngineClientConfigCache;
import com.player2.playerengine.player2api.ChatclefConfigPersistantState;
import com.player2.playerengine.player2api.manager.HeartbeatManager;
import com.player2.playerengine.player2api.network.Player2ServerNetworking;
import com.player2.playerengine.player2api.utils.AudioUtils;
import com.player2.playerengine.player2api.utils.Player2HTTPUtils;
import com.player2.playerengine.player2api.utils.STTUtils;
import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.event.events.client.ClientLifecycleEvent.ClientState;
import dev.architectury.event.events.client.ClientTickEvent.Client;
import dev.architectury.networking.NetworkManager;
import dev.architectury.networking.NetworkManager.Side;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@KeepName
public final class PlayerEngineClient {
   public static final Logger LOGGER = LogManager.getLogger("PlayerEngine");
   private static final int MAX_PAYLOAD_BYTES = 1048576;
   private static int heartbeatTickCounter;

   public static boolean isTtsEnabled() {
      return ChatclefConfigPersistantState.isTtsEnabled();
   }

   public static void setTtsEnabled(boolean enabled) {
      ChatclefConfigPersistantState.setTtsEnabled(enabled);
      syncTtsPreferenceToServer();
   }

   public static void syncTtsPreferenceToServer() {
      if (ChatclefConfigPersistantState.runWhenLoaded(() -> Minecraft.getInstance().execute(PlayerEngineClient::syncTtsPreferenceToServer))) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.getConnection() != null && mc.player != null) {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), mc.player.registryAccess());
            buf.writeBoolean(ChatclefConfigPersistantState.isTtsEnabled());
            mc.getConnection().send(NetworkManager.toPacket(Side.C2S, PlayerEngine.TTS_PREFERENCE_PACKET_ID, buf));
         }
      }
   }

   public static void onInitializeClient() {
      EntityRendererRegistry.register(PlayerEngine.FISHING_BOBBER, CustomFishingBobberRenderer::new);
      STTUtils.onInitialize();
      ChatclefConfigPersistantState.preload();
      ClientLifecycleEvent.CLIENT_STOPPING.register((ClientState)client -> {
         STTUtils.shutdown();
         ChatclefConfigPersistantState.shutdown();
      });
      NetworkManager.registerReceiver(Side.S2C, ResourceLocation.fromNamespaceAndPath("playerengine", "stream_tts"), (buf, context) -> {
         if (ChatclefConfigPersistantState.isLoaded() && ChatclefConfigPersistantState.isTtsEnabled()) {
            String clientId = buf.readUtf();
            String token = buf.readUtf();
            buf.readUtf();
            double speed = buf.readDouble();
            int voiceIdCount = buf.readVarInt();
            String[] voiceIds = new String[voiceIdCount];

            for (int i = 0; i < voiceIdCount; i++) {
               voiceIds[i] = buf.readUtf();
            }

            String botUuid = buf.readUtf();
            int chunkCount = buf.readVarInt();
            String[] chunks = new String[chunkCount];

            for (int i = 0; i < chunkCount; i++) {
               chunks[i] = buf.readUtf();
            }

            int boundaryCount = buf.readVarInt();
            int[] boundaryChunkIndex = new int[boundaryCount];

            for (int b = 0; b < boundaryCount; b++) {
               boundaryChunkIndex[b] = buf.readVarInt();
               buf.readUtf();
            }

            CompletableFuture.runAsync(() -> playChunksSequentially(clientId, token, speed, voiceIds, botUuid, chunks, boundaryChunkIndex));
         }
      });
      NetworkManager.registerReceiver(Side.S2C, PlayerEngine.CLIENT_PLAYER2_PROXY_REQUEST_PACKET_ID, (buf, context) -> {
         String requestId = buf.readUtf();
         String clientId = buf.readUtf();
         String method = buf.readUtf();
         String endpoint = buf.readUtf();
         byte[] payload = buf.readByteArray(1048576);
         CompletableFuture.runAsync(() -> handlePlayer2ProxyRequest(requestId, clientId, method, endpoint, payload));
      });
      NetworkManager.registerReceiver(Side.S2C, Player2ServerNetworking.SYNC_SERVER_PLAYER2, (buf, context) -> {
         boolean dedicated = buf.readBoolean();
         String payerMode = buf.readUtf();
         boolean ownerOffline = buf.readBoolean();
         String hbId = buf.readUtf();
         int markerPauseMs = buf.readVarInt();
         boolean gaplessPrefetch = buf.readBoolean();
         PlayerEngineClientConfigCache.applyFromSync(dedicated, payerMode, ownerOffline, hbId, markerPauseMs, gaplessPrefetch);
      });
      ClientTickEvent.CLIENT_POST.register((Client)client -> {
         heartbeatTickCounter++;
         if (heartbeatTickCounter % 1200 == 0) {
            if (PlayerEngineClientConfigCache.shouldSendPlayerHeartbeat()) {
               Minecraft mc = Minecraft.getInstance();
               if (mc.player != null && mc.getConnection() != null) {
                  String uid = mc.player.getName().getString();
                  String cid = PlayerEngineClientConfigCache.getHeartbeatClientId();
                  if (HeartbeatManager.shouldHeartbeat(uid, cid)) {
                     CompletableFuture.runAsync(() -> {
                        try {
                           Player2HTTPUtils.sendRequest(mc.player, cid, "/v1/health", false, null);
                           HeartbeatManager.storeHeartbeatTime(uid, cid);
                        } catch (Exception e) {
                           LOGGER.debug("Client heartbeat skipped: {}", e.getMessage());
                        }
                     });
                  }
               }
            }
         }
      });
   }

   private static void handlePlayer2ProxyRequest(String requestId, String clientId, String method, String endpoint, byte[] payload) {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && client.getConnection() != null) {
         try {
            JsonObject body = null;
            if (payload != null && payload.length > 0) {
               body = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
            }

            LOGGER.info("Client: Player2 proxy {} {} {}", requestId, method, endpoint);
            JsonObject response = new JsonObject();
            Map<String, JsonElement> map = Player2HTTPUtils.sendRequest(client.player, clientId, endpoint, method, body);
            map.forEach(response::add);
            sendProxyResponse(requestId, true, response.toString());
         } catch (Exception e) {
            LOGGER.warn("Client: Player2 proxy {} failed: {}", requestId, e.getMessage());
            sendProxyResponse(requestId, false, e.getMessage() == null ? e.toString() : e.getMessage());
         }
      } else {
         sendProxyResponse(requestId, false, "Client player connection is not ready");
      }
   }

   private static void playChunksSequentially(
      String clientId, String token, double speed, String[] voiceIds, String botUuid, String[] chunks, int[] boundaryChunkIndex
   ) {
      String tokenToUse;
      try {
         tokenToUse = token != null && !token.isBlank() ? token : Player2HTTPUtils.awaitToken(Minecraft.getInstance().player, clientId);
      } catch (Exception e) {
         LOGGER.warn("Client: TTS token resolution failed for clientId={}: {}", clientId, e.getMessage());
         sendTtsMessageDone(botUuid, true);
         return;
      }

      boolean degraded = false;
      String tokenFinal = tokenToUse;
      CompletableFuture<byte[]> nextAudio = chunks.length > 0 ? fetchChunkAsync(clientId, tokenFinal, chunks[0], speed, voiceIds) : null;

      for (int k = 0; k < chunks.length; k++) {
         byte[] audio = null;

         try {
            audio = nextAudio != null ? nextAudio.get() : null;
         } catch (Exception e) {
            degraded = true;
            LOGGER.warn("Client: TTS chunk {} fetch failed for clientId={}: {}", k, clientId, e.getMessage());
         }

         if (k + 1 < chunks.length) {
            nextAudio = fetchChunkAsync(clientId, tokenFinal, chunks[k + 1], speed, voiceIds);
         } else {
            nextAudio = null;
         }

         if (audio != null && audio.length > 0) {
            try {
               AudioUtils.playAudioBytes(audio);
            } catch (Exception e) {
               degraded = true;
               LOGGER.warn("Client: TTS chunk {} playback failed for clientId={}: {}", k, clientId, e.getMessage());
            }
         }

         for (int b = 0; b < boundaryChunkIndex.length; b++) {
            if (boundaryChunkIndex[b] == k) {
               sendTtsSegmentDone(botUuid, b);
            }
         }
      }

      sendTtsMessageDone(botUuid, degraded);
   }

   private static CompletableFuture<byte[]> fetchChunkAsync(String clientId, String token, String text, double speed, String[] voiceIds) {
      return text != null && !text.isEmpty()
         ? CompletableFuture.supplyAsync(() -> AudioUtils.fetchAudioBytes(clientId, token, text, speed, voiceIds))
         : CompletableFuture.completedFuture(new byte[0]);
   }

   private static void sendTtsSegmentDone(String botUuid, int segIndex) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.getConnection() != null && mc.player != null) {
         mc.execute(() -> {
            if (mc.getConnection() != null && mc.player != null) {
               RegistryFriendlyByteBuf segBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), mc.player.registryAccess());
               segBuf.writeUtf(botUuid);
               segBuf.writeVarInt(segIndex);
               mc.getConnection().send(NetworkManager.toPacket(Side.C2S, PlayerEngine.TTS_SEGMENT_DONE_PACKET_ID, segBuf));
            }
         });
      }
   }

   private static void sendTtsMessageDone(String botUuid, boolean degraded) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.getConnection() != null && mc.player != null) {
         mc.execute(() -> {
            if (mc.getConnection() != null && mc.player != null) {
               RegistryFriendlyByteBuf doneBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), mc.player.registryAccess());
               doneBuf.writeUtf(botUuid);
               doneBuf.writeBoolean(degraded);
               mc.getConnection().send(NetworkManager.toPacket(Side.C2S, PlayerEngine.TTS_MESSAGE_DONE_PACKET_ID, doneBuf));
            }
         });
      }
   }

   private static void sendProxyResponse(String requestId, boolean success, String payloadText) {
      Minecraft client = Minecraft.getInstance();
      client.execute(() -> {
         if (client.player != null && client.getConnection() != null) {
            byte[] payload = payloadText.getBytes(StandardCharsets.UTF_8);
            RegistryFriendlyByteBuf responseBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), client.player.registryAccess());
            responseBuf.writeUtf(requestId);
            responseBuf.writeBoolean(success);
            responseBuf.writeByteArray(payload);
            client.getConnection().send(NetworkManager.toPacket(Side.C2S, PlayerEngine.CLIENT_PLAYER2_PROXY_RESPONSE_PACKET_ID, responseBuf));
         }
      });
   }
}
