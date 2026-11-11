package com.player2.playerengine.tasks.farming;

import com.player2.playerengine.agentic.AgenticRunRegistry;
import com.player2.playerengine.agentic.elliegps.EllieGPSStore;
import com.player2.playerengine.agentic.elliegps.FarmObservationStatus;
import com.player2.playerengine.agentic.elliegps.FarmWaypointObservation;
import com.player2.playerengine.agentic.elliegps.FarmWaypointService;
import com.player2.playerengine.agentic.elliegps.WaypointMutationResult;
import com.player2.playerengine.agentic.elliegps.WaypointMutationStatus;
import com.player2.playerengine.agentic.elliegps.WaypointRecord;
import com.player2.playerengine.agentic.elliegps.WaypointSearchOrder;
import com.player2.playerengine.agentic.elliegps.WaypointSearchResult;
import com.player2.playerengine.agentic.elliegps.WaypointSearchService;
import com.player2.playerengine.agentic.elliegps.WaypointSearchStatus;
import com.player2.playerengine.automaton.api.utils.input.Input;
import com.player2.playerengine.structureprotection.PlayerPlacedBlockStore;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.base.TrackedTaskOutcomeProvider;
import com.player2.playerengine.tasks.movement.GetToEntityTask;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;

public final class HarvestFarmTask extends Task implements TrackedTaskOutcomeProvider {
   public static final int WHOLE_TIMEOUT_TICKS = 6000;
   public static final int PICKUP_TIMEOUT_TICKS = 200;
   public static final int PICKUP_HORIZONTAL_RADIUS = 8;
   public static final int PICKUP_VERTICAL_RADIUS = 4;
   public static final int MAX_PICKUP_ENTITIES = 64;
   private static final int PICKUP_EMPTY_SETTLE_TICKS = 5;
   private final BlockPos commandAnchor;
   private final String commandDimension;
   private final BlockPos exactCenter;
   private final AgenticRunRegistry.AgenticRunState runState;
   private final FarmActionStateSource stateSource;
   private FarmTaskPhase phase = FarmTaskPhase.HARVEST_RESOLVE;
   private WaypointRecord farm;
   private BlockPos center;
   private List<BlockPos> matureCandidates = List.of();
   private int targetIndex;
   private VerifiedBreakBlockTask breakChild;
   private Task pickupMoveChild;
   private UUID pickupMoveTarget;
   private final Set<UUID> preExistingDropIds = new HashSet<>();
   private final Set<UUID> trackedDropIds = new LinkedHashSet<>();
   private boolean terminal;
   private boolean successful;
   private FarmTaskReason failureReason = FarmTaskReason.NONE;
   private HarvestFarmOutcome terminalOutcome;
   private WaypointMutationResult waypointResult;
   private FarmWaypointObservation postPassObservation;
   private boolean commitAttempted;
   private boolean pickupEntityOverflow;
   private int wholeTicks;
   private int matureFound;
   private int harvested;
   private int raceSkipped;
   private int denied;
   private int pickupTicks;
   private int pickupEmptyTicks;
   private int pickupItemUnitsLeft;

   public HarvestFarmTask(BlockPos commandAnchor, String commandDimension) {
      this(commandAnchor, commandDimension, null, null, FarmActionStateSource.LIVE);
   }

   public HarvestFarmTask(BlockPos commandAnchor, String commandDimension, BlockPos exactCenter) {
      this(commandAnchor, commandDimension, exactCenter, null, FarmActionStateSource.LIVE);
   }

   public HarvestFarmTask(BlockPos commandAnchor, String commandDimension, BlockPos exactCenter, AgenticRunRegistry.AgenticRunState runState) {
      this(commandAnchor, commandDimension, exactCenter, runState, FarmActionStateSource.LIVE);
   }

