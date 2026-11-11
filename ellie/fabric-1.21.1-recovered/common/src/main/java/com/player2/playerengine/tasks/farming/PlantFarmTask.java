package com.player2.playerengine.tasks.farming;

import com.player2.playerengine.agentic.AgenticRunRegistry;
import com.player2.playerengine.agentic.elliegps.EllieGPSStore;
import com.player2.playerengine.agentic.elliegps.FarmObservationStatus;
import com.player2.playerengine.agentic.elliegps.FarmPlantingRestrictionUpdate;
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
import com.player2.playerengine.tasks.base.TaskSuspensionCause;
import com.player2.playerengine.tasks.base.TrackedTaskOutcomeProvider;
import com.player2.playerengine.tasks.base.TransientlyResumableTask;
import com.player2.playerengine.tasks.movement.GetToBlockTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

public final class PlantFarmTask extends Task implements TrackedTaskOutcomeProvider, TransientlyResumableTask {
   public static final int WHOLE_TIMEOUT_TICKS = 24000;
   private static final int TERMINAL_RESCAN_TRAVEL_GRACE_TICKS = 1200;
   private static final int TRANSIENT_RECEIPT_SETTLE_TICKS = 300;
   private final BlockPos commandAnchor;
   private final String commandDimension;
   private final List<FarmPlantingRequest> requests;
   private final BlockPos exactCenter;
   private final FarmPlantingPolicy policy;
   private final AgenticRunRegistry.AgenticRunState runState;
   private final int[] plantedCounts;
   private final boolean[] acquisitionCompleted;
   private final FarmPlantingItemAcquisitionTask.Checkpoint[] acquisitionCheckpoints;
   private PlantFarmPhase phase = PlantFarmPhase.RESOLVE;
   private List<FarmPlantingItemResolver.Descriptor> descriptors = List.of();
   private List<WaypointRecord> candidates = List.of();
   private int candidateIndex;
   private WaypointRecord farm;
   private BlockPos center;
   private Direction exitEdge;
   private FarmCropScanner.ScanResult scan;
   private List<BlockPos> openTargets = List.of();
   private int targetIndex;
   private int requestIndex;
   private FarmPlantingItemAcquisitionTask acquisitionChild;
   private PlantFarmBlockTask mutationChild;
   private PlantFarmBlockTask pendingMutationReceipt;
   private int pendingReceiptTicks;
   private boolean resumeStartPending;
   private boolean destinationCommitted;
   private boolean mutationAttempted;
   private boolean terminal;
   private boolean successful;
   private boolean completionPending;
   private PlantFarmReason failureReason = PlantFarmReason.NONE;
   private PlantFarmReason pendingFailureReason = PlantFarmReason.NONE;
   private PlantFarmPhase pendingFailurePhase = PlantFarmPhase.FAILED;
   private PlantFarmOutcome terminalOutcome;
   private WaypointMutationResult waypointResult;
   private FarmWaypointObservation finalObservation;
   private int wholeTicks;
   private int openSlotsAfter = -1;
   private int liveCandidateScans;
   private int liveFullScans;
   private int liveRepairRejected;

   static boolean mayTryNextCandidate(boolean exactFarmRequested, boolean destinationCommitted, boolean mutationAttempted, boolean hasNextCandidate) {
      return !exactFarmRequested && !destinationCommitted && !mutationAttempted && hasNextCandidate;
   }

   static PlantFarmPhase resumePhase(boolean hasFrozenFarm, boolean completionPending, boolean failurePending) {
      if (!hasFrozenFarm) {
         return PlantFarmPhase.RESOLVE;
      } else {
         return !completionPending && !failurePending ? PlantFarmPhase.SCAN : PlantFarmPhase.RESCAN;
      }
   }

   static PlantFarmTask.FailureTransition failureTransition(
      boolean mutationAttempted, boolean frozenFarmKnown, PlantFarmPhase currentPhase, boolean sameDimension, boolean controllerAvailable
   ) {
      Objects.requireNonNull(currentPhase, "currentPhase");
      return mutationAttempted
            && frozenFarmKnown
            && sameDimension
            && controllerAvailable
            && currentPhase != PlantFarmPhase.RESCAN
            && currentPhase != PlantFarmPhase.COMMIT
         ? PlantFarmTask.FailureTransition.RESCAN
         : PlantFarmTask.FailureTransition.TERMINAL;
   }

   static PlantFarmTask.FailureTransition cancellationTransition(
      boolean mutationAttempted, boolean frozenFarmKnown, boolean sameDimension, boolean controllerAvailable
   ) {
      return failureTransition(mutationAttempted, frozenFarmKnown, PlantFarmPhase.PLANT, sameDimension, controllerAvailable);
   }

   static PlantFarmPhase phaseAfterObservedRescan() {
      return PlantFarmPhase.COMMIT;
   }

