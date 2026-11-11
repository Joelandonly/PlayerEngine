package com.player2.playerengine.player2api.config;

import com.player2.playerengine.executor.BudgetFallbackBehavior;

public class Player2ServerRuntimeConfig implements BudgetThresholds {
   private Player2PayerMode payerMode = Player2PayerMode.PROMPTER_PAYS;
   private boolean dedicatedClientProxy = false;
   private boolean ownerOfflineServerContinuation = false;
   private boolean callByNameChat = true;
   private String heartbeatClientId = "player2-ai-npc-minecraft";
   private int maxSpawnedCompanionsPerPlayer = 3;
   private int maxStoredCharacterIdsPerPlayer = 20;
   private int chatCompletionMaxOutputTokens = 10000;
   private int softBudgetCallsPerWindow = 0;
   private int hardBudgetCallsPerWindow = 0;
   private int budgetWindowMinutes = 60;
   private int softJoulesThreshold = 0;
   private int hardJoulesThreshold = 0;
   private int joulesRefreshIntervalSeconds = 300;
   private String fallbackProfile = null;
   private BudgetFallbackBehavior budgetFallbackBehavior = BudgetFallbackBehavior.HARD_STOP;
   private int ragTopK = 12;
   private boolean ragFallbackToFullList = true;
   private boolean ragLiveEnabled = true;
   private int ragMinGoalChars = 3;
   private boolean enableDeepCheckRephrase = false;
   private boolean enableAliasLearning = false;
   private boolean enableDeepCheckMessage = false;
   private int deepCheckMaxAttemptsPerTurn = 2;
   private int deepCheckCallsPerWindow = 3;
   private int deepCheckWindowMinutes = 10;
   private double deepCheckWeakBelowScore = 0.015;
   private double deepCheckWeakGapRatio = 0.15;
   private double deepCheckWeakTokenCoverage = 0.35;
   private boolean forceDeepCheckOnEmpty = true;
   private boolean enableGraphRagMemory = true;
   private boolean enableCompanionMood = true;
   private int memoryCallsPerWindow = 50;
   private int memoryWindowMinutes = 60;
   private int memoryExtractionBatchMin = 5;
   private int memoryExtractionBatchMax = 10;
   private int memoryExtractionLengthThreshold = 40;
   private int memoryLayer2MinChars = 20;
   private int memoryMaxHops = 2;
   private int memoryMaxEgoNodes = 64;
   private int memoryBlockCharCap = 1600;
   private double memoryMinConfidence = 0.2;
   private double memoryDecayBase = 0.995;
   private long memoryGameTimeUnit = 24000L;
   private int memoryRetrievalTopK = 5;
   private int reflectionImportanceThreshold = 150;
   private int relationshipSummaryCharCap = 280;
   private int importanceRubricVersion = 1;
   private boolean modIntelligenceEnabled = true;
   private boolean modIntelligenceInspectOnLaunch = true;
   private boolean modIntelligenceEnrichmentEnabled = false;
   private int modIntelligenceMaxInspectEntriesPerLaunch = 5000;
   private int modIntelligenceMaxEnrichmentCallsPerLaunch = 50;
   private int modIntelligenceMaxEnrichmentFailuresPerLaunch = 20;
   private int modIntelligenceQueryTopK = 12;
   private double modIntelligenceMinQueryConfidence = 0.5;
   private boolean modIntelligenceObserveRuntimeNbt = false;
   private int modIntelligenceMaxObservedSamplesPerSubject = 5;
   private boolean modIntelligenceBypassLargeQueueBudgetGate = false;
   private boolean modIntelligenceBypassModelBlacklist = false;
   private boolean botTtsPlaybackAckEnabled = false;
   private int bodylangMarkerPauseMs = 250;
   private boolean bodylangGaplessPrefetch = true;
   private boolean serverOverridesPlayerConfig = true;
   private boolean serverAutoRespawn = true;
   private Boolean serverBotPermadeath = null;
   private KeepInventoryOverride botKeepInventoryOverride = KeepInventoryOverride.FOLLOW_GAMERULE;

   public Player2PayerMode getPayerMode() {
      return this.payerMode == null ? Player2PayerMode.PROMPTER_PAYS : this.payerMode;
   }

   public void setPayerMode(Player2PayerMode payerMode) {
      this.payerMode = payerMode == null ? Player2PayerMode.PROMPTER_PAYS : payerMode;
   }