   HarvestFarmTask(
      BlockPos commandAnchor, String commandDimension, BlockPos exactCenter, AgenticRunRegistry.AgenticRunState runState, FarmActionStateSource stateSource
   ) {
      this.commandAnchor = Objects.requireNonNull(commandAnchor, "commandAnchor").immutable();
      String checkedDimension = Objects.requireNonNull(commandDimension, "commandDimension").strip();
      if (checkedDimension.isEmpty()) {
         throw new IllegalArgumentException("commandDimension cannot be blank");
      }

      this.commandDimension = checkedDimension;
      this.exactCenter = exactCenter == null ? null : exactCenter.immutable();
      this.runState = runState;
      this.stateSource = Objects.requireNonNull(stateSource, "stateSource");
   }

   @Override
   protected void onStart() {
      this.phase = FarmTaskPhase.HARVEST_RESOLVE;
      this.farm = null;
      this.center = this.exactCenter;
      this.matureCandidates = List.of();
      this.targetIndex = 0;
      this.breakChild = null;
      this.pickupMoveChild = null;
      this.pickupMoveTarget = null;
      this.preExistingDropIds.clear();
      this.trackedDropIds.clear();
      this.terminal = false;
      this.successful = false;
      this.failureReason = FarmTaskReason.NONE;
      this.terminalOutcome = null;
      this.waypointResult = null;
      this.postPassObservation = null;
      this.commitAttempted = false;
      this.pickupEntityOverflow = false;
      this.wholeTicks = 0;
      this.matureFound = 0;
      this.harvested = 0;
      this.raceSkipped = 0;
      this.denied = 0;
      this.pickupTicks = 0;
      this.pickupEmptyTicks = 0;
      this.pickupItemUnitsLeft = 0;
      this.updateProgress();
   }

   @Override
   protected Task onTick() {
      if (this.terminal) {
         return null;
      }

      this.wholeTicks++;
      if (deadlineDecision(this.wholeTicks, this.pickupTicks, this.phase) == HarvestFarmTask.DeadlineDecision.WHOLE_TIMEOUT) {
         if (this.phase == FarmTaskPhase.HARVEST_PICKUP && this.center != null) {
            this.pickupItemUnitsLeft = remainingItemUnits(this.observeEligibleDrops());
            this.markPickupDegradation();
         }

         this.fail(FarmTaskReason.HARVEST_TIMEOUT);
         return null;
      } else {
         if (!this.controller.getModSettings().getEllieGpsEnabled()) {
            this.fail(FarmTaskReason.ELLIEGPS_DISABLED);
            return null;
         }

         if (EllieGPSStore.get() != null && PlayerPlacedBlockStore.get() != null) {
            if (!Objects.equals(this.commandDimension, this.currentDimension())) {
               this.fail(FarmTaskReason.DIMENSION_CHANGED);
               return null;
            }

            if (this.phase != FarmTaskPhase.HARVEST_RESOLVE) {
               if (!this.farmFootprintLoaded()) {
                  this.fail(FarmTaskReason.CHUNK_UNLOADED);
                  return null;
               }

               if (!FarmInteractionPostconditions.isStandaloneWaterSource(this.controller.getWorld().getBlockState(this.center))) {
                  this.fail(FarmTaskReason.STALE_CENTER);
                  return null;
               }
            }
            Task next = switch (this.phase) {
               case HARVEST_RESOLVE -> this.tickResolve();
               case HARVEST_SCAN -> this.tickScan();
               case HARVEST_BREAK -> this.tickBreak();
               case HARVEST_PICKUP -> this.tickPickup();
               case HARVEST_RESCAN -> this.tickRescan();
               case COMMIT -> this.tickCommit();
               case PRECHECK, SELECT_SITE, ACQUIRE_RESOURCES, REVALIDATE, CLEAR, FILL, OPEN_CENTER, REGISTER_PREPARED, REPAIR, PLACE_WATER, TILL, VERIFY, DONE, FAILED -> {
                  this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
                  yield null;
               }
            };
            this.updateProgress();
            return next;
         } else {
            this.fail(FarmTaskReason.STORE_UNAVAILABLE);
            return null;
         }
      }
   }