   public PlantFarmTask(
      BlockPos commandAnchor,
      String commandDimension,
      List<FarmPlantingRequest> requests,
      BlockPos exactCenter,
      FarmPlantingPolicy policy,
      AgenticRunRegistry.AgenticRunState runState
   ) {
      this.commandAnchor = Objects.requireNonNull(commandAnchor, "commandAnchor").immutable();
      String checkedDimension = Objects.requireNonNull(commandDimension, "commandDimension").strip();
      if (checkedDimension.isEmpty()) {
         throw new IllegalArgumentException("commandDimension cannot be blank");
      }

      this.commandDimension = checkedDimension;
      this.requests = List.copyOf(Objects.requireNonNull(requests, "requests"));
      if (this.requests.isEmpty()) {
         throw new IllegalArgumentException("at least one planting request is required");
      }

      int total = 0;

      for (FarmPlantingRequest request : this.requests) {
         total = Math.addExact(total, request.count());
      }

      if (total > 80) {
         throw new IllegalArgumentException("one planting operation cannot exceed 80 cells");
      }

      this.exactCenter = exactCenter == null ? null : exactCenter.immutable();
      this.policy = Objects.requireNonNull(policy, "policy");
      if (policy.kind() != FarmPlantingPolicy.Kind.PRESERVE && exactCenter == null) {
         throw new IllegalArgumentException("planting policy changes require an exact farm");
      }

      this.runState = runState;
      this.plantedCounts = new int[this.requests.size()];
      this.acquisitionCompleted = new boolean[this.requests.size()];
      this.acquisitionCheckpoints = new FarmPlantingItemAcquisitionTask.Checkpoint[this.requests.size()];

      for (int i = 0; i < this.acquisitionCheckpoints.length; i++) {
         this.acquisitionCheckpoints[i] = new FarmPlantingItemAcquisitionTask.Checkpoint();
      }
   }

   @Override
   protected void onStart() {
      boolean resuming = this.resumeStartPending;
      this.resumeStartPending = false;
      this.acquisitionChild = null;
      this.mutationChild = null;
      this.scan = null;
      this.openTargets = List.of();
      this.targetIndex = 0;
      if (!resuming) {
         this.phase = PlantFarmPhase.RESOLVE;
         this.descriptors = List.of();
         this.candidates = List.of();
         this.candidateIndex = 0;
         this.farm = null;
         this.center = this.exactCenter;
         this.exitEdge = null;
         this.requestIndex = 0;
         this.pendingMutationReceipt = null;
         this.pendingReceiptTicks = 0;
         this.destinationCommitted = false;
         this.mutationAttempted = false;
         this.terminal = false;
         this.successful = false;
         this.completionPending = false;
         this.failureReason = PlantFarmReason.NONE;
         this.pendingFailureReason = PlantFarmReason.NONE;
         this.pendingFailurePhase = PlantFarmPhase.FAILED;
         this.terminalOutcome = null;
         this.waypointResult = null;
         this.finalObservation = null;
         this.wholeTicks = 0;
         this.openSlotsAfter = -1;
         this.liveCandidateScans = 0;
         this.liveFullScans = 0;
         this.liveRepairRejected = 0;
      } else if (!this.terminal) {
         this.phase = resumePhase(this.farm != null, this.completionPending, this.pendingFailureReason != PlantFarmReason.NONE);
         this.failureReason = PlantFarmReason.NONE;
         this.finalObservation = null;
      }

      this.updateProgress();
   }

   @Override
   protected Task onTick() {
      if (this.terminal) {
         return null;
      }

      this.wholeTicks++;
      if (this.pendingMutationReceipt != null) {
         this.mutationAttempted = this.mutationAttempted | this.pendingMutationReceipt.hasIssuedMutation();
         if (!this.settleRetainedReceipt()) {
            this.updateProgress();
            return null;
         }

         if (this.terminal) {
            return null;
         }
      }

      if (this.wholeTicks >= 24000) {
         if (this.phase != PlantFarmPhase.RESCAN && this.phase != PlantFarmPhase.COMMIT) {
            this.requestFailure(PlantFarmReason.OPERATION_TIMEOUT);
            return null;
         }

         if (this.completionPending && this.pendingFailureReason == PlantFarmReason.NONE) {
            this.completionPending = false;
            this.pendingFailureReason = PlantFarmReason.OPERATION_TIMEOUT;
            this.pendingFailurePhase = PlantFarmPhase.PLANT;
         }
      }

      if (!Objects.equals(this.commandDimension, this.currentDimension())) {
         this.requestFailure(PlantFarmReason.DIMENSION_CHANGED);
         return null;
      }

      if (!this.controller.getModSettings().getEllieGpsEnabled()) {
         this.requestFailure(PlantFarmReason.ELLIEGPS_DISABLED);
         return null;
      }

      if (EllieGPSStore.get() != null && PlayerPlacedBlockStore.get() != null) {
         Task next = switch (this.phase) {
            case RESOLVE -> this.tickResolve();
            case TRAVEL -> this.tickTravel();
            case SCAN -> this.tickScan();
            case ACQUIRE -> this.tickAcquire();
            case PLANT -> this.tickPlant();
            case RESCAN -> this.tickRescan();
            case COMMIT -> this.tickCommit();
            case DONE, FAILED -> {
               this.requestFailure(PlantFarmReason.INTERNAL_CONTRACT_FAILURE);
               yield null;
            }
         };
         this.updateProgress();
         return next;
      } else {
         this.requestFailure(PlantFarmReason.STORE_UNAVAILABLE);
         return null;
      }
   }