   public boolean isDedicatedClientProxy() {
      return this.dedicatedClientProxy;
   }

   public void setDedicatedClientProxy(boolean dedicatedClientProxy) {
      this.dedicatedClientProxy = dedicatedClientProxy;
   }

   public boolean isOwnerOfflineServerContinuation() {
      return this.ownerOfflineServerContinuation;
   }

   public void setOwnerOfflineServerContinuation(boolean ownerOfflineServerContinuation) {
      this.ownerOfflineServerContinuation = ownerOfflineServerContinuation;
   }

   public boolean isCallByNameChat() {
      return this.callByNameChat;
   }

   public void setCallByNameChat(boolean callByNameChat) {
      this.callByNameChat = callByNameChat;
   }

   public String getHeartbeatClientId() {
      return this.heartbeatClientId != null && !this.heartbeatClientId.isBlank() ? this.heartbeatClientId : "player2-ai-npc-minecraft";
   }

   public void setHeartbeatClientId(String heartbeatClientId) {
      this.heartbeatClientId = heartbeatClientId;
   }

   public int getMaxSpawnedCompanionsPerPlayer() {
      return this.maxSpawnedCompanionsPerPlayer;
   }

   public void setMaxSpawnedCompanionsPerPlayer(int maxSpawnedCompanionsPerPlayer) {
      this.maxSpawnedCompanionsPerPlayer = maxSpawnedCompanionsPerPlayer;
   }

   public int getMaxStoredCharacterIdsPerPlayer() {
      return this.maxStoredCharacterIdsPerPlayer;
   }

   public void setMaxStoredCharacterIdsPerPlayer(int maxStoredCharacterIdsPerPlayer) {
      this.maxStoredCharacterIdsPerPlayer = maxStoredCharacterIdsPerPlayer;
   }

   public int getChatCompletionMaxOutputTokens() {
      return this.chatCompletionMaxOutputTokens;
   }

   public void setChatCompletionMaxOutputTokens(int chatCompletionMaxOutputTokens) {
      this.chatCompletionMaxOutputTokens = chatCompletionMaxOutputTokens;
   }

   public int getChatCompletionMaxOutputTokensClamped() {
      int n = this.chatCompletionMaxOutputTokens;
      if (n < 1) {
         return 1;
      } else {
         return n > 100000 ? 100000 : n;
      }
   }

   @Override
   public int getSoftBudgetCallsPerWindow() {
      return this.softBudgetCallsPerWindow;
   }

   public void setSoftBudgetCallsPerWindow(int v) {
      this.softBudgetCallsPerWindow = Math.max(0, v);
   }

   @Override
   public int getHardBudgetCallsPerWindow() {
      return this.hardBudgetCallsPerWindow;
   }

   public void setHardBudgetCallsPerWindow(int v) {
      this.hardBudgetCallsPerWindow = Math.max(0, v);
   }

   @Override
   public int getBudgetWindowMinutes() {
      return this.budgetWindowMinutes;
   }

   public void setBudgetWindowMinutes(int v) {
      this.budgetWindowMinutes = v;
   }

   @Override
   public int getSoftJoulesThreshold() {
      return this.softJoulesThreshold;
   }

   public void setSoftJoulesThreshold(int v) {
      this.softJoulesThreshold = Math.max(0, v);
   }

   @Override
   public int getHardJoulesThreshold() {
      return this.hardJoulesThreshold;
   }

   public void setHardJoulesThreshold(int v) {
      this.hardJoulesThreshold = Math.max(0, v);
   }

   @Override
   public int getJoulesRefreshIntervalSeconds() {
      return this.joulesRefreshIntervalSeconds;
   }

   public void setJoulesRefreshIntervalSeconds(int v) {
      this.joulesRefreshIntervalSeconds = v;
   }

   public String getFallbackProfile() {
      return this.fallbackProfile != null && !this.fallbackProfile.isBlank() && !"Default".equalsIgnoreCase(this.fallbackProfile.trim())
         ? this.fallbackProfile
         : null;
   }

   public void setFallbackProfile(String v) {
      this.fallbackProfile = v != null && !v.isBlank() && !"Default".equalsIgnoreCase(v.trim()) ? v : null;
   }

