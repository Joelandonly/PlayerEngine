package com.player2.playerengine.memory.ingest;

import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.memory.MemoryCaps;
import com.player2.playerengine.memory.MemoryGraph;
import com.player2.playerengine.memory.MemoryNode;
import com.player2.playerengine.memory.MemoryNodeType;
import com.player2.playerengine.memory.MemoryScope;
import com.player2.playerengine.memory.MemoryStore;
import com.player2.playerengine.memory.MergePlan;
import com.player2.playerengine.memory.budget.Layer3Context;
import com.player2.playerengine.memory.budget.MemoryBudgetResult;
import com.player2.playerengine.memory.budget.MemoryExtractionBudgetGate;
import com.player2.playerengine.memory.budget.MemoryGate;
import com.player2.playerengine.memory.budget.MemoryGateDecision;
import com.player2.playerengine.memory.budget.MemoryLlmClient;
import com.player2.playerengine.memory.dense.EmbeddingBatcher;
import com.player2.playerengine.memory.dense.EmbeddingProvider;
import com.player2.playerengine.memory.dense.EmbeddingResult;
import com.player2.playerengine.memory.reflection.ReflectionService;
import com.player2.playerengine.memory.reflection.ReflectionTrigger;
import com.player2.playerengine.memory.retrieval.MemoryRetriever;
import com.player2.playerengine.player2api.AiTaskClass;
import com.player2.playerengine.player2api.Character;
import com.player2.playerengine.player2api.ConversationHistory;
import com.player2.playerengine.player2api.Player2APIService;
import com.player2.playerengine.player2api.Player2PayerResolution;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;
import com.player2.playerengine.player2api.mood.CompanionMood;
import com.player2.playerengine.retrieval.RetrievalHit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;