   private Task tickResolve() {
      PlantFarmReason descriptorFailure = this.resolveDescriptors();
      if (descriptorFailure != PlantFarmReason.NONE) {
         this.requestFailure(descriptorFailure);
         return null;
      }

      EllieGPSStore store = EllieGPSStore.get();
      if (this.exactCenter != null) {
         WaypointRecord exact = store.byPosition(this.commandDimension, this.exactCenter);
         PlantFarmReason failure = FarmPlantingSelector.exactMetadataFailure(exact, this.requests, this.policy);
         if (failure != PlantFarmReason.NONE) {
            this.requestFailure(failure);
            return null;
         }

         this.candidates = List.of(exact.copy());
      } else {
         WaypointSearchResult search = WaypointSearchService.find(
            "", this.commandDimension, Set.of("farm"), false, WaypointSearchOrder.NEAREST, this.commandAnchor, 4096
         );
         PlantFarmReason searchFailure = searchFailure(search.status());
         if (searchFailure != PlantFarmReason.NONE) {
            this.requestFailure(searchFailure);
            return null;
         }

         FarmPlantingSelector.Selection selection = FarmPlantingSelector.automatic(search.records(), this.commandDimension, this.commandAnchor, this.requests);
         if (!selection.successful()) {
            this.requestFailure(selection.failure());
            return null;
         }

         this.candidates = selection.candidates();
      }

      this.candidateIndex = 0;
      this.freezeCandidate(this.candidates.get(0));
      this.phase = PlantFarmPhase.TRAVEL;
      return null;
   }

   private Task tickTravel() {
      FarmCropScanner.ScanResult observed = FarmCropScanner.scan(this.controller.getWorld(), this.farm);
      FarmObservationStatus status = observed.observationResult().status();
      if (status == FarmObservationStatus.UNLOADED) {
         this.setDebugState("Travelling to recorded farm " + this.center.toShortString());
         return new GetToBlockTask(this.approachStance());
      } else if (status != FarmObservationStatus.OBSERVED) {
         this.rejectCandidateOrFail(observationFailure(status));
         return null;
      } else {
         this.scan = observed;
         this.phase = PlantFarmPhase.SCAN;
         return null;
      }
   }

   private Task tickScan() {
      FarmCropScanner.ScanResult current = this.scan;
      this.scan = null;
      if (current == null) {
         current = FarmCropScanner.scan(this.controller.getWorld(), this.farm);
      }

      FarmObservationStatus status = current.observationResult().status();
      if (status == FarmObservationStatus.UNLOADED) {
         this.phase = PlantFarmPhase.TRAVEL;
         return null;
      }

      if (status != FarmObservationStatus.OBSERVED) {
         this.rejectCandidateOrFail(observationFailure(status));
         return null;
      }

      this.liveCandidateScans++;
      FarmWaypointObservation observation = current.observationResult().observation();
      if (observation.farmlandCount() == 80 && current.openCount() + current.recognizedCount() == 80) {
         int remaining = this.remainingTotal();
         if (current.openCount() == 0) {
            this.liveFullScans++;
            this.rejectCandidateOrFail(this.exactCenter == null ? PlantFarmReason.ALL_RECORDED_FARMS_FULL : PlantFarmReason.EXACT_FARM_FULL);
            return null;
         }

         if (current.openCount() < remaining) {
            this.rejectCandidateOrFail(PlantFarmReason.INSUFFICIENT_OPEN_SLOTS);
            return null;
         }

         WaypointMutationResult refreshed;
         try {
            refreshed = FarmWaypointService.registerOrRefresh(observation, Objects.toString(this.farm.origin, "bot_placed"));
         } catch (RuntimeException failedRefresh) {
            this.requestFailure(PlantFarmReason.WAYPOINT_JSON_FAILURE);
            return null;
         }

         this.waypointResult = refreshed;
         if (!mutationCommitted(refreshed.status())) {
            this.requestFailure(refreshFailure(refreshed.status()));
            return null;
         }

         if (refreshed.current() != null) {
            this.farm = refreshed.current().copy();
         }

         this.destinationCommitted = true;
         if (this.requestIndex >= this.requests.size()) {
            this.completionPending = true;
            this.phase = PlantFarmPhase.RESCAN;
            return null;
         }

         FarmPlantingRequest request = this.requests.get(this.requestIndex);
         int requestRemaining = request.count() - this.plantedCounts[this.requestIndex];
         if (requestRemaining <= 0) {
            this.requestIndex++;
            this.phase = PlantFarmPhase.SCAN;
            return null;
         }

         this.openTargets = FarmPlantingOrder.retreatOrder(this.center, current.openCells(), this.exitEdge);
         this.targetIndex = 0;
         int held = this.inventoryCount(this.descriptors.get(this.requestIndex).item());
         if (held >= requestRemaining) {
            this.acquisitionCompleted[this.requestIndex] = true;
            this.phase = PlantFarmPhase.PLANT;
         } else if (this.acquisitionCompleted[this.requestIndex]) {
            this.acquisitionCompleted[this.requestIndex] = false;
            this.acquisitionCheckpoints[this.requestIndex] = new FarmPlantingItemAcquisitionTask.Checkpoint();
            this.phase = PlantFarmPhase.ACQUIRE;
         } else {
            this.phase = PlantFarmPhase.ACQUIRE;
         }

         return null;
      } else {
         this.liveRepairRejected++;
         this.rejectCandidateOrFail(PlantFarmReason.FARM_NEEDS_REPAIR);
         return null;
      }
   }