   private Task tickResolve() {
      EllieGPSStore store = EllieGPSStore.get();
      WaypointRecord resolved;
      if (this.exactCenter != null) {
         resolved = store.byPosition(this.commandDimension, this.exactCenter);
         if (resolved == null) {
            this.fail(FarmTaskReason.FARM_NOT_FOUND);
            return null;
         }

         if (!"farm".equals(resolved.type)) {
            this.fail(FarmTaskReason.TYPE_CONFLICT);
            return null;
         }

         if (resolved.stale) {
            this.fail(FarmTaskReason.STALE_CENTER);
            return null;
         }
      } else {
         WaypointSearchResult search = WaypointSearchService.find(
            "", this.commandDimension, Set.of("farm"), false, WaypointSearchOrder.NEAREST, this.commandAnchor, 1
         );
         FarmTaskReason searchFailure = failureForSearch(search.status(), search.records().isEmpty());
         if (searchFailure != FarmTaskReason.NONE) {
            this.fail(searchFailure);
            return null;
         }

         resolved = search.records().get(0);
      }

      if (resolved.farmData() == null || !resolved.farmData().isSupportedVersion()) {
         this.fail(FarmTaskReason.UNSUPPORTED_DATA);
         return null;
      }

      if (resolved.id != null && !resolved.id.isBlank()) {
         BlockPos resolvedCenter = resolved.canonicalBlockPos();
         if (resolvedCenter == null) {
            this.fail(FarmTaskReason.UNSUPPORTED_DATA);
            return null;
         } else {
            this.farm = resolved.copy();
            this.center = resolvedCenter.immutable();
            if (!this.farmFootprintLoaded()) {
               this.fail(FarmTaskReason.CHUNK_UNLOADED);
               return null;
            } else if (!FarmInteractionPostconditions.isStandaloneWaterSource(this.controller.getWorld().getBlockState(this.center))) {
               this.fail(FarmTaskReason.STALE_CENTER);
               return null;
            } else {
               this.enterPhase(FarmTaskPhase.HARVEST_SCAN);
               return null;
            }
         }
      } else {
         this.fail(FarmTaskReason.UNSUPPORTED_DATA);
         return null;
      }
   }

   private Task tickScan() {
      FarmCropScanner.ScanResult scan;
      try {
         scan = FarmCropScanner.scan(this.controller.getWorld(), this.farm, this.stateSource);
      } catch (RuntimeException scanFailure) {
         this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
         return null;
      }

      FarmTaskReason scanFailure = failureForObservation(scan.observationResult().status());
      if (scanFailure != FarmTaskReason.NONE) {
         this.fail(scanFailure);
         return null;
      }

      this.matureCandidates = scan.matureCandidates();
      this.matureFound = this.matureCandidates.size();
      this.targetIndex = 0;
      if (this.matureFound > 0) {
         this.snapshotPreExistingDrops();
      }

      this.enterPhase(this.matureFound == 0 ? FarmTaskPhase.HARVEST_RESCAN : FarmTaskPhase.HARVEST_BREAK);
      return null;
   }

   private Task tickBreak() {
      if (this.breakChild != null) {
         if (!this.breakChild.isFinished()) {
            return this.breakChild;
         }

         if (this.breakChild.isSuccessful()) {
            this.harvested++;
         } else if (this.breakChild.wasPreAttemptTargetDrift()) {
            this.raceSkipped++;
         } else {
            if (this.breakChild.reason() == FarmTaskReason.CHUNK_UNLOADED
               || this.breakChild.reason() == FarmTaskReason.DIMENSION_CHANGED
               || this.breakChild.reason() == FarmTaskReason.STORE_UNAVAILABLE
               || this.breakChild.reason() == FarmTaskReason.CANCELLED_OPERATOR) {
               this.fail(this.breakChild.reason());
               return null;
            }

            this.denied++;
         }

         this.targetIndex++;
         this.breakChild = null;
         return null;
      } else {
         while (this.targetIndex < this.matureCandidates.size()) {
            BlockPos target = this.matureCandidates.get(this.targetIndex);
            if (!this.controller.getChunkTracker().isChunkLoaded(target)) {
               this.fail(FarmTaskReason.CHUNK_UNLOADED);
               return null;
            }

            BlockState actionState;
            try {
               actionState = Objects.requireNonNull(this.stateSource.read(this.controller.getWorld(), target), "farm state source returned null");
            } catch (RuntimeException stateFailure) {
               this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
               return null;
            }

            try {
               if (!isActionable(actionState)) {
                  this.raceSkipped++;
                  this.targetIndex++;
                  continue;
               }
            } catch (RuntimeException behaviorFailure) {
               this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
               return null;
            }

            BlockPos stance = this.chooseHarvestStance(target);
            if (stance != null) {
               this.breakChild = VerifiedBreakBlockTask.forMatureCrop(target, stance);
               return this.breakChild;
            }

            this.denied++;
            this.targetIndex++;
         }

         this.enterPhase(FarmTaskPhase.HARVEST_PICKUP);
         return null;
      }
   }

