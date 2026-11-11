package com.player2.playerengine.player2api.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.player2.playerengine.player2api.ChatclefConfigPersistantState;
import dev.architectury.networking.NetworkManager;
import dev.architectury.networking.NetworkManager.Side;
import io.netty.buffer.Unpooled;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class STTUtils {
   public static final Logger LOGGER = LogManager.getLogger();
   private static final ExecutorService sttThread = Executors.newSingleThreadExecutor();
   private static final int STT_TIMEOUT_SECONDS = 60;
   private static final long MIN_CAPTURE_WINDOW_MS = 600L;
   public static volatile boolean isListening = false;
   private static volatile boolean pushToTalkActive = false;
   private static volatile boolean sessionStarted = false;
   private static volatile boolean stopPendingAfterStart = false;
   private static final AtomicLong cycleCounter = new AtomicLong();
   private static volatile long currentCycleId = 0L;
   private static volatile long pressNanos = 0L;
   private static volatile long startRequestNanos = 0L;
   private static volatile long startCompleteNanos = 0L;
   private static volatile long releaseNanos = 0L;
   private static volatile boolean ttsActiveAtPress = false;
   public static String clientId;

   private static long msSince(long fromNanos) {
      return fromNanos == 0L ? -1L : (System.nanoTime() - fromNanos) / 1000000L;
   }

   private static long deltaMs(long fromNanos, long toNanos) {
      return fromNanos != 0L && toNanos != 0L ? (toNanos - fromNanos) / 1000000L : -1L;
   }

   private static String cycleTimingSummary() {
      long heldMs = deltaMs(pressNanos, releaseNanos);
      long startLatencyMs = deltaMs(startRequestNanos, startCompleteNanos);
      long pressToStartCompleteMs = deltaMs(pressNanos, startCompleteNanos);
      long captureWindowMs = deltaMs(startCompleteNanos, System.nanoTime());
      return String.format(
         "[cycle=%d heldMs=%d startLatencyMs=%d pressToStartCompleteMs=%d captureWindowSinceStartMs=%d ttsActiveAtPress=%b]",
         currentCycleId,
         heldMs,
         startLatencyMs,
         pressToStartCompleteMs,
         captureWindowMs,
         ttsActiveAtPress
      );
   }

   public static void update() {
   }

   public static void onInitialize() {
      LOGGER.info("STTUtils initialized (session-based /v1/stt/start and /v1/stt/stop).");
   }

   public static void setIsListening(boolean v, String clientId) {
      STTUtils.clientId = clientId;
      if (v) {
         if (!ChatclefConfigPersistantState.canUseStt()) {
            LOGGER.warn("STT: push-to-talk ignored (consent not granted or STT disabled in settings)");
            return;
         }

         if (pushToTalkActive) {
            LOGGER.debug("STT: push-to-talk start ignored (session already active)");
            return;
         }

         pushToTalkActive = true;
         isListening = true;
         stopPendingAfterStart = false;
         currentCycleId = cycleCounter.incrementAndGet();
         pressNanos = System.nanoTime();
         startRequestNanos = 0L;
         startCompleteNanos = 0L;
         releaseNanos = 0L;
         ttsActiveAtPress = AudioUtils.isPlaybackActive();
         LOGGER.info("STT: push-to-talk pressed (cycle={}, clientId={}, ttsActiveAtPress={})", currentCycleId, clientId, ttsActiveAtPress);
         sttThread.execute(STTUtils::startSession);
      } else {
         if (!pushToTalkActive) {
            LOGGER.debug("STT: push-to-talk release ignored (no active session)");
            return;
         }

         pushToTalkActive = false;
         isListening = false;
         releaseNanos = System.nanoTime();
         LOGGER.info("STT: push-to-talk released (cycle={}, heldMs={})", currentCycleId, msSince(pressNanos));
         sttThread.execute(STTUtils::stopSession);
      }
   }

   public static void abortSession() {
      LOGGER.info("STT: abortSession requested");
      pushToTalkActive = false;
      isListening = false;
      stopPendingAfterStart = false;
      pressNanos = 0L;
      startRequestNanos = 0L;
      startCompleteNanos = 0L;
      releaseNanos = 0L;
      ttsActiveAtPress = false;
      sttThread.execute(() -> {
         if (sessionStarted) {
            stopSessionInternal(false);
         }

         sessionStarted = false;
      });
   }

   public static void shutdown() {
      abortSession();
      sttThread.shutdownNow();
      LOGGER.info("STTUtils shutdown complete.");
   }

   private static void startSession() {
      if (!ChatclefConfigPersistantState.canUseStt()) {
         LOGGER.warn("STT: cannot start session (consent revoked or STT disabled before start completed)");
         resetPushToTalkState();
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null && clientId != null && !clientId.isEmpty()) {
            try {
               JsonObject requestBody = new JsonObject();
               requestBody.addProperty("timeout", 60);
               startRequestNanos = System.nanoTime();
               Map<String, JsonElement> startResponse = Player2HTTPUtils.sendRequest(mc.player, clientId, "/v1/stt/start", true, requestBody);
               startCompleteNanos = System.nanoTime();
               sessionStarted = true;
               LOGGER.info(
                  "STT: session started via /v1/stt/start (cycle={}, startLatencyMs={}, startBodyEmpty={}, clientId={})",
                  currentCycleId,
                  msSince(startRequestNanos),
                  startResponse == null || startResponse.isEmpty(),
                  clientId
               );
               if (stopPendingAfterStart) {
                  stopPendingAfterStart = false;
                  long windowSoFarMs = msSince(startCompleteNanos);
                  long remainingMs = 600L - Math.max(0L, windowSoFarMs);
                  LOGGER.info(
                     "STT: stop was queued while start was in flight (cycle={}); enforcing min capture window (remainingMs={})",
                     currentCycleId,
                     Math.max(0L, remainingMs)
                  );
                  if (remainingMs > 0L) {
                     try {
                        Thread.sleep(remainingMs);
                     } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        LOGGER.info("STT: min-capture-window sleep interrupted (cycle={}); skipping stop (shutdown)", currentCycleId);
                        return;
                     }
                  }

                  stopSessionInternal(true);
               }
            } catch (Exception e) {
               LOGGER.error("STT: failed to start session via /v1/stt/start", e);
               sessionStarted = false;
               resetPushToTalkState();
            }
         } else {
            LOGGER.warn(
               "STT: cannot start session (player={}, clientId={})",
               mc.player != null,
               clientId == null ? "<null>" : (clientId.isEmpty() ? "<empty>" : clientId)
            );
            resetPushToTalkState();
         }
      }
   }

   private static void stopSession() {
      if (!sessionStarted) {
         stopPendingAfterStart = true;
         LOGGER.info("STT: stop requested before session started (cycle={}); will stop after /v1/stt/start completes", currentCycleId);
      } else {
         long windowSoFarMs = msSince(startCompleteNanos);
         long remainingMs = 600L - Math.max(0L, windowSoFarMs);
         LOGGER.info("STT: stop after started (cycle={}, windowSinceStartMs={}, padMs={})", currentCycleId, windowSoFarMs, Math.max(0L, remainingMs));
         if (remainingMs > 0L) {
            try {
               Thread.sleep(remainingMs);
            } catch (InterruptedException ie) {
               Thread.currentThread().interrupt();
               LOGGER.info("STT: stop-pad sleep interrupted (cycle={}); skipping stop (shutdown)", currentCycleId);
               return;
            }
         }

         stopSessionInternal(true);
      }
   }

   private static void stopSessionInternal(boolean deliverTranscript) {
      sessionStarted = false;
      stopPendingAfterStart = false;
      if (!ChatclefConfigPersistantState.canUseStt()) {
         LOGGER.warn("STT: stop skipped (consent revoked or STT disabled, deliverTranscript={})", deliverTranscript);
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null && clientId != null && !clientId.isEmpty()) {
            try {
               Map<String, JsonElement> responseMap = Player2HTTPUtils.sendRequest(mc.player, clientId, "/v1/stt/stop", true, null);
               if (!deliverTranscript) {
                  LOGGER.info("STT: /v1/stt/stop completed (transcript not delivered, keys={})", SttLogging.jsonResponseKeys(responseMap));
                  return;
               }

               deliverTranscriptFromStopResponse(responseMap);
            } catch (Exception e) {
               LOGGER.error("STT: failed to stop session via /v1/stt/stop", e);
            }
         } else {
            LOGGER.warn(
               "STT: cannot stop session (player={}, clientId={}, deliverTranscript={})",
               mc.player != null,
               clientId != null && !clientId.isEmpty(),
               deliverTranscript
            );
         }
      }
   }

   private static void deliverTranscriptFromStopResponse(Map<String, JsonElement> responseMap) {
      String text = extractTranscriptText(responseMap);
      if (text == null) {
         LOGGER.warn(
            "STT: /v1/stt/stop response has no usable transcript (cycle={}, keys={}); timeline {}",
            currentCycleId,
            SttLogging.jsonResponseKeys(responseMap),
            cycleTimingSummary()
         );
      } else if (text.isBlank()) {
         LOGGER.warn(
            "STT: /v1/stt/stop returned empty transcript (cycle={}, keys={}); timeline {}",
            currentCycleId,
            SttLogging.jsonResponseKeys(responseMap),
            cycleTimingSummary()
         );
      } else {
         LOGGER.info(
            "STT: transcript received (cycle={}, len={}, preview=\"{}\"); timeline {}",
            currentCycleId,
            text.length(),
            SttLogging.messagePreview(text),
            cycleTimingSummary()
         );
         onSTTMessageGenerated(text);
      }
   }

   private static String extractTranscriptText(Map<String, JsonElement> responseMap) {
      if (responseMap != null && !responseMap.isEmpty()) {
         if (responseMap.containsKey("text")) {
            JsonElement el = responseMap.get("text");
            if (el != null && el.isJsonPrimitive()) {
               return el.getAsString();
            }

            LOGGER.warn("STT: 'text' field is not a string (type={})", el == null ? "null" : el.getClass().getSimpleName());
         }

         if (responseMap.containsKey("transcript")) {
            JsonElement el = responseMap.get("transcript");
            if (el != null && el.isJsonPrimitive()) {
               LOGGER.info("STT: using legacy 'transcript' key from stop response");
               return el.getAsString();
            }

            LOGGER.warn("STT: 'transcript' field is not a string (type={})", el == null ? "null" : el.getClass().getSimpleName());
         }

         return null;
      } else {
         return null;
      }
   }

   private static void resetPushToTalkState() {
      pushToTalkActive = false;
      isListening = false;
   }

   private static void onSTTMessageGenerated(String message) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         LOGGER.error("STT: cannot send user_message packet (local player is null); transcript lost: \"{}\"", SttLogging.messagePreview(message));
      } else if (mc.getConnection() == null) {
         LOGGER.error("STT: cannot send user_message packet (not connected to server); transcript lost: \"{}\"", SttLogging.messagePreview(message));
      } else {
         RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), mc.player.registryAccess());
         buf.writeUtf(message);
         mc.getConnection().send(NetworkManager.toPacket(Side.C2S, ResourceLocation.fromNamespaceAndPath("playerengine", "user_message"), buf));
         LOGGER.info("STT: sent user_message packet to server (len={}, preview=\"{}\")", message.length(), SttLogging.messagePreview(message));
      }
   }
}
