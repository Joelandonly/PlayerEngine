package com.player2.playerengine.retrieval.learning;

import com.player2.playerengine.commands.base.CommandExecutor;
import com.player2.playerengine.player2api.Player2APIService;
import com.player2.playerengine.player2api.Player2PayerResolution;
import com.player2.playerengine.retrieval.RetrievalConfidenceThresholds;
import com.player2.playerengine.retrieval.RetrievalResult;
import com.player2.playerengine.retrieval.ToolRetriever;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class RagDeepCheckPipeline {
   private static final Logger LOGGER = LogManager.getLogger(RagDeepCheckPipeline.class);

   private RagDeepCheckPipeline() {
   }

   public static RagDeepCheckPipeline.ApplyResult apply(
      ToolRetriever retriever,
      String goalText,
      int topK,
      Set<String> categoryFilter,
      RetrievalConfidenceThresholds thresholds,
      RetrievalResult firstPass,
      Player2APIService apiService,
      CommandExecutor commandExecutor,
      Player2PayerResolution.ApiBillingContext billing,
      boolean reserveSlot,
      String logContext
   ) {
      if (reserveSlot) {
         DeepCheckBudgetGate.SkipReason skip = DeepCheckBudgetGate.preflight(billing);
         if (skip != DeepCheckBudgetGate.SkipReason.NONE) {
            LOGGER.debug("[B5] {} deep-check skipped: {}", logContext, skip.name().toLowerCase());
            return RagDeepCheckPipeline.ApplyResult.skipped(firstPass, skip.name());
         }

         if (!DeepCheckBudgetGate.reserveDeepCheckSlot(billing)) {
            LOGGER.debug("[B5] {} deep-check cap skip", logContext);
            return RagDeepCheckPipeline.ApplyResult.skipped(firstPass, "deep_check_cap_skip");
         }
      }

      DeepCheckAttemptResult attempt = DeepCheckRephraseService.runWithoutBudgetReserve(
         apiService, commandExecutor, goalText, firstPass.hits(), retriever.getRegistry(), firstPass.confidence(), billing
      );
      if (attempt.response().isEmpty()) {
         LOGGER.debug("[B5] {} deep-check skipped: {}", logContext, attempt.skipReason());
         return RagDeepCheckPipeline.ApplyResult.skipped(firstPass, attempt.skipReason());
      }

      DeepCheckResponse response = attempt.response().get();
      RetrievalResult best = mergeRetries(retriever, goalText, topK, categoryFilter, thresholds, firstPass, response.retryQueries());
      String source = best == firstPass ? "first_pass" : "retry_query";
      Optional<AliasLearnSuggestion> learn = response.learn();
      if (learn.isPresent() && !best.hits().isEmpty()) {
         String topId = best.hits().get(0).toolId();
         if (!topId.equals(learn.get().toolId())) {
            LOGGER.debug("[B5] {} learn conflict_retry_top toolId={} top={}", logContext, learn.get().toolId(), topId);
            learn = Optional.empty();
         }
      }

      LOGGER.info("[B5] {} deep-check ok source={} retries={}", logContext, source, response.retryQueries().size());
      return new RagDeepCheckPipeline.ApplyResult(best, source, learn, true, true);
   }

   public static RetrievalResult mergeRetries(
      ToolRetriever retriever,
      String goalText,
      int topK,
      Set<String> categoryFilter,
      RetrievalConfidenceThresholds thresholds,
      RetrievalResult firstPass,
      List<String> retryQueries
   ) {
      RetrievalResult best = firstPass;

      for (String query : retryQueries) {
         RetrievalResult retry = retriever.retrieveWithConfidence(query, topK, categoryFilter, thresholds);
         if (!retry.hits().isEmpty() && (best.hits().isEmpty() || retry.confidence().topScore() > best.confidence().topScore())) {
            best = retry;
         }
      }

      return best;
   }

   public record ApplyResult(
      RetrievalResult activeResult, String promptSource, Optional<AliasLearnSuggestion> learnSuggestion, boolean deepCheckAttempted, boolean success
   ) {
      public static RagDeepCheckPipeline.ApplyResult noOp(RetrievalResult firstPass) {
         return new RagDeepCheckPipeline.ApplyResult(firstPass, "first_pass", Optional.empty(), false, false);
      }

      public static RagDeepCheckPipeline.ApplyResult skipped(RetrievalResult firstPass, String skipReason) {
         return new RagDeepCheckPipeline.ApplyResult(firstPass, "first_pass", Optional.empty(), true, false);
      }
   }
}
