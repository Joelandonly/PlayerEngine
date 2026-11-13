package com.player2.playerengine.player2api;

import com.google.gson.JsonObject;
import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.memory.MemoryScope;
import com.player2.playerengine.memory.MemoryStore;
import com.player2.playerengine.memory.MemoryStoreRegistry;
import com.player2.playerengine.memory.budget.Layer3Context;
import com.player2.playerengine.memory.budget.MemoryGate;
import com.player2.playerengine.memory.budget.MemoryGateDecision;
import com.player2.playerengine.memory.ingest.MemoryIngestionService;
import com.player2.playerengine.memory.retrieval.MemoryRetrievalResult;
import com.player2.playerengine.memory.retrieval.MemoryRetriever;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;
import com.player2.playerengine.player2api.mood.CompanionMood;
import com.player2.playerengine.player2api.mood.MoodMemoryRule;
import com.player2.playerengine.player2api.mood.MoodUpdate;
import com.player2.playerengine.player2api.status.AgentStatus;
import com.player2.playerengine.player2api.status.StatusUtils;
import com.player2.playerengine.player2api.status.WorldStatus;
import com.player2.playerengine.player2api.utils.SttLogging;
import com.player2.playerengine.player2api.utils.Utils;
import com.player2.playerengine.retrieval.RagDeepSearchCommands;
import com.player2.playerengine.retrieval.RagIndex;
import com.player2.playerengine.retrieval.RagPromptBuilder;
import com.player2.playerengine.retrieval.RetrievalConfidenceThresholds;
import com.player2.playerengine.retrieval.RetrievalHit;
import com.player2.playerengine.retrieval.RetrievalResult;
import com.player2.playerengine.retrieval.ToolRetriever;
import com.player2.playerengine.retrieval.learning.AliasLearnSuggestion;
import com.player2.playerengine.retrieval.learning.AliasLearningService;
import com.player2.playerengine.retrieval.learning.DeepCheckBudgetGate;
import com.player2.playerengine.retrieval.learning.RagDeepCheckCoordinator;
import com.player2.playerengine.retrieval.learning.RagDeepCheckPipeline;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AgentConversationData {
   private static short MAX_EVENT_QUEUE_SIZE = 10;
   private static final int MAX_DEFERRED_INFO_QUEUE_SIZE = 4;
   private static final int MAX_DEFERRED_INFO_MESSAGE_LENGTH = 512;
   public static final String INFO_RESULT_NOTE_PREFIX = "[info-result] ";
   public static final Logger LOGGER = LogManager.getLogger();
   private final PlayerEngineController mod;
   private final Deque<Event> eventQueue = new ConcurrentLinkedDeque<>();
   private final Deque<Event.InfoMessage> deferredInfoQueue = new ConcurrentLinkedDeque<>();
   private long lastProcessTime = 0L;
   private static final long NO_ACTIVE_TURN = Long.MIN_VALUE;
   private volatile boolean isProcessing = false;
   private long activeTurnTicket = Long.MIN_VALUE;
   private boolean enabled = true;
   private boolean isGreetingResponse = true;
   private boolean shouldIgnoreGreetingDance = true;
   private MessageBuffer playerEngineMsgBuffer = new MessageBuffer(10);
   private String chainInitiatorUsername;
   private List<RetrievalHit> cachedRetrievalHits = List.of();
   private int cachedPromptIdHash = 0;
   private String pendingValidCommandsBlock = null;
   private String cachedValidCommandsBlock = null;
   private String turnWorldStatus = "";
   private String turnAgentStatus = "";
   private String turnAltoClefDebugMsgs = "";
   private Optional<String> turnReminderString = Optional.empty();
   private Optional<String> turnCurrentMood = Optional.empty();
   private boolean ragFallbackWarnedOnce = false;
   private boolean liveRagManagedSystemPrompt = false;
   private List<RetrievalHit> activeRetrievalHits = List.of();
   private Set<String> activePromptToolIds = Set.of();
   private String lastRagGoalText = "";
   private Optional<AliasLearnSuggestion> pendingLearnSuggestion = Optional.empty();
   private int deepCheckAttemptsThisTurn = 0;
   private boolean postDecisionRetryAttempted = false;
   private String lastRagPromptSource = "first_pass";
   private String lastDeepCheckTriggerReason = "";
   private String commandAwaitingFinishAck = null;
   private final ConversationTurnGate conversationTurnGate = new ConversationTurnGate();
   private LLMCompleter activeLlmCompleter;
   private LLMCompleter.Submission activeLlmSubmission;
   private final RepeatedCommandFailureGuard repeatedCommandFailureGuard = new RepeatedCommandFailureGuard();
   private int consecutiveParseFailures = 0;
   private static final int MAX_PARSE_RETRY = 2;
   private int consecutivePeerReplies = 0;
   private volatile long ttsCooldownUntilNanos = 0L;
   private static final int TTS_CHARS_PER_SECOND = 25;
   private volatile List<MarkerParser.SegmentBoundary> pendingSegmentActions = List.of();
   private volatile List<String> pendingChunks = List.of();
   private volatile List<String> pendingInvalidMarkers = List.of();
   private volatile Runnable fallbackTimerCancel = null;

   public AgentConversationData(PlayerEngineController mod) {
      this.mod = mod;
   }

   public String getChainInitiatorUsername() {
      return this.chainInitiatorUsername;
   }

   public List<MarkerParser.SegmentBoundary> getPendingSegmentActions() {
      return this.pendingSegmentActions;
   }

   public List<String> getPendingChunks() {
      return this.pendingChunks;
   }

   public List<String> getPendingInvalidMarkers() {
      return this.pendingInvalidMarkers;
   }

   public void setFallbackTimerCancel(Runnable cancel) {
      this.fallbackTimerCancel = cancel;
   }

   public void cancelFallbackTimer() {
      Runnable c = this.fallbackTimerCancel;
      this.fallbackTimerCancel = null;
      if (c != null) {
         c.run();
      }
   }

   public void reportPartialSpeechToModel() {
      this.addEventToQueue(
         new Event.InfoMessage(
            "Note: part of your spoken reply did not play for the listener (a TTS chunk failed). Do not claim you said everything; if it matters, you may briefly restate the key point."
         )
      );
   }

   public long getTtsCooldownUntilNanos() {
      return this.ttsCooldownUntilNanos;
   }

   public long getPriority() {
      if (this.enabled && !this.isProcessing && hasDispatchableEvents(this.eventQueue)) {
         if (System.nanoTime() < this.ttsCooldownUntilNanos) {
            return 0L;
         }

         Event head = this.eventQueue.peek();
         if (head instanceof Event.CharacterMessage charMsg) {
            AgentConversationData sender = charMsg.sendingCharacterData();
            if (!sender.getUUID().equals(this.getUUID()) && System.nanoTime() < sender.getTtsCooldownUntilNanos()) {
               return 0L;
            }
         }

         return System.nanoTime() - this.lastProcessTime;
      } else {
         return 0L;
      }
   }

   public Player2PayerResolution.ApiBillingContext previewBilling() {
      String lastUserInBatch = null;

      for (Event e : this.eventQueue) {
         if (e instanceof Event.UserMessage um) {
            lastUserInBatch = um.userName();
         }
      }

      String relayInitiator = lastUserInBatch != null ? lastUserInBatch : this.chainInitiatorUsername;
      return Player2PayerResolution.resolve(this.mod, relayInitiator, this.mod.getPlayer2APIService().getClientId());
   }

   public void markSpeakingFor(String message) {
      if (message != null) {
         int waitTimeSec = (int)Math.ceil(message.length() / 25.0) + 1;
         long waitNanos = TimeUnit.SECONDS.toNanos(waitTimeSec);
         this.ttsCooldownUntilNanos = System.nanoTime() + waitNanos;
      }
   }

   public void clearTtsCooldown() {
      this.ttsCooldownUntilNanos = 0L;
   }

   public void resetForClear() {
      this.invalidateProcessingTurnAndClearEvents();
      synchronized (this.deferredInfoQueue) {
         this.deferredInfoQueue.clear();
      }

      this.chainInitiatorUsername = null;
      this.clearTtsCooldown();
      this.cachedRetrievalHits = List.of();
      this.cachedPromptIdHash = 0;
      this.cachedValidCommandsBlock = null;
      this.resetB5TurnState();
      this.commandAwaitingFinishAck = null;
      this.repeatedCommandFailureGuard.reset();
      this.consecutiveParseFailures = 0;
      this.consecutivePeerReplies = 0;
   }

   private void resetB5TurnState() {
      this.activeRetrievalHits = List.of();
      this.activePromptToolIds = Set.of();
      this.pendingValidCommandsBlock = null;
      this.lastRagGoalText = "";
      this.pendingLearnSuggestion = Optional.empty();
      this.deepCheckAttemptsThisTurn = 0;
      this.postDecisionRetryAttempted = false;
      this.lastRagPromptSource = "first_pass";
      this.lastDeepCheckTriggerReason = "";
      this.turnCurrentMood = Optional.empty();
   }

   public void process(Consumer<Event.CharacterMessage> onCharacterEvent, Consumer<String> extOnErrMsg, LLMCompleter completer) {
      long turnTicket;
      synchronized (this) {
         if (this.isProcessing) {
            LOGGER.warn("Called queueData.process even though it was already processing! this should not happen");
            return;
         }

         if (this.eventQueue.isEmpty()) {
            LOGGER.warn("queueData.process called on empty event queue! this should not happen");
            return;
         }

         turnTicket = this.conversationTurnGate.issueTicket();
         this.activeTurnTicket = turnTicket;
         this.isProcessing = true;
      }

      mergeDeferredInfoForProcessing(this.eventQueue, this.deferredInfoQueue);
      Consumer<String> onErrMsg = errMsg -> {
         synchronized (this) {
            if (!this.conversationTurnGate.accepts(turnTicket)) {
               LOGGER.info("Discarding stale model error after authenticated owner stop for bot={}", this.getName());
               this.releaseProcessing(turnTicket);
            } else {
               try {
                  if (errMsg == null || !errMsg.startsWith("LLM_JSON_PARSE_FAILURE")) {
                     extOnErrMsg.accept(errMsg);
                     return;
                  }

                  this.consecutiveParseFailures++;
                  if (this.consecutiveParseFailures <= 2) {
                     LOGGER.warn(
                        "[AICommandBridge]: LLM reply failed to parse as JSON for bot={} (attempt {}/{}); asking model to resend valid JSON and notifying player.",
                        this.getName(),
                        this.consecutiveParseFailures,
                        2
                     );
                     this.addEventToQueue(
                        new Event.InfoMessage(
                           "Your previous reply could not be read because it was not valid JSON. Resend ONLY a single valid JSON object with the \"message\" and \"command\" fields, no extra text, no markdown code fences."
                        )
                     );
                     extOnErrMsg.accept(this.getName() + " had trouble understanding that — let me try again.");
                  } else {
                     LOGGER.error("[AICommandBridge]: LLM reply still unparseable after {} retries for bot={}; giving up this chain.", 2, this.getName());
                     this.consecutiveParseFailures = 0;
                     this.commandAwaitingFinishAck = null;
                     extOnErrMsg.accept(this.getName() + " couldn't respond clearly just now. Please try again.");
                  }
               } finally {
                  this.releaseProcessing(turnTicket);
               }
            }
         }
      };
      this.lastProcessTime = System.nanoTime();
      this.resetB5TurnState();
      String lastUserInBatch = null;

      for (Event e : this.eventQueue) {
         if (e instanceof Event.UserMessage um) {
            lastUserInBatch = um.userName();
         }
      }

      if (lastUserInBatch != null) {
         this.chainInitiatorUsername = lastUserInBatch;
      }

      String relayInitiator = lastUserInBatch != null ? lastUserInBatch : this.chainInitiatorUsername;
      if (relayInitiator != null && !relayInitiator.isBlank()) {
         MinecraftServer srv = this.mod.getPlayer().getServer();
         if (srv != null && BotBlacklistPolicy.isBlocked(srv, relayInitiator, this)) {
            LOGGER.info("Skipping LLM/API: bot blacklist blocks initiator={} for bot={}", relayInitiator, this.getName());
            this.eventQueue.clear();
            this.releaseProcessing(turnTicket);
            return;
         }

         if (srv != null && UserBlacklistPolicy.isBlocked(srv, relayInitiator, this)) {
            LOGGER.info("Skipping LLM/API: user blacklist blocks initiator={} for bot={}", relayInitiator, this.getName());
            this.eventQueue.clear();
            this.releaseProcessing(turnTicket);
            return;
         }
      }

      Player2PayerResolution.ApiBillingContext billing = Player2PayerResolution.resolve(
         this.mod, this.chainInitiatorUsername, this.mod.getPlayer2APIService().getClientId()
      );
      this.mod.getPlayer2APIService().setActiveBillingContext(billing);
      if (billing.onlinePayer() == null && !billing.useStoredToken()) {
         this.releaseProcessing(turnTicket);
         onErrMsg.accept("Player2: no billing player/token available for this API request.");
      } else {
         Event.UserMessage lastUserMsgForRag = null;

         for (Event e : this.eventQueue) {
            if (e instanceof Event.UserMessage um) {
               lastUserMsgForRag = um;
            }
         }

         Event lastEvent = this.mod
            .getAIPersistantData()
            .dumpEventQueueToConversationHistoryAndReturnLastEvent(this.eventQueue, this.mod.getPlayer2APIService());
         Optional<String> reminderString = this.getReminderStringFromLastEvent(lastEvent);
         this.maybeUpdateRagSystemPrompt(lastUserMsgForRag);
         String defaultReminderString = " | REMEMBER TO OUTPUT ONLY VALID JSON OUTPUT";
         reminderString = reminderString.map(a -> a + defaultReminderString);
         reminderString = Optional.of(reminderString.orElse(defaultReminderString));
         String agentStatus = AgentStatus.fromMod(this.mod).toString();
         String worldStatus = WorldStatus.fromMod(this.mod).toString();
         String altoClefDebugMsgs = this.playerEngineMsgBuffer.dumpAndGetString();
         this.turnWorldStatus = worldStatus;
         this.turnAgentStatus = agentStatus;
         this.turnAltoClefDebugMsgs = altoClefDebugMsgs;
         this.turnReminderString = reminderString;
         Optional<String> memoryBlock = this.resolveMemoryBlock(lastUserMsgForRag);
         this.turnCurrentMood = this.resolveCurrentMoodBlock();
         ConversationHistory historyWithWrappedStatus = this.mod
            .getAIPersistantData()
            .getConversationHistoryWrappedWithStatus(
               worldStatus,
               agentStatus,
               altoClefDebugMsgs,
               this.mod.getPlayer2APIService(),
               reminderString,
               Optional.ofNullable(this.pendingValidCommandsBlock),
               memoryBlock,
               this.turnCurrentMood
            );
         if (!this.conversationTurnGate.accepts(turnTicket)) {
            LOGGER.info("Skipping stale model request after authenticated owner stop for bot={}", this.getName());
            this.releaseProcessing(turnTicket);
         } else {
            LOGGER.info("[AICommandBridge/processChatWithAPI]: Calling LLM: history={}", new Object[]{historyWithWrappedStatus.toString()});
            Event.UserMessage ragUserMsg = lastUserMsgForRag;
            Consumer<JsonObject> onLLMResponse = jsonResp -> this.handleLlmResponse(
               jsonResp, lastEvent, relayInitiator, onCharacterEvent, onErrMsg, completer, historyWithWrappedStatus, ragUserMsg, false, false, turnTicket
            );
            this.submitDecisionIfCurrent(turnTicket, completer, historyWithWrappedStatus, onLLMResponse, onErrMsg, "initial", false);
         }
      }
   }

   private Optional<String> resolveMemoryBlock(Event.UserMessage lastUserMsgForRag) {
      try {
         String turnText = lastUserMsgForRag != null ? lastUserMsgForRag.message() : null;
         if (turnText != null && !turnText.isBlank()) {
            LivingEntity self = this.mod.getPlayer();
            if (self == null) {
               return Optional.empty();
            }

            MinecraftServer server = self.getServer();
            if (server == null) {
               return Optional.empty();
            }

            Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
            Player2PayerResolution.ApiBillingContext ownerBilling = Player2PayerResolution.resolve(
               this.mod, this.mod.getOwnerUsername(), cfg.getHeartbeatClientId()
            );
            MemoryGateDecision decision = MemoryGate.preflight(server, ownerBilling);
            Layer3Context ctx = Layer3Context.fromGate(ownerBilling, decision);
            if (!ctx.layer3Enabled()) {
               return Optional.empty();
            }

            Character character = this.mod.getAIPersistantData() != null ? this.mod.getAIPersistantData().getCharacter() : null;
            String companionId = character != null ? character.id() : null;
            if (companionId != null && !companionId.isBlank()) {
               UUID ownerUuid = this.mod.getOwner() != null ? this.mod.getOwner().getUUID() : null;
               MemoryScope scope = ownerUuid != null ? MemoryScope.of(ownerUuid, companionId) : MemoryScope.ofEntityFallback(self.getUUID(), companionId);
               MemoryStore store = MemoryStoreRegistry.getOrLoad(server, scope);
               if (store == null) {
                  return Optional.empty();
               }

               if (!store.denseBackfillComplete()) {
                  MemoryIngestionService.backfillDenseVectors(this.mod, server, ownerBilling, scope);
               }

               long gameTime = server.overworld() != null ? server.overworld().getGameTime() : 0L;
               MemoryRetriever.Thresholds thresholds = new MemoryRetriever.Thresholds(
                  cfg.getMemoryMaxHopsClamped(),
                  cfg.getMemoryMaxEgoNodesClamped(),
                  cfg.getMemoryBlockCharCapClamped(),
                  cfg.getMemoryMinConfidenceClamped(),
                  cfg.getMemoryDecayBaseClamped(),
                  cfg.getMemoryGameTimeUnitClamped(),
                  cfg.getMemoryRetrievalTopKClamped()
               );
               MemoryRetriever retriever = new MemoryRetriever(store, new MemoryRetriever.DenseContext(this.mod, ownerBilling));
               String companionName = character != null ? character.name() : null;
               String ownerName = this.mod.getOwnerUsername();
               MemoryRetrievalResult result = retriever.retrieve(turnText, ownerUuid, companionId, companionName, ownerName, gameTime, thresholds, true);
               return result != null ? result.memoryBlock() : Optional.empty();
            } else {
               return Optional.empty();
            }
         } else {
            return Optional.empty();
         }
      } catch (Exception e) {
         LOGGER.warn("[Memory] retrieval failed; degrading to no memory block ({})", e.getClass().getSimpleName());
         return Optional.empty();
      }
   }

   private void handleMoodDeclaration(JsonObject jsonResp) {
      try {
         if (!Player2ServerConfigHolder.get().isEnableCompanionMood()) {
            return;
         }

         AIPersistantData data = this.mod.getAIPersistantData();
         if (data == null) {
            return;
         }

         JsonObject moodObj = jsonResp.has("mood") && jsonResp.get("mood").isJsonObject() ? jsonResp.getAsJsonObject("mood") : null;
         CompanionMood prevMood = data.getCurrentMood();
         MoodUpdate.MoodUpdateResult result = MoodUpdate.apply(moodObj, prevMood);
         if (result.invalidLabel()) {
            this.addEventToQueue(
               new Event.InfoMessage(
                  "Note: the mood label you declared is not valid and your mood was NOT changed. Valid moods are: neutral, happy, content, excited, curious, sad, anxious, angry, afraid, determined. Do not claim a mood you did not set."
               )
            );
            return;
         }

         CompanionMood newMood = result.mood();
         if (newMood == prevMood) {
            return;
         }

         data.updateMood(newMood);
         data.saveMoodNow();
         if (MoodMemoryRule.isMeaningfulTransition(prevMood, newMood, result.memorable())) {
            MemoryIngestionService.ingestMoodEventDirectly(this.mod, prevMood, newMood);
         }
      } catch (Exception e) {
         LOGGER.warn("[Mood] declaration handling failed; mood unchanged ({})", e.getClass().getSimpleName());
      }
   }

   private Optional<String> resolveCurrentMoodBlock() {
      try {
         if (!Player2ServerConfigHolder.get().isEnableCompanionMood()) {
            return Optional.empty();
         }

         AIPersistantData data = this.mod.getAIPersistantData();
         if (data == null) {
            return Optional.empty();
         }

         CompanionMood mood = data.getCurrentMood();
         if (mood == null) {
            return Optional.empty();
         }

         String block = mood.toPromptString();
         return block != null && !block.isBlank() ? Optional.of(block) : Optional.empty();
      } catch (Exception e) {
         LOGGER.warn("[Mood] currentMood block resolution failed; degrading to no mood block ({})", e.getClass().getSimpleName());
         return Optional.empty();
      }
   }

   private boolean isEventDuplicateOfLastMessage(Event evt) {
      boolean isDuplicate = this.eventQueue.peekLast() != null && this.eventQueue.peekLast().equals(evt);
      if (!isDuplicate) {
         return false;
      }

      if (evt instanceof Event.UserMessage um && um.fromVoice()) {
         LOGGER.warn("STT/voice: duplicate user message dropped for companion={} preview=\"{}\"", this.getName(), SttLogging.messagePreview(um.message()));
      } else {
         LOGGER.warn("[EventQueueData]: evt={} was added twice!", evt.getConversationHistoryString());
      }

      return true;
   }

   private void addEventToQueue(Event event) {
      if (!this.isEventDuplicateOfLastMessage(event)) {
         if (this.eventQueue.size() > MAX_EVENT_QUEUE_SIZE) {
            this.eventQueue.removeFirst();
         }

         LOGGER.info("queue for UUID={} name={} adding event={} ", this.getUUID(), this.getName(), event);
         this.eventQueue.add(event);
      }
   }

   public boolean deferInfo(Event.InfoMessage message) {
      return addDeferredInfo(this.deferredInfoQueue, message);
   }

   public boolean cancelPendingModelActionsForOperatorStop() {
      LLMCompleter.CancellationOutcome cancellation = this.invalidateProcessingTurnAndClearEvents();
      boolean requestStillDraining = cancellation == LLMCompleter.CancellationOutcome.RETIREMENT_LIMIT_REACHED;
      this.commandAwaitingFinishAck = null;
      this.repeatedCommandFailureGuard.reset();
      this.clearTtsCooldown();
      this.pendingSegmentActions = List.of();
      this.pendingChunks = List.of();
      this.pendingInvalidMarkers = List.of();
      String modelNote = requestStillDraining
         ? "The owner issued an immediate stop. Automation and queued actions were cancelled, but a stuck prior model request is still draining because the bounded worker safety limit was reached. New replies may be delayed; do not resume the prior action."
         : "The owner issued an immediate stop. Automation and any queued or in-flight action were cancelled. Do not claim the prior action completed or resume it unless the owner explicitly asks.";
      this.deferInfo(new Event.InfoMessage(modelNote));
      return requestStillDraining;
   }

   private synchronized LLMCompleter.CancellationOutcome invalidateProcessingTurnAndClearEvents() {
      this.conversationTurnGate.invalidate();
      this.eventQueue.clear();
      LLMCompleter completer = this.activeLlmCompleter;
      LLMCompleter.Submission submission = this.activeLlmSubmission;
      this.activeLlmCompleter = null;
      this.activeLlmSubmission = null;
      this.activeTurnTicket = Long.MIN_VALUE;
      this.isProcessing = false;
      return completer != null && submission != null ? completer.cancel(submission) : LLMCompleter.CancellationOutcome.NOT_ACTIVE;
   }

   private synchronized void releaseProcessing(long turnTicket) {
      if (this.activeTurnTicket == turnTicket) {
         this.activeLlmCompleter = null;
         this.activeLlmSubmission = null;
         this.activeTurnTicket = Long.MIN_VALUE;
         this.isProcessing = false;
      }
   }

   private synchronized boolean submitDecisionIfCurrent(
      long turnTicket,
      LLMCompleter completer,
      ConversationHistory history,
      Consumer<JsonObject> onResponse,
      Consumer<String> onError,
      String phase,
      boolean handoffFromActiveRequest
   ) {
      if (this.activeTurnTicket == turnTicket && this.conversationTurnGate.accepts(turnTicket)) {
         LLMCompleter.Submission submission = handoffFromActiveRequest
            ? completer.processToJsonAfter(this.activeLlmSubmission, this.mod.getPlayer2APIService(), history, onResponse, onError, true, AiTaskClass.DECISION)
            : completer.processToJson(this.mod.getPlayer2APIService(), history, onResponse, onError, true, AiTaskClass.DECISION);
         if (!submission.accepted()) {
            LOGGER.warn("Model request submission was rejected for bot={} phase={}", this.getName(), phase);
            this.releaseProcessing(turnTicket);
            return false;
         } else {
            this.activeLlmCompleter = completer;
            this.activeLlmSubmission = submission;
            return true;
         }
      } else {
         LOGGER.info("Skipping stale {} model request after authenticated owner stop for bot={}", phase, this.getName());
         this.releaseProcessing(turnTicket);
         return false;
      }
   }

   static boolean hasDispatchableEvents(Deque<Event> events) {
      return events != null && !events.isEmpty();
   }

   static boolean addDeferredInfo(Deque<Event.InfoMessage> target, Event.InfoMessage message) {
      if (target != null && message != null && message.message() != null && !message.message().isBlank() && message.message().length() <= 512) {
         synchronized (target) {
            if (target.contains(message)) {
               return true;
            }

            while (target.size() >= 4) {
               target.pollFirst();
            }

            target.addLast(message);
            return true;
         }
      } else {
         return false;
      }
   }

   static int mergeDeferredInfoForProcessing(Deque<Event> dispatchableEvents, Deque<Event.InfoMessage> deferredInfo) {
      if (hasDispatchableEvents(dispatchableEvents) && deferredInfo != null && !deferredInfo.isEmpty()) {
         List<Event.InfoMessage> pending = new ArrayList<>(4);
         Event.InfoMessage next;
         synchronized (deferredInfo) {
            while ((next = deferredInfo.pollFirst()) != null) {
               pending.add(next);
            }
         }

         for (int i = pending.size() - 1; i >= 0; i--) {
            dispatchableEvents.addFirst(pending.get(i));
         }

         return pending.size();
      } else {
         return 0;
      }
   }

   private Optional<String> getReminderStringFromLastEvent(Event lastEvent) {
      if (lastEvent instanceof Event.UserMessage) {
         return Optional.of(
            (
                  ((Event.UserMessage)lastEvent).userName().equals(this.getMod().getOwnerUsername())
                     ? "Last message was from your owner."
                     : "Last message was from a user that was not your owner."
               )
               + " Remember to output valid JSON reponse with reason, command and message."
         );
      } else {
         return lastEvent instanceof Event.CharacterMessage
            ? Optional.of(
               Prompts.reminderOnAIMsg(this.getConsecutivePeerReplies()) + " Remember to output valid JSON reponse with reason, command and message."
            )
            : Optional.of("Remember to output valid JSON reponse with reason, command and message.");
      }
   }

   private void maybeUpdateRagSystemPrompt(Event.UserMessage lastUserMsgForRag) {
      Player2ServerRuntimeConfig config = Player2ServerConfigHolder.get();
      if (!config.isRagLiveEnabled()) {
         this.cachedRetrievalHits = List.of();
         this.cachedPromptIdHash = 0;
         this.cachedValidCommandsBlock = null;
         this.activeRetrievalHits = List.of();
         this.activePromptToolIds = Set.of();
         this.pendingValidCommandsBlock = null;
         this.lastRagGoalText = "";
         this.ragFallbackWarnedOnce = false;
         if (this.liveRagManagedSystemPrompt) {
            this.mod.getAIPersistantData().updateSystemPrompt();
            this.liveRagManagedSystemPrompt = false;
         }
      } else {
         this.liveRagManagedSystemPrompt = true;
         if (this.isGreetingResponse) {
            this.updateSystemPromptAlwaysIncludeOnly();
            LOGGER.debug("[RAG] skip retrieval: greeting turn for bot={}", this.getName());
         } else if (lastUserMsgForRag == null) {
            this.applyRagPromptFromCache(config);
         } else {
            String goalText = lastUserMsgForRag.message();
            if (!RagPromptBuilder.hasSubstantiveGoalText(goalText, config.getRagMinGoalCharsClamped())) {
               LOGGER.debug("[RAG] skip retrieval: short goal for bot={}", this.getName());
               this.applyRagPromptFromCache(config);
            } else {
               MinecraftServer server = this.mod.getPlayer().getServer();
               UUID ownerUuid = this.mod.getOwner() != null ? this.mod.getOwner().getUUID() : null;
               ToolRetriever retriever = RagIndex.getForOwner(server, ownerUuid);
               if (retriever == null) {
                  if (!this.ragFallbackWarnedOnce) {
                     LOGGER.warn(
                        "[RAG] retriever null for bot={} owner={}; set ragLiveEnabled=false to silence. Falling back to full command list.",
                        this.getName(),
                        ownerUuid
                     );
                     this.ragFallbackWarnedOnce = true;
                  }

                  if (config.isRagFallbackToFullList()) {
                     this.mod.getAIPersistantData().updateSystemPrompt();
                  } else {
                     this.updateSystemPromptAlwaysIncludeOnly();
                  }
               } else {
                  this.lastRagGoalText = goalText;
                  int topK = config.getRagTopKClamped();
                  RetrievalConfidenceThresholds thresholds = RetrievalConfidenceThresholds.fromConfig(config);
                  RetrievalResult firstPass = retriever.retrieveWithConfidence(goalText, topK, null, thresholds);
                  Player2PayerResolution.ApiBillingContext billing = Player2PayerResolution.resolve(
                     this.mod, this.chainInitiatorUsername, this.mod.getPlayer2APIService().getClientId()
                  );
                  RagDeepCheckCoordinator.RetryMergeResult merged = RagDeepCheckCoordinator.maybeImproveRetrieval(
                     retriever,
                     goalText,
                     topK,
                     null,
                     thresholds,
                     firstPass,
                     this.mod.getPlayer2APIService(),
                     this.mod.getCommandExecutor(),
                     billing,
                     config.isEnableDeepCheckRephrase()
                  );
                  if (merged.deepCheckAttempted()) {
                     this.deepCheckAttemptsThisTurn++;
                     this.lastDeepCheckTriggerReason = "heuristic_weak";
                  }

                  this.lastRagPromptSource = merged.promptSource();
                  this.pendingLearnSuggestion = merged.learnSuggestion();
                  List<RetrievalHit> hits = merged.activeResult().hits();
                  if (hits.isEmpty()) {
                     LOGGER.debug("[RAG] empty retrieval for goal=\"{}\" bot={} source={}", goalText, this.getName(), this.lastRagPromptSource);
                     this.activeRetrievalHits = hits;
                     this.activePromptToolIds = Set.of();
                     this.cachedRetrievalHits = hits;
                     this.cachedPromptIdHash = 0;
                     if (config.isRagFallbackToFullList()) {
                        this.mod.getAIPersistantData().updateSystemPrompt();
                     } else {
                        this.updateSystemPromptAlwaysIncludeOnly();
                     }
                  } else {
                     this.activeRetrievalHits = hits;
                     this.activePromptToolIds = toolIdsFromHits(hits);
                     int newHash = RagPromptBuilder.toolIdSetHash(RagPromptBuilder.ALWAYS_INCLUDE_IDS, hits);
                     if (newHash == this.cachedPromptIdHash && this.cachedValidCommandsBlock != null) {
                        LOGGER.debug("[RAG] same tool set (hash={}), reusing cached block for bot={}", newHash, this.getName());
                        this.pendingValidCommandsBlock = this.cachedValidCommandsBlock;
                        this.mod.getAIPersistantData().updateSystemPromptStatic();
                     } else {
                        this.cachedRetrievalHits = hits;
                        this.cachedPromptIdHash = newHash;
                        String block = RagPromptBuilder.buildValidCommandsBlock(
                           retriever.getRegistry(), hits, this.mod.getCommandExecutor(), RagPromptBuilder.ALWAYS_INCLUDE_IDS
                        );
                        LOGGER.debug(
                           "[RAG] injecting {} hits into user-tail validCommands for goal=\"{}\" bot={} source={}",
                           hits.size(),
                           goalText,
                           this.getName(),
                           this.lastRagPromptSource
                        );
                        this.cachedValidCommandsBlock = block;
                        this.pendingValidCommandsBlock = block;
                        this.mod.getAIPersistantData().updateSystemPromptStatic();
                     }
                  }
               }
            }
         }
      }
   }

   private static Set<String> toolIdsFromHits(List<RetrievalHit> hits) {
      Set<String> ids = new HashSet<>();

      for (RetrievalHit h : hits) {
         ids.add(h.toolId());
      }

      ids.addAll(RagPromptBuilder.ALWAYS_INCLUDE_IDS);
      return ids;
   }

   private void handleLlmResponse(
      JsonObject jsonResp,
      Event lastEvent,
      String relayInitiator,
      Consumer<Event.CharacterMessage> onCharacterEvent,
      Consumer<String> onErrMsg,
      LLMCompleter completer,
      ConversationHistory historyWithWrappedStatus,
      Event.UserMessage lastUserMsgForRag,
      boolean isFollowUpDecision,
      boolean isModelDeepSearchFollowUp,
      long turnTicket
   ) {
      boolean greetingResponse;
      synchronized (this) {
         if (!this.conversationTurnGate.accepts(turnTicket)) {
            LOGGER.info("Discarding stale model response after authenticated owner stop for bot={}", this.getName());
            this.releaseProcessing(turnTicket);
            return;
         }

         this.consecutiveParseFailures = 0;
         greetingResponse = this.isGreetingResponse;
         this.isGreetingResponse = false;
         if (!isFollowUpDecision && !isModelDeepSearchFollowUp) {
            this.handleMoodDeclaration(jsonResp);
         }
      }

      String llmMessage = Utils.getStringJsonSafely(jsonResp, "message");
      String command = greetingResponse ? "bodylang greeting" : Utils.getStringJsonSafely(jsonResp, "command");
      String cmdId = this.resolveCommandId(command);
      if (isModelDeepSearchFollowUp && RagDeepSearchCommands.isMetaCommandId(cmdId)) {
         LOGGER.warn("[B5] model_deepsearch_loop_blocked bot={}", this.getName());
         command = "idle";
         cmdId = "idle";
      }

      if (isFollowUpDecision
         || !this.maybeModelRequestedDeepSearch(
            command, cmdId, lastUserMsgForRag, relayInitiator, onCharacterEvent, onErrMsg, completer, historyWithWrappedStatus, lastEvent, turnTicket
         )) {
         if (isFollowUpDecision
            || !this.maybePostDecisionRetry(
               command, lastUserMsgForRag, relayInitiator, onCharacterEvent, onErrMsg, completer, historyWithWrappedStatus, lastEvent, turnTicket
            )) {
            if (RagDeepSearchCommands.isMetaCommandId(cmdId)) {
               LOGGER.debug("[B5] model_deepsearch_skipped bot={} (not handled)", this.getName());
               command = "idle";
               cmdId = "idle";
            }

            String previousAssistant = this.mod.getAIPersistantData().getLastAssistantContent().orElse("");
            boolean redundantAfterInfo = lastEvent instanceof Event.InfoMessage
               && ConversationHistory.isRedundantAssistantAfterInfo(previousAssistant, llmMessage);
            if (redundantAfterInfo) {
               LOGGER.info("[AICommandBridge/processCharWithAPI]: Suppressing duplicate assistant chat after Info (command feedback) round");
               llmMessage = "";
            }

            if (llmMessage == null) {
               llmMessage = "";
            }

            MarkerParser.ParsedMessage parsed = MarkerParser.parse(llmMessage);
            String strippedMessage = parsed.strippedText();
            List<MarkerParser.SegmentBoundary> validBoundaries = new ArrayList<>();
            List<String> invalidTokens = new ArrayList<>();

            for (MarkerParser.SegmentBoundary b : parsed.boundaries()) {
               if (b.valid()) {
                  validBoundaries.add(b);
               } else {
                  invalidTokens.add(b.rawToken());
               }
            }

            LOGGER.info("[AICommandBridge/processCharWithAPI]: Processed LLM response: message={} command={}", strippedMessage, command);
            boolean isPeerTurn = lastEvent instanceof Event.CharacterMessage;
            boolean substantiveReply = !strippedMessage.isEmpty() || cmdId != null && !"idle".equals(cmdId) || !validBoundaries.isEmpty();
            synchronized (this) {
               if (!this.conversationTurnGate.accepts(turnTicket)) {
                  LOGGER.info("Discarding stale model side effects after authenticated owner stop for bot={}", this.getName());
                  this.releaseProcessing(turnTicket);
               } else {
                  this.pendingSegmentActions = List.copyOf(validBoundaries);
                  this.pendingChunks = List.copyOf(parsed.chunks());
                  this.pendingInvalidMarkers = List.copyOf(invalidTokens);
                  if (!invalidTokens.isEmpty()) {
                     LOGGER.warn("[Bodylang] bot={} emitted unknown gesture marker(s): {}", this.getName(), invalidTokens);
                     this.addEventToQueue(
                        new Event.InfoMessage(
                           String.format(
                              "Note: the gesture marker(s) %s are not valid and were NOT performed. Valid gestures are: greeting, nod_head, shake_head, victory. Do not claim you performed an invalid gesture.",
                              String.join(", ", invalidTokens)
                           )
                        )
                     );
                  }

                  try {
                     if (strippedMessage.isEmpty() && command == null && validBoundaries.isEmpty()) {
                        LOGGER.warn("[AICommandBridge/processChatWithAPI/onLLMResponse]: Generated null llm message and command");
                     } else {
                        this.registerPendingLearnCandidate();
                        this.mod.getAIPersistantData().addAssistantMessage(strippedMessage, this.mod.getPlayer2APIService());
                        onCharacterEvent.accept(new Event.CharacterMessage(strippedMessage, command, this, relayInitiator));
                        if (isPeerTurn && substantiveReply) {
                           this.consecutivePeerReplies++;
                        }
                     }
                  } catch (Exception e) {
                     LOGGER.error("[AICommandBridge/processChatWithAPI/onLLMResponse]: ERROR RUNNING SIDE EFFECTS, errMsg={}", e.getMessage());
                  } finally {
                     if (!isPeerTurn) {
                        this.consecutivePeerReplies = 0;
                     } else if (!substantiveReply) {
                        LOGGER.debug("peer_talk_silence bot={} streak_before={}", this.getName(), this.consecutivePeerReplies);
                        this.consecutivePeerReplies = 0;
                     }

                     this.acknowledgeCommandFinishRoundIfComplete(lastEvent, command);
                     this.releaseProcessing(turnTicket);
                  }
               }
            }
         }
      }
   }

   private String resolveCommandId(String command) {
      if (command != null && !command.isBlank()) {
         String withPrefix = this.mod.getCommandExecutor().isClientCommand(command) ? command : this.mod.getCommandExecutor().getCommandPrefix() + command;
         return AgentSideEffects.firstCommandId(withPrefix, this.mod.getCommandExecutor());
      } else {
         return null;
      }
   }

   private boolean maybeModelRequestedDeepSearch(
      String command,
      String cmdId,
      Event.UserMessage lastUserMsgForRag,
      String relayInitiator,
      Consumer<Event.CharacterMessage> onCharacterEvent,
      Consumer<String> onErrMsg,
      LLMCompleter completer,
      ConversationHistory historyWithWrappedStatus,
      Event lastEvent,
      long turnTicket
   ) {
      Player2ServerRuntimeConfig config = Player2ServerConfigHolder.get();
      if (config.isEnableDeepCheckRephrase() && RagDeepSearchCommands.isMetaCommandId(cmdId)) {
         if (this.deepCheckAttemptsThisTurn >= config.getDeepCheckMaxAttemptsPerTurnClamped()) {
            LOGGER.debug("[B5] model_deepsearch_skipped reason=attempt_cap bot={}", this.getName());
            return false;
         }

         if (lastUserMsgForRag != null && !this.lastRagGoalText.isBlank()) {
            LOGGER.info("[B5] model_deepsearch requested bot={} goal=\"{}\"", this.getName(), this.lastRagGoalText);
            Player2PayerResolution.ApiBillingContext billing = Player2PayerResolution.resolve(
               this.mod, this.chainInitiatorUsername, this.mod.getPlayer2APIService().getClientId()
            );
            DeepCheckBudgetGate.SkipReason skip = DeepCheckBudgetGate.preflight(billing);
            if (skip != DeepCheckBudgetGate.SkipReason.NONE) {
               LOGGER.debug("[B5] model_deepsearch_skipped reason={} bot={}", skip.name().toLowerCase(), this.getName());
               return false;
            }

            MinecraftServer server = this.mod.getPlayer().getServer();
            UUID ownerUuid = this.mod.getOwner() != null ? this.mod.getOwner().getUUID() : null;
            ToolRetriever retriever = RagIndex.getForOwner(server, ownerUuid);
            if (retriever == null) {
               return false;
            }

            synchronized (this) {
               if (!this.conversationTurnGate.accepts(turnTicket)) {
                  this.releaseProcessing(turnTicket);
                  return true;
               }

               this.deepCheckAttemptsThisTurn++;
               if (config.isEnableDeepCheckMessage()) {
                  this.addEventToQueue(new Event.InfoMessage("Let me check the command list for a better match."));
               }
            }

            RetrievalConfidenceThresholds thresholds = RetrievalConfidenceThresholds.fromConfig(config);
            RetrievalResult firstPass = retriever.retrieveWithConfidence(this.lastRagGoalText, config.getRagTopKClamped(), null, thresholds);
            RagDeepCheckPipeline.ApplyResult applied = RagDeepCheckPipeline.apply(
               retriever,
               this.lastRagGoalText,
               config.getRagTopKClamped(),
               null,
               thresholds,
               firstPass,
               this.mod.getPlayer2APIService(),
               this.mod.getCommandExecutor(),
               billing,
               true,
               "model_requested"
            );
            if (!applied.success()) {
               return false;
            }

            synchronized (this) {
               if (!this.conversationTurnGate.accepts(turnTicket)) {
                  this.releaseProcessing(turnTicket);
                  return true;
               } else {
                  this.applyRetrievalToPrompt(retriever, applied);
                  this.lastRagPromptSource = "model_deepsearch";
                  this.lastDeepCheckTriggerReason = "model_requested";
                  LOGGER.info("[B5] model_deepsearch_ok source={} bot={}", applied.promptSource(), this.getName());
                  ConversationHistory followUpHistory = this.rebuildWrappedStatusForFollowUp();
                  Consumer<JsonObject> followUp = jsonResp -> this.handleLlmResponse(
                     jsonResp, lastEvent, relayInitiator, onCharacterEvent, onErrMsg, completer, followUpHistory, lastUserMsgForRag, true, true, turnTicket
                  );
                  this.submitDecisionIfCurrent(turnTicket, completer, followUpHistory, followUp, onErrMsg, "deep-search-follow-up", true);
                  return true;
               }
            }
         } else {
            LOGGER.debug("[B5] model_deepsearch_skipped reason=no_goal bot={}", this.getName());
            return false;
         }
      } else {
         return false;
      }
   }

   private void applyRetrievalToPrompt(ToolRetriever retriever, RagDeepCheckPipeline.ApplyResult applied) {
      this.activeRetrievalHits = applied.activeResult().hits();
      this.activePromptToolIds = toolIdsFromHits(applied.activeResult().hits());
      this.pendingLearnSuggestion = applied.learnSuggestion();
      String block = RagPromptBuilder.buildValidCommandsBlock(
         retriever.getRegistry(), applied.activeResult().hits(), this.mod.getCommandExecutor(), RagPromptBuilder.ALWAYS_INCLUDE_IDS
      );
      this.cachedValidCommandsBlock = block;
      this.pendingValidCommandsBlock = block;
      this.cachedRetrievalHits = applied.activeResult().hits();
      this.cachedPromptIdHash = RagPromptBuilder.toolIdSetHash(RagPromptBuilder.ALWAYS_INCLUDE_IDS, applied.activeResult().hits());
   }

   private ConversationHistory rebuildWrappedStatusForFollowUp() {
      return this.mod
         .getAIPersistantData()
         .getConversationHistoryWrappedWithStatus(
            this.turnWorldStatus,
            this.turnAgentStatus,
            this.turnAltoClefDebugMsgs,
            this.mod.getPlayer2APIService(),
            this.turnReminderString,
            Optional.ofNullable(this.pendingValidCommandsBlock),
            Optional.empty(),
            this.turnCurrentMood
         );
   }

   private void registerPendingLearnCandidate() {
      UUID ownerUuid = this.mod.getOwner() != null ? this.mod.getOwner().getUUID() : null;
      if (ownerUuid != null && !this.pendingLearnSuggestion.isEmpty()) {
         String triggerReason = this.lastDeepCheckTriggerReason.isBlank() ? this.lastRagPromptSource : this.lastDeepCheckTriggerReason;
         this.pendingLearnSuggestion
            .ifPresent(s -> AliasLearningService.registerPendingFromSuggestion(ownerUuid, this.getUUID(), this.lastRagGoalText, s, triggerReason));
         this.pendingLearnSuggestion = Optional.empty();
      }
   }

   private boolean maybePostDecisionRetry(
      String command,
      Event.UserMessage lastUserMsgForRag,
      String relayInitiator,
      Consumer<Event.CharacterMessage> onCharacterEvent,
      Consumer<String> onErrMsg,
      LLMCompleter completer,
      ConversationHistory historyWithWrappedStatus,
      Event lastEvent,
      long turnTicket
   ) {
      Player2ServerRuntimeConfig config = Player2ServerConfigHolder.get();
      if (config.isEnableDeepCheckRephrase() && !this.postDecisionRetryAttempted) {
         String cmdId = this.resolveCommandId(command);
         if (cmdId != null && !cmdId.isBlank() && !this.activePromptToolIds.contains(cmdId)) {
            if (this.deepCheckAttemptsThisTurn >= config.getDeepCheckMaxAttemptsPerTurnClamped()) {
               return false;
            }

            Player2PayerResolution.ApiBillingContext billing = Player2PayerResolution.resolve(
               this.mod, this.chainInitiatorUsername, this.mod.getPlayer2APIService().getClientId()
            );
            if (DeepCheckBudgetGate.preflight(billing) != DeepCheckBudgetGate.SkipReason.NONE) {
               LOGGER.debug("[B5] post_decision_retry_skipped cmd={}", cmdId);
               return false;
            }

            if (lastUserMsgForRag != null && !this.lastRagGoalText.isBlank()) {
               synchronized (this) {
                  if (!this.conversationTurnGate.accepts(turnTicket)) {
                     this.releaseProcessing(turnTicket);
                     return true;
                  }

                  this.postDecisionRetryAttempted = true;
               }

               MinecraftServer server = this.mod.getPlayer().getServer();
               UUID ownerUuid = this.mod.getOwner() != null ? this.mod.getOwner().getUUID() : null;
               ToolRetriever retriever = RagIndex.getForOwner(server, ownerUuid);
               if (retriever == null) {
                  return false;
               }

               RetrievalConfidenceThresholds thresholds = RetrievalConfidenceThresholds.fromConfig(config);
               RetrievalResult firstPass = retriever.retrieveWithConfidence(this.lastRagGoalText, config.getRagTopKClamped(), null, thresholds);
               synchronized (this) {
                  if (!this.conversationTurnGate.accepts(turnTicket)) {
                     this.releaseProcessing(turnTicket);
                     return true;
                  }

                  this.deepCheckAttemptsThisTurn++;
               }

               String enrichedGoal = this.lastRagGoalText + " (model chose command: " + cmdId + ")";
               RagDeepCheckPipeline.ApplyResult applied = RagDeepCheckPipeline.apply(
                  retriever,
                  enrichedGoal,
                  config.getRagTopKClamped(),
                  null,
                  thresholds,
                  firstPass,
                  this.mod.getPlayer2APIService(),
                  this.mod.getCommandExecutor(),
                  billing,
                  true,
                  "post_decision_out_of_top_k"
               );
               if (!applied.success()) {
                  return false;
               }

               synchronized (this) {
                  if (!this.conversationTurnGate.accepts(turnTicket)) {
                     this.releaseProcessing(turnTicket);
                     return true;
                  } else {
                     this.applyRetrievalToPrompt(retriever, applied);
                     this.lastRagPromptSource = applied.promptSource();
                     this.lastDeepCheckTriggerReason = "post_decision_out_of_top_k";
                     ConversationHistory retryHistory = this.rebuildWrappedStatusForFollowUp();
                     Consumer<JsonObject> retryHandler = jsonResp -> this.handleLlmResponse(
                        jsonResp, lastEvent, relayInitiator, onCharacterEvent, onErrMsg, completer, retryHistory, lastUserMsgForRag, true, false, turnTicket
                     );
                     this.submitDecisionIfCurrent(turnTicket, completer, retryHistory, retryHandler, onErrMsg, "post-decision-retry", true);
                     return true;
                  }
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void applyRagPromptFromCache(Player2ServerRuntimeConfig config) {
      if (this.cachedRetrievalHits.isEmpty()) {
         if (config.isRagFallbackToFullList()) {
            this.mod.getAIPersistantData().updateSystemPrompt();
         } else {
            this.updateSystemPromptAlwaysIncludeOnly();
         }
      } else if (this.cachedValidCommandsBlock == null) {
         LOGGER.warn(
            "[RAG] applyRagPromptFromCache: non-empty cachedRetrievalHits but cachedValidCommandsBlock is null for bot={} — falling back to system-message list",
            this.getName()
         );
         if (config.isRagFallbackToFullList()) {
            this.mod.getAIPersistantData().updateSystemPrompt();
         } else {
            this.updateSystemPromptAlwaysIncludeOnly();
         }
      } else {
         this.pendingValidCommandsBlock = this.cachedValidCommandsBlock;
         this.mod.getAIPersistantData().updateSystemPromptStatic();
      }
   }

   private void updateSystemPromptAlwaysIncludeOnly() {
      ToolRetriever global = RagIndex.getGlobal();
      if (global == null) {
         this.mod.getAIPersistantData().updateSystemPrompt();
      } else {
         String block = RagPromptBuilder.buildValidCommandsBlock(
            global.getRegistry(), List.of(), this.mod.getCommandExecutor(), RagPromptBuilder.ALWAYS_INCLUDE_IDS
         );
         if (block.isBlank()) {
            this.mod.getAIPersistantData().updateSystemPrompt();
         } else {
            this.mod.getAIPersistantData().updateSystemPromptWithBlock(block);
         }
      }
   }

   public void onEvent(Event event) {
      if (!SetupDirectRoute.tryRoute(this, event)) {
         if (event instanceof Event.UserMessage) {
            this.commandAwaitingFinishAck = null;
            this.repeatedCommandFailureGuard.reset();
         }

         this.addEventToQueue(event);
      }
   }

   public void onAICharacterMessage(Event.CharacterMessage msg) {
      boolean comingFromThisCharacter = msg.sendingCharacterData().getUUID().equals(this.getUUID());
      if (!comingFromThisCharacter) {
         this.eventQueue.add(msg);
      }
   }

   public void onGreeting() {
      this.addEventToQueue(this.mod.getAIPersistantData().getGreetingEvent());
   }

   public void onReturn(String ownerName) {
      this.addEventToQueue(this.mod.getAIPersistantData().getReturnEvent(ownerName));
   }

   public void onDeathRevival(String deathCause) {
      this.addEventToQueue(this.mod.getAIPersistantData().getDeathRevivalEvent(deathCause));
   }

   private static boolean isCommandFinishPromptMessage(String message) {
      return message != null && message.startsWith("Command feedback:") && message.contains("finished running");
   }

   private static String normalizeCommandNameForFinishAck(String commandName) {
      if (commandName == null) {
         return "";
      }

      String s = commandName.trim().toLowerCase(Locale.ROOT);
      if (s.startsWith("@")) {
         s = s.substring(1);
      }

      int space = s.indexOf(32);
      return space > 0 ? s.substring(0, space) : s;
   }

   private static boolean isNonTaskCommandForFinishPrompt(String commandName) {
      String base = normalizeCommandNameForFinishAck(commandName);
      return base.contains("bodylang") || "idle".equals(base) || "place_sign".equals(base) || "read_signs".equals(base);
   }

   private boolean shouldEnqueueCommandFinishPrompt(String commandName) {
      if (isNonTaskCommandForFinishPrompt(commandName)) {
         LOGGER.info("Skipping command finish prompt for non-task cmd={}", commandName);
         return false;
      } else {
         String normalized = normalizeCommandNameForFinishAck(commandName);
         if (this.commandAwaitingFinishAck != null && this.commandAwaitingFinishAck.equals(normalized)) {
            LOGGER.info(
               "Skipping duplicate command finish prompt for cmd={} (held ack={} normalized={})", commandName, this.commandAwaitingFinishAck, normalized
            );
            return false;
         } else {
            return true;
         }
      }
   }

   private void enqueueCommandFinishPrompt(String commandName) {
      this.enqueueCommandFinishPrompt(commandName, null);
   }

   private void enqueueCommandFinishPrompt(String commandName, String note) {
      if (note == null || note.isBlank()) {
         this.addEventToQueue(
            new Event.InfoMessage(
               String.format(
                  "Command feedback: %s finished running. What shall we do next? If no new action is needed to finish user's request, generate empty command `\"\"`.",
                  commandName
               )
            )
         );
      } else if (note.startsWith("[info-result] ")) {
         this.addEventToQueue(
            new Event.InfoMessage(
               String.format(
                  "Command feedback: %s finished running. Result: %s. What shall we do next? If no new action is needed to finish user's request, generate empty command `\"\"`.",
                  commandName,
                  note.substring("[info-result] ".length())
               )
            )
         );
      } else {
         this.addEventToQueue(
            new Event.InfoMessage(
               String.format(
                  "Command feedback: %s finished running, but: %s. What shall we do next? If no new action is needed to finish user's request, generate empty command `\"\"`.",
                  commandName,
                  note
               )
            )
         );
      }

      this.commandAwaitingFinishAck = normalizeCommandNameForFinishAck(commandName);
   }

   private void acknowledgeCommandFinishRoundIfComplete(Event lastEvent, String command) {
      if (lastEvent instanceof Event.InfoMessage info && isCommandFinishPromptMessage(info.message())) {
         LOGGER.info("Command-finish round complete (model responded with cmd={}); clearing finish-ack for cmd={}", command, this.commandAwaitingFinishAck);
         this.commandAwaitingFinishAck = null;
      }
   }

   public void onCommandFinish(AgentSideEffects.CommandExecutionStopReason stopReason) {
      LOGGER.info("on command finish for cmd={}", stopReason.commandName());
      if (stopReason instanceof AgentSideEffects.CommandExecutionStopReason.Finished) {
         this.repeatedCommandFailureGuard.reset();
         LOGGER.info("on command={} finish case", stopReason.commandName());
         if (this.shouldIgnoreGreetingDance && stopReason.commandName().contains("bodylang greeting")) {
            LOGGER.info("Skipping on command finish because should ignore greeting dance");
            this.shouldIgnoreGreetingDance = false;
            return;
         }

         this.shouldIgnoreGreetingDance = false;
         if (!this.eventQueue.isEmpty()) {
            LOGGER.info("Skipping command finish prompt for cmd={} because event queue is not empty", stopReason.commandName());
         } else if (this.shouldEnqueueCommandFinishPrompt(stopReason.commandName())) {
            LOGGER.info("Enqueueing command finish prompt for cmd={}", stopReason.commandName());
            String note = ((AgentSideEffects.CommandExecutionStopReason.Finished)stopReason).note();
            this.enqueueCommandFinishPrompt(stopReason.commandName(), note);
         }
      } else if (stopReason instanceof AgentSideEffects.CommandExecutionStopReason.Error error) {
         String boundedReason = RepeatedCommandFailureGuard.boundedFailureReason(error.errMsg());
         RepeatedCommandFailureGuard.Decision decision = this.repeatedCommandFailureGuard.record(stopReason.commandName(), boundedReason);
         if (decision == RepeatedCommandFailureGuard.Decision.HALT_AUTOMATIC_RETRY) {
            String commandId = RepeatedCommandFailureGuard.commandId(stopReason.commandName());
            LOGGER.warn("Stopping repeated command-error feedback loop for bot={} commandId={}", this.getName(), commandId);
            this.deferInfo(
               new Event.InfoMessage(
                  "Automatic retrying stopped after the command '"
                     + commandId
                     + "' failed twice with the same error. Do not claim it succeeded or retry it unless the player explicitly asks."
               )
            );
            this.reportRepeatedCommandFailureStopped(commandId);
            this.commandAwaitingFinishAck = null;
            return;
         }

         LOGGER.info("adding cmd={} to queue because it errored", stopReason.commandName());
         this.addEventToQueue(
            new Event.InfoMessage(
               String.format(
                  "Command feedback: %s FAILED. The error was %s.", RepeatedCommandFailureGuard.boundedCommandForModel(stopReason.commandName()), boundedReason
               )
            )
         );
      } else {
         this.repeatedCommandFailureGuard.reset();
         LOGGER.info("Skipping command stop for cmd={} because it was cancelled", stopReason.commandName());
      }
   }

   private void reportRepeatedCommandFailureStopped(String commandId) {
      if (this.mod.getOwner() instanceof ServerPlayer owner && owner.getServer() != null) {
         AgentSideEffects.broadcastChatToPlayer(
            owner.getServer(), Component.translatable("message.playerengine.agent.repeated_command_stopped", new Object[]{this.getName(), commandId}), owner
         );
      }
   }

   public float getDistance(UUID target) {
      return StatusUtils.getDistanceToUUID(this.mod, target);
   }

   public UUID getUUID() {
      return this.mod.getPlayer().getUUID();
   }

   public PlayerEngineController getMod() {
      return this.mod;
   }

   public boolean isOwner(UUID playerToCheck) {
      return this.mod.isOwner(playerToCheck);
   }

   public LivingEntity getEntity() {
      return this.mod.getPlayer();
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public Character getCharacter() {
      return this.mod.getAIPersistantData().getCharacter();
   }

   public Player2APIService getPlayer2apiService() {
      return this.mod.getPlayer2APIService();
   }

   public String getName() {
      return this.getCharacter().shortName();
   }

   int getConsecutivePeerReplies() {
      return this.consecutivePeerReplies;
   }
}
