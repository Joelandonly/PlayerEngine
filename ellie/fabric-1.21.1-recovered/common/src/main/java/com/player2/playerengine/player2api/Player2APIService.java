package com.player2.playerengine.player2api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.executor.BudgetFallbackBehavior;
import com.player2.playerengine.executor.BudgetTracker;
import com.player2.playerengine.executor.StopReason;
import com.player2.playerengine.player2api.auth.AuthenticationManager;
import com.player2.playerengine.player2api.auth.TokenStorage;
import com.player2.playerengine.player2api.config.BudgetThresholds;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;
import com.player2.playerengine.player2api.manager.HeartbeatManager;
import com.player2.playerengine.player2api.network.TtsClientPreferenceStore;
import com.player2.playerengine.player2api.utils.Player2HTTPUtils;
import com.player2.playerengine.player2api.utils.Utils;
import dev.architectury.networking.NetworkManager;
import dev.architectury.networking.NetworkManager.Side;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Player2APIService {
   private static final Logger LOGGER = LogManager.getLogger();
   private String clientId;
   private PlayerEngineController controller;
   private volatile Player2PayerResolution.ApiBillingContext activeBillingContext;

   public Player2APIService(PlayerEngineController controller, String clientId) {
      this.clientId = clientId;
      this.controller = controller;
   }

   public String getClientId() {
      return this.clientId;
   }

   public PlayerEngineController getController() {
      return this.controller;
   }

   public void setActiveBillingContext(Player2PayerResolution.ApiBillingContext ctx) {
      this.activeBillingContext = ctx;
   }

   public Player2PayerResolution.ApiBillingContext billingOrFallback() {
      Player2PayerResolution.ApiBillingContext ctx = this.activeBillingContext;
      return ctx != null ? ctx : Player2PayerResolution.resolve(this.controller, null, this.clientId);
   }

   private Map<String, JsonElement> api(String method, String endpoint, JsonObject body) throws Exception {
      return Player2ApiDispatcher.route(this.controller, this.clientId, method, endpoint, body, this.billingOrFallback());
   }

   @Deprecated
   public JsonObject completeConversation(ConversationHistory conversationHistory) throws Exception {
      return this.completeConversation(conversationHistory, AiTaskClass.DECISION);
   }

   public JsonObject completeConversation(ConversationHistory conversationHistory, AiTaskClass taskClass) throws Exception {
      JsonObject requestBody = new JsonObject();
      JsonArray messagesArray = LogEgressGuard.cappedMessages(conversationHistory.getListJSON(), "Player2APIService.completeConversation");
      String lastMessageForDebug = LogEgressGuard.capForModel(
         conversationHistory.getListJSON().get(conversationHistory.getListJSON().size() - 1).toString(), "debug"
      );
      requestBody.add("messages", messagesArray);
      LogEgressGuard.applyChatCompletionRequestCaps(requestBody, "Player2APIService.completeConversation");
      LOGGER.info("Called complete conversation (string) HTTP request, last msg={}", lastMessageForDebug);
      Map<String, JsonElement> responseMap = this.sendChatCompletionRequest(requestBody, taskClass);
      if (responseMap.containsKey("choices")) {
         JsonArray choices = responseMap.get("choices").getAsJsonArray();
         if (choices.size() != 0) {
            JsonObject messageObject = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (messageObject != null && messageObject.has("content")) {
               String content = messageObject.get("content").getAsString();
               LOGGER.info("Finished complete conversation HTTP request last msg={}", lastMessageForDebug);
               return Utils.parseCleanedJson(content);
            }
         }
      }

      throw new Exception("Invalid response format: " + responseMap.toString());
   }

   public String completeConversationToString(ConversationHistory conversationHistory, AiTaskClass taskClass) throws Exception {
      JsonObject requestBody = new JsonObject();
      JsonArray messagesArray = LogEgressGuard.cappedMessages(conversationHistory.getListJSON(), "Player2APIService.completeConversationToString");
      requestBody.add("messages", messagesArray);
      LogEgressGuard.applyChatCompletionRequestCaps(requestBody, "Player2APIService.completeConversationToString");
      String lastMessageForDebug = LogEgressGuard.capForModel(
         conversationHistory.getListJSON().get(conversationHistory.getListJSON().size() - 1).toString(), "debug"
      );
      LOGGER.info("Called complete conversation (string) HTTP request, last msg={}", lastMessageForDebug);
      Map<String, JsonElement> responseMap = this.sendChatCompletionRequest(requestBody, taskClass);
      if (responseMap.containsKey("choices")) {
         JsonArray choices = responseMap.get("choices").getAsJsonArray();
         if (choices.size() != 0) {
            JsonObject messageObject = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (messageObject != null && messageObject.has("content")) {
               LOGGER.info("Finished complete conversation HTTP request last msg={}", lastMessageForDebug);
               return messageObject.get("content").getAsString();
            }
         }
      }

      throw new Exception("Invalid response format: " + responseMap.toString());
   }

   @Deprecated
   public String completeConversationToString(ConversationHistory conversationHistory) throws Exception {
      return this.completeConversationToString(conversationHistory, AiTaskClass.SUMMARIZATION);
   }

   static boolean canUseNamedFallback(JoulesCache.JoulesSnapshot snapshot, BudgetThresholds thresholds) {
      return JoulesCache.isFreshPatronSnapshot(snapshot, thresholds);
   }

   private Map<String, JsonElement> sendChatCompletionRequest(JsonObject requestBody, AiTaskClass taskClass) throws Exception {
      Player2PayerResolution.ApiBillingContext billing = this.billingOrFallback();
      String billingKey = billing != null ? billing.billingKey() : null;
      Player2ServerRuntimeConfig config = Player2ServerConfigHolder.get();
      ServerPlayer payerToNotify = billing != null ? billing.onlinePayer() : null;
      MinecraftServer budgetServer = payerToNotify != null ? payerToNotify.getServer() : null;
      BudgetThresholds thresholds = BudgetThresholdsResolver.resolve(budgetServer, billing);
      BudgetTracker.BudgetCheckResult callResult = BudgetTracker.checkAndRecord(billingKey, thresholds);
      JoulesCache.maybeRefresh(this, billingKey, thresholds);
      JoulesCache.JoulesSnapshot joulesSnap = JoulesCache.get(billingKey).orElse(null);
      BudgetTracker.BudgetCheckResult joulesResult = JoulesCache.checkJoulesThreshold(joulesSnap, thresholds);
      BudgetTracker.BudgetCheckResult result = BudgetTracker.stricter(callResult, joulesResult);
      if (result == BudgetTracker.BudgetCheckResult.HARD_LIMIT) {
         boolean callWasFirst = callResult == BudgetTracker.BudgetCheckResult.HARD_LIMIT && BudgetTracker.shouldSendHardMessage(billingKey);
         boolean joulesWasFirst = joulesResult == BudgetTracker.BudgetCheckResult.HARD_LIMIT && JoulesCache.shouldSendHardMessage(billingKey);
         if ((callWasFirst || joulesWasFirst) && payerToNotify != null) {
            if (callResult == BudgetTracker.BudgetCheckResult.HARD_LIMIT) {
               payerToNotify.sendSystemMessage(
                  Component.translatable("message.playerengine.budget.hard_limit_calls", new Object[]{thresholds.getBudgetWindowMinutes()})
                     .withStyle(ChatFormatting.RED)
               );
            } else {
               payerToNotify.sendSystemMessage(Component.translatable("message.playerengine.budget.hard_limit_joules").withStyle(ChatFormatting.RED));
            }
         }

         throw new Exception(StopReason.BUDGET_HARD_LIMIT.name() + ":limit_reached");
      } else if (result != BudgetTracker.BudgetCheckResult.SOFT_LIMIT) {
         RoutingDecision routing = ModelTierRouter.resolve(taskClass, this, joulesSnap, thresholds, config);
         if (routing.isOnDevice()) {
            LOGGER.error("ModelTierRouter: RETRIEVAL task class reached sendChatCompletionRequest — caller bug; using Default");
            return this.api("POST", "/v1/chat/completions", requestBody);
         }

         if (routing.profileBaseUrlOverride().isPresent()) {
            Player2HTTPUtils.setProfileBaseUrlOverride(routing.profileBaseUrlOverride().get());

            try {
               return this.api("POST", "/v1/chat/completions", requestBody);
            } finally {
               Player2HTTPUtils.clearProfileBaseUrlOverride();
            }
         } else {
            return this.api("POST", "/v1/chat/completions", requestBody);
         }
      } else {
         String fallbackProfile = config.getFallbackProfile();
         boolean implicitDefaultFallback = fallbackProfile == null || fallbackProfile.isBlank();
         boolean isDedicatedProxy = config.isDedicatedClientProxy();
         if (config.getBudgetFallbackBehavior() == BudgetFallbackBehavior.HARD_STOP) {
            throw softLimitHardStop(billingKey, payerToNotify);
         }

         if (implicitDefaultFallback) {
            boolean shouldMsg = BudgetTracker.shouldSendSoftMessage(billingKey) || JoulesCache.shouldSendSoftMessage(billingKey);
            if (shouldMsg && payerToNotify != null) {
               payerToNotify.sendSystemMessage(
                  Component.translatable(
                        "message.playerengine.budget.soft_limit_switch", new Object[]{Component.translatable("message.playerengine.profile.default")}
                     )
                     .withStyle(ChatFormatting.YELLOW)
               );
            }

            return this.api("POST", "/v1/chat/completions", requestBody);
         } else if (!isDedicatedProxy && canUseNamedFallback(joulesSnap, thresholds)) {
            Optional<String> profileUrl = ProfileUrlResolver.resolve(this, fallbackProfile);
            if (profileUrl.isEmpty()) {
               LOGGER.warn("sendChatCompletionRequest: configured named fallback could not be resolved; stopping at soft limit");
               throw softLimitHardStop(billingKey, payerToNotify);
            }

            boolean shouldMsg = BudgetTracker.shouldSendSoftMessage(billingKey) || JoulesCache.shouldSendSoftMessage(billingKey);
            if (shouldMsg && payerToNotify != null) {
               payerToNotify.sendSystemMessage(
                  Component.translatable("message.playerengine.budget.soft_limit_switch", new Object[]{fallbackProfile}).withStyle(ChatFormatting.YELLOW)
               );
            }

            Player2HTTPUtils.setProfileBaseUrlOverride(profileUrl.get());

            try {
               return this.api("POST", "/v1/chat/completions", requestBody);
            } finally {
               Player2HTTPUtils.clearProfileBaseUrlOverride();
            }
         } else {
            throw softLimitHardStop(billingKey, payerToNotify);
         }
      }
   }

   private static Exception softLimitHardStop(String billingKey, ServerPlayer payerToNotify) {
      boolean shouldMsg = BudgetTracker.shouldSendSoftMessage(billingKey) || JoulesCache.shouldSendSoftMessage(billingKey);
      if (shouldMsg && payerToNotify != null) {
         payerToNotify.sendSystemMessage(Component.translatable("message.playerengine.budget.soft_limit_paused").withStyle(ChatFormatting.YELLOW));
      }

      return new Exception(StopReason.BUDGET_HARD_LIMIT.name() + ":soft_limit_hard_stop");
   }

   public void textToSpeech(String message, Character character, UUID botUuid, Consumer<Map<String, JsonElement>> onFinish) {
      this.textToSpeech(message, List.of(message), List.of(), character, botUuid, onFinish);
   }

   public void textToSpeech(
      String message,
      List<String> chunks,
      List<MarkerParser.SegmentBoundary> validBoundaries,
      Character character,
      UUID botUuid,
      Consumer<Map<String, JsonElement>> onFinish
   ) {
      try {
         Entity botEntity = this.controller.getPlayer();
         if (botEntity == null) {
            return;
         }

         MinecraftServer server = botEntity.getServer();
         if (server == null) {
            return;
         }

         double TTS_RANGE = 64.0;
         if (chunks == null || chunks.isEmpty()) {
            chunks = List.of(message);
         }

         if (validBoundaries == null) {
            validBoundaries = List.of();
         }

         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (TtsClientPreferenceStore.isTtsEnabled(player.getUUID())
               && player.level() == botEntity.level()
               && player.distanceTo(botEntity) <= TTS_RANGE) {
               RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
               buf.writeUtf(this.clientId);
               buf.writeUtf("");
               buf.writeUtf(message);
               buf.writeDouble(1.0);
               buf.writeVarInt(character.voiceIds().length);

               for (String id : character.voiceIds()) {
                  buf.writeUtf(id);
               }

               buf.writeUtf(botUuid.toString());
               buf.writeVarInt(chunks.size());

               for (String chunkText : chunks) {
                  buf.writeUtf(chunkText);
               }

               buf.writeVarInt(validBoundaries.size());

               for (MarkerParser.SegmentBoundary b : validBoundaries) {
                  buf.writeVarInt(b.chunkIndexAfter());
                  buf.writeUtf(b.action().name().toLowerCase(Locale.ROOT));
               }

               player.connection.send(NetworkManager.toPacket(Side.S2C, ResourceLocation.fromNamespaceAndPath("playerengine", "stream_tts"), buf));
            }
         }

         PlayerEngine.scheduleTtsFallbackTimer(server, botUuid, message == null ? 0 : message.length());
         onFinish.accept(null);
      } catch (Exception var9) {
         LOGGER.error("Error broadcasting TTS", var9);
      }
   }

   public void startSTT() {
      JsonObject requestBody = new JsonObject();
      requestBody.addProperty("timeout", 60);

      try {
         this.api("POST", "/v1/stt/start", requestBody);
      } catch (Exception var3) {
         System.err.println("[Player2APIService/startSTT]: Error" + var3.getMessage());
      }
   }

   public String stopSTT() {
      try {
         Map<String, JsonElement> responseMap = this.api("POST", "/v1/stt/stop", null);
         if (!responseMap.containsKey("text")) {
            throw new Exception("Could not find key 'text' in response");
         } else {
            return responseMap.get("text").getAsString();
         }
      } catch (Exception var2) {
         return var2.getMessage();
      }
   }

   public void trySendHeartbeat() {
      Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
      String ownerName = this.controller.getOwnerUsername();
      boolean tokenPresent = !TokenStorage.getToken(ownerName, this.clientId).isEmpty();
      boolean ownerOnline = this.controller.getOwner() instanceof ServerPlayer sp
         && this.controller.getPlayer().level().getServer().getPlayerList().getPlayer(sp.getUUID()) != null;
      if (Player2ServerConfigHolder.shouldSendServerControllerHeartbeat(cfg, tokenPresent, ownerOnline)) {
         if (HeartbeatManager.shouldHeartbeat(ownerName, this.clientId)) {
            if (tokenPresent) {
               this.sendHeartbeat();
            }

            HeartbeatManager.storeHeartbeatTime(ownerName, this.clientId);
         }
      }
   }

   public void sendHeartbeat() {
      AuthenticationManager.getExecutor().submit(() -> {
         try {
            System.out.println("Sending Heartbeat " + this.clientId);
            Player2PayerResolution.ApiBillingContext bill = Player2PayerResolution.resolve(this.controller, null, this.clientId);
            Player2ApiDispatcher.route(this.controller, this.clientId, "GET", "/v1/health", null, bill);
            System.out.println("Heartbeat Successful");
         } catch (Exception var2) {
            System.err.printf("Heartbeat Fail: %s\n", var2.getMessage());
         }
      });
   }

   public List<JsonObject> searchSchematics(String query) {
      try {
         JsonObject requestBody = new JsonObject();
         requestBody.addProperty("query", query);
         requestBody.addProperty("max_results", 10);
         Map<String, JsonElement> responseMap = this.api("POST", "/v1/minecraft/schematics/search", requestBody);
         JsonElement resultsJsonElement = responseMap.get("results");
         if (resultsJsonElement != null && resultsJsonElement.isJsonArray()) {
            JsonArray resultsJsonArray = resultsJsonElement.getAsJsonArray();
            List<JsonObject> schematics = new ArrayList<>();

            for (JsonElement voiceElement : resultsJsonArray) {
               JsonObject voiceObject = voiceElement.getAsJsonObject();
               schematics.add(voiceObject);
            }

            return schematics;
         }

         System.err.println("No results field array in response with keys: [" + String.join(",", responseMap.keySet()) + "]");
      } catch (Exception e) {
         System.err.println("Search schematics request failed: " + e.getMessage());
      }

      return Collections.emptyList();
   }

   public String getSchematicBinary(String schematicId) {
      try {
         Map<String, JsonElement> responseMap = this.api("GET", "/v1/minecraft/schematics/" + schematicId, null);
         JsonElement dataJsonElement = responseMap.get("data");
         if (dataJsonElement != null && dataJsonElement.isJsonPrimitive()) {
            return dataJsonElement.getAsString();
         }

         System.err.println("No data field primitive in response with keys: [" + String.join(",", responseMap.keySet()) + "]");
      } catch (Exception e) {
         System.err.println("Get schematic binary request failed: " + e.getMessage());
      }

      return "";
   }

   public String getGameData(String key) {
      try {
         Map<String, JsonElement> responseMap = this.api("GET", "/v1/games/" + this.clientId + "/data/user?key=" + key, null);
         JsonElement valueElement = responseMap.get("value");
         if (valueElement != null && valueElement.isJsonPrimitive()) {
            return valueElement.getAsString();
         }
      } catch (Exception e) {
         System.err.println("Get game data request failed: " + e.getMessage());
      }

      return null;
   }

   public boolean setGameData(String key, String value) {
      try {
         JsonObject requestBody = new JsonObject();
         requestBody.addProperty("key", key);
         requestBody.addProperty("value", value);
         Map<String, JsonElement> responseMap = this.api("PUT", "/v1/games/" + this.clientId + "/data/user", requestBody);
         JsonElement successElement = responseMap.get("success");
         if (successElement != null && successElement.isJsonPrimitive()) {
            return successElement.getAsBoolean();
         }
      } catch (Exception e) {
         System.err.println("Set game data request failed: " + e.getMessage());
      }

      return false;
   }
}