   public BudgetFallbackBehavior getBudgetFallbackBehavior() {
      return this.budgetFallbackBehavior == null ? BudgetFallbackBehavior.HARD_STOP : this.budgetFallbackBehavior;
   }

   public void setBudgetFallbackBehavior(BudgetFallbackBehavior v) {
      this.budgetFallbackBehavior = v == null ? BudgetFallbackBehavior.HARD_STOP : v;
   }

   public int getRagTopK() {
      return this.ragTopK;
   }

   public void setRagTopK(int v) {
      this.ragTopK = v;
   }

   public int getRagTopKClamped() {
      int k = this.ragTopK;
      if (k < 1) {
         return 1;
      } else {
         return k > 50 ? 50 : k;
      }
   }

   public boolean isRagFallbackToFullList() {
      return this.ragFallbackToFullList;
   }

   public void setRagFallbackToFullList(boolean ragFallbackToFullList) {
      this.ragFallbackToFullList = ragFallbackToFullList;
   }

   public boolean isRagLiveEnabled() {
      return this.ragLiveEnabled;
   }

   public void setRagLiveEnabled(boolean ragLiveEnabled) {
      this.ragLiveEnabled = ragLiveEnabled;
   }

   public int getRagMinGoalChars() {
      return this.ragMinGoalChars;
   }

   public void setRagMinGoalChars(int v) {
      this.ragMinGoalChars = v;
   }

   public int getRagMinGoalCharsClamped() {
      int m = this.ragMinGoalChars;
      if (m < 1) {
         return 1;
      } else {
         return m > 16 ? 16 : m;
      }
   }

   public boolean isModIntelligenceEnabled() {
      return this.modIntelligenceEnabled;
   }

   public void setModIntelligenceEnabled(boolean v) {
      this.modIntelligenceEnabled = v;
   }

   public boolean isModIntelligenceInspectOnLaunch() {
      return this.modIntelligenceInspectOnLaunch;
   }

   public void setModIntelligenceInspectOnLaunch(boolean v) {
      this.modIntelligenceInspectOnLaunch = v;
   }

   public boolean isModIntelligenceEnrichmentEnabled() {
      return this.modIntelligenceEnrichmentEnabled;
   }

   public void setModIntelligenceEnrichmentEnabled(boolean v) {
      this.modIntelligenceEnrichmentEnabled = v;
   }

   public int getModIntelligenceMaxInspectEntriesPerLaunch() {
      return this.modIntelligenceMaxInspectEntriesPerLaunch;
   }

   public void setModIntelligenceMaxInspectEntriesPerLaunch(int v) {
      this.modIntelligenceMaxInspectEntriesPerLaunch = v;
   }

   public int getModIntelligenceMaxEnrichmentCallsPerLaunch() {
      return this.modIntelligenceMaxEnrichmentCallsPerLaunch;
   }

   public void setModIntelligenceMaxEnrichmentCallsPerLaunch(int v) {
      this.modIntelligenceMaxEnrichmentCallsPerLaunch = v;
   }

   public int getModIntelligenceMaxEnrichmentFailuresPerLaunch() {
      return this.modIntelligenceMaxEnrichmentFailuresPerLaunch;
   }

   public void setModIntelligenceMaxEnrichmentFailuresPerLaunch(int v) {
      this.modIntelligenceMaxEnrichmentFailuresPerLaunch = v;
   }

   public int getModIntelligenceQueryTopK() {
      return this.modIntelligenceQueryTopK;
   }

   public void setModIntelligenceQueryTopK(int v) {
      this.modIntelligenceQueryTopK = v;
   }

   public int getModIntelligenceQueryTopKClamped() {
      int k = this.modIntelligenceQueryTopK;
      if (k < 1) {
         return 1;
      } else {
         return k > 50 ? 50 : k;
      }
   }

   public double getModIntelligenceMinQueryConfidence() {
      return this.modIntelligenceMinQueryConfidence;
   }

   public void setModIntelligenceMinQueryConfidence(double v) {
      this.modIntelligenceMinQueryConfidence = v;
   }

   public boolean isModIntelligenceObserveRuntimeNbt() {
      return this.modIntelligenceObserveRuntimeNbt;
   }

   public void setModIntelligenceObserveRuntimeNbt(boolean v) {
      this.modIntelligenceObserveRuntimeNbt = v;
   }