   private Task tickAcquire() {
      if (this.requestIndex >= this.requests.size()) {
         this.requestFailure(PlantFarmReason.INTERNAL_CONTRACT_FAILURE);
         return null;
      }

      int remaining = this.requests.get(this.requestIndex).count() - this.plantedCounts[this.requestIndex];
      Item item = this.descriptors.get(this.requestIndex).item();
      if (this.inventoryCount(item) >= remaining) {
         this.acquisitionCompleted[this.requestIndex] = true;
         this.acquisitionChild = null;
         this.phase = PlantFarmPhase.SCAN;
         return null;
      }

      if (this.acquisitionChild == null) {
         this.acquisitionChild = new FarmPlantingItemAcquisitionTask(
            item, remaining, this.center, this.commandDimension, this.acquisitionCheckpoints[this.requestIndex]
         );
         return this.acquisitionChild;
      }

      if (!this.acquisitionChild.isFinished()) {
         return this.acquisitionChild;
      }

      if (this.acquisitionChild.isSuccessful() && this.inventoryCount(item) >= remaining) {
         this.acquisitionCompleted[this.requestIndex] = true;
         this.acquisitionChild = null;
         this.phase = PlantFarmPhase.SCAN;
         return null;
      }

      PlantFarmReason failure = switch (this.acquisitionChild.outcome()) {
         case DEADLINE_EXCEEDED -> PlantFarmReason.ACQUISITION_TIMEOUT;
         case DIMENSION_CHANGED -> PlantFarmReason.DIMENSION_CHANGED;
         case WORLD_GUARDS_UNAVAILABLE -> PlantFarmReason.STORE_UNAVAILABLE;
         case SOURCE_EXHAUSTED, SOURCE_DISCOVERY_FAILED -> PlantFarmReason.PLANTABLE_UNAVAILABLE;
         case CANCELLED -> PlantFarmReason.CANCELLED_OPERATOR;
         case RUNNING, ACQUIRED -> PlantFarmReason.INTERNAL_CONTRACT_FAILURE;
      };
      this.acquisitionChild = null;
      this.requestFailure(failure);
      return null;
   }

   private Task tickPlant() {
      if (this.requestIndex >= this.requests.size()) {
         this.completionPending = true;
         this.phase = PlantFarmPhase.RESCAN;
         return null;
      }

      FarmPlantingRequest request = this.requests.get(this.requestIndex);
      if (this.plantedCounts[this.requestIndex] >= request.count()) {
         this.requestIndex++;
         this.phase = PlantFarmPhase.SCAN;
         return null;
      }

      if (this.mutationChild != null) {
         if (!this.mutationChild.isFinished()) {
            return this.mutationChild;
         } else {
            PlantFarmBlockTask completed = this.mutationChild;
            this.mutationChild = null;
            this.mutationAttempted = this.mutationAttempted | completed.hasIssuedMutation();
            if (completed.isSuccessful()) {
               this.accountPlantingReceipt(completed);
               return null;
            } else if (!completed.hasIssuedMutation() && completed.reason() == FarmTaskReason.MANIFEST_DRIFT) {
               this.phase = PlantFarmPhase.SCAN;
               return null;
            } else {
               this.requestFailure(mapMutationFailure(completed.reason(), completed.hasIssuedMutation()));
               return null;
            }
         }
      } else {
         if (this.targetIndex >= this.openTargets.size()) {
            this.requestFailure(PlantFarmReason.INSUFFICIENT_OPEN_SLOTS);
            return null;
         }

         BlockPos target = this.openTargets.get(this.targetIndex++);
         if (this.controller.getWorld().getBlockState(target).isAir()
            && this.controller.getWorld().getBlockState(target.below()).is(Blocks.FARMLAND)) {
            BlockPos stance = this.chooseStance(target);
            if (stance == null) {
               this.requestFailure(PlantFarmReason.NO_SAFE_STANCE);
               return null;
            } else {
               this.mutationChild = new PlantFarmBlockTask(target, stance, this.descriptors.get(this.requestIndex));
               return this.mutationChild;
            }
         } else {
            this.phase = PlantFarmPhase.SCAN;
            return null;
         }
      }
   }

   private Task tickRescan() {
      FarmCropScanner.ScanResult current = FarmCropScanner.scan(this.controller.getWorld(), this.farm);
      FarmObservationStatus status = current.observationResult().status();
      if (status == FarmObservationStatus.UNLOADED && this.wholeTicks < 25200) {
         this.setDebugState("Returning to frozen farm for terminal metadata reconciliation");
         return new GetToBlockTask(this.approachStance());
      } else if (status != FarmObservationStatus.OBSERVED) {
         this.finalizeFailure(observationFailure(status), PlantFarmPhase.RESCAN);
         return null;
      } else {
         this.finalObservation = current.observationResult().observation();
         this.openSlotsAfter = current.openCount();
         this.phase = phaseAfterObservedRescan();
         return null;
      }
   }