   private Task tickPickup() {
      this.pickupTicks++;
      List<ItemEntity> active = this.observeEligibleDrops();
      this.pickupItemUnitsLeft = remainingItemUnits(active);
      if (deadlineDecision(this.wholeTicks, this.pickupTicks, this.phase) == HarvestFarmTask.DeadlineDecision.PICKUP_BUDGET_EXPIRED) {
         this.enterPhase(FarmTaskPhase.HARVEST_RESCAN);
         this.markPickupDegradation();
         return null;
      }

      if (active.isEmpty()) {
         this.pickupEmptyTicks++;
         this.pickupMoveChild = null;
         this.pickupMoveTarget = null;
         if (this.pickupEmptyTicks >= 5) {
            this.pickupItemUnitsLeft = 0;
            this.enterPhase(FarmTaskPhase.HARVEST_RESCAN);
            this.markPickupDegradation();
         }

         return null;
      } else {
         this.pickupEmptyTicks = 0;
         ItemEntity target = active.get(0);
         if (!target.getUUID().equals(this.pickupMoveTarget)) {
            this.pickupMoveTarget = target.getUUID();
            this.pickupMoveChild = new GetToEntityTask(target, 1.0);
         }

         return this.pickupMoveChild;
      }
   }

   private Task tickRescan() {
      FarmCropScanner.ScanResult scan;
      try {
         scan = FarmCropScanner.scan(this.controller.getWorld(), this.farm, this.stateSource);
      } catch (RuntimeException scanFailure) {
         this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
         return null;
      }

      FarmTaskReason scanFailure = failureForObservation(scan.observationResult().status());
      if (scanFailure != FarmTaskReason.NONE) {
         this.fail(scanFailure);
         return null;
      } else {
         this.enterPhase(FarmTaskPhase.COMMIT);
         this.postPassObservation = scan.observationResult().observation();
         return null;
      }
   }

   private Task tickCommit() {
      if (!this.commitAttempted && this.postPassObservation != null) {
         this.commitAttempted = true;
         EllieGPSStore store = EllieGPSStore.get();
         WaypointRecord current = store == null ? null : store.byPosition(this.commandDimension, this.center);
         if (store == null) {
            this.waypointResult = new WaypointMutationResult(WaypointMutationStatus.FAILED_STORE_UNAVAILABLE, null, null);
         } else if (current == null || current.stale || !Objects.equals(current.id, this.farm.id)) {
            this.waypointResult = new WaypointMutationResult(WaypointMutationStatus.NOT_FOUND, this.farm.copy(), null);
         } else if (!"farm".equals(current.type)) {
            this.waypointResult = new WaypointMutationResult(WaypointMutationStatus.REJECTED_TYPE_CONFLICT, this.farm.copy(), current.copy());
         } else {
            try {
               this.waypointResult = FarmWaypointService.registerOrRefresh(this.postPassObservation, current.origin);
            } catch (UncheckedIOException jsonFailure) {
               this.waypointResult = new WaypointMutationResult(WaypointMutationStatus.FAILED_JSON_COMMIT, current.copy(), current.copy());
            } catch (RuntimeException commitFailure) {
               this.waypointResult = new WaypointMutationResult(WaypointMutationStatus.FAILED_JSON_COMMIT, current.copy(), current.copy());
            }
         }

         if (this.waypointResult == null) {
            this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
            return null;
         } else {
            FarmTaskReason mutationFailure = failureForMutation(this.waypointResult.status());
            if (mutationFailure != FarmTaskReason.NONE) {
               this.markIndexDegradation();
               this.fail(mutationFailure);
               return null;
            } else {
               this.markIndexDegradation();
               if (this.denied <= 0) {
                  this.succeed();
                  return null;
               } else {
                  this.failAt(
                     this.harvested == 0 && this.denied == this.matureFound ? FarmTaskReason.ALL_DENIED : FarmTaskReason.PARTIAL_DENIED,
                     FarmTaskPhase.HARVEST_BREAK
                  );
                  return null;
               }
            }
         }
      } else {
         this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
         return null;
      }
   }

