package com.player2.playerengine.memory.budget;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.player2.playerengine.executor.BudgetTracker;
import com.player2.playerengine.player2api.AiTaskClass;
import com.player2.playerengine.player2api.BudgetThresholdsResolver;
import com.player2.playerengine.player2api.ConversationHistory;
import com.player2.playerengine.player2api.JoulesCache;
import com.player2.playerengine.player2api.LogEgressGuard;
import com.player2.playerengine.player2api.ModelTierRouter;
import com.player2.playerengine.player2api.Player2PayerResolution;
import com.player2.playerengine.player2api.RoutingDecision;
import com.player2.playerengine.player2api.config.BudgetThresholds;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;
import com.player2.playerengine.player2api.utils.Player2HTTPUtils;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

public final class MemoryLlmClient {
   public static final ExecutorService MEMORY_EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
      @Override
      public Thread newThread(Runnable r) {
         Thread t = new Thread(r, "memory-llm");
         t.setDaemon(true);
         return t;
      }
   });

   private MemoryLlmClient() {
   }

   public static String complete(MinecraftServer server, Player2PayerResolution.ApiBillingContext ownerBilling, ConversationHistory history) throws Exception {
      if (ownerBilling != null && ownerBilling.billingKey() != null) {
         Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
         String clientId = cfg.getHeartbeatClientId();
         BudgetThresholds thresholds = BudgetThresholdsResolver.resolve(server, ownerBilling);
         BudgetTracker.BudgetCheckResult callResult = BudgetTracker.checkAndRecord(ownerBilling.billingKey(), thresholds);
         JoulesCache.JoulesSnapshot joulesSnap = JoulesCache.get(ownerBilling.billingKey()).orElse(null);
         BudgetTracker.BudgetCheckResult joulesResult = JoulesCache.checkJoulesThreshold(joulesSnap, thresholds);
         if (BudgetTracker.stricter(callResult, joulesResult) == BudgetTracker.BudgetCheckResult.HARD_LIMIT) {
            throw new IllegalStateException("budget_hard_limit");
         }

         JsonObject requestBody = new JsonObject();
         JsonArray messages = LogEgressGuard.cappedMessages(history.getListJSON(), "MemoryLlmClient.complete");
         requestBody.add("messages", messages);
         LogEgressGuard.applyChatCompletionRequestCaps(requestBody, "MemoryLlmClient.complete");
         JsonObject responseFormat = new JsonObject();
         responseFormat.addProperty("type", "json_object");
         requestBody.add("response_format", responseFormat);
         RoutingDecision routing = ModelTierRouter.resolve(AiTaskClass.SUMMARIZATION, null, joulesSnap, thresholds, cfg);
         Map<String, JsonElement> response;
         if (routing.profileBaseUrlOverride().isPresent()) {
            Player2HTTPUtils.setProfileBaseUrlOverride(routing.profileBaseUrlOverride().get());

            try {
               response = dispatchCompletion(ownerBilling, clientId, requestBody);
            } finally {
               Player2HTTPUtils.clearProfileBaseUrlOverride();
            }
         } else {
            response = dispatchCompletion(ownerBilling, clientId, requestBody);
         }

         if (response.containsKey("choices")) {
            JsonElement choicesEl = response.get("choices");
            if (choicesEl.isJsonArray()) {
               JsonArray choices = choicesEl.getAsJsonArray();
               if (!choices.isEmpty()) {
                  JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                  if (message != null && message.has("content")) {
                     return message.get("content").getAsString();
                  }
               }
            }
         }

         throw new IllegalStateException("invalid_response keys=" + response.keySet());
      } else {
         throw new IllegalStateException("billing_unavailable");
      }
   }

   public static String completeConversationToString(
      MinecraftServer server, Player2PayerResolution.ApiBillingContext ownerBilling, ConversationHistory history, AiTaskClass taskClass
   ) throws Exception {
      return complete(server, ownerBilling, history);
   }

   private static Map<String, JsonElement> dispatchCompletion(Player2PayerResolution.ApiBillingContext billing, String clientId, JsonObject requestBody) throws Exception {
      if (billing.useStoredToken() && billing.storedTokenUsername() != null) {
         return Player2HTTPUtils.sendRequestWithStoredToken(billing.storedTokenUsername(), clientId, "/v1/chat/completions", "POST", requestBody, 600000);
      } else {
         ServerPlayer payer = billing.onlinePayer();
         if (payer != null) {
            return Player2HTTPUtils.sendRequest(payer, clientId, "/v1/chat/completions", "POST", requestBody, 600000);
         } else {
            throw new IllegalStateException("billing_unavailable");
         }
      }
   }
}