   private Task tickCommit() {
      if (this.finalObservation == null) {
         this.finalizeFailure(PlantFarmReason.INTERNAL_CONTRACT_FAILURE, PlantFarmPhase.COMMIT);
         return null;
      }

      FarmPlantingRestrictionUpdate restriction = this.completionPending ? restrictionUpdate(this.policy) : FarmPlantingRestrictionUpdate.preserve();

      try {
         this.waypointResult = FarmWaypointService.registerOrRefresh(this.finalObservation, Objects.toString(this.farm.origin, "bot_placed"), restriction);
      } catch (RuntimeException failedCommit) {
         this.finalizeFailure(PlantFarmReason.WAYPOINT_JSON_FAILURE, PlantFarmPhase.COMMIT);
         return null;
      }

      if (!mutationCommitted(this.waypointResult.status())) {
         this.finalizeFailure(refreshFailure(this.waypointResult.status()), PlantFarmPhase.COMMIT);
         return null;
      }

      if (this.completionPending) {
         this.succeed();
      } else {
         PlantFarmReason reason = this.pendingFailureReason == PlantFarmReason.NONE ? PlantFarmReason.INTERNAL_CONTRACT_FAILURE : this.pendingFailureReason;
         PlantFarmPhase failurePhase = this.pendingFailureReason == PlantFarmReason.NONE ? PlantFarmPhase.COMMIT : this.pendingFailurePhase;
         this.finalizeFailure(reason, failurePhase);
      }

      return null;
   }

   private PlantFarmReason resolveDescriptors() {
      if (!this.descriptors.isEmpty()) {
         return PlantFarmReason.NONE;
      }

      ArrayList<FarmPlantingItemResolver.Descriptor> resolved = new ArrayList<>(this.requests.size());

      for (FarmPlantingRequest request : this.requests) {
         ResourceLocation id = ResourceLocation.tryParse(request.plantingItemId());
         Item item = id == null ? null : (Item)BuiltInRegistries.ITEM.get(id);
         FarmPlantingItemResolver.Result result = FarmPlantingItemResolver.resolve(item);
         if (!result.supported()) {
            return result.status() == FarmPlantingItemResolver.Status.EXCLUDED_STEM
               ? PlantFarmReason.STEM_CROP_EXCLUDED
               : PlantFarmReason.UNSUPPORTED_PLANTABLE;
         }

         if (!result.descriptor().plantingItemId().toString().equals(request.plantingItemId())) {
            return PlantFarmReason.UNSUPPORTED_PLANTABLE;
         }

         resolved.add(result.descriptor());
      }

      this.descriptors = List.copyOf(resolved);
      return PlantFarmReason.NONE;
   }

   private void freezeCandidate(WaypointRecord candidate) {
      this.farm = Objects.requireNonNull(candidate, "candidate").copy();
      this.center = Objects.requireNonNull(this.farm.canonicalBlockPos(), "farm center").immutable();
      this.exitEdge = FarmPlantingOrder.selectExitEdge(this.center, this.commandAnchor);
      this.scan = null;
      this.openTargets = List.of();
      this.targetIndex = 0;
   }

   private void rejectCandidateOrFail(PlantFarmReason reason) {
      boolean hasNextCandidate = this.candidateIndex + 1 < this.candidates.size();
      if (mayTryNextCandidate(this.exactCenter != null, this.destinationCommitted, this.mutationAttempted, hasNextCandidate)) {
         this.candidateIndex++;
         this.freezeCandidate(this.candidates.get(this.candidateIndex));
         this.phase = PlantFarmPhase.TRAVEL;
      } else {
         PlantFarmReason terminalReason = reason;
         if (this.exactCenter == null && !this.mutationAttempted && !hasNextCandidate) {
            if (this.liveCandidateScans > 0 && this.liveFullScans == this.liveCandidateScans) {
               terminalReason = PlantFarmReason.ALL_RECORDED_FARMS_FULL;
            } else if (this.liveCandidateScans > 0 && this.liveRepairRejected == this.liveCandidateScans) {
               terminalReason = PlantFarmReason.FARM_NEEDS_REPAIR;
            }
         }

         this.requestFailure(terminalReason);
      }
   }

   private BlockPos approachStance() {
      return this.center.relative(this.exitEdge, 5).above().immutable();
   }

   private BlockPos chooseStance(BlockPos target) {
      for (BlockPos candidate : FarmPlotGeometry.stanceCandidates(this.center, target)) {
         if (FarmStanceNavigation.hasPlannerSafeLiveGeometry(this.controller, candidate)) {
            return candidate;
         }
      }

      return null;
   }

   private void accountPlantingReceipt(PlantFarmBlockTask receipt) {
      if (this.requestIndex < this.requests.size()
         && receipt.descriptor().item() == this.descriptors.get(this.requestIndex).item()
         && this.plantedCounts[this.requestIndex] < this.requests.get(this.requestIndex).count()) {
         this.plantedCounts[this.requestIndex]++;
         this.mutationAttempted = true;
      } else {
         this.requestFailure(PlantFarmReason.INTERNAL_CONTRACT_FAILURE);
      }
   }