   static FarmTaskReason failureForSearch(WaypointSearchStatus status, boolean empty) {
      return switch ((WaypointSearchStatus)Objects.requireNonNull(status, "status")) {
         case FAILED_STORE_UNAVAILABLE -> FarmTaskReason.STORE_UNAVAILABLE;
         case FAILED_SCAN_LIMIT -> FarmTaskReason.SEARCH_SCAN_LIMIT;
         case FAILED_SEARCH_ERROR -> FarmTaskReason.INTERNAL_CONTRACT_FAILURE;
         case INDEXED, AUTHORITATIVE_SPATIAL, FULL_STORE_FALLBACK -> empty ? FarmTaskReason.FARM_NOT_FOUND : FarmTaskReason.NONE;
      };
   }

   static boolean isActionable(BlockState state) {
      Objects.requireNonNull(state, "state");
      HarvestBehavior behavior = HarvestBehaviorRegistry.DEFAULT.resolve(state).orElse(null);
      return behavior != null && behavior.isMature(state) && behavior.action() == HarvestBehavior.HarvestAction.BREAK;
   }

   static HarvestFarmTask.DeadlineDecision deadlineDecision(int wholeTicks, int pickupTicks, FarmTaskPhase phase) {
      if (wholeTicks >= 0 && pickupTicks >= 0) {
         Objects.requireNonNull(phase, "phase");
         if (wholeTicks >= 6000) {
            return HarvestFarmTask.DeadlineDecision.WHOLE_TIMEOUT;
         } else {
            return phase == FarmTaskPhase.HARVEST_PICKUP && pickupTicks >= 200
               ? HarvestFarmTask.DeadlineDecision.PICKUP_BUDGET_EXPIRED
               : HarvestFarmTask.DeadlineDecision.RUNNING;
         }
      } else {
         throw new IllegalArgumentException("tick counts cannot be negative");
      }
   }

   static FarmTaskReason failureForObservation(FarmObservationStatus status) {
      return switch ((FarmObservationStatus)Objects.requireNonNull(status, "status")) {
         case OBSERVED -> FarmTaskReason.NONE;
         case UNLOADED -> FarmTaskReason.CHUNK_UNLOADED;
         case STALE_CENTER -> FarmTaskReason.STALE_CENTER;
         case UNSUPPORTED_DATA -> FarmTaskReason.UNSUPPORTED_DATA;
         case HANDLER_UNAVAILABLE -> FarmTaskReason.INTERNAL_CONTRACT_FAILURE;
      };
   }

   static FarmTaskReason failureForMutation(WaypointMutationStatus status) {
      return switch ((WaypointMutationStatus)Objects.requireNonNull(status, "status")) {
         case COMMITTED, COMMITTED_INDEX_DEGRADED, NO_CHANGE, NO_CHANGE_INDEX_DEGRADED -> FarmTaskReason.NONE;
         case NOT_FOUND, NOT_FOUND_INDEX_DEGRADED -> FarmTaskReason.FARM_NOT_FOUND;
         case REJECTED_TYPE_CONFLICT, REJECTED_TARGET_CONFLICT -> FarmTaskReason.TYPE_CONFLICT;
         case FAILED_STORE_UNAVAILABLE -> FarmTaskReason.STORE_UNAVAILABLE;
         case FAILED_JSON_COMMIT -> FarmTaskReason.WAYPOINT_JSON_FAILURE;
      };
   }