public final class MemoryIngestionService {
   private static final ExecutorService INGESTION_EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
      @Override
      public Thread newThread(Runnable r) {
         Thread t = new Thread(r, "memory-ingestion");
         t.setDaemon(true);
         return t;
      }
   });
   private static final MemoryTurnBatcher BATCHER = new MemoryTurnBatcher();
   private static final ConcurrentHashMap<MemoryScope, AtomicBoolean> REFLECTION_IN_FLIGHT = new ConcurrentHashMap<>();
   private static volatile MemoryIngestionService.MemoryStoreProvider storeProvider;
   private static final int EMBED_BATCH_MAX = 100;
   private static final Set<MemoryScope> EMBED_IN_FLIGHT = ConcurrentHashMap.newKeySet();

   private MemoryIngestionService() {
   }

   public static void setStoreProvider(MemoryIngestionService.MemoryStoreProvider provider) {
      storeProvider = provider;
   }

   public static void clearSessionState() {
      BATCHER.clear();
      REFLECTION_IN_FLIGHT.clear();
   }

   public static void onCuratedTurnsReady(PlayerEngineController controller, List<String> newTurns) {
      try {
         if (controller == null || newTurns == null || newTurns.isEmpty()) {
            return;
         }

         MinecraftServer server = resolveServer(controller);
         if (server == null) {
            return;
         }

         Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
         String ownerUsername = controller.getOwnerUsername();
         String clientId = cfg.getHeartbeatClientId();
         Player2PayerResolution.ApiBillingContext ownerBilling = Player2PayerResolution.resolve(controller, ownerUsername, clientId);
         MemoryGateDecision decision = MemoryGate.preflight(server, ownerBilling);
         if (!decision.allowed()) {
            return;
         }

         MemoryScope scope = resolveScope(controller);
         if (scope == null) {
            return;
         }

         MemoryStore store = lookupStore(server, scope);
         MemoryGraph graph = store != null && store.snapshot() != null ? store.snapshot().graph() : null;
         int lengthThreshold = cfg.getMemoryExtractionLengthThreshold();
         int batchMin = cfg.getMemoryExtractionBatchMin();
         int batchMax = cfg.getMemoryExtractionBatchMax();
         long nowTick = currentGameTime(server);
         Character companion = controller.getAIPersistantData() != null ? controller.getAIPersistantData().getCharacter() : null;
         String companionName = companion != null ? companion.name() : null;
         String companionShortName = companion != null ? companion.shortName() : null;
         List<String> batchToExtract = null;

         for (String turn : newTurns) {
            List<MemoryExplicitFactExtractor.ExplicitMemory> explicit = MemoryExplicitFactExtractor.extract(
               turn, ownerUsername, companionName, companionShortName
            );
            if (!explicit.isEmpty() && store != null) {
               MergePlan explicitPlan = MemoryExplicitFactExtractor.buildPlan(explicit, nowTick);
               if (explicitPlan != null && !explicitPlan.isEmpty()) {
                  applyMerge(controller, server, ownerBilling, scope, store, explicitPlan);
                  continue;
               }
            }

            if (MemoryExtractionGate.isEligible(turn, lengthThreshold, graph)) {
               List<String> flushed = BATCHER.offer(scope, turn, batchMin, batchMax);
               if (flushed != null) {
                  batchToExtract = flushed;
               }
            }
         }

         if (batchToExtract == null) {
            return;
         }

         scheduleExtraction(controller, server, ownerBilling, scope, batchToExtract);
      } catch (RuntimeException e) {
         PlayerEngine.LOGGER.warn("Memory ingestion: server-thread entry failed ({})", e.getClass().getSimpleName());
      }
   }

   public static void ingestMoodEventDirectly(PlayerEngineController controller, CompanionMood prevMood, CompanionMood newMood) {
      try {
         if (controller == null || prevMood == null || newMood == null) {
            return;
         }

         MinecraftServer server = resolveServer(controller);
         if (server == null) {
            return;
         }

         Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
         String ownerUsername = controller.getOwnerUsername();
         String clientId = cfg.getHeartbeatClientId();
         Player2PayerResolution.ApiBillingContext ownerBilling = Player2PayerResolution.resolve(controller, ownerUsername, clientId);
         MemoryGateDecision decision = MemoryGate.preflight(server, ownerBilling);
         if (!decision.allowed()) {
            return;
         }

         MemoryScope scope = resolveScope(controller);
         if (scope == null) {
            return;
         }

         MemoryStore store = lookupStore(server, scope);
         if (store == null) {
            return;
         }

         String prevLabelName = prevMood.label().name().toLowerCase(Locale.ROOT);
         String newLabelName = newMood.label().name().toLowerCase(Locale.ROOT);
         String cause = newMood.cause();
         String rawContent;
         if (cause != null && !cause.isBlank()) {
            rawContent = "Felt " + prevLabelName + " then became " + newLabelName + ": " + cause;
         } else {
            rawContent = "Felt " + prevLabelName + " then became " + newLabelName;
         }

         String content = MemoryCaps.capContent(rawContent);
         int importance = newMood.intensity();
         long nowMs = System.currentTimeMillis();
         long nowTick = server.getTickCount();
         String nodeId = "mood_event_" + prevLabelName + "_to_" + newLabelName + "_" + nowMs;
         MergePlan plan = MergePlan.builder()
            .upsert(
               new MergePlan.NodeUpsert(
                  nodeId,
                  content,
                  MemoryNodeType.EVENT.wire(),
                  MemoryCaps.capName("mood: " + prevLabelName + " → " + newLabelName),
                  List.of(),
                  List.of(),
                  importance,
                  nowMs,
                  nowTick
               )
            )
            .build();
         server.execute(() -> {
            try {
               MemoryStore applyStore = lookupStore(server, scope);
               if (applyStore == null) {
                  return;
               }

               applyStore.mergeCandidates(plan);
               PlayerEngine.LOGGER.info("Mood memory: EVENT node minted ({} -> {}) for {}.", prevLabelName, newLabelName, scope);
               enqueueEmbedBackfill(controller, server, ownerBilling, scope);
               maybeDispatchReflection(controller, server, ownerBilling, scope, applyStore);
            } catch (RuntimeException applyEx) {
               PlayerEngine.LOGGER.warn("Mood memory: EVENT apply failed ({})", applyEx.getClass().getSimpleName());
            }
         });
      } catch (RuntimeException e) {
         PlayerEngine.LOGGER.warn("Mood memory: ingestMoodEventDirectly failed ({})", e.getClass().getSimpleName());
      }
   }

   private static void scheduleExtraction(
      PlayerEngineController controller, MinecraftServer server, Player2PayerResolution.ApiBillingContext ownerBilling, MemoryScope scope, List<String> batch
   ) {
      Player2APIService api = controller.getPlayer2APIService();
      if (api != null) {
         List<String> turns = List.copyOf(batch);
         long nowTick = server.getTickCount();
         INGESTION_EXECUTOR.submit(
            () -> {
               try {
                  if (MemoryExtractionBudgetGate.peekCap(ownerBilling) == MemoryBudgetResult.CAP_REACHED) {
                     return;
                  }

                  if (!MemoryExtractionBudgetGate.reserveSlot(ownerBilling)) {
                     return;
                  }

                  ConversationHistory history = new ConversationHistory(MemoryExtractionPrompt.systemPrompt());

                  for (String turn : turns) {
                     history.addUserMessage(turn, api);
                  }

                  history.addUserMessage(MemoryExtractionPrompt.extractionInstruction(), api);
                  String reply = MemoryLlmClient.completeConversationToString(server, ownerBilling, history, AiTaskClass.SUMMARIZATION);
                  MemoryExtractionResponse validated = MemoryExtractionValidator.validate(reply);
                  if (validated == null) {
                     return;
                  }

                  MemoryExtractionResponse enriched = MemoryEmotionEnricher.enrich(validated);
                  MergePlan plan = MemoryMergePlanBuilder.build(enriched, nowTick);
                  if (plan == null || plan.isEmpty()) {
                     return;
                  }

                  server.execute(
                     () -> {
                        try {
                           MemoryStore store = lookupStore(server, scope);
                           if (store == null) {
                              return;
                           }

                           store.mergeCandidates(plan);
                           enqueueEmbedBackfill(controller, server, ownerBilling, scope);
                           MemoryStore.Snapshot snap = store.snapshot();
                           int nodes = snap != null && snap.graph() != null ? snap.graph().nodeCount() : 0;
                           int edges = snap != null && snap.graph() != null ? snap.graph().edgeCount() : 0;
                           PlayerEngine.LOGGER
                              .info(
                                 "Memory: merged {} entities / {} relations; nodes={} edges={} cumImportance={} for {}.",
                                 plan.upserts().size(),
                                 plan.edges().size(),
                                 nodes,
                                 edges,
                                 store.cumulativeImportanceSinceLastReflection(),
                                 scope
                              );
                           maybeDispatchReflection(controller, server, ownerBilling, scope, store);
                        } catch (RuntimeException applyEx) {
                           PlayerEngine.LOGGER.warn("Memory ingestion: merge apply failed ({})", applyEx.getClass().getSimpleName());
                        }
                     }
                  );
               } catch (Exception e) {
                  PlayerEngine.LOGGER.warn("Memory ingestion: extraction failed ({})", e.getClass().getSimpleName());
               }
            }
         );
      }
   }

   private static void applyMerge(
      PlayerEngineController controller,
      MinecraftServer server,
      Player2PayerResolution.ApiBillingContext ownerBilling,
      MemoryScope scope,
      MemoryStore store,
      MergePlan plan
   ) {
      if (store != null && plan != null && !plan.isEmpty()) {
         store.mergeCandidates(plan);
         enqueueEmbedBackfill(controller, server, ownerBilling, scope);
         MemoryStore.Snapshot snap = store.snapshot();
         int nodes = snap != null && snap.graph() != null ? snap.graph().nodeCount() : 0;
         int edges = snap != null && snap.graph() != null ? snap.graph().edgeCount() : 0;
         PlayerEngine.LOGGER
            .info(
               "Memory: merged {} entities / {} relations; nodes={} edges={} cumImportance={} for {}.",
               plan.upserts().size(),
               plan.edges().size(),
               nodes,
               edges,
               store.cumulativeImportanceSinceLastReflection(),
               scope
            );
         maybeDispatchReflection(controller, server, ownerBilling, scope, store);
      }
   }

   private static void maybeDispatchReflection(
      PlayerEngineController controller, MinecraftServer server, Player2PayerResolution.ApiBillingContext ownerBilling, MemoryScope scope, MemoryStore store
   ) {
      try {
         if (controller == null || server == null || scope == null || store == null || ownerBilling == null || ownerBilling.billingKey() == null) {
            return;
         }

         Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
         long threshold = cfg != null ? cfg.getReflectionImportanceThresholdClamped() : 0L;
         if (!ReflectionTrigger.shouldReflect(store, threshold)) {
            return;
         }

         MemoryGateDecision decision = MemoryGate.preflight(server, ownerBilling);
         Layer3Context layer3Context = Layer3Context.fromGate(ownerBilling, decision);
         if (!layer3Context.layer3Enabled()) {
            return;
         }

         AtomicBoolean inFlight = REFLECTION_IN_FLIGHT.computeIfAbsent(scope, s -> new AtomicBoolean(false));
         ReflectionService.Retriever retriever = (q, k) -> retrieveNodes(store, q, k);
         Consumer<String> onSummaryUpdated = newSummary -> refreshSystemBlock(controller);
         ReflectionService.DegradationSink degradation = (playerMsg, modelToken) -> {};
         long nowTick = server.getTickCount();
         ReflectionService.scheduleReflection(server, store, layer3Context, inFlight, retriever, nowTick, onSummaryUpdated, degradation);
      } catch (RuntimeException e) {
         PlayerEngine.LOGGER.warn("Memory reflection: dispatch failed ({})", e.getClass().getSimpleName());
      }
   }

   private static List<MemoryNode> retrieveNodes(MemoryStore store, String question, int k) {
      List<MemoryNode> out = new ArrayList<>();
      if (store != null && question != null && !question.isBlank()) {
         MemoryStore.Snapshot snap = store.snapshot();
         if (snap != null && snap.graph() != null) {
            List<RetrievalHit> hits = new MemoryRetriever(store).query(question, k);
            if (hits == null) {
               return out;
            }

            for (RetrievalHit hit : hits) {
               if (k > 0 && out.size() >= k) {
                  break;
               }

               if (hit != null && hit.id() != null) {
                  MemoryNode node = snap.graph().node(hit.id());
                  if (node != null) {
                     out.add(node);
                  }
               }
            }

            return out;
         } else {
            return out;
         }
      } else {
         return out;
      }
   }

   private static void refreshSystemBlock(PlayerEngineController controller) {
      try {
         if (controller != null && controller.getAIPersistantData() != null) {
            controller.getAIPersistantData().updateSystemPrompt();
         }
      } catch (RuntimeException e) {
         PlayerEngine.LOGGER.warn("Memory reflection: system-block refresh failed ({})", e.getClass().getSimpleName());
      }
   }

   static void enqueueEmbedBackfill(
      PlayerEngineController controller, MinecraftServer server, Player2PayerResolution.ApiBillingContext ownerBilling, MemoryScope scope
   ) {
      if (controller != null && server != null && scope != null && ownerBilling != null && ownerBilling.billingKey() != null) {
         if (EmbeddingProvider.isAvailable()) {
            if (EMBED_IN_FLIGHT.add(scope)) {
               MemoryLlmClient.MEMORY_EXECUTOR.submit(() -> {
                  try {
                     runEmbedPass(controller, server, ownerBilling, scope);
                  } catch (RuntimeException e) {
                     PlayerEngine.LOGGER.warn("Memory embed: pass failed ({})", e.getClass().getSimpleName());
                  } finally {
                     EMBED_IN_FLIGHT.remove(scope);
                  }
               });
            }
         }
      }
   }

   private static void runEmbedPass(
      PlayerEngineController controller, MinecraftServer server, Player2PayerResolution.ApiBillingContext ownerBilling, MemoryScope scope
   ) {
      CompletableFuture<List<String[]>> collected = new CompletableFuture<>();
      server.execute(() -> {
         try {
            MemoryStore store = lookupStore(server, scope);
            if (store == null) {
               collected.complete(List.of());
               return;
            }

            List<String> ids = store.denseBackfillCandidates(100);
            if (ids.isEmpty()) {
               store.setDenseBackfillComplete(true);
               collected.complete(List.of());
               return;
            }

            List<String[]> batchx = new ArrayList<>(ids.size());

            for (String id : ids) {
               String text = store.denseEmbeddableText(id);
               if (text != null && !text.isBlank()) {
                  batchx.add(new String[]{id, text});
               }
            }

            collected.complete(batchx);
         } catch (RuntimeException e) {
            collected.complete(List.of());
         }
      });

      List<String[]> batch;
      try {
         batch = collected.get(5L, TimeUnit.SECONDS);
      } catch (Exception e) {
         return;
      }

      if (!batch.isEmpty()) {
         List<String> texts = new ArrayList<>(batch.size());

         for (String[] pair : batch) {
            texts.add(pair[1]);
         }

         EmbeddingResult result = EmbeddingBatcher.embedAll(texts, controller, ownerBilling);
         if (!result.ok()) {
            PlayerEngine.LOGGER.info("Memory embed: batch degraded ({}) for {}.", result.degradedToken(), scope);
         } else {
            float[][] vecs = result.vectors();
            boolean maybeMore = batch.size() >= 100;
            server.execute(() -> {
               try {
                  MemoryStore store = lookupStore(server, scope);
                  if (store == null) {
                     return;
                  }

                  int applied = 0;

                  for (int i = 0; i < batch.size() && i < vecs.length; i++) {
                     if (vecs[i] != null) {
                        store.applyNodeVector(batch.get(i)[0], vecs[i]);
                        applied++;
                     }
                  }

                  PlayerEngine.LOGGER.info("Memory embed: applied {} vector(s) for {}.", applied, scope);
                  if (!maybeMore) {
                     store.setDenseBackfillComplete(true);
                  }
               } catch (RuntimeException e) {
                  PlayerEngine.LOGGER.warn("Memory embed: apply failed ({})", e.getClass().getSimpleName());
               }
            });
            if (maybeMore) {
               enqueueEmbedBackfill(controller, server, ownerBilling, scope);
            }
         }
      }
   }

   public static void backfillDenseVectors(
      PlayerEngineController controller, MinecraftServer server, Player2PayerResolution.ApiBillingContext ownerBilling, MemoryScope scope
   ) {
      enqueueEmbedBackfill(controller, server, ownerBilling, scope);
   }

   private static long currentGameTime(MinecraftServer server) {
      return server != null && server.overworld() != null ? server.overworld().getGameTime() : 0L;
   }

   private static MemoryStore lookupStore(MinecraftServer server, MemoryScope scope) {
      MemoryIngestionService.MemoryStoreProvider provider = storeProvider;
      if (provider == null) {
         return null;
      }

      try {
         return provider.storeFor(server, scope);
      } catch (RuntimeException e) {
         return null;
      }
   }

   private static MemoryScope resolveScope(PlayerEngineController controller) {
      Character character = controller.getAIPersistantData() != null ? controller.getAIPersistantData().getCharacter() : null;
      String companionId = character != null ? character.id() : null;
      if (companionId != null && !companionId.isBlank()) {
         UUID ownerUuid = controller.getOwner() != null ? controller.getOwner().getUUID() : null;
         if (ownerUuid != null) {
            return MemoryScope.of(ownerUuid, companionId);
         }

         LivingEntity entity = controller.getPlayer();
         return entity == null ? null : MemoryScope.ofEntityFallback(entity.getUUID(), companionId);
      } else {
         return null;
      }
   }

   private static MinecraftServer resolveServer(PlayerEngineController controller) {
      try {
         LivingEntity entity = controller.getPlayer();
         if (entity != null && entity.getServer() != null) {
            return entity.getServer();
         }
      } catch (RuntimeException var2) {
      }

      return null;
   }

   @FunctionalInterface
   public interface MemoryStoreProvider {
      MemoryStore storeFor(MinecraftServer var1, MemoryScope var2);
   }
}