   public int getModIntelligenceMaxObservedSamplesPerSubject() {
      return this.modIntelligenceMaxObservedSamplesPerSubject;
   }

   public void setModIntelligenceMaxObservedSamplesPerSubject(int v) {
      this.modIntelligenceMaxObservedSamplesPerSubject = v;
   }

   public boolean isModIntelligenceBypassLargeQueueBudgetGate() {
      return this.modIntelligenceBypassLargeQueueBudgetGate;
   }

   public void setModIntelligenceBypassLargeQueueBudgetGate(boolean v) {
      this.modIntelligenceBypassLargeQueueBudgetGate = v;
   }

   public boolean isModIntelligenceBypassModelBlacklist() {
      return this.modIntelligenceBypassModelBlacklist;
   }

   public void setModIntelligenceBypassModelBlacklist(boolean v) {
      this.modIntelligenceBypassModelBlacklist = v;
   }

   public boolean isBotTtsPlaybackAckEnabled() {
      return this.botTtsPlaybackAckEnabled;
   }

   public void setBotTtsPlaybackAckEnabled(boolean botTtsPlaybackAckEnabled) {
      this.botTtsPlaybackAckEnabled = botTtsPlaybackAckEnabled;
   }

   public int getBodylangMarkerPauseMs() {
      return this.bodylangMarkerPauseMs;
   }

   public void setBodylangMarkerPauseMs(int v) {
      if (v < 0) {
         v = 0;
      } else if (v > 2000) {
         v = 2000;
      }

      this.bodylangMarkerPauseMs = v;
   }

   public boolean isBodylangGaplessPrefetch() {
      return this.bodylangGaplessPrefetch;
   }

   public void setBodylangGaplessPrefetch(boolean bodylangGaplessPrefetch) {
      this.bodylangGaplessPrefetch = bodylangGaplessPrefetch;
   }

   public boolean isServerOverridesPlayerConfig() {
      return this.serverOverridesPlayerConfig;
   }

   public void setServerOverridesPlayerConfig(boolean serverOverridesPlayerConfig) {
      this.serverOverridesPlayerConfig = serverOverridesPlayerConfig;
   }

   public boolean isServerAutoRespawn() {
      return this.serverAutoRespawn;
   }

   public void setServerAutoRespawn(boolean serverAutoRespawn) {
      this.serverAutoRespawn = serverAutoRespawn;
   }

   public Boolean getServerBotPermadeath() {
      return this.serverBotPermadeath;
   }

   public void setServerBotPermadeath(Boolean serverBotPermadeath) {
      this.serverBotPermadeath = serverBotPermadeath;
   }

   public KeepInventoryOverride getBotKeepInventoryOverride() {
      return this.botKeepInventoryOverride == null ? KeepInventoryOverride.FOLLOW_GAMERULE : this.botKeepInventoryOverride;
   }

   public void setBotKeepInventoryOverride(KeepInventoryOverride botKeepInventoryOverride) {
      this.botKeepInventoryOverride = botKeepInventoryOverride == null ? KeepInventoryOverride.FOLLOW_GAMERULE : botKeepInventoryOverride;
   }

   public boolean isEnableDeepCheckRephrase() {
      return this.enableDeepCheckRephrase;
   }

   public void setEnableDeepCheckRephrase(boolean v) {
      this.enableDeepCheckRephrase = v;
   }

   public boolean isEnableAliasLearning() {
      return this.enableAliasLearning;
   }

   public void setEnableAliasLearning(boolean v) {
      this.enableAliasLearning = v;
   }

   public boolean isEnableDeepCheckMessage() {
      return this.enableDeepCheckMessage;
   }

   public void setEnableDeepCheckMessage(boolean v) {
      this.enableDeepCheckMessage = v;
   }

   public int getDeepCheckMaxAttemptsPerTurn() {
      return this.deepCheckMaxAttemptsPerTurn;
   }

   public void setDeepCheckMaxAttemptsPerTurn(int v) {
      this.deepCheckMaxAttemptsPerTurn = v;
   }

   public int getDeepCheckMaxAttemptsPerTurnClamped() {
      int n = this.deepCheckMaxAttemptsPerTurn;
      if (n < 0) {
         return 0;
      } else {
         return n > 3 ? 3 : n;
      }
   }

   public int getDeepCheckCallsPerWindow() {
      return this.deepCheckCallsPerWindow;
   }

