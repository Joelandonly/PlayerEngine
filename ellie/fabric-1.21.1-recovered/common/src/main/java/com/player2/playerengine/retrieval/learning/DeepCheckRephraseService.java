package com.player2.playerengine.retrieval.learning;

import com.google.gson.JsonObject;
import com.player2.playerengine.commands.base.CommandExecutor;
import com.player2.playerengine.player2api.AiTaskClass;
import com.player2.playerengine.player2api.ConversationHistory;
import com.player2.playerengine.player2api.Player2APIService;
import com.player2.playerengine.player2api.Player2PayerResolution;
import com.player2.playerengine.retrieval.RetrievalConfidence;
import com.player2.playerengine.retrieval.RetrievalHit;
import com.player2.playerengine.retrieval.ToolMetadataRegistry;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class DeepCheckRephraseService {
   private static final Logger LOGGER = LogManager.getLogger(DeepCheckRephraseService.class);

   private DeepCheckRephraseService() {
   }

   public static DeepCheckAttemptResult run(
      Player2APIService apiService,
      CommandExecutor commandExecutor,
      String ownerUtterance,
      List<RetrievalHit> firstPassHits,
      ToolMetadataRegistry registry,
      RetrievalConfidence confidence,
      Player2PayerResolution.ApiBillingContext billing
   ) {
      DeepCheckBudgetGate.SkipReason skip = DeepCheckBudgetGate.preflight(billing);
      if (skip != DeepCheckBudgetGate.SkipReason.NONE) {
         return DeepCheckAttemptResult.skipped(skip.name().toLowerCase());
      } else {
         return !DeepCheckBudgetGate.reserveDeepCheckSlot(billing)
            ? DeepCheckAttemptResult.skipped("deep_check_cap_skip")
            : runWithoutBudgetReserve(apiService, commandExecutor, ownerUtterance, firstPassHits, registry, confidence, billing);
      }
   }

   public static DeepCheckAttemptResult runWithoutBudgetReserve(
      Player2APIService apiService,
      CommandExecutor commandExecutor,
      String ownerUtterance,
      List<RetrievalHit> firstPassHits,
      ToolMetadataRegistry registry,
      RetrievalConfidence confidence,
      Player2PayerResolution.ApiBillingContext billing
   ) {
      String prompt = DeepCheckPrompt.buildUserPrompt(ownerUtterance, firstPassHits, registry, confidence);
      ConversationHistory history = new ConversationHistory("You output strict JSON only for command retrieval repair.", null);
      history.addUserMessage(prompt, apiService);

      try {
         String content = apiService.completeConversationToString(history, AiTaskClass.RERANKING);
         JsonObject json = DeepCheckJsonParser.parseOrExtract(content);
         if (json == null) {
            LOGGER.warn("[B5] deep-check malformed_json");
            return DeepCheckAttemptResult.skipped("malformed_json");
         } else {
            return DeepCheckResponseValidator.validate(json, commandExecutor);
         }
      } catch (Exception e) {
         String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
         if (msg.contains("budget_hard")) {
            return DeepCheckAttemptResult.skipped("budget_hard_skip");
         }

         LOGGER.warn("[B5] deep-check failed: {}", msg);
         return DeepCheckAttemptResult.skipped("deep_check_request_failed");
      }
   }
}