   private void snapshotPreExistingDrops() {
      this.preExistingDropIds.clear();

      for (ItemEntity item : this.itemEntitiesInEnvelope()) {
         this.preExistingDropIds.add(item.getUUID());
      }
   }

   private List<ItemEntity> observeEligibleDrops() {
      List<ItemEntity> candidates = this.itemEntitiesInEnvelope()
         .stream()
         .filter(entity -> !this.preExistingDropIds.contains(entity.getUUID()))
         .sorted(dropComparator(this.center))
         .toList();
      this.pickupEntityOverflow = this.pickupEntityOverflow
         | trackEligibleDropIds(candidates.stream().<UUID>map(Entity::getUUID).toList(), this.preExistingDropIds, this.trackedDropIds);
      return candidates.stream().filter(entity -> this.trackedDropIds.contains(entity.getUUID())).toList();
   }

   static boolean trackEligibleDropIds(Iterable<UUID> observed, Set<UUID> preExisting, Set<UUID> tracked) {
      Objects.requireNonNull(observed, "observed");
      Objects.requireNonNull(preExisting, "preExisting");
      Objects.requireNonNull(tracked, "tracked");
      boolean overflow = false;

      for (UUID id : observed) {
         UUID checked = Objects.requireNonNull(id, "drop UUID");
         if (!preExisting.contains(checked) && !tracked.contains(checked)) {
            if (tracked.size() < 64) {
               tracked.add(checked);
            } else {
               overflow = true;
            }
         }
      }

      return overflow;
   }

   private List<ItemEntity> itemEntitiesInEnvelope() {
      if (this.center == null) {
         return List.of();
      }

      ServerLevel level = this.controller.getWorld();
      Vec3 origin = Vec3.atCenterOf(this.center);
      AABB bounds = new AABB(origin, origin).inflate(8.0, 4.0, 8.0);
      return level.getEntitiesOfClass(
         ItemEntity.class, bounds, entity -> entity.isAlive() && !entity.isRemoved() && isInsidePickupEnvelope(this.center, entity.position())
      );
   }

   static boolean isInsidePickupEnvelope(BlockPos center, Vec3 position) {
      Objects.requireNonNull(center, "center");
      Objects.requireNonNull(position, "position");
      Vec3 origin = Vec3.atCenterOf(center);
      double dx = position.x - origin.x;
      double dz = position.z - origin.z;
      return dx * dx + dz * dz <= 64.0 && Math.abs(position.y - origin.y) <= 4.0;
   }

   static Comparator<ItemEntity> dropComparator(BlockPos center) {
      Vec3 origin = Vec3.atCenterOf(Objects.requireNonNull(center, "center"));
      return Comparator.<ItemEntity>comparingDouble(entity -> entity.distanceToSqr(origin)).thenComparing(Entity::getUUID);
   }

   private static int remainingItemUnits(List<ItemEntity> active) {
      return saturatingItemUnits(active.stream().map(entity -> Math.max(0, entity.getItem().getCount())).toList());
   }

   static int saturatingItemUnits(Iterable<Integer> counts) {
      Objects.requireNonNull(counts, "counts");
      long total = 0L;

      for (Integer count : counts) {
         total += Math.max(0, Objects.requireNonNull(count, "item count"));
         if (total >= 999L) {
            return 999;
         }
      }

      return (int)total;
   }

   private BlockPos chooseHarvestStance(BlockPos target) {
      int edge = 5;
      ArrayList<BlockPos> candidates = new ArrayList<>(4);
      candidates.add(new BlockPos(target.getX(), this.center.getY() + 1, this.center.getZ() - edge));
      candidates.add(new BlockPos(this.center.getX() + edge, this.center.getY() + 1, target.getZ()));
      candidates.add(new BlockPos(target.getX(), this.center.getY() + 1, this.center.getZ() + edge));
      candidates.add(new BlockPos(this.center.getX() - edge, this.center.getY() + 1, target.getZ()));
      candidates.sort(
         Comparator.<BlockPos>comparingLong(stancex -> FarmPlotGeometry.horizontalDistanceSquared(stancex, target))
            .thenComparingInt(Vec3i::getX)
            .thenComparingInt(Vec3i::getZ)
      );

      for (BlockPos stance : candidates) {
         if (this.openStance(stance)) {
            return stance.immutable();
         }
      }

      return null;
   }

