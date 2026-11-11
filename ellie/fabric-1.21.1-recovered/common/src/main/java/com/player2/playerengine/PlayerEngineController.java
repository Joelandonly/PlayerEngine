package com.player2.playerengine;

import com.player2.playerengine.agentic.AgenticRunRegistry;
import com.player2.playerengine.agentic.StorageIntelligence;
import com.player2.playerengine.automaton.AdditionalBaritoneSettings;
import com.player2.playerengine.automaton.Baritone;
import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.Settings;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.automaton.api.utils.IEntityContext;
import com.player2.playerengine.automaton.api.utils.IInteractionController;
import com.player2.playerengine.chains.FoodChain;
import com.player2.playerengine.chains.ForceEquipGearChain;
import com.player2.playerengine.chains.MLGBucketFallChain;
import com.player2.playerengine.chains.MobDefenseChain;
import com.player2.playerengine.chains.PlayerDefenseChain;
import com.player2.playerengine.chains.PlayerInteractionFixChain;
import com.player2.playerengine.chains.PreEquipItemChain;
import com.player2.playerengine.chains.UnstuckChain;
import com.player2.playerengine.chains.UserTaskChain;
import com.player2.playerengine.chains.WorldSurvivalChain;
import com.player2.playerengine.commands.BlockScanner;
import com.player2.playerengine.commands.base.CommandExecutor;
import com.player2.playerengine.control.InputControls;
import com.player2.playerengine.control.PlayerExtraController;
import com.player2.playerengine.control.SlotHandler;
import com.player2.playerengine.equip.ExplicitEquipPolicy;
import com.player2.playerengine.equip.PickupArmorEvalQueue;
import com.player2.playerengine.equip.PickupWeaponEvalQueue;
import com.player2.playerengine.executor.IStepExecutorAdapter;
import com.player2.playerengine.executor.RollbackPolicy;
import com.player2.playerengine.executor.StepExecution;
import com.player2.playerengine.executor.StopReason;
import com.player2.playerengine.executor.TaskStepExecutorAdapter;
import com.player2.playerengine.navigation.NavStallRecovery;
import com.player2.playerengine.observatory.ChunkTicketWatchdog;
import com.player2.playerengine.observatory.DistanceDiagnostics;
import com.player2.playerengine.player2api.AIPersistantData;
import com.player2.playerengine.player2api.AgentSideEffects;
import com.player2.playerengine.player2api.AiConversationFeedback;
import com.player2.playerengine.player2api.Character;
import com.player2.playerengine.player2api.Player2APIService;
import com.player2.playerengine.player2api.manager.ConversationManager;
import com.player2.playerengine.supervisor.CoreGoalSupervisor;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.base.TaskRunner;
import com.player2.playerengine.tasks.movement.BodyLanguageTask;
import com.player2.playerengine.trackers.CacheTracker;
import com.player2.playerengine.trackers.ChunkLoadingTracker;
import com.player2.playerengine.trackers.CraftingRecipeTracker;
import com.player2.playerengine.trackers.EntityStuckTracker;
import com.player2.playerengine.trackers.EntityTracker;
import com.player2.playerengine.trackers.MiscBlockTracker;
import com.player2.playerengine.trackers.SimpleChunkTracker;
import com.player2.playerengine.trackers.TrackerManager;
import com.player2.playerengine.trackers.UserBlockRangeTracker;
import com.player2.playerengine.trackers.storage.ContainerSubTracker;
import com.player2.playerengine.trackers.storage.ItemStorageTracker;
import com.player2.playerengine.trackers.storage.SurvivalConsumptionLedger;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.util.Playground;
import com.player2.playerengine.util.time.TimerGame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.TickTask;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PlayerEngineController {
   private final IBaritone baritone;
   private AIPersistantData aiPersistantData;
   private Player2APIService player2apiService;
   private final IEntityContext ctx;
   private CommandExecutor commandExecutor;
   private TaskRunner taskRunner;
   private TrackerManager trackerManager;
   private BotBehaviour botBehaviour;
   private UserTaskChain userTaskChain;
   private TaskStepExecutorAdapter stepExecutorAdapter;
   private FoodChain foodChain;
   private MobDefenseChain mobDefenseChain;
   private ForceEquipGearChain forceEquipGearChain;
   private MLGBucketFallChain mlgBucketChain;
   private ItemStorageTracker storageTracker;
   private ContainerSubTracker containerSubTracker;
   private EntityTracker entityTracker;
   private BlockScanner blockScanner;
   private SimpleChunkTracker chunkTracker;
   private CacheTracker cacheTracker;
   private MiscBlockTracker miscBlockTracker;
   private CraftingRecipeTracker craftingRecipeTracker;
   private EntityStuckTracker entityStuckTracker;
   private UserBlockRangeTracker userBlockRangeTracker;
   private InputControls inputControls;
   private SlotHandler slotHandler;
   private final ExplicitEquipPolicy explicitEquipPolicy = new ExplicitEquipPolicy();
   private final SurvivalConsumptionLedger survivalConsumptionLedger = new SurvivalConsumptionLedger();
   private final PickupArmorEvalQueue pickupArmorEvalQueue;
   private final PickupWeaponEvalQueue pickupWeaponEvalQueue;
   private PlayerExtraController extraController;
   private PlayerEngineSettings settings;
   private PlayerEngineSettings stagedSettings;
   private Task observedUserTask;
   private int stagedAdoptionFailures;
   private volatile long settingsPublicationGeneration;
   private volatile PlayerEngineController.SettingsLiveState settingsLiveState = PlayerEngineController.SettingsLiveState.APPLIED;
   private ChunkLoadingTracker chunkLoader;
   private boolean paused = false;
   private Task storedTask;
   public boolean isStopping = false;
   private Player owner;
   public static HashMap<UUID, Player2APIService> staticAPIServices = new HashMap<>();
   public static final ConcurrentHashMap<UUID, PlayerEngineController> staticControllers = new ConcurrentHashMap<>();
   private static final IdentityHashMap<List<Item>, LinkedHashSet<Item>> THROWAWAY_INJECTIONS = new IdentityHashMap<>();
   private static final AtomicLong SETTINGS_PUBLICATION_GENERATION = new AtomicLong();
   private boolean shouldDefendFromHostiles = false;
   private static final Logger LOGGER = LogManager.getLogger();
   private FollowMode followMode = FollowMode.NORMAL;
   private static final long MIN_REPORT_INTERVAL_MS = 1500L;
   private long lastAgenticReportMs = 0L;
   private String lastAgenticReportMessage = "";

   public PlayerEngineController(IBaritone baritone, Character character, String player2GameId) {
      this(baritone, character, player2GameId, null);
   }

   public PlayerEngineController(IBaritone baritone, Character character, String player2GameId, Player owner) {
      this.owner = owner;
      this.baritone = baritone;
      this.ctx = baritone.getEntityContext();
      this.commandExecutor = new CommandExecutor(this);
      this.taskRunner = new TaskRunner(this);
      this.trackerManager = new TrackerManager(this);
      this.userTaskChain = new UserTaskChain(this.taskRunner);
      this.stepExecutorAdapter = new TaskStepExecutorAdapter(this);
      this.mobDefenseChain = new MobDefenseChain(this.taskRunner);
      new PlayerInteractionFixChain(this.taskRunner);
      this.mlgBucketChain = new MLGBucketFallChain(this.taskRunner);
      new UnstuckChain(this.taskRunner);
      new PreEquipItemChain(this.taskRunner);
      new WorldSurvivalChain(this.taskRunner);
      this.foodChain = new FoodChain(this.taskRunner);
      this.forceEquipGearChain = new ForceEquipGearChain(this.taskRunner);
      new PlayerDefenseChain(this.taskRunner);
      this.storageTracker = new ItemStorageTracker(this, this.trackerManager, container -> this.containerSubTracker = container);
      this.entityTracker = new EntityTracker(this.trackerManager);
      this.blockScanner = new BlockScanner(this);
      this.chunkTracker = new SimpleChunkTracker(this);
      this.miscBlockTracker = new MiscBlockTracker(this);
      this.craftingRecipeTracker = new CraftingRecipeTracker(this.trackerManager);
      this.entityStuckTracker = new EntityStuckTracker(this.trackerManager);
      this.userBlockRangeTracker = new UserBlockRangeTracker(this.trackerManager);
      this.inputControls = new InputControls(this);
      this.slotHandler = new SlotHandler(this);
      this.pickupArmorEvalQueue = new PickupArmorEvalQueue(this);
      this.pickupWeaponEvalQueue = new PickupWeaponEvalQueue(this);
      this.extraController = new PlayerExtraController(this);
      this.initializeBaritoneSettings();
      this.botBehaviour = new BotBehaviour(this);
      this.cacheTracker = new CacheTracker(this);
      this.initializeCommands();
      PlayerEngineSettings.load(
         newSettings -> {
            this.applyInitiallyLoadedSettings(newSettings);
            if ((!this.getUserTaskChain().isActive() || this.getUserTaskChain().isRunningIdleTask())
               && this.getModSettings().shouldRunIdleCommandWhenNotActive()) {
               this.getUserTaskChain().runIdleCommand(() -> this.getCommandExecutor().executeWithPrefix(this.getModSettings().getIdleCommand()));
            }

            this.getExtraBaritoneSettings().avoidBlockBreak(this.userBlockRangeTracker::isNearUserTrackedBlock);
            this.getExtraBaritoneSettings().avoidBlockPlace(this.entityStuckTracker::isBlockedByEntity);
         }
      );
      Playground.IDLE_TEST_INIT_FUNCTION(this);
      ConversationManager.getOrCreateEventQueueData(this);
      this.aiPersistantData = new AIPersistantData(this, character);
      this.player2apiService = new Player2APIService(this, player2GameId);
      staticAPIServices.put(this.getEntity().getUUID(), this.player2apiService);
      staticControllers.put(this.getEntity().getUUID(), this);
      this.chunkLoader = new ChunkLoadingTracker(this);
   }

   public void serverTick() {
      DistanceDiagnostics.onControllerTick(this);
      StorageIntelligence.heartbeat(this);
      this.observeSettingsTaskBoundary();
      this.inputControls.onTickPre();
      this.storageTracker.setDirty();
      this.miscBlockTracker.tick();
      this.trackerManager.tick();
      this.blockScanner.tick();
      this.taskRunner.tick();
      this.cacheTracker.tick();
      this.inputControls.onTickPost();
      this.baritone.serverTick();
      this.player2apiService.trySendHeartbeat();
      this.chunkLoader.tick();
      NavStallRecovery.onTick(this);
   }

   public static void staticServerTick(MinecraftServer server) {
      DistanceDiagnostics.onServerTick(server);
      TimerGame.incrementServerTick();
      ConversationManager.injectOnTick(server);
      if (server != null && server.getTickCount() % 200 == 0) {
         pruneStaleControllers(server);
      }
   }

   public void unregisterFromGlobalRegistry() {
      unregisterFromGlobalRegistry(this.getEntity().getUUID());
   }

   public static void unregisterFromGlobalRegistry(UUID entityUuid) {
      ChunkTicketWatchdog.release(entityUuid);
      if (entityUuid != null) {
         staticControllers.remove(entityUuid);
         staticAPIServices.remove(entityUuid);
         AgenticRunRegistry.clear(entityUuid);
      }
   }

   public static int pruneStaleControllers(MinecraftServer server) {
      if (server != null && !staticControllers.isEmpty()) {
         int removed = 0;
         Iterator<Entry<UUID, PlayerEngineController>> it = staticControllers.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, PlayerEngineController> entry = it.next();
            UUID uuid = entry.getKey();
            PlayerEngineController controller = entry.getValue();
            if (!isControllerEntityActive(server, controller, uuid)) {
               it.remove();
               staticAPIServices.remove(uuid);
               AgenticRunRegistry.clear(uuid);
               removed++;
            }
         }

         return removed;
      } else {
         return 0;
      }
   }

   private static boolean isControllerEntityActive(MinecraftServer server, PlayerEngineController controller, UUID entityUuid) {
      if (controller == null) {
         return false;
      }

      try {
         LivingEntity entity = controller.getEntity();
         if (entity != null && !entity.isRemoved()) {
            if (!entity.isAlive()) {
               return false;
            } else if (entity.level() instanceof ServerLevel serverLevel && serverLevel.getEntity(entityUuid) == entity) {
               return true;
            } else {
               for (ServerLevel level : server.getAllLevels()) {
                  if (level.getEntity(entityUuid) == entity) {
                     return true;
                  }
               }

               return true;
            }
         } else {
            return false;
         }
      } catch (RuntimeException ignored) {
         return false;
      }
   }

   public String getChainStatusDisplayName() {
      Character character = this.getAIPersistantData() != null ? this.getAIPersistantData().getCharacter() : null;
      String name = character != null ? character.shortName() : this.getEntity().getName().getString();
      String shortId = this.getEntity().getUUID().toString().substring(0, 8);
      return name + " (" + shortId + ")";
   }

   public void stop() {
      this.stop(StopReason.CANCELLED_OPERATOR);
   }

   public void stop(StopReason trackedStepCancelReason) {
      ChunkTicketWatchdog.stopped(this);
      this.stepExecutorAdapter.armPendingChainCancel(trackedStepCancelReason);
      this.getUserTaskChain().cancel(this);
      if (this.taskRunner.getCurrentTaskChain() != null) {
         this.taskRunner.getCurrentTaskChain().stop();
      }

      this.getTaskRunner().disable();
      this.getBaritone().getPathingBehavior().forceCancel();
      this.getBaritone().getInputOverrideHandler().clearAllKeys();
   }

   public void stopWithRespawnNotification(ServerPlayer ownerPlayer) {
      boolean active = this.hasActiveNonIdleUserTask() || AgenticRunRegistry.get(this.getEntity().getUUID()).isPresent();
      if (active) {
         AiConversationFeedback.enqueueInfo(
            this, "You were despawned while a task was running. The task has been cancelled and will not resume. Do not claim the task completed or succeeded."
         );
         if (ownerPlayer != null && ownerPlayer.getServer() != null) {
            Character character = this.getAIPersistantData() != null ? this.getAIPersistantData().getCharacter() : null;
            Component nameComponent = character != null && character.shortName() != null
               ? Component.literal(character.shortName())
               : Component.translatable("message.playerengine.agent.companion_default_name");
            AgentSideEffects.broadcastChatToPlayer(
               ownerPlayer.getServer(), Component.translatable("message.playerengine.agent.despawned_with_task", new Object[]{nameComponent}), ownerPlayer
            );
         }
      }

      this.stop(StopReason.CANCELLED_RESPAWN);
   }

   private void initializeBaritoneSettings() {
      this.getExtraBaritoneSettings().canWalkOnEndPortal(false);
      this.getExtraBaritoneSettings().avoidBlockPlace(this.entityStuckTracker::isBlockedByEntity);
      this.getExtraBaritoneSettings().avoidBlockBreak(this.userBlockRangeTracker::isNearUserTrackedBlock);
      // OWNER_SAFEZONE_PATH_POLICY: affects placement/breaking cost, never walking.
      this.getExtraBaritoneSettings().avoidBlockBreak(pos ->
          com.player2.playerengine.safezone.SafeZoneService.protectedAt(this.getWorld(),pos));
      this.getExtraBaritoneSettings().avoidBlockPlace(pos ->
          com.player2.playerengine.safezone.SafeZoneService.protectedAt(this.getWorld(),pos));
      this.getBaritoneSettings().freeLook.set(false);
      this.getBaritoneSettings().overshootTraverse.set(true);
      this.getBaritoneSettings().allowOvershootDiagonalDescend.set(true);
      this.getBaritoneSettings().allowInventory.set(true);
      this.getBaritoneSettings().allowParkour.set(false);
      this.getBaritoneSettings().allowParkourAscend.set(false);
      this.getBaritoneSettings().allowParkourPlace.set(false);
      this.getBaritoneSettings().allowDiagonalDescend.set(false);
      this.getBaritoneSettings().allowDiagonalAscend.set(false);
      this.getBaritoneSettings().fadePath.set(true);
      this.getBaritoneSettings().mineScanDroppedItems.set(false);
      this.getBaritoneSettings().mineDropLoiterDurationMSThanksLouca.set(0L);
      this.getExtraBaritoneSettings().configurePlaceBucketButDontFall(true);
      this.getBaritoneSettings().randomLooking.set(0.0);
      this.getBaritoneSettings().randomLooking113.set(0.0);
      this.getBaritoneSettings().failureTimeoutMS.reset();
      this.getBaritoneSettings().planAheadFailureTimeoutMS.reset();
      this.getBaritoneSettings().movementTimeoutTicks.reset();
   }

   private void initializeCommands() {
      try {
         PlayerEngineCommands.init(this);
      } catch (Exception var2) {
         var2.printStackTrace();
      }
   }

   public void runUserTask(Task task, Runnable onFinish) {
      this.userTaskChain.runTask(this, task, onFinish);
   }

   public void runUserTask(Task task) {
      this.runUserTask(task, () -> {});
   }

   public void runIdleUserTask(Task task, Runnable onFinish) {
      this.userTaskChain.runIdleTask(this, task, onFinish);
   }

   public void runIdleUserTask(Task task) {
      this.runIdleUserTask(task, () -> {});
   }

   public boolean clearPolicyIdleUserTask() {
      return this.userTaskChain.clearPolicyIdleTask();
   }

   public boolean rejectsConfiguredIdleTrackedSubmission() {
      return this.userTaskChain.rejectsConfiguredIdleTrackedSubmission();
   }

   public StepExecution runUserTaskTracked(String stepId, String stepKind, Task task, RollbackPolicy rollbackPolicy, Runnable onComplete) {
      return CoreGoalSupervisor.submit(this, stepId, stepKind, task, rollbackPolicy, onComplete);
   }

   public IStepExecutorAdapter getStepExecutorAdapter() {
      return this.stepExecutorAdapter;
   }

   public void cancelUserTask() {
      this.userTaskChain.cancel(this);
   }

   public CommandExecutor getCommandExecutor() {
      return this.commandExecutor;
   }

   public LivingEntity getEntity() {
      return this.ctx.entity();
   }

   public ServerLevel getWorld() {
      return this.ctx.world();
   }

   public IInteractionController getInteractionManager() {
      return this.ctx.playerController();
   }

   public IBaritone getBaritone() {
      return this.baritone;
   }

   public Settings getBaritoneSettings() {
      return this.baritone.settings();
   }

   public AdditionalBaritoneSettings getExtraBaritoneSettings() {
      return ((Baritone)this.baritone).getExtraBaritoneSettings();
   }

   public TaskRunner getTaskRunner() {
      return this.taskRunner;
   }

   public UserTaskChain getUserTaskChain() {
      return this.userTaskChain;
   }

   public boolean hasActiveNonIdleUserTask() {
      return this.userTaskChain.hasActiveNonIdleUserTask();
   }

   public Optional<StepExecution> getActiveTrackedStep() {
      return this.stepExecutorAdapter.getRunningExecution();
   }

   public BotBehaviour getBehaviour() {
      return this.botBehaviour;
   }

   public boolean isPaused() {
      return this.paused;
   }

   public void setPaused(boolean pausing) {
      this.paused = pausing;
   }

   public Task getStoredTask() {
      return this.storedTask;
   }

   public void setStoredTask(Task currentTask) {
      this.storedTask = currentTask;
   }

   public ItemStorageTracker getItemStorage() {
      return this.storageTracker;
   }

   public SurvivalConsumptionLedger getSurvivalConsumptionLedger() {
      return this.survivalConsumptionLedger;
   }

   public EntityTracker getEntityTracker() {
      return this.entityTracker;
   }

   public CraftingRecipeTracker getCraftingRecipeTracker() {
      return this.craftingRecipeTracker;
   }

   public BlockScanner getBlockScanner() {
      return this.blockScanner;
   }

   public SimpleChunkTracker getChunkTracker() {
      return this.chunkTracker;
   }

   public MiscBlockTracker getMiscBlockTracker() {
      return this.miscBlockTracker;
   }

   public PlayerEngineSettings getModSettings() {
      if (this.settings == null) {
         this.settings = new PlayerEngineSettings();
      }

      return this.settings;
   }

   public static PlayerEngineController.SettingsPublicationOutcome publishSettingsFromAdmin(PlayerEngineSettings settings) {
      if (settings == null) {
         return new PlayerEngineController.SettingsPublicationOutcome(1, 0, 0, 1);
      }

      List<PlayerEngineController> controllers;
      try {
         controllers = List.copyOf(staticControllers.values());
      } catch (RuntimeException e) {
         LOGGER.warn("Unable to snapshot PlayerEngine controllers for an admin settings publication.", e);
         return new PlayerEngineController.SettingsPublicationOutcome(1, 0, 0, 1);
      }

      long generation = SETTINGS_PUBLICATION_GENERATION.incrementAndGet();
      int applied = 0;
      int pending = 0;
      int failed = 0;

      for (PlayerEngineController controller : controllers) {
         PlayerEngineController.ControllerPublicationState state = PlayerEngineController.ControllerPublicationState.FAILED;

         try {
            LivingEntity player = controller.getPlayer();
            MinecraftServer server = player == null ? null : player.getServer();
            if (server == null) {
               controller.markSettingsPublicationState(generation, PlayerEngineController.SettingsLiveState.FAILED);
               state = PlayerEngineController.ControllerPublicationState.FAILED;
            } else {
               PlayerEngineSettings controllerCopy = settings.defensiveCopy();
               if (server.isSameThread()) {
                  state = applyAdminSettingsSafely(controller, controllerCopy, server, generation, true);
               } else {
                  controller.markSettingsPublicationState(generation, PlayerEngineController.SettingsLiveState.PENDING);
                  server.execute(() -> applyAdminSettingsSafely(controller, controllerCopy, server, generation, true));
                  state = PlayerEngineController.ControllerPublicationState.PENDING;
               }
            }
         } catch (RuntimeException e) {
            controller.markSettingsPublicationState(generation, PlayerEngineController.SettingsLiveState.FAILED);
            LOGGER.warn("Unable to publish admin settings to one PlayerEngine controller; continuing.", e);
         }

         switch (state) {
            case APPLIED:
               applied++;
               break;
            case PENDING:
               pending++;
               break;
            case FAILED:
               failed++;
         }
      }

      return new PlayerEngineController.SettingsPublicationOutcome(controllers.size(), applied, pending, failed);
   }

   public static PlayerEngineController.SettingsLiveState currentSettingsLiveState() {
      PlayerEngineController.SettingsLiveState aggregate = PlayerEngineController.SettingsLiveState.APPLIED;

      try {
         for (PlayerEngineController controller : staticControllers.values()) {
            if (controller != null) {
               PlayerEngineController.SettingsLiveState state = controller.settingsLiveState;
               if (state == PlayerEngineController.SettingsLiveState.FAILED) {
                  return PlayerEngineController.SettingsLiveState.FAILED;
               }

               if (state == PlayerEngineController.SettingsLiveState.PENDING) {
                  aggregate = PlayerEngineController.SettingsLiveState.PENDING;
               }
            }
         }

         return aggregate;
      } catch (RuntimeException e) {
         LOGGER.warn("Unable to inspect PlayerEngine live settings state.", e);
         return PlayerEngineController.SettingsLiveState.FAILED;
      }
   }

   private static PlayerEngineController.ControllerPublicationState applyAdminSettingsSafely(
      PlayerEngineController controller, PlayerEngineSettings settings, MinecraftServer server, long generation, boolean retryAllowed
   ) {
      try {
         return controller.applyAdminSettings(settings, generation);
      } catch (RuntimeException e) {
         LOGGER.warn("Unable to apply admin settings to one PlayerEngine controller.", e);
         if (retryAllowed && server != null) {
            try {
               server.tell(new TickTask(server.getTickCount() + 1, () -> applyAdminSettingsSafely(controller, settings, server, generation, false)));
               controller.markSettingsPublicationState(generation, PlayerEngineController.SettingsLiveState.PENDING);
               return PlayerEngineController.ControllerPublicationState.PENDING;
            } catch (RuntimeException retryScheduleFailure) {
               LOGGER.warn("Unable to schedule one PlayerEngine admin settings retry.", retryScheduleFailure);
            }
         }

         controller.markSettingsPublicationState(generation, PlayerEngineController.SettingsLiveState.FAILED);
         return PlayerEngineController.ControllerPublicationState.FAILED;
      }
   }

   private void applyInitiallyLoadedSettings(PlayerEngineSettings newSettings) {
      PlayerEngineSettings initial = newSettings != null ? newSettings.defensiveCopy() : new PlayerEngineSettings();
      this.reconcileAcceptableThrowawayItems(initial);
      this.settings = initial;
      this.stagedSettings = null;
      this.stagedAdoptionFailures = 0;
      this.settingsLiveState = PlayerEngineController.SettingsLiveState.APPLIED;
   }

   private synchronized PlayerEngineController.ControllerPublicationState applyAdminSettings(PlayerEngineSettings newSettings, long generation) {
      if (newSettings == null) {
         throw new IllegalArgumentException("Admin settings publication cannot be null");
      }

      if (generation < this.settingsPublicationGeneration) {
         return switch (this.settingsLiveState) {
            case APPLIED -> PlayerEngineController.ControllerPublicationState.APPLIED;
            case PENDING -> PlayerEngineController.ControllerPublicationState.PENDING;
            case FAILED -> PlayerEngineController.ControllerPublicationState.FAILED;
         };
      } else {
         PlayerEngineSettings desired = newSettings.defensiveCopy();
         Task currentTask = this.userTaskChain == null ? null : this.userTaskChain.getCurrentTask();
         boolean activeRealTask = this.userTaskChain != null && this.userTaskChain.hasActiveNonIdleUserTask();
         if (activeRealTask) {
            PlayerEngineSettings live = desired.defensiveCopy();
            live.copyNextTaskValuesFrom(this.getModSettings());
            this.settings = live;
            this.stagedSettings = desired;
            this.stagedAdoptionFailures = 0;
            if (!(currentTask instanceof BodyLanguageTask)) {
               this.observedUserTask = currentTask;
            }

            this.settingsPublicationGeneration = generation;
            this.settingsLiveState = PlayerEngineController.SettingsLiveState.PENDING;
            return PlayerEngineController.ControllerPublicationState.PENDING;
         } else {
            this.reconcileAcceptableThrowawayItems(desired);
            this.settings = desired;
            this.stagedSettings = null;
            this.observedUserTask = null;
            this.stagedAdoptionFailures = 0;
            this.settingsPublicationGeneration = generation;
            this.settingsLiveState = PlayerEngineController.SettingsLiveState.APPLIED;
            return PlayerEngineController.ControllerPublicationState.APPLIED;
         }
      }
   }

   private synchronized void markSettingsPublicationState(long generation, PlayerEngineController.SettingsLiveState state) {
      if (state != null && generation >= this.settingsPublicationGeneration) {
         this.settingsPublicationGeneration = generation;
         this.settingsLiveState = state;
      }
   }

   private void observeSettingsTaskBoundary() {
      if (this.userTaskChain != null) {
         Task currentTask = this.userTaskChain.getCurrentTask();
         if (!(currentTask instanceof BodyLanguageTask)) {
            if (!this.userTaskChain.hasActiveNonIdleUserTask() || currentTask == null) {
               this.observedUserTask = null;
               this.adoptStagedSettings();
            } else if (this.observedUserTask == null) {
               this.observedUserTask = currentTask;
            } else if (this.observedUserTask != currentTask) {
               this.observedUserTask = currentTask;
               this.adoptStagedSettings();
            }
         }
      }
   }

   private void adoptStagedSettings() {
      if (this.stagedSettings != null && this.stagedAdoptionFailures < 2) {
         PlayerEngineSettings desired = this.stagedSettings;

         try {
            this.reconcileAcceptableThrowawayItems(desired);
            this.settings = desired;
            this.stagedSettings = null;
            this.stagedAdoptionFailures = 0;
            this.settingsLiveState = PlayerEngineController.SettingsLiveState.APPLIED;
         } catch (RuntimeException e) {
            this.stagedAdoptionFailures++;
            this.settingsLiveState = this.stagedAdoptionFailures >= 2
               ? PlayerEngineController.SettingsLiveState.FAILED
               : PlayerEngineController.SettingsLiveState.PENDING;
            LOGGER.warn("Unable to adopt staged PlayerEngine settings at a task boundary (attempt {}/2).", this.stagedAdoptionFailures, e);
         }
      }
   }

   private void reconcileAcceptableThrowawayItems(PlayerEngineSettings newSettings) {
      List<Item> acceptable = this.getBaritoneSettings().acceptableThrowawayItems.get();
      LinkedHashSet<Item> configured = new LinkedHashSet<>();

      for (Item item : newSettings.getThrowawayItems(this, true)) {
         if (item != null) {
            configured.add(item);
         }
      }

      synchronized (THROWAWAY_INJECTIONS) {
         List<Item> underlying = new ArrayList<>(acceptable);
         LinkedHashSet<Item> previouslyInjected = THROWAWAY_INJECTIONS.get(acceptable);
         if (previouslyInjected != null) {
            for (Item item : previouslyInjected) {
               underlying.remove(item);
            }
         }

         LinkedHashSet<Item> underlyingItems = new LinkedHashSet<>(underlying);
         LinkedHashSet<Item> newlyInjected = new LinkedHashSet<>();

         for (Item item : configured) {
            if (underlyingItems.add(item)) {
               underlying.add(item);
               newlyInjected.add(item);
            }
         }

         acceptable.clear();
         acceptable.addAll(underlying);
         if (newlyInjected.isEmpty()) {
            THROWAWAY_INJECTIONS.remove(acceptable);
         } else {
            THROWAWAY_INJECTIONS.put(acceptable, newlyInjected);
         }
      }
   }

   public FoodChain getFoodChain() {
      return this.foodChain;
   }

   public MobDefenseChain getMobDefenseChain() {
      return this.mobDefenseChain;
   }

   public MLGBucketFallChain getMLGBucketChain() {
      return this.mlgBucketChain;
   }

   public void log(String message) {
      Debug.logMessage(message);
   }

   public void logWarning(String message) {
      Debug.logWarning(message);
   }

   public static boolean inGame() {
      return true;
   }

   public LivingEntity getPlayer() {
      return this.ctx.entity();
   }

   public InputControls getInputControls() {
      return this.inputControls;
   }

   public SlotHandler getSlotHandler() {
      return this.slotHandler;
   }

   public PickupArmorEvalQueue getPickupArmorEvalQueue() {
      return this.pickupArmorEvalQueue;
   }

   public PickupWeaponEvalQueue getPickupWeaponEvalQueue() {
      return this.pickupWeaponEvalQueue;
   }

   public ExplicitEquipPolicy getExplicitEquipPolicy() {
      return this.explicitEquipPolicy;
   }

   public LivingEntityInventory getInventory() {
      return this.getBaritone().getEntityContext().inventory();
   }

   public PlayerExtraController getControllerExtras() {
      return this.extraController;
   }

   public void setChatClefEnabled(boolean enabled) {
      ConversationManager.getOrCreateEventQueueData(this).setEnabled(enabled);
      if (!enabled) {
         this.getUserTaskChain().cancel(this);
         this.getTaskRunner().disable();
      }
   }

   public void logCharacterMessage(String message, Character character, boolean isPublic) {
      int maxLength = 256;
      int start = 0;

      while (start < message.length()) {
         int end = Math.min(start + maxLength, message.length());
         String chunk = message.substring(start, end);
         if (chunk.length() > 0 && !chunk.isBlank()) {
            Debug.logCharacterMessage(chunk, character, isPublic);
         }

         start = end;
      }
   }

   public Player getOwner() {
      return this.owner;
   }

   public void setOwner(Player owner) {
      this.owner = owner;
      if (this.aiPersistantData != null) {
         this.aiPersistantData.rebindOwnerPersistenceIfChanged();
         this.aiPersistantData.updateSystemPrompt();
      }
   }

   public boolean isOwner(UUID playerToCheck) {
      return playerToCheck.equals(this.owner.getUUID());
   }

   public AIPersistantData getAIPersistantData() {
      return this.aiPersistantData;
   }

   public Player2APIService getPlayer2APIService() {
      return this.player2apiService;
   }

   public String getOwnerUsername() {
      return this.getOwner() == null ? "UNKNOWN OWNER" : this.getOwner().getName().getString();
   }

   public Optional<ServerPlayer> getClosestPlayer() {
      return this.getWorld().players().stream().sorted((a, b) -> {
         float adist = a.distanceTo(this.getEntity());
         float bdist = b.distanceTo(this.getEntity());
         return Float.compare(adist, bdist);
      }).findFirst();
   }

   public void reportAgenticProgress(String message) {
      this.reportAgenticProgress(message, false);
   }

   public void reportAgenticProgress(String message, boolean milestone) {
      if (message != null && !message.isBlank()) {
         MinecraftServer server = this.getPlayer() != null ? this.getPlayer().getServer() : null;
         if (server != null) {
            ServerPlayer target;
            if (this.getOwner() instanceof ServerPlayer sp) {
               target = sp;
            } else {
               target = this.getClosestPlayer().orElse(null);
            }

            if (target != null) {
               long now = System.currentTimeMillis();
               if (milestone || now - this.lastAgenticReportMs >= 1500L) {
                  if (!message.equals(this.lastAgenticReportMessage)) {
                     AgentSideEffects.broadcastChatToPlayer(
                        server,
                        Component.translatable("message.playerengine.agent.progress_prefix", new Object[]{this.agenticReportBotName(), message}),
                        target
                     );
                     this.lastAgenticReportMs = now;
                     this.lastAgenticReportMessage = message;
                  }
               }
            }
         }
      }
   }

   public void reportAgenticProgress(Component message) {
      this.reportAgenticProgress(message, false);
   }

   public void reportAgenticProgress(Component message, boolean milestone) {
      if (message != null) {
         String dedupKey = message.getString();
         if (!dedupKey.isBlank()) {
            MinecraftServer server = this.getPlayer() != null ? this.getPlayer().getServer() : null;
            if (server != null) {
               ServerPlayer target;
               if (this.getOwner() instanceof ServerPlayer sp) {
                  target = sp;
               } else {
                  target = this.getClosestPlayer().orElse(null);
               }

               if (target != null) {
                  long now = System.currentTimeMillis();
                  if (milestone || now - this.lastAgenticReportMs >= 1500L) {
                     if (!dedupKey.equals(this.lastAgenticReportMessage)) {
                        AgentSideEffects.broadcastChatToPlayer(
                           server,
                           Component.translatable("message.playerengine.agent.progress_prefix", new Object[]{this.agenticReportBotName(), message}),
                           target
                        );
                        this.lastAgenticReportMs = now;
                        this.lastAgenticReportMessage = dedupKey;
                     }
                  }
               }
            }
         }
      }
   }

   private String agenticReportBotName() {
      Character character = this.getAIPersistantData() != null ? this.getAIPersistantData().getCharacter() : null;
      return character != null && character.shortName() != null && !character.shortName().isBlank()
         ? character.shortName()
         : this.getEntity().getName().getString();
   }

   public boolean getShouldDefendFromHostiles() {
      return this.shouldDefendFromHostiles;
   }

   public void setShouldDefendFromHostiles(boolean toSet) {
      this.shouldDefendFromHostiles = toSet;
   }

   public FollowMode getFollowMode() {
      return this.followMode;
   }

   public void setFollowMode(FollowMode mode) {
      FollowMode resolved = mode == null ? FollowMode.NORMAL : mode;
      if (resolved != this.followMode) {
         LOGGER.info("[FollowDiag] MODE-CHANGE: {} -> {}", this.followMode, resolved);
      }

      this.followMode = resolved;
      this.setShouldDefendFromHostiles(this.followMode == FollowMode.COWARD);
   }

   private enum ControllerPublicationState {
      APPLIED,
      PENDING,
      FAILED;
   }

   public enum SettingsLiveState {
      APPLIED,
      PENDING,
      FAILED;
   }

   public record SettingsPublicationOutcome(int controllerCount, int appliedCount, int pendingCount, int failedCount) {
      public boolean fullyApplied() {
         return this.pendingCount == 0 && this.failedCount == 0;
      }

      public boolean hasFailures() {
         return this.failedCount > 0;
      }
   }
}
