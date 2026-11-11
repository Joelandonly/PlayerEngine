package com.player2.playerengine.player2api;

import com.player2.playerengine.player2api.config.BudgetThresholds;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ModelTierRouter {
   private static final Logger LOGGER = LogManager.getLogger(ModelTierRouter.class);

   private ModelTierRouter() {
   }

   public static RoutingDecision resolve(
      AiTaskClass taskClass,
      Player2APIService apiService,
      JoulesCache.JoulesSnapshot snapshot,
      BudgetThresholds thresholds,
      Player2ServerRuntimeConfig serverConfig
   ) {
      boolean mayUseNamed = isPlanningOrDecision(taskClass)
         && JoulesCache.isFreshPatronSnapshot(snapshot, thresholds)
         && (serverConfig == null || !serverConfig.isDedicatedClientProxy());
      Optional<String> soleNamed = mayUseNamed && apiService != null ? ProfileUrlResolver.getSoleNamedProfileBaseUrl(apiService) : Optional.empty();
      return resolveWithRule(taskClass, soleNamed, snapshot, thresholds, serverConfig).decision();
   }

   public static RoutingResult resolveWithRule(
      AiTaskClass taskClass,
      Optional<String> soleNamedProfileBaseUrl,
      JoulesCache.JoulesSnapshot snapshot,
      BudgetThresholds thresholds,
      Player2ServerRuntimeConfig serverConfig
   ) {
      if (taskClass == AiTaskClass.RETRIEVAL) {
         return new RoutingResult(RoutingDecision.onDevice(), 1);
      }

      if (serverConfig != null && serverConfig.isDedicatedClientProxy()) {
         LOGGER.debug("ModelTierRouter: rule 2 — dedicatedClientProxy=true → Default");
         return new RoutingResult(RoutingDecision.defaultProfile(), 2);
      }

      if (taskClass != AiTaskClass.RERANKING && taskClass != AiTaskClass.SUMMARIZATION) {
         if (taskClass == AiTaskClass.PLANNING || taskClass == AiTaskClass.DECISION) {
            if (snapshot != null
               && thresholds != null
               && thresholds.getSoftJoulesThreshold() > 0
               && snapshot.joulesDisplay() < thresholds.getSoftJoulesThreshold()) {
               LOGGER.debug(
                  "ModelTierRouter: rule 4 — PLANNING/DECISION demoted to Default (joules {} < soft {})",
                  snapshot.joulesDisplay(),
                  thresholds.getSoftJoulesThreshold()
               );
               return new RoutingResult(RoutingDecision.defaultProfile(), 4);
            }

            if (soleNamedProfileBaseUrl != null && soleNamedProfileBaseUrl.isPresent() && JoulesCache.isFreshPatronSnapshot(snapshot, thresholds)) {
               LOGGER.debug("ModelTierRouter: rule 5 — {} → named profile", taskClass);
               return new RoutingResult(RoutingDecision.namedProfile(soleNamedProfileBaseUrl.get()), 5);
            }
         }

         return new RoutingResult(RoutingDecision.defaultProfile(), 6);
      } else {
         return new RoutingResult(RoutingDecision.defaultProfile(), 3);
      }
   }

   private static boolean isPlanningOrDecision(AiTaskClass taskClass) {
      return taskClass == AiTaskClass.PLANNING || taskClass == AiTaskClass.DECISION;
   }

   public static String describeOutcome(RoutingDecision decision) {
      if (decision.isOnDevice()) {
         return "on-device (no HTTP)";
      } else {
         return decision.profileBaseUrlOverride().isPresent() ? "named profile: " + decision.profileBaseUrlOverride().get() : "Default (no URL override)";
      }
   }
}