   private boolean settleRetainedReceipt() {
      PlantFarmBlockTask retained = this.pendingMutationReceipt;
      if (!retained.settlePendingTransition()) {
         this.pendingMutationReceipt = null;
         this.requestFailure(mapMutationFailure(retained.reason(), retained.hasIssuedMutation()));
         return false;
      }

      if (retained.isFinished()) {
         this.pendingMutationReceipt = null;
         this.pendingReceiptTicks = 0;
         if (retained.isSuccessful()) {
            this.accountPlantingReceipt(retained);
         } else {
            this.requestFailure(mapMutationFailure(retained.reason(), retained.hasIssuedMutation()));
         }

         return true;
      } else {
         this.pendingReceiptTicks++;
         if (this.pendingReceiptTicks <= 300) {
            this.setDebugState("Settling planting receipt after interruption");
            return false;
         } else {
            this.pendingMutationReceipt = null;
            this.pendingReceiptTicks = 0;
            this.phase = PlantFarmPhase.SCAN;
            return true;
         }
      }
   }

   private int remainingTotal() {
      int remaining = 0;

      for (int i = 0; i < this.requests.size(); i++) {
         remaining = Math.addExact(remaining, this.requests.get(i).count() - this.plantedCounts[i]);
      }

      return remaining;
   }

   private int inventoryCount(Item item) {
      return this.controller.getItemStorage().getItemCountInventoryOnly(item);
   }

   private void requestFailure(PlantFarmReason reason) {
      if (!this.terminal) {
         PlantFarmReason checked = Objects.requireNonNull(reason, "reason");
         PlantFarmPhase failedDuring = this.phase == PlantFarmPhase.DONE ? PlantFarmPhase.FAILED : this.phase;
         boolean mayHaveMutatedFarm = this.mutationAttempted
            || this.pendingMutationReceipt != null && this.pendingMutationReceipt.hasIssuedMutation()
            || this.mutationChild != null && this.mutationChild.hasIssuedMutation();
         this.mutationAttempted |= mayHaveMutatedFarm;
         boolean controllerAvailable = this.controller != null;
         boolean frozenFarmKnown = this.farm != null && this.center != null;
         boolean sameDimension = mayHaveMutatedFarm && frozenFarmKnown && controllerAvailable && Objects.equals(this.commandDimension, this.currentDimension());
         if (failureTransition(mayHaveMutatedFarm, frozenFarmKnown, this.phase, sameDimension, controllerAvailable) == PlantFarmTask.FailureTransition.RESCAN) {
            this.completionPending = false;
            this.pendingFailureReason = checked;
            this.pendingFailurePhase = failedDuring;
            this.phase = PlantFarmPhase.RESCAN;
         } else {
            this.finalizeFailure(checked, failedDuring);
         }
      }
   }

   private void succeed() {
      if (!this.terminal) {
         this.terminal = true;
         this.successful = true;
         this.failureReason = PlantFarmReason.NONE;
         this.phase = PlantFarmPhase.DONE;
         this.terminalOutcome = new PlantFarmOutcome(
            true,
            true,
            PlantFarmReason.NONE,
            PlantFarmPhase.DONE,
            this.commandDimension,
            this.center,
            this.requests,
            this.plantedSnapshot(),
            this.openSlotsAfter,
            this.waypointResult
         );
         this.updateProgress();
      }
   }

   private void finalizeFailure(PlantFarmReason reason, PlantFarmPhase failedDuring) {
      if (!this.terminal) {
         this.terminal = true;
         this.successful = false;
         this.failureReason = Objects.requireNonNull(reason, "reason");
         PlantFarmPhase outcomePhase = failedDuring == PlantFarmPhase.DONE ? PlantFarmPhase.FAILED : failedDuring;
         this.phase = outcomePhase;
         this.terminalOutcome = new PlantFarmOutcome(
            true,
            false,
            this.failureReason,
            outcomePhase,
            this.commandDimension,
            this.center,
            this.requests,
            this.plantedSnapshot(),
            this.openSlotsAfter,
            this.waypointResult
         );
         this.updateProgress();
      }
   }

   private List<FarmPlantingRequest> plantedSnapshot() {
      ArrayList<FarmPlantingRequest> result = new ArrayList<>();

      for (int i = 0; i < this.requests.size(); i++) {
         if (this.plantedCounts[i] > 0) {
            result.add(new FarmPlantingRequest(this.requests.get(i).plantingItemId(), this.plantedCounts[i]));
         }
      }

      return List.copyOf(result);
   }

