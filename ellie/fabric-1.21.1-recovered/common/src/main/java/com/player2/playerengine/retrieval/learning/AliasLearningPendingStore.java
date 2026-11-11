package com.player2.playerengine.retrieval.learning;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public final class AliasLearningPendingStore {
   private static final Map<String, AliasLearningCandidate> PENDING = new ConcurrentHashMap<>();

   private AliasLearningPendingStore() {
   }

   public static String key(UUID botUuid, String toolId, String candidateId) {
      return botUuid + ":" + toolId + ":" + candidateId;
   }

   public static void put(AliasLearningCandidate candidate) {
      PENDING.put(key(candidate.botUuid(), candidate.toolId(), candidate.candidateId()), candidate);
   }

   public static Optional<AliasLearningCandidate> remove(UUID botUuid, String toolId, String candidateId) {
      return Optional.ofNullable(PENDING.remove(key(botUuid, toolId, candidateId)));
   }

   public static Optional<AliasLearningCandidate> findByBotAndTool(UUID botUuid, String toolId) {
      String prefix = botUuid + ":" + toolId + ":";

      for (Entry<String, AliasLearningCandidate> e : PENDING.entrySet()) {
         if (e.getKey().startsWith(prefix)) {
            return Optional.of(e.getValue());
         }
      }

      return Optional.empty();
   }
}