   private boolean openStance(BlockPos stance) {
      BlockPos head = stance.above();
      BlockPos supportPos = stance.below();
      if (this.controller.getChunkTracker().isChunkLoaded(stance)
         && this.controller.getChunkTracker().isChunkLoaded(head)
         && this.controller.getChunkTracker().isChunkLoaded(supportPos)) {
         BlockState feetState = this.controller.getWorld().getBlockState(stance);
         BlockState headState = this.controller.getWorld().getBlockState(head);
         BlockState support = this.controller.getWorld().getBlockState(supportPos);
         return feetState.isAir()
            && headState.isAir()
            && !support.getCollisionShape(this.controller.getWorld(), supportPos).isEmpty()
            && support.getFluidState().isEmpty();
      } else {
         return false;
      }
   }

   private boolean farmFootprintLoaded() {
      if (this.center == null) {
         return false;
      }

      for (BlockPos soil : FarmPlotGeometry.allCells(this.center)) {
         if (!this.controller.getChunkTracker().isChunkLoaded(soil) || !this.controller.getChunkTracker().isChunkLoaded(soil.above())) {
            return false;
         }
      }

      return true;
   }

   private void markPickupDegradation() {
      if (this.runState != null && (this.pickupItemUnitsLeft > 0 || this.pickupEntityOverflow)) {
         this.runState.setFarmDegraded(AgenticRunRegistry.DegradationLevel.PARTIAL, "farm_pickup_incomplete");
      }
   }

   private void markIndexDegradation() {
      if (this.runState != null && this.waypointResult != null) {
         if (this.waypointResult.status() == WaypointMutationStatus.COMMITTED_INDEX_DEGRADED
            || this.waypointResult.status() == WaypointMutationStatus.NO_CHANGE_INDEX_DEGRADED
            || this.waypointResult.status() == WaypointMutationStatus.NOT_FOUND_INDEX_DEGRADED) {
            this.runState.setWaypointDegraded(AgenticRunRegistry.DegradationLevel.PARTIAL, "farm_waypoint_index_degraded");
         }
      }
   }

   private void enterPhase(FarmTaskPhase next) {
      this.phase = Objects.requireNonNull(next, "next");
      this.breakChild = null;
      this.pickupMoveChild = null;
      this.pickupMoveTarget = null;
   }

   private String currentDimension() {
      return this.controller.getWorld().dimension().location().toString();
   }

   private void succeed() {
      this.terminal = true;
      this.successful = true;
      this.failureReason = FarmTaskReason.NONE;
      this.phase = FarmTaskPhase.DONE;
      this.terminalOutcome = new HarvestFarmOutcome(
         true,
         true,
         FarmTaskReason.NONE,
         FarmTaskPhase.DONE,
         this.commandDimension,
         this.center,
         this.matureFound,
         this.harvested,
         this.raceSkipped,
         this.denied,
         this.pickupItemUnitsLeft,
         this.pickupEntityOverflow,
         this.waypointResult
      );
      this.updateProgress();
   }

   private void fail(FarmTaskReason reason) {
      this.failAt(reason, this.phase == FarmTaskPhase.DONE ? FarmTaskPhase.FAILED : this.phase);
   }