   private void terminalizeCancellation() {
      if (!this.terminal) {
         PlantFarmPhase cancelledDuring = this.phase == PlantFarmPhase.DONE ? PlantFarmPhase.FAILED : this.phase;
         this.settleActiveReceiptForTerminal();
         if (!this.terminal) {
            PlantFarmReason terminalReason = PlantFarmReason.CANCELLED_OPERATOR;
            boolean controllerAvailable = this.controller != null;
            boolean frozenFarmKnown = this.farm != null && this.center != null;
            boolean sameDimension = this.mutationAttempted
               && frozenFarmKnown
               && controllerAvailable
               && Objects.equals(this.commandDimension, this.currentDimension());
            PlantFarmTask.FailureTransition transition = cancellationTransition(this.mutationAttempted, frozenFarmKnown, sameDimension, controllerAvailable);
            if (!this.mutationAttempted || controllerAvailable && this.farm != null && this.center != null) {
               if (this.mutationAttempted && !sameDimension) {
                  terminalReason = PlantFarmReason.DIMENSION_CHANGED;
               } else if (transition == PlantFarmTask.FailureTransition.RESCAN) {
                  this.phase = PlantFarmPhase.RESCAN;
                  FarmCropScanner.ScanResult current = FarmCropScanner.scan(this.controller.getWorld(), this.farm);
                  if (current.observationResult().status() == FarmObservationStatus.OBSERVED) {
                     this.finalObservation = current.observationResult().observation();
                     this.openSlotsAfter = current.openCount();
                     this.phase = phaseAfterObservedRescan();

                     try {
                        this.waypointResult = FarmWaypointService.registerOrRefresh(this.finalObservation, Objects.toString(this.farm.origin, "bot_placed"));
                        if (!mutationCommitted(this.waypointResult.status())) {
                           terminalReason = refreshFailure(this.waypointResult.status());
                        }
                     } catch (RuntimeException failedCommit) {
                        this.waypointResult = null;
                        terminalReason = PlantFarmReason.WAYPOINT_JSON_FAILURE;
                     }
                  } else {
                     terminalReason = observationFailure(current.observationResult().status());
                  }
               }
            } else {
               terminalReason = PlantFarmReason.STORE_UNAVAILABLE;
            }

            this.finalizeFailure(terminalReason, cancelledDuring);
         }
      }
   }

   private void settleActiveReceiptForTerminal() {
      PlantFarmBlockTask active = this.pendingMutationReceipt != null ? this.pendingMutationReceipt : this.mutationChild;
      this.pendingMutationReceipt = null;
      this.mutationChild = null;
      if (active != null && active.hasIssuedMutation()) {
         this.mutationAttempted = true;
         if (active.settlePendingTransition() && active.isFinished() && active.isSuccessful()) {
            this.accountPlantingReceipt(active);
         }
      }
   }

   @Override
   public boolean prepareForTransientResume(TaskSuspensionCause cause) {
      Objects.requireNonNull(cause, "cause");
      if (this.terminal) {
         return false;
      }

      if (this.acquisitionChild != null
         && this.requestIndex < this.acquisitionCompleted.length
         && this.acquisitionChild.isFinished()
         && this.acquisitionChild.isSuccessful()) {
         this.acquisitionCompleted[this.requestIndex] = true;
      }

      if (this.mutationChild != null) {
         this.mutationAttempted = this.mutationAttempted | this.mutationChild.hasIssuedMutation();
         if (!this.mutationChild.settlePendingTransition()) {
            PlantFarmReason childFailure = mapMutationFailure(this.mutationChild.reason(), this.mutationChild.hasIssuedMutation());
            this.requestFailure(childFailure);
            this.resumeStartPending = !this.terminal;
            this.quiesce();
            return !this.terminal;
         }

         if (this.mutationChild.isFinished()) {
            if (!this.mutationChild.isSuccessful()) {
               PlantFarmReason childFailure = mapMutationFailure(this.mutationChild.reason(), this.mutationChild.hasIssuedMutation());
               this.requestFailure(childFailure);
               this.resumeStartPending = !this.terminal;
               this.quiesce();
               return !this.terminal;
            }

            this.accountPlantingReceipt(this.mutationChild);
         } else if (this.mutationChild.hasIssuedMutation()) {
            this.mutationChild.prepareForTransientDetach();
            this.pendingMutationReceipt = this.mutationChild;
            this.pendingReceiptTicks = 0;
         }
      }

      this.resumeStartPending = true;
      this.quiesce();
      return true;
   }

   @Override
   public void onTransientResumeAbandoned() {
      this.resumeStartPending = false;
      this.terminalizeCancellation();
      this.quiesce();
   }

   @Override
   public void afterChildrenStopped() {
      this.acquisitionChild = null;
      this.mutationChild = null;
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.resumeStartPending = false;
      this.terminalizeCancellation();
      this.quiesce();
   }

   private void quiesce() {
      if (this.controller != null) {
         this.controller.getInputControls().release(Input.CLICK_LEFT);
         this.controller.getInputControls().release(Input.CLICK_RIGHT);
         this.controller.getBaritone().getPathingBehavior().forceCancel();
         if (this.controller.getBaritone().getCustomGoalProcess().isActive()) {
            this.controller.getBaritone().getCustomGoalProcess().onLostControl();
         }
      }
   }

   private void updateProgress() {
      if (this.runState != null && !this.runState.isTerminal()) {
         this.runState.setFarmProgress(this.describeProgress());
      }
   }

   public String describeProgress() {
      if (this.terminal && !this.successful) {
         return "farm planting failed: " + this.failureReason.controlledReason();
      }

      return switch (this.phase) {
         case RESOLVE -> "resolving a compatible recorded farm";
         case TRAVEL -> "travelling to the selected farm";
         case SCAN -> "checking live farm capacity";
         case ACQUIRE -> "acquiring planting items for crop " + Math.min(this.requestIndex + 1, this.requests.size()) + "/" + this.requests.size();
         case PLANT -> "planting crop "
            + Math.min(this.requestIndex + 1, this.requests.size())
            + "/"
            + this.requests.size()
            + " ("
            + this.plantedTotal()
            + "/"
            + this.requestedTotal()
            + ")";
         case RESCAN -> "rescanning planted farm";
         case COMMIT -> "saving exact farm crop and capacity metadata";
         case DONE -> "farm planting complete";
         case FAILED -> "farm planting failed";
      };
   }