   public void setDeepCheckCallsPerWindow(int v) {
      this.deepCheckCallsPerWindow = v;
   }

   public int getDeepCheckCallsPerWindowClamped() {
      int n = this.deepCheckCallsPerWindow;
      if (n < 0) {
         return 0;
      } else {
         return n > 100 ? 100 : n;
      }
   }

   public int getDeepCheckWindowMinutes() {
      return this.deepCheckWindowMinutes;
   }

   public void setDeepCheckWindowMinutes(int v) {
      this.deepCheckWindowMinutes = v;
   }

   public int getDeepCheckWindowMinutesClamped() {
      int m = this.deepCheckWindowMinutes;
      if (m < 1) {
         return 1;
      } else {
         return m > 1440 ? 1440 : m;
      }
   }

   public double getDeepCheckWeakBelowScore() {
      return this.deepCheckWeakBelowScore;
   }

   public void setDeepCheckWeakBelowScore(double v) {
      this.deepCheckWeakBelowScore = v;
   }

   public double getDeepCheckWeakBelowScoreClamped() {
      double s = this.deepCheckWeakBelowScore;
      if (s < 0.0) {
         return 0.0;
      } else {
         return s > 1.0 ? 1.0 : s;
      }
   }

   public double getDeepCheckWeakGapRatio() {
      return this.deepCheckWeakGapRatio;
   }

   public void setDeepCheckWeakGapRatio(double v) {
      this.deepCheckWeakGapRatio = v;
   }

   public double getDeepCheckWeakGapRatioClamped() {
      double g = this.deepCheckWeakGapRatio;
      if (g < 0.0) {
         return 0.0;
      } else {
         return g > 1.0 ? 1.0 : g;
      }
   }

   public double getDeepCheckWeakTokenCoverage() {
      return this.deepCheckWeakTokenCoverage;
   }

   public void setDeepCheckWeakTokenCoverage(double v) {
      this.deepCheckWeakTokenCoverage = v;
   }

   public double getDeepCheckWeakTokenCoverageClamped() {
      double c = this.deepCheckWeakTokenCoverage;
      if (c < 0.0) {
         return 0.0;
      } else {
         return c > 1.0 ? 1.0 : c;
      }
   }

   public boolean isForceDeepCheckOnEmpty() {
      return this.forceDeepCheckOnEmpty;
   }

   public void setForceDeepCheckOnEmpty(boolean v) {
      this.forceDeepCheckOnEmpty = v;
   }

   public boolean isEnableGraphRagMemory() {
      return this.enableGraphRagMemory;
   }

   public void setEnableGraphRagMemory(boolean v) {
      this.enableGraphRagMemory = v;
   }

   public boolean isEnableCompanionMood() {
      return this.enableCompanionMood;
   }

   public void setEnableCompanionMood(boolean v) {
      this.enableCompanionMood = v;
   }

   public int getMemoryCallsPerWindow() {
      return this.memoryCallsPerWindow;
   }

   public void setMemoryCallsPerWindow(int v) {
      this.memoryCallsPerWindow = v;
   }

   public int getMemoryCallsPerWindowClamped() {
      int n = this.memoryCallsPerWindow;
      if (n < 0) {
         return 0;
      } else {
         return n > 1000 ? 1000 : n;
      }
   }

   public int getMemoryWindowMinutes() {
      return this.memoryWindowMinutes;
   }

   public void setMemoryWindowMinutes(int v) {
      this.memoryWindowMinutes = v;
   }

   public int getMemoryWindowMinutesClamped() {
      int m = this.memoryWindowMinutes;
      if (m < 1) {
         return 1;
      } else {
         return m > 1440 ? 1440 : m;
      }
   }

   public int getMemoryExtractionBatchMin() {
      return this.memoryExtractionBatchMin;
   }

   public void setMemoryExtractionBatchMin(int v) {
      this.memoryExtractionBatchMin = v;
   }

   public int getMemoryExtractionBatchMax() {
      return this.memoryExtractionBatchMax;
   }

   public void setMemoryExtractionBatchMax(int v) {
      this.memoryExtractionBatchMax = v;
   }

   public int getMemoryExtractionLengthThreshold() {
      return this.memoryExtractionLengthThreshold;
   }