   private void failAt(FarmTaskReason reason, FarmTaskPhase failurePhase) {
      if (!this.terminal) {
         this.terminal = true;
         this.successful = false;
         this.failureReason = Objects.requireNonNull(reason, "reason");
         failurePhase = Objects.requireNonNull(failurePhase, "failurePhase");
         if (failurePhase == FarmTaskPhase.DONE) {
            failurePhase = FarmTaskPhase.FAILED;
         }

         this.phase = failurePhase;
         this.terminalOutcome = new HarvestFarmOutcome(
            true,
            false,
            this.failureReason,
            failurePhase,
            this.commandDimension,
            this.center,
            this.matureFound,
            this.harvested,
            this.raceSkipped,
            this.denied,
            this.pickupItemUnitsLeft,
            this.pickupEntityOverflow,
            this.waypointResult
         );
         this.updateProgress();
      }
   }

   private void updateProgress() {
      if (this.runState != null && !this.runState.isTerminal()) {
         this.runState.setFarmProgress(this.describeProgress());
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      if (!this.terminal && this.phase == FarmTaskPhase.HARVEST_PICKUP && this.controller != null && this.center != null) {
         this.pickupItemUnitsLeft = remainingItemUnits(this.observeEligibleDrops());
         this.markPickupDegradation();
      }

      if (!this.terminal) {
         this.fail(FarmTaskReason.CANCELLED_OPERATOR);
      }

      if (this.controller != null) {
         this.controller.getInputControls().release(Input.CLICK_LEFT);
         this.controller.getBaritone().getPathingBehavior().forceCancel();
         if (this.controller.getBaritone().getCustomGoalProcess().isActive()) {
            this.controller.getBaritone().getCustomGoalProcess().onLostControl();
         }
      }
   }

   @Override
   public boolean isFinished() {
      return this.terminal;
   }

   @Override
   public boolean isTerminal() {
      return this.terminal;
   }

   @Override
   public boolean isSuccessful() {
      return this.terminal && this.successful;
   }

   @Override
   public String controlledReason() {
      return this.failureReason.controlledReason();
   }

   public HarvestFarmOutcome outcome() {
      return this.terminalOutcome != null
         ? this.terminalOutcome
         : new HarvestFarmOutcome(
            false,
            false,
            FarmTaskReason.NONE,
            this.phase,
            this.commandDimension,
            this.center,
            this.matureFound,
            this.harvested,
            this.raceSkipped,
            this.denied,
            this.pickupItemUnitsLeft,
            this.pickupEntityOverflow,
            this.waypointResult
         );
   }

   public HarvestFarmTask.Progress progress() {
      return new HarvestFarmTask.Progress(
         this.phase,
         this.center,
         this.matureFound,
         this.harvested,
         this.raceSkipped,
         this.denied,
         this.pickupTicks,
         this.pickupItemUnitsLeft,
         this.pickupEntityOverflow
      );
   }

   public String describeProgress() {
      String centerText = this.center == null ? "unresolved" : this.center.toShortString();
      return "phase="
         + this.phase.name().toLowerCase(Locale.ROOT)
         + " center="
         + centerText
         + " mature="
         + this.matureFound
         + " harvested="
         + this.harvested
         + " skipped="
         + this.raceSkipped
         + " denied="
         + this.denied
         + " pickup_ticks="
         + this.pickupTicks
         + " pickup_left="
         + this.pickupItemUnitsLeft
         + " pickup_overflow="
         + this.pickupEntityOverflow;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof HarvestFarmTask task
         && task.commandAnchor.equals(this.commandAnchor)
         && task.commandDimension.equals(this.commandDimension)
         && Objects.equals(task.exactCenter, this.exactCenter);
   }

   @Override
   protected String toDebugString() {
      return this.exactCenter == null ? "Harvest nearest EllieGPS farm" : "Harvest EllieGPS farm at " + this.exactCenter.toShortString();
   }

   enum DeadlineDecision {
      RUNNING,
      PICKUP_BUDGET_EXPIRED,
      WHOLE_TIMEOUT;
   }

   public record Progress(
      FarmTaskPhase phase,
      BlockPos center,
      int matureFound,
      int harvested,
      int raceSkipped,
      int denied,
      int pickupTicks,
      int pickupItemUnitsLeft,
      boolean pickupEntityOverflow
   ) {
      public Progress {
         Objects.requireNonNull(phase, "phase");
         if (center != null) {
            center = center.immutable();
         }
      }
   }
}