   private int plantedTotal() {
      int total = 0;

      for (int value : this.plantedCounts) {
         total = Math.addExact(total, value);
      }

      return total;
   }

   private int requestedTotal() {
      int total = 0;

      for (FarmPlantingRequest request : this.requests) {
         total = Math.addExact(total, request.count());
      }

      return total;
   }

   private String currentDimension() {
      return this.controller.getWorld().dimension().location().toString();
   }

   private static PlantFarmReason mapMutationFailure(FarmTaskReason reason, boolean issuedMutation) {
      if (reason == FarmTaskReason.CHUNK_UNLOADED) {
         return PlantFarmReason.CHUNK_UNLOADED;
      } else if (reason == FarmTaskReason.DIMENSION_CHANGED) {
         return PlantFarmReason.DIMENSION_CHANGED;
      } else if (reason == FarmTaskReason.STORE_UNAVAILABLE) {
         return PlantFarmReason.STORE_UNAVAILABLE;
      } else if (reason == FarmTaskReason.CANCELLED_OPERATOR) {
         return PlantFarmReason.CANCELLED_OPERATOR;
      } else if (issuedMutation && reason == FarmTaskReason.MANIFEST_DRIFT) {
         return PlantFarmReason.RECEIPT_MISMATCH;
      } else {
         return reason == FarmTaskReason.INTERACTION_DENIED ? PlantFarmReason.INTERACTION_DENIED : PlantFarmReason.FARM_NEEDS_REPAIR;
      }
   }

   private static PlantFarmReason observationFailure(FarmObservationStatus status) {
      return switch (status) {
         case OBSERVED -> PlantFarmReason.NONE;
         case UNLOADED -> PlantFarmReason.CHUNK_UNLOADED;
         case STALE_CENTER -> PlantFarmReason.STALE_FARM;
         case UNSUPPORTED_DATA -> PlantFarmReason.UNSUPPORTED_FARM_DATA;
         case HANDLER_UNAVAILABLE -> PlantFarmReason.STORE_UNAVAILABLE;
      };
   }

   private static PlantFarmReason searchFailure(WaypointSearchStatus status) {
      return switch (status) {
         case INDEXED, AUTHORITATIVE_SPATIAL, FULL_STORE_FALLBACK -> PlantFarmReason.NONE;
         case FAILED_STORE_UNAVAILABLE -> PlantFarmReason.STORE_UNAVAILABLE;
         case FAILED_SCAN_LIMIT -> PlantFarmReason.SEARCH_SCAN_LIMIT;
         case FAILED_SEARCH_ERROR -> PlantFarmReason.INTERNAL_CONTRACT_FAILURE;
      };
   }

   private static PlantFarmReason refreshFailure(WaypointMutationStatus status) {
      return switch (status) {
         case REJECTED_TYPE_CONFLICT, REJECTED_TARGET_CONFLICT -> PlantFarmReason.TYPE_CONFLICT;
         case FAILED_STORE_UNAVAILABLE -> PlantFarmReason.STORE_UNAVAILABLE;
         case FAILED_JSON_COMMIT -> PlantFarmReason.WAYPOINT_JSON_FAILURE;
         default -> PlantFarmReason.INTERNAL_CONTRACT_FAILURE;
      };
   }

   private static boolean mutationCommitted(WaypointMutationStatus status) {
      return status == WaypointMutationStatus.COMMITTED
         || status == WaypointMutationStatus.COMMITTED_INDEX_DEGRADED
         || status == WaypointMutationStatus.NO_CHANGE
         || status == WaypointMutationStatus.NO_CHANGE_INDEX_DEGRADED;
   }

   private static FarmPlantingRestrictionUpdate restrictionUpdate(FarmPlantingPolicy policy) {
      return switch (policy.kind()) {
         case PRESERVE -> FarmPlantingRestrictionUpdate.preserve();
         case CLEAR -> FarmPlantingRestrictionUpdate.clear();
         case SET -> FarmPlantingRestrictionUpdate.set(policy.plantingItemId());
      };
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

   public PlantFarmOutcome outcome() {
      return this.terminalOutcome != null
         ? this.terminalOutcome
         : new PlantFarmOutcome(
            false,
            false,
            PlantFarmReason.NONE,
            this.phase,
            this.commandDimension,
            this.center,
            this.requests,
            this.plantedSnapshot(),
            this.openSlotsAfter,
            this.waypointResult
         );
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof PlantFarmTask task
         && task.commandAnchor.equals(this.commandAnchor)
         && task.commandDimension.equals(this.commandDimension)
         && task.requests.equals(this.requests)
         && Objects.equals(task.exactCenter, this.exactCenter)
         && task.policy.equals(this.policy);
   }

   @Override
   protected String toDebugString() {
      return this.exactCenter == null ? "Plant ordered crops at nearest compatible farm" : "Plant ordered crops at farm " + this.exactCenter.toShortString();
   }

   enum FailureTransition {
      RESCAN,
      TERMINAL;
   }
}
