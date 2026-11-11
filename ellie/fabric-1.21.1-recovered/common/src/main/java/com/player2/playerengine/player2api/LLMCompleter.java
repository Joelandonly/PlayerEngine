package com.player2.playerengine.player2api;

import com.google.gson.JsonObject;
import com.player2.playerengine.executor.StopReason;
import com.player2.playerengine.player2api.utils.Utils;
import com.player2.playerengine.util.ExecutorShutdown;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LLMCompleter {
   public static final boolean DEBUG_LLM_PROBE = false;
   private static final long NO_REQUEST_ID = 0L;
   private static final int MAX_RETIRED_WORKERS = 2;
   private static final long LLM_CHAT_WORKER_TIMEOUT_SECONDS = Player2ClientApiBridge.defaultTimeoutForEndpoint("/v1/chat/completions") + 15L;
   private static final ScheduledExecutorService LLM_WATCHDOG_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "playerengine-llm-watchdog");
      t.setDaemon(true);
      return t;
   });
   private ExecutorService llmThread = newWorkerExecutor();
   private final List<ExecutorService> retiredExecutors = new ArrayList<>();
   private final Set<Long> runningRequestIds = new HashSet<>();
   private final Set<Long> cancelledBeforeStartRequestIds = new HashSet<>();
   private long nextRequestId = 0L;
   private long activeRequestId = 0L;
   private Future<?> activeFuture;
   private static final Logger LOGGER = LogManager.getLogger();
   private volatile boolean executorShutDown = false;

   public void shutdown() {
      this.shutdown(null);
   }

   void shutdown(Runnable afterShutdownFlagSet) {
      List<ExecutorService> executors;
      Future<?> future;
      synchronized (this) {
         if (this.executorShutDown) {
            return;
         }

         this.executorShutDown = true;
         future = this.activeFuture;
         this.activeFuture = null;
         this.activeRequestId = 0L;
         executors = new ArrayList<>(this.retiredExecutors);
         executors.add(this.llmThread);
         this.retiredExecutors.clear();
      }

      try {
         if (afterShutdownFlagSet != null) {
            afterShutdownFlagSet.run();
         }
      } finally {
         if (future != null) {
            future.cancel(true);
         }

         for (ExecutorService executor : executors) {
            ExecutorShutdown.shutdownNowAwait("LLMCompleter", executor);
         }
      }
   }

   private synchronized <T> LLMCompleter.Submission process(
      Player2APIService player2apiService,
      ConversationHistory history,
      Consumer<T> extOnLLMResponse,
      Consumer<String> extOnErrMsg,
      Utils.ThrowingFunction<ConversationHistory, T> completeConversation,
      boolean isConversation,
      LLMCompleter.Submission predecessor
   ) {
      LOGGER.info("Called completer.process with history={}", history);
      if (this.executorShutDown) {
         LOGGER.warn("Called llmcompleter.process after shutdown; ignoring.");
         return LLMCompleter.Submission.rejected();
      }

      boolean authorizedHandoff = predecessor != null && predecessor.accepted() && this.activeRequestId == predecessor.requestId();
      if ((this.activeRequestId == 0L || authorizedHandoff) && (this.activeRequestId != 0L || predecessor == null)) {
         long previousRequestId = this.activeRequestId;
         Future<?> previousFuture = this.activeFuture;
         long requestId = ++this.nextRequestId;
         this.activeRequestId = requestId;
         Consumer<T> onLLMResponse = resp -> {
            try {
               extOnLLMResponse.accept(resp);
            } catch (Exception e) {
               LOGGER.error("[LLMCompleter/process/onLLMResponse]: Error in external llm resp, errMsg={} llmResp={}", e.getMessage(), resp.toString());
            } finally {
               LOGGER.info("Done processing, releasing completer requestId={}", requestId);
               this.releaseRequest(requestId);
            }
         };
         Consumer<String> onErrMsg = errMsg -> {
            try {
               extOnErrMsg.accept(errMsg);
            } catch (Exception e) {
               LOGGER.error("[LLMCompleter/process/onErrMsg]: Error in external onErrmsg, errMsgFromException={} errMsg={}", e.getMessage(), errMsg);
            } finally {
               LOGGER.info("Done processing (err path), releasing completer requestId={}", requestId);
               this.releaseRequest(requestId);
            }
         };

         try {
            this.activeFuture = this.llmThread
               .submit(
                  () -> {
                     if (this.beginRequest(requestId)) {
                        Thread workerThread = Thread.currentThread();
                        ScheduledFuture<?> watchdog = LLM_WATCHDOG_SCHEDULER.schedule(
                           () -> workerThread.interrupt(), LLM_CHAT_WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS
                        );
                        long dbgStartNs = 0L;

                        try {
                           T response = completeConversation.apply(history);
                           LOGGER.info("LLMCompleter returned json={}", response);
                           onLLMResponse.accept(response);
                        } catch (Exception e) {
                           if (isInterruptedLike(e)) {
                              Thread.currentThread().interrupt();
                              onErrMsg.accept(StopReason.FATAL.name() + ":llm_worker_timeout");
                           } else {
                              onErrMsg.accept(e.getMessage() == null ? "Unknown error from CompleteConversation API" : e.getMessage());
                           }
                        } finally {
                           watchdog.cancel(false);
                           Thread.interrupted();
                           this.markRequestFinished(requestId);
                        }
                     }
                  }
               );
            return new LLMCompleter.Submission(requestId, true);
         } catch (RuntimeException submitFailure) {
            this.activeFuture = authorizedHandoff ? previousFuture : null;
            this.activeRequestId = authorizedHandoff ? previousRequestId : 0L;
            LOGGER.error("Unable to submit LLM request: type={}", submitFailure.getClass().getSimpleName());
            return LLMCompleter.Submission.rejected();
         }
      } else {
         LOGGER.warn("Called llmcompleter.process when it was already processing! This should not happen.");
         return LLMCompleter.Submission.rejected();
      }
   }

   public LLMCompleter.CancellationOutcome cancel(LLMCompleter.Submission submission) {
      if (submission != null && submission.accepted()) {
         ExecutorService retired = null;
         Future<?> future;
         LLMCompleter.CancellationOutcome outcome;
         synchronized (this) {
            if (this.activeRequestId != submission.requestId()) {
               return LLMCompleter.CancellationOutcome.NOT_ACTIVE;
            }

            this.retiredExecutors.removeIf(ExecutorService::isTerminated);
            future = this.activeFuture;
            if (!this.runningRequestIds.contains(submission.requestId())) {
               this.cancelledBeforeStartRequestIds.add(submission.requestId());
               this.activeFuture = null;
               this.activeRequestId = 0L;
               outcome = LLMCompleter.CancellationOutcome.CANCELLED_QUEUED;
            } else if (this.retiredExecutors.size() >= 2) {
               outcome = LLMCompleter.CancellationOutcome.RETIREMENT_LIMIT_REACHED;
            } else {
               this.activeFuture = null;
               this.activeRequestId = 0L;
               retired = this.llmThread;
               this.llmThread = newWorkerExecutor();
               this.retiredExecutors.add(retired);
               outcome = LLMCompleter.CancellationOutcome.CANCELLED_AND_RETIRED;
            }
         }

         if (future != null && outcome != LLMCompleter.CancellationOutcome.CANCELLED_QUEUED) {
            future.cancel(true);
         }

         if (retired != null) {
            retired.shutdownNow();
         }

         LOGGER.info("LLM cancellation requestId={} outcome={}", submission.requestId(), outcome);
         return outcome;
      } else {
         return LLMCompleter.CancellationOutcome.NOT_ACTIVE;
      }
   }

   private synchronized void releaseRequest(long requestId) {
      if (this.activeRequestId == requestId) {
         this.activeRequestId = 0L;
         this.activeFuture = null;
      }
   }

   private synchronized boolean beginRequest(long requestId) {
      if (!this.executorShutDown && !this.cancelledBeforeStartRequestIds.remove(requestId)) {
         this.runningRequestIds.add(requestId);
         return true;
      } else {
         return false;
      }
   }

   private synchronized void markRequestFinished(long requestId) {
      this.runningRequestIds.remove(requestId);
      this.retiredExecutors.removeIf(ExecutorService::isTerminated);
   }

   private static ExecutorService newWorkerExecutor() {
      return Executors.newSingleThreadExecutor(r -> {
         Thread thread = new Thread(r, "playerengine-llm-worker");
         thread.setDaemon(true);
         return thread;
      });
   }

   public LLMCompleter.Submission processToJson(
      Player2APIService player2apiService,
      ConversationHistory history,
      Consumer<JsonObject> extOnLLMResponse,
      Consumer<String> extOnErrMsg,
      boolean isConversation,
      AiTaskClass taskClass
   ) {
      return this.process(
         player2apiService, history, extOnLLMResponse, extOnErrMsg, h -> player2apiService.completeConversation(h, taskClass), isConversation, null
      );
   }

   public LLMCompleter.Submission processToJsonAfter(
      LLMCompleter.Submission predecessor,
      Player2APIService player2apiService,
      ConversationHistory history,
      Consumer<JsonObject> extOnLLMResponse,
      Consumer<String> extOnErrMsg,
      boolean isConversation,
      AiTaskClass taskClass
   ) {
      return this.process(
         player2apiService, history, extOnLLMResponse, extOnErrMsg, h -> player2apiService.completeConversation(h, taskClass), isConversation, predecessor
      );
   }

   @Deprecated
   public LLMCompleter.Submission processToJson(
      Player2APIService player2apiService,
      ConversationHistory history,
      Consumer<JsonObject> extOnLLMResponse,
      Consumer<String> extOnErrMsg,
      boolean isConversation
   ) {
      return this.processToJson(player2apiService, history, extOnLLMResponse, extOnErrMsg, isConversation, AiTaskClass.DECISION);
   }

   public LLMCompleter.Submission processToString(
      Player2APIService player2apiService,
      ConversationHistory history,
      Consumer<String> extOnLLMResponse,
      Consumer<String> extOnErrMsg,
      boolean isConversation,
      AiTaskClass taskClass
   ) {
      return this.process(
         player2apiService, history, extOnLLMResponse, extOnErrMsg, h -> player2apiService.completeConversationToString(h, taskClass), isConversation, null
      );
   }

   @Deprecated
   public LLMCompleter.Submission processToString(
      Player2APIService player2apiService, ConversationHistory history, Consumer<String> extOnLLMResponse, Consumer<String> extOnErrMsg, boolean isConversation
   ) {
      return this.processToString(player2apiService, history, extOnLLMResponse, extOnErrMsg, isConversation, AiTaskClass.DECISION);
   }

   public synchronized boolean isAvailible() {
      return !this.executorShutDown && this.activeRequestId == 0L;
   }

   private static boolean isInterruptedLike(Throwable e) {
      if (Thread.currentThread().isInterrupted()) {
         return true;
      }

      while (e != null) {
         if (e instanceof InterruptedException) {
            return true;
         }

         e = e.getCause();
      }

      return false;
   }

   public enum CancellationOutcome {
      NOT_ACTIVE,
      CANCELLED_QUEUED,
      CANCELLED_AND_RETIRED,
      RETIREMENT_LIMIT_REACHED;
   }

   public record Submission(long requestId, boolean accepted) {
      static LLMCompleter.Submission rejected() {
         return new LLMCompleter.Submission(0L, false);
      }
   }
}
