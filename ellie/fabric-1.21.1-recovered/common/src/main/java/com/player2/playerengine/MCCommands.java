package com.player2.playerengine;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.player2.playerengine.agentic.elliegps.EllieGPSStore;
import com.player2.playerengine.agentic.elliegps.EllieGPSWaypointCountingService;
import com.player2.playerengine.agentic.elliegps.EllieGPSWaypointIndex;
import com.player2.playerengine.executor.BudgetFallbackBehavior;
import com.player2.playerengine.executor.BudgetTracker;
import com.player2.playerengine.help.ArgNote;
import com.player2.playerengine.help.HelpCoverageVerifier;
import com.player2.playerengine.help.HelpEntry;
import com.player2.playerengine.help.HelpRegistry;
import com.player2.playerengine.help.HelpRenderer;
import com.player2.playerengine.memory.MemoryScope;
import com.player2.playerengine.memory.MemoryStore;
import com.player2.playerengine.memory.MemoryStoreRegistry;
import com.player2.playerengine.memory.budget.MemoryGate;
import com.player2.playerengine.memory.budget.MemoryGateDecision;
import com.player2.playerengine.memory.ingest.MemoryIngestionService;
import com.player2.playerengine.memory.reflection.ReflectionTrigger;
import com.player2.playerengine.modintelligence.ModIntelligenceService;
import com.player2.playerengine.modintelligence.ModIntelligenceStatus;
import com.player2.playerengine.modintelligence.capability.CapabilityMap;
import com.player2.playerengine.modintelligence.capability.CapabilitySubjectKind;
import com.player2.playerengine.modintelligence.enrich.ModIntelligenceEnrichmentClient;
import com.player2.playerengine.modintelligence.enrich.ModIntelligenceSpendSafety;
import com.player2.playerengine.modintelligence.enrich.ModelBlacklist;
import com.player2.playerengine.modintelligence.query.CapabilityHit;
import com.player2.playerengine.modintelligence.query.CapabilityQuery;
import com.player2.playerengine.player2api.AgentConversationData;
import com.player2.playerengine.player2api.AgentSideEffects;
import com.player2.playerengine.player2api.AiTaskClass;
import com.player2.playerengine.player2api.BudgetThresholdsResolver;
import com.player2.playerengine.player2api.Character;
import com.player2.playerengine.player2api.JoulesCache;
import com.player2.playerengine.player2api.ModelTierRouter;
import com.player2.playerengine.player2api.Player2APIService;
import com.player2.playerengine.player2api.Player2PayerResolution;
import com.player2.playerengine.player2api.ProfileUrlResolver;
import com.player2.playerengine.player2api.RoutingResult;
import com.player2.playerengine.player2api.auth.AuthenticationManager;
import com.player2.playerengine.player2api.config.BudgetThresholds;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;
import com.player2.playerengine.player2api.manager.ConversationManager;
import com.player2.playerengine.retrieval.RagIndex;
import com.player2.playerengine.retrieval.RetrievalHit;
import com.player2.playerengine.retrieval.SeedToolMetadata;
import com.player2.playerengine.retrieval.ToolDocument;
import com.player2.playerengine.retrieval.ToolRetriever;
import com.player2.playerengine.retrieval.learning.AliasLearningService;
import com.player2.playerengine.structureprotection.PlayerPlacedBlockStore;
import com.player2.playerengine.tasks.cooking.resolver.CookingRecipeAccessImpl;
import com.player2.playerengine.tasks.crafting.CraftMacroStep;
import com.player2.playerengine.tasks.crafting.resolver.IngredientInspectorImpl;
import com.player2.playerengine.tasks.crafting.resolver.MaterialResolver;
import com.player2.playerengine.tasks.crafting.resolver.RecipeAccessImpl;
import com.player2.playerengine.tasks.crafting.resolver.ResolverResult;
import com.player2.playerengine.tasks.deferred.DeferredJobStore;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.MaterialAvailability;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.event.events.common.LifecycleEvent.ServerState;
import dev.architectury.event.events.common.TickEvent.Server;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MCCommands {
   public static final Logger LOGGER = LogManager.getLogger("PlayerEngine");
   private static final int RAG_RETRIEVE_MAX_CATEGORIES = 8;
   private static final int MEMORY_FLUSH_INTERVAL_TICKS = 100;
   private static final SuggestionProvider<CommandSourceStack> ROUTING_TASK_CLASS_SUGGEST = (ctx, builder) -> {
      for (AiTaskClass c : AiTaskClass.values()) {
         builder.suggest(c.name());
      }

      return builder.buildFuture();
   };
   private static final SuggestionProvider<CommandSourceStack> NPC_SUGGEST = (context, builder) -> {
      CommandSourceStack src = (CommandSourceStack)context.getSource();
      Player player = src.getPlayerOrException();
      UUID ownerUUID = player.getUUID();
      String remaining = builder.getRemaining().toLowerCase();

      for (AgentConversationData data : ConversationManager.getDataByOwner(ownerUUID)) {
         String name = data.getName();
         if (name.toLowerCase().startsWith(remaining)) {
            builder.suggest(name);
         }
      }

      return builder.buildFuture();
   };

   public static void onInit() {
      LifecycleEvent.SERVER_STARTING.register((ServerState)server -> {
         PlayerEngine.resetBackgroundExecutorsShutdownGate();
         LOGGER.info("Server starting, registering MC commands");
         register(server);
         ModIntelligenceService.initialize(server);

         try {
            EllieGPSStore store = EllieGPSStore.loadForServer(server);
            EllieGPSWaypointIndex.setCurrent(EllieGPSWaypointIndex.loadOrRebuild(store));
            MaterialAvailability.setWaypointSource(new EllieGPSWaypointCountingService());
         } catch (Exception e) {
            LOGGER.warn("EllieGPS startup wiring failed — waypoint features degrade to the stub: {}", e.getMessage());
         }

         try {
            DeferredJobStore.loadForServer(server);
         } catch (Exception e) {
            LOGGER.warn("DeferredJobStore startup load failed — deferred-smelt resume unavailable: {}", e.getMessage());
         }

         try {
            PlayerPlacedBlockStore.loadForServer(server);
         } catch (Exception e) {
            LOGGER.warn("PlayerPlacedBlockStore startup load failed — block protection unavailable: {}", e.getMessage());
         }

         try {
            MemoryIngestionService.setStoreProvider(MemoryStoreRegistry::getOrLoad);
         } catch (Exception e) {
            LOGGER.warn("Memory store-provider wiring failed — memory ingestion disabled: {}", e.getClass().getSimpleName());
         }
      });
      TickEvent.SERVER_POST.register((Server)server -> {
         try {
            if (server != null && server.getTickCount() % 100 == 0) {
               MemoryStoreRegistry.flushAllIfDirty(server);
            }
         } catch (Exception e) {
            LOGGER.warn("Memory tick-end flush failed: {}", e.getClass().getSimpleName());
         }
      });
      LifecycleEvent.SERVER_STOPPING.register((ServerState)server -> {
         com.player2.playerengine.tasks.movement.OwnerFollowTransfer.clear(server);
         if (server != null && server.isDedicatedServer()) {
            try {
               ConversationManager.QueueClearSummary summary = ConversationManager.clearPendingWork();
               LOGGER.info("SERVER_STOPPING (dedicated): drained queues={} buckets={}", summary.queuesCleared(), summary.bucketsShutdown());
            } catch (Exception e) {
               LOGGER.warn("SERVER_STOPPING clearPendingWork failed: {}", e.getMessage());
            }
         }

         try {
            EllieGPSStore ellieStore = EllieGPSStore.get();
            if (ellieStore != null) {
               ellieStore.clear();
            }

            EllieGPSWaypointIndex.setCurrent(null);
         } catch (Exception e) {
            LOGGER.warn("SERVER_STOPPING EllieGPS cleanup failed: {}", e.getMessage());
         }

         try {
            DeferredJobStore deferredStore = DeferredJobStore.get();
            if (deferredStore != null) {
               deferredStore.clear();
            }
         } catch (Exception e) {
            LOGGER.warn("SERVER_STOPPING DeferredJobStore cleanup failed: {}", e.getMessage());
         }

         try {
            PlayerPlacedBlockStore placedStore = PlayerPlacedBlockStore.get();
            if (placedStore != null) {
               placedStore.clear();
            }
         } catch (Exception e) {
            LOGGER.warn("SERVER_STOPPING PlayerPlacedBlockStore cleanup failed: {}", e.getMessage());
         }

         try {
            MemoryStoreRegistry.clearAll(server);
         } catch (Exception e) {
            LOGGER.warn("SERVER_STOPPING memory store cleanup failed: {}", e.getClass().getSimpleName());
         }

         try {
            MemoryIngestionService.clearSessionState();
         } catch (Exception e) {
            LOGGER.warn("SERVER_STOPPING memory ingestion cleanup failed: {}", e.getClass().getSimpleName());
         }

         PlayerEngine.shutdownBackgroundExecutors();
      });
   }

   public static void register(MinecraftServer server) {
      CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
      registerFromDispatch(dispatcher);
   }

   private static void registerFromDispatch(CommandDispatcher<CommandSourceStack> dispatcher) {
      contributeHelpEntries();
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                       "playerengine"
                                    )
                                    .then(registerRelog()))
                                 .then(registerSummon()))
                              .then(registerQueueClear()))
                           .then(registerRag()))
                        .then(registerRouting()))
                     .then(registerCapability()))
                  .then(registerResolve()))
               .then(registerMemory()))
            .then(registerHelp())
      );
      HelpCoverageVerifier.verify(dispatcher);
   }

   private static LiteralArgumentBuilder<CommandSourceStack> registerMemory() {
      return (LiteralArgumentBuilder<CommandSourceStack>)((LiteralArgumentBuilder)Commands.literal("memory").requires(src -> src.hasPermission(2)))
         .then(Commands.literal("status").executes(MCCommands::executeMemoryStatus));
   }

   private static int executeMemoryStatus(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      MinecraftServer server = src.getServer();
      Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
      StringBuilder sb = new StringBuilder();
      sb.append("Memory (Phase D graph-RAG) status\n");
      sb.append("  enableGraphRagMemory: ").append(cfg.isEnableGraphRagMemory()).append('\n');
      ServerPlayer ownerPlayer = null;

      try {
         ownerPlayer = src.getPlayerOrException();
      } catch (Exception var24) {
      }

      if (ownerPlayer != null && server != null) {
         String clientId = cfg.getHeartbeatClientId();
         Player2PayerResolution.ApiBillingContext ownerBilling = new Player2PayerResolution.ApiBillingContext(ownerPlayer, null);
         MemoryGateDecision decision = MemoryGate.preflight(server, ownerBilling);
         sb.append("  owner: ").append(ownerPlayer.getName().getString()).append('\n');
         sb.append("  owner patron/gate: ")
            .append(decision.allowed() ? "allowed (confirmed patron, within budget)" : "blocked — " + decision.reason().name())
            .append('\n');
         UUID ownerUuid = ownerPlayer.getUUID();
         long threshold = cfg.getReflectionImportanceThresholdClamped();
         int shown = 0;

         for (PlayerEngineController controller : PlayerEngineController.staticControllers.values()) {
            if (controller != null && controller.getOwner() != null && ownerUuid.equals(controller.getOwner().getUUID())) {
               Character character = controller.getAIPersistantData() != null ? controller.getAIPersistantData().getCharacter() : null;
               String companionId = character != null ? character.id() : null;
               if (companionId != null && !companionId.isBlank()) {
                  MemoryScope scope = MemoryScope.of(ownerUuid, companionId);
                  MemoryStore store = MemoryStoreRegistry.peek(scope);
                  String companionName = character.name() != null ? character.name() : companionId;
                  sb.append("  companion '").append(companionName).append("':");
                  if (store != null && store.snapshot() != null && store.snapshot().graph() != null) {
                     MemoryStore.Snapshot snap = store.snapshot();
                     long cumImportance = store.cumulativeImportanceSinceLastReflection();
                     boolean summarySet = store.relationshipSummary() != null && !store.relationshipSummary().isBlank();
                     sb.append('\n');
                     sb.append("    nodes=").append(snap.graph().nodeCount()).append(" edges=").append(snap.graph().edgeCount()).append('\n');
                     sb.append("    cumulativeImportanceSinceLastReflection=")
                        .append(cumImportance)
                        .append(" (reflection threshold=")
                        .append(threshold)
                        .append(ReflectionTrigger.shouldReflect(store, threshold) ? ", DUE)" : ")")
                        .append('\n');
                     sb.append("    relationshipSummary set: ").append(summarySet).append(" (summaryVersion=").append(store.summaryVersion()).append(")\n");
                     shown++;
                  } else {
                     sb.append(" no memory store loaded\n");
                  }
               }
            }
         }

         if (shown == 0) {
            sb.append("  (no active companions for this owner)\n");
         }
      } else {
         sb.append("  (run as a player to see owner patron/gate + per-companion stats)\n");
      }

      String body = sb.toString();
      src.sendSuccess(() -> Component.literal(body), false);
      return 1;
   }

   private static LiteralArgumentBuilder<CommandSourceStack> registerHelp() {
      return (LiteralArgumentBuilder<CommandSourceStack>)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("help").executes(context -> {
         HelpRenderer.index("playerengine", 1, (CommandSourceStack)context.getSource());
         return 1;
      })).then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(context -> {
         HelpRenderer.index("playerengine", IntegerArgumentType.getInteger(context, "page"), (CommandSourceStack)context.getSource());
         return 1;
      }))).then(Commands.argument("command", StringArgumentType.greedyString()).executes(context -> {
         String raw = StringArgumentType.getString(context, "command");
         int page = 1;
         String command = raw;
         int lastSpace = raw.lastIndexOf(32);
         if (lastSpace > 0) {
            String tail = raw.substring(lastSpace + 1);

            try {
               page = Integer.parseInt(tail);
               command = raw.substring(0, lastSpace).trim();
            } catch (NumberFormatException var7) {
            }
         }

         HelpRenderer.detail("playerengine", command, page, (CommandSourceStack)context.getSource());
         return 1;
      }));
   }

   private static void contributeHelpEntries() {
      HelpRegistry.register(
         new HelpEntry("playerengine", "relog", "relog", "help.playerengine.relog.short", "help.playerengine.relog.long", List.of(), 0, null, "general")
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "tpto",
            "tpto <username>",
            "help.playerengine.tpto.short",
            null,
            List.of(new ArgNote("username", "help.playerengine.tpto.arg.username")),
            0,
            null,
            "general"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "help",
            "help [<command>] [page]",
            "help.playerengine.help.short",
            "help.playerengine.help.long",
            List.of(new ArgNote("command", "help.playerengine.help.arg.command"), new ArgNote("page", "help.playerengine.help.arg.page")),
            0,
            null,
            "general"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "queue clear",
            "queue clear [player]",
            "help.playerengine.queue-clear.short",
            "help.playerengine.queue-clear.long",
            List.of(new ArgNote("player", "help.playerengine.queue-clear.arg.player")),
            2,
            null,
            "diagnostics"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "resolve",
            "resolve <item> [count]",
            "help.playerengine.resolve.short",
            "help.playerengine.resolve.long",
            List.of(new ArgNote("item", "help.playerengine.resolve.arg.item"), new ArgNote("count", "help.playerengine.resolve.arg.count")),
            2,
            null,
            "diagnostics"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "memory status",
            "memory status",
            "help.playerengine.memory-status.short",
            "help.playerengine.memory-status.long",
            List.of(),
            2,
            null,
            "diagnostics"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "rag retrieve",
            "rag retrieve [--category <cat>] <goal>",
            "help.playerengine.rag-retrieve.short",
            "help.playerengine.rag-retrieve.long",
            List.of(new ArgNote("goal", "help.playerengine.rag-retrieve.arg.goal"), new ArgNote("category", "help.playerengine.rag-retrieve.arg.category")),
            2,
            null,
            "rag"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine", "rag reload", "rag reload", "help.playerengine.rag-reload.short", "help.playerengine.rag-reload.long", List.of(), 2, null, "rag"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "rag audit tail",
            "rag audit tail [n]",
            "help.playerengine.rag-audit-tail.short",
            null,
            List.of(new ArgNote("n", "help.playerengine.rag-audit-tail.arg.n")),
            2,
            null,
            "rag"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "rag reset_learned",
            "rag reset_learned [toolId] [--all-owners]",
            "help.playerengine.rag-reset-learned.short",
            "help.playerengine.rag-reset-learned.long",
            List.of(new ArgNote("toolId", "help.playerengine.rag-reset-learned.arg.toolId")),
            2,
            null,
            "rag"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "rag reset_learned --all-owners",
            "rag reset_learned [toolId] --all-owners",
            "help.playerengine.rag-reset-learned-all-owners.short",
            null,
            List.of(),
            2,
            null,
            "rag"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "rag inspect",
            "rag inspect <toolId>",
            "help.playerengine.rag-inspect.short",
            "help.playerengine.rag-inspect.long",
            List.of(new ArgNote("toolId", "help.playerengine.rag-inspect.arg.toolId")),
            2,
            null,
            "rag"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "routing probe",
            "routing probe <TASK_CLASS> [--simulate-joules <n>] [--simulate-soft-budget]",
            "help.playerengine.routing-probe.short",
            "help.playerengine.routing-probe.long",
            List.of(new ArgNote("taskClass", "help.playerengine.routing-probe.arg.taskClass")),
            2,
            null,
            "routing"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "routing probe --simulate-soft-budget",
            "routing probe --simulate-soft-budget <TASK_CLASS>",
            "help.playerengine.routing-probe-simulate-soft-budget.short",
            null,
            List.of(),
            2,
            null,
            "routing"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "routing probe --simulate-joules --simulate-soft-budget",
            "routing probe --simulate-joules <n> --simulate-soft-budget <TASK_CLASS>",
            "help.playerengine.routing-probe-simulate-joules-soft-budget.short",
            null,
            List.of(),
            2,
            null,
            "routing"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine", "capability status", "capability status", "help.playerengine.capability-status.short", null, List.of(), 2, null, "capability"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "capability query",
            "capability query <text>",
            "help.playerengine.capability-query.short",
            null,
            List.of(new ArgNote("text", "help.playerengine.capability-query.arg.text")),
            2,
            null,
            "capability"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "capability inspect",
            "capability inspect <kind> <id>",
            "help.playerengine.capability-inspect.short",
            null,
            List.of(new ArgNote("kind", "help.playerengine.capability-inspect.arg.kind"), new ArgNote("id", "help.playerengine.capability-inspect.arg.id")),
            2,
            null,
            "capability"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "capability rebuild",
            "capability rebuild [force]",
            "help.playerengine.capability-rebuild.short",
            "help.playerengine.capability-rebuild.long",
            List.of(),
            2,
            null,
            "capability"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "capability rebuild force",
            "capability rebuild force",
            "help.playerengine.capability-rebuild-force.short",
            null,
            List.of(),
            2,
            null,
            "capability"
         )
      );
      HelpRegistry.register(
         new HelpEntry(
            "playerengine",
            "capability enrich",
            "capability enrich [limit]",
            "help.playerengine.capability-enrich.short",
            "help.playerengine.capability-enrich.long",
            List.of(new ArgNote("limit", "help.playerengine.capability-enrich.arg.limit")),
            2,
            null,
            "capability"
         )
      );
   }

   private static LiteralArgumentBuilder<CommandSourceStack> registerQueueClear() {
      return (LiteralArgumentBuilder<CommandSourceStack>)((LiteralArgumentBuilder)Commands.literal("queue").requires(src -> src.hasPermission(2)))
         .then(((LiteralArgumentBuilder)Commands.literal("clear").executes(ctx -> {
            ConversationManager.QueueClearSummary summary = ConversationManager.clearPendingWork();
            sendQueueClearFeedback((CommandSourceStack)ctx.getSource(), summary, null);
            return summary.queuesCleared();
         })).then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            ConversationManager.QueueClearSummary summary = ConversationManager.clearPendingWorkFor(target.getUUID());
            sendQueueClearFeedback((CommandSourceStack)ctx.getSource(), summary, target.getName().getString());
            return summary.queuesCleared();
         })));
   }

   private static void sendQueueClearFeedback(CommandSourceStack src, ConversationManager.QueueClearSummary summary, String targetName) {
      String label = targetName != null ? "PlayerEngine queue clear (" + targetName + ")" : "PlayerEngine queue clear";
      String body = String.format(
         "%s: drained %d conversation queue(s), shut down %d LLM bucket(s).", label, summary.queuesCleared(), summary.bucketsShutdown()
      );
      LOGGER.info(body);
      src.sendSuccess(
         () -> targetName != null
            ? Component.translatable(
               "message.playerengine.commands.queue_clear_scoped", new Object[]{targetName, summary.queuesCleared(), summary.bucketsShutdown()}
            )
            : Component.translatable("message.playerengine.commands.queue_clear", new Object[]{summary.queuesCleared(), summary.bucketsShutdown()}),
         true
      );
   }

   private static LiteralArgumentBuilder<CommandSourceStack> registerRag() {
      return (LiteralArgumentBuilder<CommandSourceStack>)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                           "rag"
                        )
                        .requires(src -> src.hasPermission(2)))
                     .then(buildRagRetrieveCommand()))
                  .then(
                     Commands.literal("reload")
                        .executes(
                           ctx -> {
                              MinecraftServer server = ((CommandSourceStack)ctx.getSource()).getServer();
                              RagIndex.reloadAll(server);
                              ToolRetriever global = RagIndex.getGlobal();
                              String logMsg = "RAG reload complete. Global index: " + (global != null ? global.documentCount() + " docs" : "FAILED");
                              LOGGER.info(logMsg);
                              Component displayMsg = global != null
                                 ? Component.translatable("message.playerengine.rag.reload_complete", new Object[]{global.documentCount()})
                                 : Component.translatable("message.playerengine.rag.reload_failed");
                              ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> displayMsg, true);
                              return 1;
                           }
                        )
                  ))
               .then(
                  Commands.literal("audit")
                     .then(
                        ((LiteralArgumentBuilder)Commands.literal("tail").executes(ctx -> executeRagAuditTail(ctx, 20)))
                           .then(
                              Commands.argument("n", IntegerArgumentType.integer(1, 100))
                                 .executes(ctx -> executeRagAuditTail(ctx, IntegerArgumentType.getInteger(ctx, "n")))
                           )
                     )
               ))
            .then(buildRagResetLearnedCommand()))
         .then(Commands.literal("inspect").then(Commands.argument("toolId", StringArgumentType.word()).executes(ctx -> {
            CommandSourceStack src = (CommandSourceStack)ctx.getSource();
            String toolId = StringArgumentType.getString(ctx, "toolId");
            ToolRetriever retriever = resolveRetriever(src);
            if (retriever == null) {
               src.sendFailure(Component.translatable("message.playerengine.rag.not_initialized"));
               return 0;
            }

            ToolDocument merged = retriever.getRegistry().getDocument(toolId);
            if (merged == null) {
               src.sendFailure(Component.translatable("message.playerengine.rag.inspect_tool_not_found", new Object[]{toolId}));
               return 0;
            }

            ToolDocument baseline = SeedToolMetadata.byId(toolId);
            StringBuilder sb = new StringBuilder();
            sb.append("=== inspect: ").append(toolId).append(" ===\n");
            sb.append("name: ").append(merged.name()).append("\n");
            sb.append("description: ").append(merged.description()).append("\n");
            sb.append("whenToUse: ").append(merged.whenToUse()).append("\n");
            sb.append("categoryTags: ").append(String.join(", ", merged.categoryTags())).append("\n");
            sb.append("keywords:\n");
            Set<String> baseKeywords = baseline == null ? Collections.emptySet() : new HashSet<>(baseline.keywords());

            for (String kw : merged.keywords()) {
               boolean overlay = !baseKeywords.contains(kw) && !baseKeywords.contains(kw.toLowerCase());
               sb.append("  ").append(overlay ? "[+] " : "    ").append(kw).append("\n");
            }

            sb.append("examples:\n");
            Set<String> baseExamples = baseline == null ? Collections.emptySet() : new HashSet<>(baseline.examples());

            for (String ex : merged.examples()) {
               boolean overlay = !baseExamples.contains(ex);
               sb.append("  ").append(overlay ? "[+] " : "    ").append(ex).append("\n");
            }

            LOGGER.info(sb.toString());
            src.sendSuccess(() -> Component.literal(sb.toString()), false);
            return 1;
         })));
   }

   private static LiteralArgumentBuilder<CommandSourceStack> buildRagRetrieveCommand() {
      LiteralArgumentBuilder<CommandSourceStack> retrieve = Commands.literal("retrieve");
      retrieve.then(Commands.argument("goal", StringArgumentType.greedyString()).executes(ctx -> executeRagRetrieve(ctx, Collections.emptyList())));
      ArgumentBuilder<CommandSourceStack, ?> withCategories = Commands.argument("goal", StringArgumentType.greedyString())
         .executes(ctx -> executeRagRetrieve(ctx, collectRagCategoryArgs(ctx)));

      for (int i = 8; i >= 1; i--) {
         int idx = i;
         withCategories = Commands.literal("--category").then(Commands.argument("cat" + idx, StringArgumentType.word()).then(withCategories));
      }

      retrieve.then(withCategories);
      return retrieve;
   }

   private static List<String> collectRagCategoryArgs(CommandContext<CommandSourceStack> ctx) {
      List<String> cats = new ArrayList<>();

      for (int i = 1; i <= 8; i++) {
         String key = "cat" + i;

         try {
            cats.add(StringArgumentType.getString(ctx, key));
         } catch (IllegalArgumentException ignored) {
            break;
         }
      }

      return cats;
   }

   private static int executeRagRetrieve(CommandContext<CommandSourceStack> ctx, List<String> categoryTags) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      String goal = StringArgumentType.getString(ctx, "goal");
      ToolRetriever retriever = resolveRetriever(src);
      if (retriever == null) {
         src.sendFailure(Component.translatable("message.playerengine.rag.not_initialized"));
         return 0;
      }

      Set<String> filter = categoryTags.isEmpty() ? null : new HashSet<>(categoryTags);
      List<RetrievalHit> hits = retriever.retrieve(goal, 5, filter);
      StringBuilder sb = new StringBuilder();
      if (filter != null && !filter.isEmpty()) {
         sb.append("Categories: ").append(String.join(", ", filter)).append("\n");
      }

      sb.append("RAG top-").append(hits.size()).append(" for \"").append(goal).append("\":\n");

      for (int i = 0; i < hits.size(); i++) {
         RetrievalHit h = hits.get(i);
         sb.append(i + 1)
            .append(". ")
            .append(h.toolId())
            .append(" (score=")
            .append(String.format("%.4f", h.score()))
            .append(", bm25=")
            .append(h.bm25Rank() == Integer.MAX_VALUE ? "-" : h.bm25Rank())
            .append(", minhash=")
            .append(h.minHashRank() == Integer.MAX_VALUE ? "-" : h.minHashRank())
            .append(")\n");
      }

      if (hits.isEmpty()) {
         sb.append("  (no results)");
      }

      LOGGER.info(sb.toString());
      src.sendSuccess(() -> Component.literal(sb.toString()), false);
      return hits.size();
   }

   private static LiteralArgumentBuilder<CommandSourceStack> registerRouting() {
      return (LiteralArgumentBuilder<CommandSourceStack>)((LiteralArgumentBuilder)Commands.literal("routing").requires(src -> src.hasPermission(2)))
         .then(buildRoutingProbeCommand());
   }

   private static ArgumentBuilder<CommandSourceStack, ?> buildRoutingProbeCommand() {
      LiteralArgumentBuilder<CommandSourceStack> probe = Commands.literal("probe");
      probe.then(routingProbeTaskClassArg(null, false));
      probe.then(Commands.literal("--simulate-soft-budget").then(routingProbeTaskClassArg(null, true)));
      probe.then(
         Commands.literal("--simulate-joules")
            .then(Commands.argument("joules", IntegerArgumentType.integer(0)).then(routingProbeTaskClassArg("joules", false)))
      );
      probe.then(
         Commands.literal("--simulate-joules")
            .then(
               Commands.argument("joules", IntegerArgumentType.integer(0))
                  .then(Commands.literal("--simulate-soft-budget").then(routingProbeTaskClassArg("joules", true)))
            )
      );
      return probe;
   }

   private static ArgumentBuilder<CommandSourceStack, ?> routingProbeTaskClassArg(String joulesArgName, boolean simulateSoftBudget) {
      return Commands.argument("taskClass", StringArgumentType.word()).suggests(ROUTING_TASK_CLASS_SUGGEST).executes(ctx -> {
         Integer simJoules = joulesArgName != null ? IntegerArgumentType.getInteger(ctx, joulesArgName) : null;
         return executeRoutingProbe(ctx, simJoules, simulateSoftBudget);
      });
   }

   private static int executeRoutingProbe(CommandContext<CommandSourceStack> ctx, Integer simulateJoules, boolean simulateSoftBudget) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      String taskClassRaw = StringArgumentType.getString(ctx, "taskClass");

      AiTaskClass taskClass;
      try {
         taskClass = AiTaskClass.valueOf(taskClassRaw.toUpperCase());
      } catch (IllegalArgumentException e) {
         src.sendFailure(Component.translatable("message.playerengine.routing.unknown_task_class", new Object[]{taskClassRaw}));
         return 0;
      }

      if (PlayerEngineController.staticAPIServices.isEmpty()) {
         src.sendFailure(Component.translatable("message.playerengine.commands.no_active_bots"));
         return 0;
      }

      Player2APIService apiService = PlayerEngineController.staticAPIServices.values().iterator().next();
      PlayerEngineController controller = apiService.getController();
      String initiatorName = null;

      try {
         initiatorName = src.getPlayerOrException().getName().getString();
      } catch (Exception var25) {
      }

      Player2PayerResolution.ApiBillingContext billing = Player2PayerResolution.resolve(controller, initiatorName, apiService.getClientId());
      String billingKey = billing.billingKey();
      MinecraftServer probeServer = src.getServer();
      BudgetThresholds thresholds = BudgetThresholdsResolver.resolve(probeServer, billing);
      Player2ServerRuntimeConfig serverConfig = Player2ServerConfigHolder.get();
      JoulesCache.JoulesSnapshot liveSnap = billingKey != null ? JoulesCache.get(billingKey).orElse(null) : null;
      JoulesCache.JoulesSnapshot snap = liveSnap;
      if (simulateJoules != null) {
         String tier = liveSnap != null ? liveSnap.patronTier : "";
         snap = JoulesCache.snapshotForProbe(simulateJoules.intValue(), tier);
      }

      BudgetTracker.BudgetCheckResult callBudget = simulateSoftBudget ? BudgetTracker.BudgetCheckResult.SOFT_LIMIT : BudgetTracker.peek(billingKey, thresholds);
      BudgetTracker.BudgetCheckResult joulesBudget = JoulesCache.checkJoulesThreshold(snap, thresholds);
      BudgetTracker.BudgetCheckResult combined = BudgetTracker.stricter(callBudget, joulesBudget);
      boolean a4WouldSupersede = false;
      String a4Note = "no";
      if (combined == BudgetTracker.BudgetCheckResult.SOFT_LIMIT) {
         String fallback = serverConfig.getFallbackProfile();
         boolean dedicated = serverConfig.isDedicatedClientProxy();
         if (serverConfig.getBudgetFallbackBehavior() == BudgetFallbackBehavior.SWITCH_PROFILE && fallback != null && !fallback.isBlank() && !dedicated) {
            a4WouldSupersede = ProfileUrlResolver.resolve(apiService, fallback).isPresent();
            a4Note = a4WouldSupersede
               ? "yes — A4 SWITCH_PROFILE would use fallback '" + fallback + "' (B3 skipped)"
               : "soft budget active but fallback profile '" + fallback + "' not resolved";
         } else if (simulateSoftBudget) {
            a4Note = "simulated soft budget — "
               + (
                  serverConfig.getBudgetFallbackBehavior() == BudgetFallbackBehavior.HARD_STOP
                     ? "HARD_STOP would block"
                     : "no SWITCH_PROFILE / missing fallback / dedicated proxy"
               );
         } else {
            a4Note = "soft budget active — " + serverConfig.getBudgetFallbackBehavior().name();
         }
      } else if (combined == BudgetTracker.BudgetCheckResult.HARD_LIMIT) {
         a4Note = "hard budget — all AI calls blocked (B3 not reached)";
      }

      RoutingResult routing = ModelTierRouter.resolveWithRule(
         taskClass, ProfileUrlResolver.getSoleNamedProfileBaseUrl(apiService), snap, thresholds, serverConfig
      );
      StringBuilder sb = new StringBuilder();
      sb.append("Routing probe — ").append(taskClass.name()).append('\n');
      sb.append("  B3 outcome: ").append(ModelTierRouter.describeOutcome(routing.decision())).append('\n');
      sb.append("  B3 matched rule: ").append(routing.matchedRule()).append('\n');
      sb.append("  on-device: ").append(routing.decision().isOnDevice()).append('\n');
      if (snap != null) {
         sb.append("  joules: ").append(snap.joulesDisplay());
         if (simulateJoules != null) {
            sb.append(" (simulated)");
         }

         sb.append('\n');
         sb.append("  patron_tier: ").append(snap.patronTier.isEmpty() ? "(none)" : snap.patronTier).append('\n');
         long ageMs = System.currentTimeMillis() - snap.refreshedAtMs;
         sb.append("  joules cache age: ").append(ageMs / 1000L).append("s\n");
      } else {
         sb.append("  joules: (no cached snapshot)\n");
      }

      sb.append("  softJoulesThreshold: ").append(thresholds.getSoftJoulesThreshold()).append('\n');
      sb.append("  hardJoulesThreshold: ").append(thresholds.getHardJoulesThreshold()).append('\n');
      sb.append("  call budget (peek): ").append(callBudget.name());
      if (simulateSoftBudget) {
         sb.append(" (simulated soft)");
      }

      sb.append('\n');
      sb.append("  joules budget: ").append(joulesBudget.name()).append('\n');
      sb.append("  combined budget: ").append(combined.name()).append('\n');
      sb.append("  A4 would supersede B3: ").append(a4Note).append('\n');
      sb.append("  fallbackProfile: ").append(serverConfig.getFallbackProfile() != null ? serverConfig.getFallbackProfile() : "(none)").append('\n');
      sb.append("  budgetFallbackBehavior: ").append(serverConfig.getBudgetFallbackBehavior().name()).append('\n');
      sb.append("  dedicatedClientProxy: ").append(serverConfig.isDedicatedClientProxy()).append('\n');
      sb.append("  cached ai_profiles: ").append(ProfileUrlResolver.getProfileNames(apiService)).append('\n');
      sb.append("  sole named profile URL: ").append(ProfileUrlResolver.getSoleNamedProfileBaseUrl(apiService).orElse("(none)")).append('\n');
      sb.append("  billingKey: ").append(billingKey != null ? billingKey : "(none)").append('\n');
      LOGGER.info(sb.toString());
      src.sendSuccess(() -> Component.literal(sb.toString()), false);
      return 1;
   }

   private static int executeRagAuditTail(CommandContext<CommandSourceStack> ctx, int n) {
      MinecraftServer server = ((CommandSourceStack)ctx.getSource()).getServer();
      List<String> lines = AliasLearningService.auditTail(server, n);
      String body = String.join("\n", lines);
      ((CommandSourceStack)ctx.getSource())
         .sendSuccess(() -> body.isEmpty() ? Component.translatable("message.playerengine.rag.audit_no_rows") : Component.literal(body), false);
      return 1;
   }

   private static LiteralArgumentBuilder<CommandSourceStack> buildRagResetLearnedCommand() {
      return (LiteralArgumentBuilder<CommandSourceStack>)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("reset_learned")
               .executes(ctx -> executeRagResetLearned(ctx, null, false)))
            .then(
               ((LiteralArgumentBuilder)Commands.literal("--all-owners").executes(ctx -> executeRagResetLearned(ctx, null, true)))
                  .then(
                     Commands.argument("toolId", StringArgumentType.word())
                        .executes(ctx -> executeRagResetLearned(ctx, StringArgumentType.getString(ctx, "toolId"), true))
                  )
            ))
         .then(
            ((RequiredArgumentBuilder)Commands.argument("toolId", StringArgumentType.word())
                  .executes(ctx -> executeRagResetLearned(ctx, StringArgumentType.getString(ctx, "toolId"), false)))
               .then(Commands.literal("--all-owners").executes(ctx -> executeRagResetLearned(ctx, StringArgumentType.getString(ctx, "toolId"), true)))
         );
   }

   private static int executeRagResetLearned(CommandContext<CommandSourceStack> ctx, String toolId, boolean allOwners) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      MinecraftServer server = src.getServer();
      UUID ownerUuid = null;
      if (!allOwners) {
         try {
            ServerPlayer player = src.getPlayerOrException();
            ownerUuid = player.getUUID();
         } catch (Exception e) {
            src.sendFailure(Component.translatable("message.playerengine.rag.reset_learned_console_error"));
            return 0;
         }
      }

      int count = AliasLearningService.resetLearned(server, ownerUuid, toolId);
      String scope = allOwners ? "all owners" : "owner " + ownerUuid;
      String tool = toolId != null ? " tool=" + toolId : " (all tools)";
      src.sendSuccess(() -> Component.translatable("message.playerengine.rag.reset_learned_success", new Object[]{scope, tool, count}), true);
      return 1;
   }

   private static ToolRetriever resolveRetriever(CommandSourceStack src) {
      try {
         ServerPlayer player = src.getPlayerOrException();
         MinecraftServer server = src.getServer();
         return RagIndex.getForOwner(server, player.getUUID());
      } catch (Exception e) {
         return RagIndex.getGlobal();
      }
   }

   private static LiteralArgumentBuilder<CommandSourceStack> registerRelog() {
      return (LiteralArgumentBuilder<CommandSourceStack>)Commands.literal("relog").executes(context -> {
         for (Player2APIService service : PlayerEngineController.staticAPIServices.values()) {
            LOGGER.info("relog command");
            String clientId = service.getClientId();
            Player player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
            AuthenticationManager.getInstance().invalidateToken(player, clientId);
         }

         return 1;
      });
   }

   private static LiteralArgumentBuilder<CommandSourceStack> registerSummon() {
      return (LiteralArgumentBuilder<CommandSourceStack>)Commands.literal("tpto")
         .then(Commands.argument("username", StringArgumentType.string()).suggests(NPC_SUGGEST).executes(context -> {
            String username = StringArgumentType.getString(context, "username");
            Player owner = ((CommandSourceStack)context.getSource()).getPlayerOrException();
            UUID ownerUUID = owner.getUUID();
            LOGGER.info("tpto command: {} {}", username, ownerUUID);

            for (AgentConversationData data : ConversationManager.getDataByOwner(ownerUUID)) {
               LOGGER.info("looking for name {}", data.getName());
               if (data.getName().equals(username)) {
                  LOGGER.info("Match on username: {}", username);
                  AgentSideEffects.teleportOwnerTo(data);
               }
            }

            return 1;
         }));
   }

   private static LiteralArgumentBuilder<CommandSourceStack> registerCapability() {
      return (LiteralArgumentBuilder<CommandSourceStack>)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                           "capability"
                        )
                        .requires(src -> src.hasPermission(2)))
                     .then(Commands.literal("status").executes(ctx -> capabilityStatus((CommandSourceStack)ctx.getSource()))))
                  .then(
                     Commands.literal("query")
                        .then(
                           Commands.argument("text", StringArgumentType.greedyString())
                              .executes(ctx -> capabilityQuery((CommandSourceStack)ctx.getSource(), StringArgumentType.getString(ctx, "text"), null, null, null))
                        )
                  ))
               .then(
                  Commands.literal("inspect")
                     .then(
                        Commands.argument("kind", StringArgumentType.word())
                           .then(
                              Commands.argument("id", StringArgumentType.greedyString())
                                 .executes(
                                    ctx -> capabilityInspect(
                                       (CommandSourceStack)ctx.getSource(), StringArgumentType.getString(ctx, "kind"), StringArgumentType.getString(ctx, "id")
                                    )
                                 )
                           )
                     )
               ))
            .then(
               ((LiteralArgumentBuilder)Commands.literal("rebuild").executes(ctx -> capabilityRebuild((CommandSourceStack)ctx.getSource(), false)))
                  .then(Commands.literal("force").executes(ctx -> capabilityRebuild((CommandSourceStack)ctx.getSource(), true)))
            ))
         .then(
            ((LiteralArgumentBuilder)Commands.literal("enrich").executes(ctx -> capabilityEnrich((CommandSourceStack)ctx.getSource(), null)))
               .then(
                  Commands.argument("limit", IntegerArgumentType.integer(0))
                     .executes(ctx -> capabilityEnrich((CommandSourceStack)ctx.getSource(), IntegerArgumentType.getInteger(ctx, "limit")))
               )
         );
   }

   private static int capabilityStatus(CommandSourceStack src) {
      ModIntelligenceStatus st = ModIntelligenceService.status();
      String msg = String.format(
         "ModIntelligence enabled=%s inspecting=%s enriching=%s entries=%d ready=%d partial=%d unknown=%d inspectFailed=%d tombstoned=%d queued=%d enriched=%d enrichFailures=%d lastBatch=%d/%d/%d packFp=%s lastError=%s",
         st.isEnabled(),
         st.isInspecting(),
         st.isEnriching(),
         st.getTotalEntries(),
         st.getReadyEntries(),
         st.getPartialEntries(),
         st.getUnknownEntries(),
         st.getFailedEntries(),
         st.getTombstonedEntries(),
         st.getQueuedEnrichments(),
         st.getEnrichedEntries(),
         st.getEnrichmentFailures(),
         st.getLastBatchValidated(),
         st.getLastBatchFailures(),
         st.getLastBatchRemaining(),
         st.getActivePackFingerprint() != null ? st.getActivePackFingerprint().substring(0, Math.min(24, st.getActivePackFingerprint().length())) : "",
         st.getLastError() != null ? st.getLastError() : ""
      );
      src.sendSuccess(() -> Component.literal(msg), false);
      return 1;
   }

   private static int capabilityQuery(CommandSourceStack src, String text, String kindFilter, String capFilter, Double minConf) {
      Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
      CapabilityQuery query = CapabilityQuery.defaults().setText(text).setMinConfidence(minConf != null ? minConf : cfg.getModIntelligenceMinQueryConfidence());
      if (kindFilter != null) {
         CapabilitySubjectKind kind = CapabilitySubjectKind.valueOf(kindFilter.toUpperCase(Locale.ROOT));
         query.setSubjectKinds(EnumSet.of(kind));
      }

      if (capFilter != null) {
         query.setRequiredCapabilities(Set.of(capFilter));
      }

      List<CapabilityHit> hits = ModIntelligenceService.queryService().query(query, cfg.getModIntelligenceQueryTopKClamped());
      StringBuilder sb = new StringBuilder("Capability query (").append(hits.size()).append(" hits):\n");

      for (CapabilityHit hit : hits) {
         sb.append(
            String.format(
               "  %s %s score=%.3f conf=%.2f caps=%s status=%s%n",
               hit.getSubjectKind(),
               hit.getSubjectId(),
               hit.getScore(),
               hit.getBestCapabilityConfidence(),
               hit.getMatchedCapabilities(),
               hit.getStatus()
            )
         );
      }

      src.sendSuccess(() -> Component.literal(sb.toString()), false);
      return 1;
   }

   private static int capabilityInspect(CommandSourceStack src, String kindRaw, String id) {
      CapabilitySubjectKind kind = CapabilitySubjectKind.valueOf(kindRaw.toUpperCase(Locale.ROOT));
      Optional<CapabilityMap> map = ModIntelligenceService.queryService().get(kind, id);
      if (map.isEmpty()) {
         src.sendFailure(Component.translatable("message.playerengine.capability.inspect_not_found", new Object[]{kind, id}));
         return 0;
      }

      CapabilityMap m = map.get();
      StringBuilder sb = new StringBuilder();
      sb.append("inspect ").append(kind).append(' ').append(id).append('\n');
      sb.append("status=").append(m.getStatus()).append(" fingerprint=").append(m.getEntryFingerprint()).append('\n');
      sb.append("capabilities=").append(m.getCapabilities().size()).append(" warnings=").append(m.getWarnings()).append('\n');
      if (m.getEnrichment() != null) {
         sb.append("enrichment=").append(m.getEnrichment().getShortDescription()).append('\n');
      }

      src.sendSuccess(() -> Component.literal(sb.toString()), false);
      return 1;
   }

   private static int capabilityRebuild(CommandSourceStack src, boolean force) {
      MinecraftServer server = src.getServer();

      return switch (ModIntelligenceService.requestIngestion(server, force)) {
         case STARTED -> {
            src.sendSuccess(() -> Component.translatable("message.playerengine.capability.rebuild_started"), false);
            yield 1;
         }
         case ALREADY_RUNNING -> {
            src.sendFailure(Component.translatable("message.playerengine.capability.rebuild_already_running"));
            yield 0;
         }
         case DISABLED -> {
            src.sendFailure(Component.translatable("message.playerengine.capability.rebuild_disabled"));
            yield 0;
         }
         case EXECUTOR_UNAVAILABLE -> {
            src.sendFailure(Component.translatable("message.playerengine.capability.rebuild_executor_unavailable"));
            yield 0;
         }
      };
   }

   private static int capabilityEnrich(CommandSourceStack src, Integer limit) {
      if (!Player2ServerConfigHolder.get().isModIntelligenceEnrichmentEnabled()) {
         src.sendFailure(Component.translatable("message.playerengine.capability.enrich_disabled"));
         return 0;
      }

      MinecraftServer server = src.getServer();
      int queued = ModIntelligenceService.status().getQueuedEnrichments();
      if (queued == 0) {
         src.sendFailure(Component.translatable("message.playerengine.capability.enrich_queue_empty"));
         return 0;
      }

      if (!ModIntelligenceEnrichmentClient.isBillingAvailable(server)) {
         src.sendFailure(Component.translatable("message.playerengine.capability.enrich_billing_unavailable"));
         return 0;
      }

      ModelBlacklist.ModelBlacklistSnapshot blacklist = ModelBlacklist.load();
      Optional<ModIntelligenceSpendSafety.DeferReason> defer = ModIntelligenceSpendSafety.preflight(server, queued, blacklist, limit != null);
      if (defer.isPresent()) {
         Component msg = ModIntelligenceSpendSafety.messageFor(defer.get(), queued, blacklist);
         src.sendFailure(msg);
         ModIntelligenceSpendSafety.notifyPlayer(server, msg);
         return 0;
      }

      int configMax = Player2ServerConfigHolder.get().getModIntelligenceMaxEnrichmentCallsPerLaunch();
      String configMaxText = configMax <= 0 ? "unlimited" : String.valueOf(configMax);
      String limitText;
      if (limit == null) {
         limitText = configMaxText + " (config)";
      } else {
         limitText = (limit <= 0 ? "unlimited" : String.valueOf(limit)) + " — overrides config " + configMaxText;
      }

      ModIntelligenceService.ScheduleResult result = ModIntelligenceService.scheduleEnrichmentBatch(server, limit);

      return switch (result) {
         case STARTED -> {
            src.sendSuccess(() -> Component.translatable("message.playerengine.capability.enrich_batch_started", new Object[]{queued, limitText}), false);
            yield 1;
         }
         case ALREADY_RUNNING -> {
            String pendingText = limit == null ? "config-limit" : (limit <= 0 ? "unlimited" : limit + "-limit");
            src.sendSuccess(() -> Component.translatable("message.playerengine.capability.enrich_already_running", new Object[]{pendingText, queued}), false);
            yield 1;
         }
         case NOTHING_QUEUED -> {
            src.sendFailure(Component.translatable("message.playerengine.capability.enrich_nothing_queued"));
            yield 0;
         }
         case DISABLED -> {
            src.sendFailure(Component.translatable("message.playerengine.capability.enrich_disabled_config"));
            yield 0;
         }
         case BILLING_UNAVAILABLE -> {
            src.sendFailure(Component.translatable("message.playerengine.capability.enrich_billing_not_yet"));
            yield 0;
         }
         case EXECUTOR_UNAVAILABLE -> {
            src.sendFailure(Component.translatable("message.playerengine.capability.enrich_executor_unavailable"));
            yield 0;
         }
      };
   }

   private static LiteralArgumentBuilder<CommandSourceStack> registerResolve() {
      return (LiteralArgumentBuilder<CommandSourceStack>)((LiteralArgumentBuilder)Commands.literal("resolve").requires(src -> src.hasPermission(2)))
         .then(
            ((RequiredArgumentBuilder)Commands.argument("item", StringArgumentType.word()).executes(ctx -> executeResolve(ctx, 1)))
               .then(
                  Commands.argument("count", IntegerArgumentType.integer(1))
                     .executes(ctx -> executeResolve(ctx, IntegerArgumentType.getInteger(ctx, "count")))
               )
         );
   }

   private static int executeResolve(CommandContext<CommandSourceStack> ctx, int count) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      String itemName = StringArgumentType.getString(ctx, "item");
      String qualified = itemName.contains(":") ? itemName : "minecraft:" + itemName;
      ResourceLocation id = ResourceLocation.tryParse(qualified);
      Optional<Item> targetOpt = id != null ? BuiltInRegistries.ITEM.getOptional(id) : Optional.empty();
      if (targetOpt.isEmpty()) {
         src.sendFailure(Component.translatable("message.playerengine.resolve.unknown_item", new Object[]{itemName, qualified}));
         return 0;
      }

      Item target = targetOpt.get();
      if (PlayerEngineController.staticAPIServices.isEmpty()) {
         src.sendFailure(Component.translatable("message.playerengine.commands.no_active_bots"));
         return 0;
      }

      Player2APIService apiService = PlayerEngineController.staticAPIServices.values().iterator().next();
      PlayerEngineController controller = apiService.getController();
      MaterialResolver resolver = new MaterialResolver(new IngredientInspectorImpl(), new RecipeAccessImpl(), new CookingRecipeAccessImpl());
      ResolverResult result = resolver.resolve(controller, target, count);
      StringBuilder sb = new StringBuilder();
      sb.append("Resolve dry-run — ").append(qualified).append(" x").append(count).append('\n');
      sb.append("  status: ").append(result.status()).append('\n');
      sb.append("  remainingDeficit (tiered): ").append(result.remainingDeficit()).append('\n');
      sb.append("  steps (").append(result.remainingSteps().size()).append("):\n");

      for (CraftMacroStep step : result.remainingSteps()) {
         String pooled = step.outputMatches() != null ? " pooled=" + step.outputMatches().length : " pooled=none";
         sb.append("    - ")
            .append(step.kind())
            .append(" x")
            .append(step.craftsNeeded())
            .append(" [")
            .append(step.debugLabel())
            .append("]")
            .append(pooled)
            .append('\n');
      }

      sb.append("  external (").append(result.externalNeeded().size()).append("):\n");

      for (ItemTarget t : result.externalNeeded()) {
         sb.append("    - ").append(t.getCatalogueName()).append(" x").append(t.getTargetCount()).append('\n');
      }

      if (result.failureReason() != null && !result.failureReason().isEmpty()) {
         sb.append("  failureReason: ").append(result.failureReason()).append('\n');
      }

      LOGGER.info(sb.toString());
      src.sendSuccess(() -> Component.literal(sb.toString()), false);
      return result.status() == ResolverResult.Status.UNOBTAINABLE ? 0 : 1;
   }
}