   public void setMemoryExtractionLengthThreshold(int v) {
      this.memoryExtractionLengthThreshold = v;
   }

   public int getMemoryLayer2MinChars() {
      return this.memoryLayer2MinChars;
   }

   public void setMemoryLayer2MinChars(int v) {
      this.memoryLayer2MinChars = v;
   }

   public int getMemoryLayer2MinCharsClamped() {
      int n = this.memoryLayer2MinChars;
      if (n < 0) {
         return 0;
      } else {
         return n > 200 ? 200 : n;
      }
   }

   public int getMemoryMaxHops() {
      return this.memoryMaxHops;
   }

   public void setMemoryMaxHops(int v) {
      this.memoryMaxHops = v;
   }

   public int getMemoryMaxHopsClamped() {
      int n = this.memoryMaxHops;
      if (n < 1) {
         return 1;
      } else {
         return n > 6 ? 6 : n;
      }
   }

   public int getMemoryMaxEgoNodes() {
      return this.memoryMaxEgoNodes;
   }

   public void setMemoryMaxEgoNodes(int v) {
      this.memoryMaxEgoNodes = v;
   }

   public int getMemoryMaxEgoNodesClamped() {
      int n = this.memoryMaxEgoNodes;
      if (n < 1) {
         return 1;
      } else {
         return n > 4096 ? 4096 : n;
      }
   }

   public int getMemoryBlockCharCap() {
      return this.memoryBlockCharCap;
   }

   public void setMemoryBlockCharCap(int v) {
      this.memoryBlockCharCap = v;
   }

   public int getMemoryBlockCharCapClamped() {
      int n = this.memoryBlockCharCap;
      if (n < 0) {
         return 0;
      } else {
         return n > 8000 ? 8000 : n;
      }
   }

   public double getMemoryMinConfidence() {
      return this.memoryMinConfidence;
   }

   public void setMemoryMinConfidence(double v) {
      this.memoryMinConfidence = v;
   }

   public double getMemoryMinConfidenceClamped() {
      double d = this.memoryMinConfidence;
      if (d < 0.0) {
         return 0.0;
      } else {
         return d > 1.0 ? 1.0 : d;
      }
   }

   public double getMemoryDecayBase() {
      return this.memoryDecayBase;
   }

   public void setMemoryDecayBase(double v) {
      this.memoryDecayBase = v;
   }

   public double getMemoryDecayBaseClamped() {
      double d = this.memoryDecayBase;
      return !(d <= 0.0) && !(d > 1.0) ? d : 0.995;
   }

   public long getMemoryGameTimeUnit() {
      return this.memoryGameTimeUnit;
   }

   public void setMemoryGameTimeUnit(long v) {
      this.memoryGameTimeUnit = v;
   }

   public long getMemoryGameTimeUnitClamped() {
      return this.memoryGameTimeUnit < 1L ? 1L : this.memoryGameTimeUnit;
   }

   public int getMemoryRetrievalTopK() {
      return this.memoryRetrievalTopK;
   }

   public void setMemoryRetrievalTopK(int v) {
      this.memoryRetrievalTopK = v;
   }

   public int getMemoryRetrievalTopKClamped() {
      int n = this.memoryRetrievalTopK;
      if (n < 1) {
         return 1;
      } else {
         return n > 50 ? 50 : n;
      }
   }

   public int getReflectionImportanceThreshold() {
      return this.reflectionImportanceThreshold;
   }

   public void setReflectionImportanceThreshold(int v) {
      this.reflectionImportanceThreshold = v;
   }

   public int getReflectionImportanceThresholdClamped() {
      int n = this.reflectionImportanceThreshold;
      if (n < 1) {
         return 1;
      } else {
         return n > 100000 ? 100000 : n;
      }
   }

   public int getRelationshipSummaryCharCap() {
      return this.relationshipSummaryCharCap;
   }

   public void setRelationshipSummaryCharCap(int v) {
      this.relationshipSummaryCharCap = v;
   }

   public int getRelationshipSummaryCharCapClamped() {
      int n = this.relationshipSummaryCharCap;
      if (n < 0) {
         return 0;
      } else {
         return n > 1000 ? 1000 : n;
      }
   }

   public int getImportanceRubricVersion() {
      return this.importanceRubricVersion;
   }

   public void setImportanceRubricVersion(int v) {
      this.importanceRubricVersion = v;
   }
}
