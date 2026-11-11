package com.player2.playerengine.tasks.farming;

import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.agentic.AgenticRunRegistry;
import com.player2.playerengine.agentic.elliegps.EllieGPSStore;
import com.player2.playerengine.agentic.elliegps.FarmObservationResult;
import com.player2.playerengine.agentic.elliegps.FarmObservationStatus;
import com.player2.playerengine.agentic.elliegps.FarmWaypointData;
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
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.agentic.FarmHoeAcquisitionTask;
import com.player2.playerengine.tasks.agentic.FarmRepairToolAcquisitionTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.base.TaskSuspensionCause;
import com.player2.playerengine.tasks.base.TrackedTaskOutcomeProvider;
import com.player2.playerengine.tasks.base.TransientlyResumableTask;
import com.player2.playerengine.tasks.movement.GetToBlockTask;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;

public final class SetupFarmTask extends Task implements TrackedTaskOutcomeProvider, TransientlyResumableTask {
   private final BlockPos commandAnchor;
   private final String commandDimension;
   private final BlockPos exactCenter;
   private final AgenticRunRegistry.AgenticRunState runState;
   private FarmTaskWatchdog watchdog = new FarmTaskWatchdog();
   private FarmTaskPhase phase = FarmTaskPhase.PRECHECK;
   private FarmSiteWorldView worldView;
   private FarmSitePlanner sitePlanner;
   private FarmSitePlan sitePlan;
   private SetupFarmTask.ResourceStage resourceStage;
   private SetupFarmTask.ResourcePurpose resourcePurpose;
   private Task resourceChild;
   private Task mutationChild;
   private Task pendingMutationReceipt;
   private int pendingMutationReceiptTicks;
   private boolean acquisitionProtectionPushed;
   private boolean terminal;
   private boolean successful;
   private FarmTaskReason failureReason = FarmTaskReason.NONE;
   private FarmTaskOutcome terminalOutcome;
   private WaypointMutationResult waypointResult;
   private FarmWaypointObservation verifiedObservation;
   private boolean commitAttempted;
   private boolean preparedCommitAttempted;
   private SetupFarmTask.PreparedFarmHandle preparedFarmHandle;
   private boolean sourcePlaced;
   private boolean resumeTillAfterResources;
   private int resourceTicks;
   private int clearIndex;
   private int fillIndex;
   private int tillIndex;
   private int cleared;
   private int filled;
   private int tilled;
   private int clearTotal;
   private int fillTotal;
   private int tillTotal;
   private FarmPlotRepairPlan repairPlan;
   private int repairBreakIndex;
   private int repairFillIndex;
   private int repairMutationPasses;
   private int repairResourceTrips;
   private int requiredRepairDirt;
   private FarmRepairToolPlan requiredRepairTools;
   private List<BlockPos> activeTillTargets = List.of();
   private boolean tillPlanReady;
   private boolean resumeStartPending;
   private BlockPos resumeCenter;
   private SetupFarmTask.ResourceReturnMode resourceReturnMode = SetupFarmTask.ResourceReturnMode.NONE;
   private BlockPos resourceReturnStance;
   private List<BlockPos> resourceReturnCandidates = List.of();
   private int resourceReturnCandidateIndex;
   private int resourceReturnCandidateTicks;
   private Task resourceReturnChild;
   private int resourceReturnTicks;
   private FarmTaskReason pendingResourceFailure = FarmTaskReason.NONE;
   private FarmReturnStatus resourceReturnStatus = FarmReturnStatus.NOT_REQUIRED;
   private FarmHoeAcquisitionTask.Checkpoint initialHoeCheckpoint = new FarmHoeAcquisitionTask.Checkpoint();
   private FarmHoeAcquisitionTask.Checkpoint replacementHoeCheckpoint = new FarmHoeAcquisitionTask.Checkpoint();
   private FarmRepairToolAcquisitionTask.Checkpoint repairToolCheckpoint = new FarmRepairToolAcquisitionTask.Checkpoint();

   public SetupFarmTask(BlockPos commandAnchor, String commandDimension) {
      this(commandAnchor, commandDimension, null, null);
   }

   public SetupFarmTask(BlockPos commandAnchor, String commandDimension, BlockPos exactCenter) {
      this(commandAnchor, commandDimension, exactCenter, null);
   }

   public SetupFarmTask(BlockPos commandAnchor, String commandDimension, BlockPos exactCenter, AgenticRunRegistry.AgenticRunState runState) {
      this.commandAnchor = Objects.requireNonNull(commandAnchor, "commandAnchor").immutable();
      String checkedDimension = Objects.requireNonNull(commandDimension, "commandDimension").strip();
      if (checkedDimension.isEmpty()) {
         throw new IllegalArgumentException("commandDimension cannot be blank");
      }

      this.commandDimension = checkedDimension;
      this.exactCenter = exactCenter == null ? null : exactCenter.immutable();
      this.runState = runState;
   }

   @Override
   protected void onStart() {
      boolean resuming = this.resumeStartPending;
      FarmTaskPhase interruptedPhase = this.phase;
      boolean resumingReturn = resuming && this.resourceReturnMode != SetupFarmTask.ResourceReturnMode.NONE && this.sitePlan != null;
      boolean resumingPreparedResources = resuming
         && !resumingReturn
         && this.preparedFarmHandle != null
         && this.sitePlan != null
         && interruptedPhase == FarmTaskPhase.ACQUIRE_RESOURCES
         && this.resourceStage != null
         && this.resourcePurpose != null;
      boolean resumingPreparedCheckpoint = resuming
         && !resumingReturn
         && !resumingPreparedResources
         && this.preparedFarmHandle != null
         && this.sitePlan != null;
      boolean restoringPreparedState = resumingReturn || resumingPreparedResources || resumingPreparedCheckpoint;
      this.resumeStartPending = false;
      if (!resuming) {
         this.watchdog = new FarmTaskWatchdog();
         this.resumeCenter = null;
         this.sourcePlaced = false;
         this.cleared = 0;
         this.filled = 0;
         this.tilled = 0;
         this.clearTotal = 0;
         this.fillTotal = 0;
         this.tillTotal = 0;
         this.resourceTicks = 0;
         this.repairMutationPasses = 0;
         this.repairResourceTrips = 0;
         this.requiredRepairDirt = 0;
         this.requiredRepairTools = null;
         this.preparedFarmHandle = null;
         this.waypointResult = null;
         this.pendingMutationReceipt = null;
         this.pendingMutationReceiptTicks = 0;
         this.resourceReturnMode = SetupFarmTask.ResourceReturnMode.NONE;
         this.resourceReturnStance = null;
         this.resourceReturnChild = null;
         this.resourceReturnTicks = 0;
         this.pendingResourceFailure = FarmTaskReason.NONE;
         this.resourceReturnStatus = FarmReturnStatus.NOT_REQUIRED;
         this.resourceReturnCandidates = List.of();
         this.resourceReturnCandidateIndex = 0;
         this.resourceReturnCandidateTicks = 0;
         this.initialHoeCheckpoint = new FarmHoeAcquisitionTask.Checkpoint();
         this.replacementHoeCheckpoint = new FarmHoeAcquisitionTask.Checkpoint();
         this.repairToolCheckpoint = new FarmRepairToolAcquisitionTask.Checkpoint();
      }

      this.phase = restoringPreparedState ? FarmTaskPhase.ACQUIRE_RESOURCES : FarmTaskPhase.PRECHECK;
      this.terminal = false;
      this.successful = false;
      this.failureReason = FarmTaskReason.NONE;
      this.terminalOutcome = null;
      this.verifiedObservation = null;
      this.commitAttempted = false;
      this.preparedCommitAttempted = false;
      if (!resuming) {
         this.resumeTillAfterResources = false;
      }

      this.clearIndex = 0;
      this.fillIndex = 0;
      this.tillIndex = 0;
      this.repairPlan = null;
      this.repairBreakIndex = 0;
      this.repairFillIndex = 0;
      this.activeTillTargets = List.of();
      this.tillPlanReady = false;
      this.worldView = null;
      if (restoringPreparedState) {
         try {
            this.worldView = FarmSiteWorldView.live(this.controller.getWorld());
         } catch (RuntimeException unavailableWorldView) {
            if (this.pendingResourceFailure == FarmTaskReason.NONE) {
               this.pendingResourceFailure = FarmTaskReason.STORE_UNAVAILABLE;
               this.resourceReturnMode = SetupFarmTask.ResourceReturnMode.FINALIZE_FAILURE;
            }
         }
      }

      this.sitePlanner = null;
      if (!restoringPreparedState) {
         this.sitePlan = null;
         this.resourceStage = null;
         this.resourcePurpose = null;
      }

      this.resourceChild = null;
      this.mutationChild = null;
      this.acquisitionProtectionPushed = false;
      this.resourceReturnChild = null;
      if (restoringPreparedState) {
         this.protectSiteForAcquisition();
      }

      if (resumingPreparedCheckpoint) {
         this.resourcePurpose = SetupFarmTask.ResourcePurpose.COMPLETE_SETUP;
         this.beginResourceReturn(SetupFarmTask.ResourceReturnMode.CONTINUE_SETUP, FarmTaskReason.NONE);
      }

      if (resuming) {
         this.watchdog.restartFromCheckpoint();
      } else {
         this.watchdog.reset();
      }

      this.updateProgress();
   }

   @Override
   protected Task onTick() {
      if (this.terminal) {
         return null;
      }

      this.watchdog.tick();
      if (this.pendingMutationReceipt != null && !this.settleRetainedMutationReceiptOnResume()) {
         this.updateProgress();
         return null;
      }

      if (this.resourceReturnMode != SetupFarmTask.ResourceReturnMode.NONE) {
         if (!Objects.equals(this.commandDimension, this.currentDimension())) {
            this.resourceReturnStatus = FarmReturnStatus.UNAVAILABLE;
            FarmTaskReason reason = this.pendingResourceFailure == FarmTaskReason.NONE ? FarmTaskReason.DIMENSION_CHANGED : this.pendingResourceFailure;
            this.finalizeFailure(reason);
            this.updateProgress();
            return null;
         } else {
            Task next = this.tickResourceReturn();
            this.updateProgress();
            return next;
         }
      } else {
         if (!Objects.equals(this.commandDimension, this.currentDimension())) {
            this.fail(FarmTaskReason.DIMENSION_CHANGED);
            return null;
         }

         if (this.phase != FarmTaskPhase.PRECHECK && !this.controller.getModSettings().getEllieGpsEnabled()) {
            this.fail(FarmTaskReason.ELLIEGPS_DISABLED);
            return null;
         }

         if (this.phase == FarmTaskPhase.PRECHECK || EllieGPSStore.get() != null && PlayerPlacedBlockStore.get() != null) {
            if (this.preparedFarmHandle != null && requiresPreparedWaypoint(this.phase) && this.resolvePreparedFarmWaypoint() == null) {
               this.fail(FarmTaskReason.FARM_NOT_FOUND);
               return null;
            }

            if (this.watchdog.wholeSetupExpired()) {
               this.fail(FarmTaskReason.SETUP_TIMEOUT);
               return null;
            }

            Task next = switch (this.phase) {
               case PRECHECK -> this.tickPrecheck();
               case SELECT_SITE -> this.tickSiteSelection();
               case ACQUIRE_RESOURCES -> this.tickResources();
               case REVALIDATE -> this.tickRevalidation();
               case CLEAR -> this.tickClear(false);
               case FILL -> this.tickFill();
               case OPEN_CENTER -> this.tickClear(true);
               case REGISTER_PREPARED -> this.tickRegisterPrepared();
               case REPAIR -> this.tickRepair();
               case PLACE_WATER -> this.tickPlaceWater();
               case TILL -> this.tickTill();
               case VERIFY -> this.tickVerify();
               case COMMIT -> this.tickCommit();
               case DONE, FAILED, HARVEST_RESOLVE, HARVEST_SCAN, HARVEST_BREAK, HARVEST_PICKUP, HARVEST_RESCAN -> {
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

   private Task tickPrecheck() {
      if (!this.controller.getModSettings().getEllieGpsEnabled()) {
         this.fail(FarmTaskReason.ELLIEGPS_DISABLED);
         return null;
      }

      if (EllieGPSStore.get() != null && PlayerPlacedBlockStore.get() != null) {
         try {
            FarmTaskReason environmentFailure = waterEnvironmentFailure(FarmSiteWorldView.live(this.controller.getWorld()).observeWorld(this.commandAnchor));
            if (environmentFailure != FarmTaskReason.NONE) {
               this.fail(environmentFailure);
               return null;
            }
         } catch (RuntimeException unavailable) {
            this.fail(FarmTaskReason.STORE_UNAVAILABLE);
            return null;
         }

         if (this.exactCenter != null && this.resumeCenter == null) {
            return this.beginExactCenter();
         }

         if (shouldResolveRecordedResume(this.exactCenter, this.resumeCenter)) {
            WaypointSearchResult search = WaypointSearchService.find(
               "", this.commandDimension, Set.of("farm"), true, WaypointSearchOrder.NEAREST, this.commandAnchor, 4096
            );
            FarmTaskReason searchFailure = recordedResumeSearchFailure(search.status());
            if (searchFailure != FarmTaskReason.NONE) {
               this.fail(searchFailure);
               return null;
            }

            SetupFarmTask.RecordedResumeResolution recorded;
            try {
               recorded = chooseRecordedResume(search.records(), this.commandDimension, this.commandAnchor, 48, this::farmEnvelopeLoaded);
            } catch (RuntimeException invalidRecord) {
               this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
               return null;
            }

            if (recorded.status() == SetupFarmTask.RecordedResumeStatus.UNLOADED) {
               this.fail(FarmTaskReason.CHUNK_UNLOADED);
               return null;
            }

            if (recorded.status() == SetupFarmTask.RecordedResumeStatus.SELECTED) {
               return this.beginRecordedResume(recorded);
            }
         }

         BlockPos planningCenter = this.resumeCenter == null ? this.exactCenter : this.resumeCenter;
         if (planningCenter != null && !this.farmEnvelopeLoaded(planningCenter)) {
            this.fail(FarmTaskReason.CHUNK_UNLOADED);
            return null;
         }

         try {
            this.worldView = FarmSiteWorldView.live(this.controller.getWorld());
            if (this.resumeCenter != null) {
               this.sitePlanner = FarmSitePlanner.resume(this.worldView, this.commandAnchor, this.resumeCenter);
            } else if (planningCenter == null) {
               this.sitePlanner = FarmSitePlanner.auto(this.worldView, this.resolveAutoPlanningAnchor());
            } else {
               this.sitePlanner = FarmSitePlanner.explicit(this.worldView, this.commandAnchor, planningCenter);
            }
         } catch (RuntimeException viewFailure) {
            this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
            return null;
         }

         this.enterPhase(FarmTaskPhase.SELECT_SITE);
         return null;
      } else {
         this.fail(FarmTaskReason.STORE_UNAVAILABLE);
         return null;
      }
   }

   private Task beginExactCenter() {
      WaypointRecord positionOccupant;
      WaypointRecord canonicalIdOccupant;
      try {
         positionOccupant = EllieGPSStore.get().byPosition(this.commandDimension, this.exactCenter);
         canonicalIdOccupant = EllieGPSStore.get().byId(WaypointRecord.idFor(this.commandDimension, this.exactCenter));
      } catch (RuntimeException lookupFailure) {
         this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
         return null;
      }

      SetupFarmTask.ExactCenterResolution resolution = resolveExactCenter(
         positionOccupant, canonicalIdOccupant, this.commandDimension, this.exactCenter, this.farmEnvelopeLoaded(this.exactCenter)
      );

      return switch (resolution.status()) {
         case NEW_FORCED -> this.beginForcedRepair(this.exactCenter);
         case RECORDED -> this.beginRecordedResume(resolution.recorded(), true);
         case UNLOADED -> {
            this.fail(FarmTaskReason.CHUNK_UNLOADED);
            yield null;
         }
         case TYPE_CONFLICT -> {
            this.fail(FarmTaskReason.TYPE_CONFLICT);
            yield null;
         }
         case UNSUPPORTED_DATA -> {
            this.fail(FarmTaskReason.UNSUPPORTED_DATA);
            yield null;
         }
      };
   }

   static SetupFarmTask.ExactCenterResolution resolveExactCenter(WaypointRecord occupant, String dimension, BlockPos center, boolean envelopeLoaded) {
      return resolveExactCenter(occupant, occupant, dimension, center, envelopeLoaded);
   }

   static SetupFarmTask.ExactCenterResolution resolveExactCenter(
      WaypointRecord positionOccupant, WaypointRecord canonicalIdOccupant, String dimension, BlockPos center, boolean envelopeLoaded
   ) {
      String requiredDimension = Objects.requireNonNull(dimension, "dimension").strip();
      BlockPos requiredCenter = Objects.requireNonNull(center, "center").immutable();
      if (requiredDimension.isEmpty()) {
         throw new IllegalArgumentException("dimension cannot be blank");
      }

      if ((positionOccupant == null || "farm".equals(positionOccupant.type)) && (canonicalIdOccupant == null || "farm".equals(canonicalIdOccupant.type))) {
         if (positionOccupant == null && canonicalIdOccupant == null) {
            return SetupFarmTask.ExactCenterResolution.status(
               envelopeLoaded ? SetupFarmTask.ExactCenterStatus.NEW_FORCED : SetupFarmTask.ExactCenterStatus.UNLOADED
            );
         }

         if (positionOccupant != null && canonicalIdOccupant != null && sameWaypointIdentity(canonicalIdOccupant, positionOccupant)) {
            WaypointRecord occupant = canonicalIdOccupant;
            if (!"farm".equals(occupant.type)) {
               return SetupFarmTask.ExactCenterResolution.status(SetupFarmTask.ExactCenterStatus.TYPE_CONFLICT);
            }

            FarmWaypointData data;
            BlockPos storedCenter;
            try {
               data = occupant.farmData();
               storedCenter = occupant.canonicalBlockPos();
            } catch (RuntimeException malformed) {
               return SetupFarmTask.ExactCenterResolution.status(SetupFarmTask.ExactCenterStatus.UNSUPPORTED_DATA);
            }

            if (occupant.schemaVersion == 1
               && occupant.pos != null
               && occupant.pos.length == 3
               && requiredDimension.equals(occupant.dimension)
               && requiredCenter.equals(storedCenter)
               && occupant.id != null
               && occupant.id.equals(WaypointRecord.idFor(requiredDimension, requiredCenter))
               && data != null
               && data.isSupportedVersion()
               && data.radius() == 4) {
               return envelopeLoaded
                  ? SetupFarmTask.ExactCenterResolution.recorded(occupant, requiredCenter)
                  : SetupFarmTask.ExactCenterResolution.status(SetupFarmTask.ExactCenterStatus.UNLOADED);
            } else {
               return SetupFarmTask.ExactCenterResolution.status(SetupFarmTask.ExactCenterStatus.UNSUPPORTED_DATA);
            }
         } else {
            return SetupFarmTask.ExactCenterResolution.status(SetupFarmTask.ExactCenterStatus.UNSUPPORTED_DATA);
         }
      } else {
         return SetupFarmTask.ExactCenterResolution.status(SetupFarmTask.ExactCenterStatus.TYPE_CONFLICT);
      }
   }

   static boolean shouldResolveRecordedResume(BlockPos explicitCenter, BlockPos transientCenter) {
      return explicitCenter == null && transientCenter == null;
   }

   static FarmTaskReason recordedResumeSearchFailure(WaypointSearchStatus status) {
      return switch ((WaypointSearchStatus)Objects.requireNonNull(status, "status")) {
         case INDEXED, AUTHORITATIVE_SPATIAL, FULL_STORE_FALLBACK -> FarmTaskReason.NONE;
         case FAILED_STORE_UNAVAILABLE -> FarmTaskReason.STORE_UNAVAILABLE;
         case FAILED_SCAN_LIMIT -> FarmTaskReason.SEARCH_SCAN_LIMIT;
         case FAILED_SEARCH_ERROR -> FarmTaskReason.INTERNAL_CONTRACT_FAILURE;
      };
   }

   static SetupFarmTask.RecordedResumeResolution chooseRecordedResume(
      List<WaypointRecord> records, String dimension, BlockPos anchor, int searchRadius, Predicate<BlockPos> footprintLoaded
   ) {
      Objects.requireNonNull(records, "records");
      String requiredDimension = Objects.requireNonNull(dimension, "dimension").strip();
      if (requiredDimension.isEmpty()) {
         throw new IllegalArgumentException("dimension cannot be blank");
      }

      Objects.requireNonNull(anchor, "anchor");
      Objects.requireNonNull(footprintLoaded, "footprintLoaded");
      if (searchRadius < 0) {
         throw new IllegalArgumentException("searchRadius cannot be negative");
      }

      long maximumDistanceSquared = (long)searchRadius * searchRadius;
      ArrayList<SetupFarmTask.RecordedFarmCandidate> candidates = new ArrayList<>();

      for (WaypointRecord record : records) {
         if (record != null
            && record.stale
            && record.schemaVersion == 1
            && record.id != null
            && !record.id.isBlank()
            && "farm".equals(record.type)
            && requiredDimension.equals(record.dimension)
            && record.pos != null
            && record.pos.length == 3) {
            FarmWaypointData data;
            BlockPos center;
            try {
               data = record.farmData();
               center = record.canonicalBlockPos();
            } catch (RuntimeException malformed) {
               continue;
            }

            if (data != null
               && data.isSupportedVersion()
               && data.radius() == 4
               && center != null
               && record.id.equals(WaypointRecord.idFor(requiredDimension, center))) {
               long horizontalDistanceSquared = recordedFarmHorizontalDistanceSquared(anchor, center);
               if (horizontalDistanceSquared <= maximumDistanceSquared) {
                  candidates.add(new SetupFarmTask.RecordedFarmCandidate(record, center, recordedFarmDistanceSquared(anchor, center)));
               }
            }
         }
      }

      candidates.sort(Comparator.comparingLong(SetupFarmTask.RecordedFarmCandidate::spatialDistanceSquared).thenComparing(candidatex -> candidatex.record().id));

      for (SetupFarmTask.RecordedFarmCandidate candidate : candidates) {
         if (footprintLoaded.test(candidate.center())) {
            return SetupFarmTask.RecordedResumeResolution.selected(candidate.record(), candidate.center());
         }
      }

      return candidates.isEmpty() ? SetupFarmTask.RecordedResumeResolution.none() : SetupFarmTask.RecordedResumeResolution.unloaded();
   }

   static long recordedFarmDistanceSquared(BlockPos anchor, BlockPos center) {
      Objects.requireNonNull(anchor, "anchor");
      Objects.requireNonNull(center, "center");
      long horizontal = recordedFarmHorizontalDistanceSquared(anchor, center);
      if (horizontal == Long.MAX_VALUE) {
         return Long.MAX_VALUE;
      }

      long dy = (long)center.getY() - anchor.getY();
      long vertical = saturatedSquare(dy);
      return Long.MAX_VALUE - horizontal < vertical ? Long.MAX_VALUE : horizontal + vertical;
   }

   private static long recordedFarmHorizontalDistanceSquared(BlockPos anchor, BlockPos center) {
      long dx = (long)center.getX() - anchor.getX();
      long dz = (long)center.getZ() - anchor.getZ();
      return saturatedAdd(saturatedSquare(dx), saturatedSquare(dz));
   }

   private static long saturatedSquare(long value) {
      return value <= 3037000499L && value >= -3037000499L ? value * value : Long.MAX_VALUE;
   }

   private static long saturatedAdd(long left, long right) {
      return left != Long.MAX_VALUE && right != Long.MAX_VALUE && Long.MAX_VALUE - left >= right ? left + right : Long.MAX_VALUE;
   }

   private Task beginRecordedResume(SetupFarmTask.RecordedResumeResolution recorded) {
      return this.beginRecordedResume(recorded, false);
   }

   private Task beginRecordedResume(SetupFarmTask.RecordedResumeResolution recorded, boolean refreshPreparedRecord) {
      BlockPos center = recorded.center();
      this.preparedFarmHandle = new SetupFarmTask.PreparedFarmHandle(recorded.id(), recorded.dimension(), center);
      if (this.resolvePreparedFarmWaypointAtExactPosition() == null) {
         this.fail(FarmTaskReason.FARM_NOT_FOUND);
         return null;
      }

      FarmSiteWorldView liveView;
      FarmSitePlan continuationPlan;
      try {
         liveView = FarmSiteWorldView.live(this.controller.getWorld());
         FarmTaskReason worldFailure = recordedResumeSafetyFailure(liveView.observeWorld(center), false);
         if (worldFailure != FarmTaskReason.NONE) {
            this.fail(worldFailure);
            return null;
         }

         FarmTaskReason orphanFailure = this.retireOrphanedFarmProtection(center);
         if (orphanFailure != FarmTaskReason.NONE) {
            this.fail(orphanFailure);
            return null;
         }

         FarmPlotRepairPlan.ScanResult currentShape = FarmPlotRepairPlan.scan(liveView, center);
         if (!currentShape.ready()) {
            this.fail(currentShape.reason() == FarmPlotRepairPlan.UnsafeReason.UNREADABLE ? FarmTaskReason.CHUNK_UNLOADED : FarmTaskReason.MANIFEST_DRIFT);
            return null;
         }

         continuationPlan = this.buildRecordedResumeContext(liveView, center, currentShape.plan());
         FarmTaskReason safetyFailure = recordedResumeSafetyFailure(
            continuationPlan.snapshot().worldObservation(), FarmFreezePolicy.wouldFreeze(continuationPlan.snapshot())
         );
         if (safetyFailure != FarmTaskReason.NONE) {
            this.fail(safetyFailure);
            return null;
         }
      } catch (RuntimeException unavailable) {
         this.fail(FarmTaskReason.MANIFEST_DRIFT);
         return null;
      }

      if (refreshPreparedRecord) {
         FarmTaskReason registrationFailure = this.registerPreparedCheckpoint(center);
         if (registrationFailure != FarmTaskReason.NONE) {
            this.fail(registrationFailure);
            return null;
         }
      }

      return this.enterRepairContext(center, liveView, continuationPlan);
   }

   private Task beginForcedRepair(BlockPos center) {
      FarmSiteWorldView liveView;
      FarmSitePlan continuationPlan;
      try {
         liveView = FarmSiteWorldView.live(this.controller.getWorld());
         FarmTaskReason worldFailure = recordedResumeSafetyFailure(liveView.observeWorld(center), false);
         if (worldFailure != FarmTaskReason.NONE) {
            this.fail(worldFailure);
            return null;
         }

         FarmPlotRepairPlan.ScanResult currentShape = FarmPlotRepairPlan.scan(liveView, center);
         if (!currentShape.ready()) {
            this.fail(exactRepairFailureReason(currentShape.reason()));
            return null;
         }

         continuationPlan = this.buildRecordedResumeContext(liveView, center, currentShape.plan());
         FarmTaskReason safetyFailure = recordedResumeSafetyFailure(
            continuationPlan.snapshot().worldObservation(), FarmFreezePolicy.wouldFreeze(continuationPlan.snapshot())
         );
         if (safetyFailure != FarmTaskReason.NONE) {
            this.fail(exactWorldSafetyFailure(safetyFailure));
            return null;
         }
      } catch (RuntimeException unavailable) {
         this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
         return null;
      }

      FarmTaskReason registrationFailure = this.registerPreparedCheckpoint(center);
      if (registrationFailure != FarmTaskReason.NONE) {
         this.fail(registrationFailure);
         return null;
      } else {
         FarmTaskReason orphanFailure = this.retireOrphanedFarmProtection(center);
         if (orphanFailure != FarmTaskReason.NONE) {
            this.fail(orphanFailure);
            return null;
         } else {
            return this.enterRepairContext(center, liveView, continuationPlan);
         }
      }
   }

   static FarmTaskReason exactRepairFailureReason(FarmPlotRepairPlan.UnsafeReason unsafeReason) {
      if (unsafeReason == null) {
         return FarmTaskReason.INTERNAL_CONTRACT_FAILURE;
      }

      return switch (unsafeReason) {
         case UNREADABLE -> FarmTaskReason.CHUNK_UNLOADED;
         case UNSAFE_SUPPORT -> FarmTaskReason.EXACT_SITE_UNSAFE_SUPPORT;
         case UNSAFE_OBSTRUCTION -> FarmTaskReason.EXACT_SITE_UNSAFE_OBSTRUCTION;
         case NON_CENTER_FLUID -> FarmTaskReason.EXACT_SITE_FLUID_CONFLICT;
         case CROP_CONFLICT -> FarmTaskReason.EXACT_SITE_CROP_CONFLICT;
         case REPAIR_LIMIT_EXCEEDED -> FarmTaskReason.EXACT_SITE_REPAIR_LIMIT_EXCEEDED;
         case NO_RETURN_STANCE, NO_ACTION_STANCE -> FarmTaskReason.EXACT_SITE_NO_SAFE_STANCE;
         case NONE -> FarmTaskReason.INTERNAL_CONTRACT_FAILURE;
      };
   }

   private Task enterRepairContext(BlockPos center, FarmSiteWorldView liveView, FarmSitePlan continuationPlan) {
      this.worldView = liveView;
      this.resumeCenter = center.immutable();
      this.sitePlan = continuationPlan;
      this.sitePlanner = null;
      this.repairPlan = null;
      this.repairBreakIndex = 0;
      this.repairFillIndex = 0;
      this.activeTillTargets = List.of();
      this.tillPlanReady = false;
      this.clearTotal = this.cleared;
      this.fillTotal = this.filled;
      this.tillTotal = this.tilled;
      this.watchdog.markProgress();
      this.enterPhase(FarmTaskPhase.REPAIR);
      return null;
   }

   static FarmTaskReason recordedResumeSafetyFailure(FarmSiteWorldView.WorldObservation world, boolean freezeRisk) {
      Objects.requireNonNull(world, "world");
      if (!world.protectionStoreAvailable() || !world.waypointStoreAvailable()) {
         return FarmTaskReason.STORE_UNAVAILABLE;
      } else if (world.waypointCollision()) {
         return FarmTaskReason.TYPE_CONFLICT;
      } else {
         FarmTaskReason environmentFailure = waterEnvironmentFailure(world);
         if (environmentFailure != FarmTaskReason.NONE) {
            return environmentFailure;
         } else {
            return freezeRisk ? FarmTaskReason.FREEZE_RISK : FarmTaskReason.NONE;
         }
      }
   }

   static FarmTaskReason waterEnvironmentFailure(FarmSiteWorldView.WorldObservation world) {
      Objects.requireNonNull(world, "world");
      return world.ultraWarm() ? FarmTaskReason.WATER_EVAPORATES : FarmTaskReason.NONE;
   }

   static FarmTaskReason exactWorldSafetyFailure(FarmTaskReason reason) {
      return reason == FarmTaskReason.FREEZE_RISK ? FarmTaskReason.EXACT_SITE_FREEZE_RISK : Objects.requireNonNull(reason, "reason");
   }

   static boolean shouldRetireOrphanedFarmProtection(boolean protectedPosition, boolean airBlock) {
      return protectedPosition && airBlock;
   }

   private FarmTaskReason retireOrphanedFarmProtection(BlockPos center) {
      PlayerPlacedBlockStore store = PlayerPlacedBlockStore.get();
      if (store == null) {
         return FarmTaskReason.STORE_UNAVAILABLE;
      }

      for (BlockPos position : FarmPlotRepairPlan.scanEnvelope(center)) {
         if (!this.controller.getChunkTracker().isChunkLoaded(position)) {
            return FarmTaskReason.CHUNK_UNLOADED;
         }

         boolean protectedPosition = store.contains(this.commandDimension, position);
         if (shouldRetireOrphanedFarmProtection(protectedPosition, protectedPosition && this.controller.getWorld().getBlockState(position).isAir())) {
            store.remove(this.commandDimension, position);
         }
      }

      return FarmTaskReason.NONE;
   }

   private FarmSitePlan buildRecordedResumeContext(FarmSiteWorldView liveView, BlockPos center, FarmPlotRepairPlan currentShape) {
      List<BlockPos> returnStances = currentShape.returnStanceCandidates();
      if (returnStances.isEmpty()) {
         throw new IllegalStateException("recorded farm has no safe return stance");
      }

      BlockPos returnStance = returnStances.get(0);
      LinkedHashMap<BlockPos, FarmSiteWorldView.CellObservation> observations = new LinkedHashMap<>();

      for (BlockPos position : FarmPlotGeometry.scanEnvelope(center)) {
         observations.put(position, liveView.observe(position));
      }

      FarmSiteSnapshot snapshot = new FarmSiteSnapshot(center, true, liveView.observeWorld(center), observations);
      boolean waterRequired = !currentShape.centerHasWaterSource();
      FarmSitePlan.WaterAction waterAction = waterRequired
         ? new FarmSitePlan.WaterAction(center, returnStance, center.below(), stateFingerprint(this.controller.getWorld().getBlockState(center)))
         : null;
      return new FarmSitePlan(
         this.commandAnchor,
         center,
         true,
         waterRequired,
         0,
         0,
         currentShape.repairColumns(),
         List.of(),
         List.of(),
         List.of(),
         waterAction,
         returnStance,
         snapshot
      );
   }

   private boolean farmEnvelopeLoaded(BlockPos center) {
      for (BlockPos position : FarmPlotGeometry.scanEnvelope(center)) {
         if (!this.controller.getChunkTracker().isChunkLoaded(position)) {
            return false;
         }
      }

      return true;
   }

   private BlockPos resolveAutoPlanningAnchor() {
      ArrayList<BlockPos> surfaceGrass = new ArrayList<>();
      BlockPos surfaceFallback = this.commandAnchor;
      if (this.controller.getChunkTracker().isChunkLoaded(this.commandAnchor)) {
         int freeY = this.controller.getWorld().getHeight(Types.MOTION_BLOCKING_NO_LEAVES, this.commandAnchor.getX(), this.commandAnchor.getZ());
         surfaceFallback = new BlockPos(this.commandAnchor.getX(), freeY - 1, this.commandAnchor.getZ());
      }

      for (BlockPos column : FarmPlotGeometry.autoCandidateCenters(this.commandAnchor)) {
         if (this.controller.getChunkTracker().isChunkLoaded(column)) {
            int freeY = this.controller.getWorld().getHeight(Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
            BlockPos surface = new BlockPos(column.getX(), freeY - 1, column.getZ());
            if (this.controller.getChunkTracker().isChunkLoaded(surface)
               && this.controller.getWorld().getBlockState(surface).is(Blocks.GRASS_BLOCK)) {
               surfaceGrass.add(surface.immutable());
            }
         }
      }

      return chooseSurfaceGrassAnchor(surfaceFallback, surfaceGrass);
   }

   static BlockPos chooseSurfaceGrassAnchor(BlockPos anchor, List<BlockPos> surfaceGrass) {
      Objects.requireNonNull(anchor, "anchor");
      Objects.requireNonNull(surfaceGrass, "surfaceGrass");
      Map<Integer, List<BlockPos>> byLevel = new HashMap<>();

      for (BlockPos candidate : surfaceGrass) {
         if (candidate != null) {
            byLevel.computeIfAbsent(candidate.getY(), ignored -> new ArrayList<>()).add(candidate);
         }
      }

      Integer selectedY = byLevel.entrySet()
         .stream()
         .min(
            Comparator.<Entry<Integer, List<BlockPos>>>comparingInt(entry -> entry.getValue().size())
               .reversed()
               .thenComparingLong(
                  entry -> entry.getValue()
                     .stream()
                     .mapToLong(candidatex -> FarmPlotGeometry.horizontalDistanceSquared(anchor, candidatex))
                     .min()
                     .orElse(Long.MAX_VALUE)
               )
               .thenComparing(Comparator.<Entry<Integer, List<BlockPos>>>comparingInt(entry -> entry.getKey()).reversed())
         )
         .map(Entry::getKey)
         .orElse(null);
      return selectedY == null ? anchor.immutable() : new BlockPos(anchor.getX(), selectedY, anchor.getZ());
   }

   private Task tickSiteSelection() {
      FarmSitePlanner.TickResult result;
      try {
         result = this.sitePlanner.onTick();
      } catch (RuntimeException plannerFailure) {
         this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
         return null;
      }

      switch (result.status()) {
         case RUNNING:
            if (this.watchdog.phaseExpired(200)) {
               this.fail(FarmTaskReason.NO_SUITABLE_SITE);
            }

            return null;
         case SELECTED:
            this.sitePlan = Objects.requireNonNull(result.selectedPlan(), "selectedPlan");
            if (withinCumulativeMutationBudget(this.cleared, this.sitePlan.clearActions().size(), 1040)
               && withinCumulativeMutationBudget(this.filled, this.sitePlan.fillActions().size(), 64)
               && withinCumulativeMutationBudget(this.tilled, this.sitePlan.tillActions().size(), 320)) {
               this.clearTotal = this.cleared + this.sitePlan.clearActions().size();
               this.fillTotal = this.filled + this.sitePlan.fillActions().size();
               this.tillTotal = this.tilled + this.sitePlan.tillActions().size();
               if (this.resumeTillAfterResources) {
                  this.protectSiteForAcquisition();
                  this.resourcePurpose = SetupFarmTask.ResourcePurpose.REPLACE_HOE;
                  this.resourceStage = SetupFarmTask.ResourceStage.REACQUIRE_HOE;
                  this.enterPhase(FarmTaskPhase.ACQUIRE_RESOURCES);
               } else if (this.count(Items.DIRT) < this.sitePlan.requiredDirt()) {
                  this.protectSiteForAcquisition();
                  this.resourcePurpose = SetupFarmTask.ResourcePurpose.PREPARE_SITE;
                  this.resourceStage = SetupFarmTask.ResourceStage.ENSURE_BUILD_DIRT;
                  this.enterPhase(FarmTaskPhase.ACQUIRE_RESOURCES);
               } else {
                  this.enterPhase(FarmTaskPhase.REVALIDATE);
               }

               return null;
            }

            this.fail(FarmTaskReason.MANIFEST_DRIFT);
            return null;
         case FREEZE_RISK:
            this.fail(FarmTaskReason.FREEZE_RISK);
            break;
         case NO_SUITABLE_SITE:
         case TIMED_OUT:
            this.fail(FarmTaskReason.NO_SUITABLE_SITE);
            break;
         case WORLD_VIEW_FAILED:
            this.fail(FarmTaskReason.STORE_UNAVAILABLE);
      }

      return null;
   }

   private Task tickResources() {
      if (this.resourceStage != null && this.resourcePurpose != null) {
         this.resourceTicks++;
         boolean childStopped = this.resourceChild != null && this.resourceChild.stopped();
         boolean requirementMet = this.currentResourceRequirementMet();
         FarmTaskReason terminalReason = resourceTerminalReason(this.resourceStage, childStopped, requirementMet, this.resourceTicks);
         if (terminalReason != FarmTaskReason.NONE) {
            this.fail(terminalReason);
            return null;
         }

         if (childStopped) {
            this.resourceChild = null;
         }

         if (this.resourceChild == null && this.getSub() != null) {
            return null;
         }

         return switch (this.resourceStage) {
            case ENSURE_BUILD_DIRT, ENSURE_REPAIR_DIRT -> this.ensureDirt();
            case ENSURE_BUCKET -> this.ensureBucket();
            case ACQUIRE_WATER -> this.acquireWater();
            case ENSURE_HOE -> this.ensureHoe(false);
            case ENSURE_REPAIR_TOOLS -> this.ensureRepairTools();
            case REACQUIRE_HOE -> this.ensureHoe(true);
            case COMPLETE -> this.completeResourcePhase();
         };
      } else {
         this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
         return null;
      }
   }

   private Task ensureBucket() {
      if (this.count(Items.WATER_BUCKET) < 1 && this.count(Items.BUCKET) < 1) {
         return this.ensureCatalogueItem(Items.BUCKET, 1, FarmTaskReason.BUCKET_UNAVAILABLE);
      }

      this.resourceChild = null;
      this.resourceStage = nextSatisfiedResourceStage(SetupFarmTask.ResourceStage.ENSURE_BUCKET);
      this.watchdog.markProgress();
      return null;
   }

   private Task acquireWater() {
      if (this.count(Items.WATER_BUCKET) >= 1) {
         this.resourceChild = null;
         this.resourceStage = nextSatisfiedResourceStage(SetupFarmTask.ResourceStage.ACQUIRE_WATER);
         this.watchdog.markProgress();
         return null;
      }

      if (this.resourceChild == null) {
         this.resourceChild = new AcquireWaterBucketTask(this.sitePlan.center(), this.sitePlan.acquisitionReturnStance());
      }

      if (!(this.resourceChild instanceof AcquireWaterBucketTask water && water.isFinished())) {
         return this.resourceChild;
      } else if (!water.isSuccessful()) {
         this.fail(water.reason());
         return null;
      } else {
         this.resourceChild = null;
         this.resourceStage = nextSatisfiedResourceStage(SetupFarmTask.ResourceStage.ACQUIRE_WATER);
         this.watchdog.markProgress();
         return null;
      }
   }

   private Task ensureHoe(boolean replacement) {
      boolean resumedReplacement = !replacement && this.replacementHoeCheckpoint.isBound();
      boolean logicalReplacement = replacement || resumedReplacement;
      FarmHoeAcquisitionTask.Checkpoint checkpoint = logicalReplacement ? this.replacementHoeCheckpoint : this.initialHoeCheckpoint;
      int needed = checkpoint.isBound()
         ? checkpoint.requiredDurability()
         : (logicalReplacement ? Math.max(1, this.remainingTillTargets()) : this.remainingTillTargets());
      if (this.remainingHoeDurability() >= needed) {
         this.resourceChild = null;
         if (logicalReplacement) {
            this.replacementHoeCheckpoint = new FarmHoeAcquisitionTask.Checkpoint();
         } else {
            this.initialHoeCheckpoint = new FarmHoeAcquisitionTask.Checkpoint();
         }

         this.resourceStage = nextSatisfiedResourceStage(replacement ? SetupFarmTask.ResourceStage.REACQUIRE_HOE : SetupFarmTask.ResourceStage.ENSURE_HOE);
         this.watchdog.markProgress();
         return null;
      } else {
         FarmTaskReason unavailable = logicalReplacement ? FarmTaskReason.HOE_REACQUIRE_FAILED : FarmTaskReason.HOE_UNAVAILABLE;
         int checkpointDurability = checkpoint.isBound() ? checkpoint.requiredDurability() : needed;
         if (this.resourceChild == null) {
            try {
               this.resourceChild = new FarmHoeAcquisitionTask(checkpointDurability, this.sitePlan.center(), this.commandDimension, checkpoint);
            } catch (RuntimeException invalidCheckpoint) {
               this.fail(unavailable);
               return null;
            }
         }

         if (this.resourceChild instanceof FarmHoeAcquisitionTask acquisition && acquisition.isFinished()) {
            if (!acquisition.isSuccessful()) {
               this.fail(unavailable);
               return null;
            }

            this.resourceChild = null;
            if (this.remainingHoeDurability() < needed) {
               if (logicalReplacement) {
                  this.replacementHoeCheckpoint = new FarmHoeAcquisitionTask.Checkpoint();
               } else {
                  this.initialHoeCheckpoint = new FarmHoeAcquisitionTask.Checkpoint();
               }

               this.watchdog.markProgress();
               return null;
            } else {
               if (logicalReplacement) {
                  this.replacementHoeCheckpoint = new FarmHoeAcquisitionTask.Checkpoint();
               } else {
                  this.initialHoeCheckpoint = new FarmHoeAcquisitionTask.Checkpoint();
               }

               this.resourceStage = nextSatisfiedResourceStage(
                  logicalReplacement ? SetupFarmTask.ResourceStage.REACQUIRE_HOE : SetupFarmTask.ResourceStage.ENSURE_HOE
               );
               this.watchdog.markProgress();
               return null;
            }
         } else if (this.resourceChild.stopped()) {
            this.fail(unavailable);
            return null;
         } else {
            return this.resourceChild;
         }
      }
   }

   private Task ensureRepairTools() {
      if (this.requiredRepairTools == null) {
         this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
         return null;
      }

      if (this.requiredRepairTools.hasUnsatisfiedCustom(this.controller.getInventory())) {
         this.fail(FarmTaskReason.REPAIR_TOOL_UNAVAILABLE);
         return null;
      }

      if (this.requiredRepairTools.isSatisfied(this.controller.getInventory())) {
         this.resourceChild = null;
         this.repairToolCheckpoint = new FarmRepairToolAcquisitionTask.Checkpoint();
         this.resourceStage = this.count(Items.DIRT) < this.requiredRepairDirt
            ? SetupFarmTask.ResourceStage.ENSURE_REPAIR_DIRT
            : SetupFarmTask.ResourceStage.COMPLETE;
         this.watchdog.markProgress();
         return null;
      }

      if (this.resourceChild == null) {
         try {
            this.resourceChild = new FarmRepairToolAcquisitionTask(
               this.requiredRepairTools, this.sitePlan.center(), this.commandDimension, this.repairToolCheckpoint
            );
         } catch (RuntimeException invalidCheckpoint) {
            this.fail(FarmTaskReason.REPAIR_TOOL_UNAVAILABLE);
            return null;
         }
      }

      if (this.resourceChild instanceof FarmRepairToolAcquisitionTask acquisition && acquisition.isFinished()) {
         if (acquisition.isSuccessful() && this.requiredRepairTools.isSatisfied(this.controller.getInventory())) {
            this.resourceChild = null;
            this.repairToolCheckpoint = new FarmRepairToolAcquisitionTask.Checkpoint();
            this.resourceStage = this.count(Items.DIRT) < this.requiredRepairDirt
               ? SetupFarmTask.ResourceStage.ENSURE_REPAIR_DIRT
               : SetupFarmTask.ResourceStage.COMPLETE;
            this.watchdog.markProgress();
            return null;
         } else {
            this.fail(FarmTaskReason.REPAIR_TOOL_UNAVAILABLE);
            return null;
         }
      } else if (this.resourceChild.stopped()) {
         this.fail(FarmTaskReason.REPAIR_TOOL_UNAVAILABLE);
         return null;
      } else {
         return this.resourceChild;
      }
   }

   private Task ensureDirt() {
      int absoluteDirt = this.resourceStage == SetupFarmTask.ResourceStage.ENSURE_BUILD_DIRT ? this.sitePlan.requiredDirt() : this.requiredRepairDirt;
      if (this.count(Items.DIRT) < absoluteDirt) {
         return this.ensureCatalogueItem(Items.DIRT, absoluteDirt, FarmTaskReason.DIRT_UNAVAILABLE);
      }

      this.resourceChild = null;
      SetupFarmTask.ResourceStage completedStage = this.resourceStage;
      if (completedStage == SetupFarmTask.ResourceStage.ENSURE_REPAIR_DIRT) {
         this.resourceStage = this.requiredRepairTools != null && !this.requiredRepairTools.isSatisfied(this.controller.getInventory())
            ? SetupFarmTask.ResourceStage.ENSURE_REPAIR_TOOLS
            : SetupFarmTask.ResourceStage.COMPLETE;
      } else {
         this.resourceStage = nextSatisfiedResourceStage(completedStage);
      }

      this.watchdog.markProgress();
      return null;
   }

   private Task ensureCatalogueItem(Item item, int absoluteCount, FarmTaskReason unavailable) {
      if (this.count(item) >= absoluteCount) {
         this.resourceChild = null;
         this.watchdog.markProgress();
         return null;
      }

      if (this.resourceChild == null) {
         ResourceTask created;
         try {
            created = TaskCatalogue.getItemTask(item, absoluteCount);
         } catch (RuntimeException catalogueFailure) {
            this.fail(unavailable);
            return null;
         }

         if (created == null) {
            this.fail(unavailable);
            return null;
         }

         this.resourceChild = created;
      }

      if (this.resourceChild.stopped()) {
         this.fail(unavailable);
         return null;
      } else {
         return this.resourceChild;
      }
   }

   private Task completeResourcePhase() {
      if (this.getSub() != null) {
         return null;
      }

      if (this.resourcePurpose == SetupFarmTask.ResourcePurpose.REPAIR_PREFLIGHT) {
         if (this.requiredRepairTools == null) {
            this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
            return null;
         }

         if (!this.requiredRepairTools.isSatisfied(this.controller.getInventory())) {
            this.resourceStage = SetupFarmTask.ResourceStage.ENSURE_REPAIR_TOOLS;
            this.watchdog.markProgress();
            return null;
         }

         if (this.count(Items.DIRT) < this.requiredRepairDirt) {
            this.resourceStage = SetupFarmTask.ResourceStage.ENSURE_REPAIR_DIRT;
            this.watchdog.markProgress();
            return null;
         }
      }

      this.beginResourceReturn(SetupFarmTask.ResourceReturnMode.CONTINUE_SETUP, FarmTaskReason.NONE);
      return null;
   }

   private void beginResourceReturn(SetupFarmTask.ResourceReturnMode mode, FarmTaskReason failure) {
      if (this.resourceReturnMode == SetupFarmTask.ResourceReturnMode.NONE) {
         this.resourceReturnMode = Objects.requireNonNull(mode, "mode");
         this.pendingResourceFailure = Objects.requireNonNull(failure, "failure");
         this.resourceReturnStatus = FarmReturnStatus.NOT_REQUIRED;
         this.resourceReturnCandidates = this.buildResourceReturnCandidates();
         this.resourceReturnCandidateIndex = 0;
         this.resourceReturnCandidateTicks = 0;
         this.resourceReturnStance = this.resourceReturnCandidates.isEmpty() ? null : this.resourceReturnCandidates.get(0);
         this.resourceReturnChild = null;
         this.resourceReturnTicks = 0;
         this.resourceChild = null;
         this.setDebugState(
            mode == SetupFarmTask.ResourceReturnMode.FINALIZE_FAILURE
               ? "Returning to the marked farm before reporting resource failure"
               : "Returning to the marked farm before validation and repair"
         );
      }
   }

   private Task tickResourceReturn() {
      this.resourceReturnTicks++;
      this.resourceReturnCandidateTicks++;
      if (this.preparedFarmHandle != null && this.resolvePreparedFarmWaypoint() == null) {
         if (this.pendingResourceFailure == FarmTaskReason.NONE) {
            this.pendingResourceFailure = FarmTaskReason.FARM_NOT_FOUND;
         }

         this.resourceReturnMode = SetupFarmTask.ResourceReturnMode.FINALIZE_FAILURE;
      }

      if (this.resourceReturnStance == null) {
         this.resourceReturnStatus = FarmReturnStatus.UNAVAILABLE;
         this.finishResourceReturn();
         return null;
      }

      if (this.resourceReturnTicks > 600) {
         this.resourceReturnStatus = FarmReturnStatus.TIMED_OUT;
         this.finishResourceReturn();
         return null;
      }

      BlockPos logicalFeet = this.controller.getBaritone().getEntityContext().feetPos();
      boolean arrived = this.preparedFarmHandle == null
         ? FarmStanceNavigation.isAtFrozenStance(logicalFeet, this.resourceReturnStance)
         : this.isUsableFarmLevelStance(this.preparedFarmHandle.center(), logicalFeet);
      if (arrived) {
         if (this.getSub() != null) {
            this.resourceReturnChild = null;
            return null;
         } else {
            this.finishResourceReturn();
            return null;
         }
      } else {
         if (this.preparedFarmHandle != null
            && (this.resourceReturnCandidateTicks >= 75 || this.isLoadedButUnusableReturnStance(this.preparedFarmHandle.center(), this.resourceReturnStance))) {
            if (!this.advanceResourceReturnCandidate()) {
               this.resourceReturnStatus = FarmReturnStatus.UNAVAILABLE;
               this.finishResourceReturn();
               return null;
            }

            if (this.getSub() != null) {
               this.resourceReturnChild = null;
               return null;
            }
         }

         Task active = this.getSub();
         if (active != null) {
            if (active == this.resourceReturnChild && !this.resourceReturnChild.stopped()) {
               return this.resourceReturnChild;
            }

            this.resourceReturnChild = null;
            return null;
         } else {
            this.resourceReturnChild = new GetToBlockTask(this.resourceReturnStance);
            return this.resourceReturnChild;
         }
      }
   }

   private List<BlockPos> buildResourceReturnCandidates() {
      if (this.sitePlan == null) {
         return List.of();
      }

      if (this.preparedFarmHandle == null) {
         return List.of(this.sitePlan.acquisitionReturnStance().immutable());
      }

      BlockPos center = this.preparedFarmHandle.center();
      BlockPos origin = this.controller.getBaritone().getEntityContext().feetPos();
      ArrayList<BlockPos> candidates = new ArrayList<>(80);

      for (BlockPos soil : FarmPlotGeometry.soilCells(center)) {
         candidates.add(soil.above().immutable());
      }

      candidates.sort(
         Comparator.<BlockPos>comparingInt(stance -> isPerimeterSoil(center, stance.below()) ? 0 : 1)
            .thenComparingLong(stance -> FarmPlotGeometry.horizontalDistanceSquared(origin, stance))
            .thenComparingInt(Vec3i::getX)
            .thenComparingInt(Vec3i::getZ)
      );
      return List.copyOf(candidates);
   }

   private static boolean isPerimeterSoil(BlockPos center, BlockPos soil) {
      return Math.abs(soil.getX() - center.getX()) == 4 || Math.abs(soil.getZ() - center.getZ()) == 4;
   }

   private boolean advanceResourceReturnCandidate() {
      this.resourceReturnChild = null;
      this.resourceReturnCandidateTicks = 0;
      this.resourceReturnCandidateIndex++;
      if (this.resourceReturnCandidateIndex >= this.resourceReturnCandidates.size()) {
         this.resourceReturnStance = null;
         return false;
      } else {
         this.resourceReturnStance = this.resourceReturnCandidates.get(this.resourceReturnCandidateIndex);
         return true;
      }
   }

   private boolean isLoadedButUnusableReturnStance(BlockPos center, BlockPos stance) {
      return this.controller.getChunkTracker().isChunkLoaded(stance)
         && this.controller.getChunkTracker().isChunkLoaded(stance.above())
         && this.controller.getChunkTracker().isChunkLoaded(stance.below())
         && !this.isUsableFarmLevelStance(center, stance);
   }

   private boolean isUsableFarmLevelStance(BlockPos center, BlockPos stance) {
      if (center != null && stance != null && stance.getY() == center.getY() + 1) {
         BlockPos support = stance.below();
         if (!support.equals(center)
            && FarmPlotGeometry.contains(center, support)
            && this.controller.getChunkTracker().isChunkLoaded(support)
            && this.controller.getChunkTracker().isChunkLoaded(stance)
            && this.controller.getChunkTracker().isChunkLoaded(stance.above())) {
            BlockState supportState = this.controller.getWorld().getBlockState(support);
            BlockState feetState = this.controller.getWorld().getBlockState(stance);
            BlockState headState = this.controller.getWorld().getBlockState(stance.above());
            return !supportState.getCollisionShape(this.controller.getWorld(), support).isEmpty()
               && supportState.getFluidState().isEmpty()
               && feetState.getCollisionShape(this.controller.getWorld(), stance).isEmpty()
               && feetState.getFluidState().isEmpty()
               && headState.getCollisionShape(this.controller.getWorld(), stance.above()).isEmpty()
               && headState.getFluidState().isEmpty();
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private WaypointRecord resolvePreparedFarmWaypoint() {
      if (this.preparedFarmHandle == null) {
         return null;
      }

      EllieGPSStore store = EllieGPSStore.get();
      WaypointRecord record = store == null ? null : store.byId(this.preparedFarmHandle.id());
      FarmWaypointData data = record == null ? null : record.farmData();
      BlockPos center = record == null ? null : record.canonicalBlockPos();
      return record != null
            && record.schemaVersion == 1
            && record.pos != null
            && record.pos.length == 3
            && "farm".equals(record.type)
            && Objects.equals(this.preparedFarmHandle.dimension(), record.dimension)
            && this.preparedFarmHandle.center().equals(center)
            && record.id != null
            && record.id.equals(WaypointRecord.idFor(record.dimension, center))
            && data != null
            && data.isSupportedVersion()
            && data.radius() == 4
         ? record
         : null;
   }

   private WaypointRecord resolvePreparedFarmWaypointAtExactPosition() {
      WaypointRecord record = this.resolvePreparedFarmWaypoint();
      EllieGPSStore store = EllieGPSStore.get();
      WaypointRecord occupant = record != null && store != null
         ? store.byPosition(this.preparedFarmHandle.dimension(), this.preparedFarmHandle.center())
         : null;
      return sameWaypointIdentity(record, occupant) ? record : null;
   }

   static boolean sameWaypointIdentity(WaypointRecord byId, WaypointRecord byPosition) {
      return byId != null && byPosition != null && byId.id != null && !byId.id.isBlank() && byId.id.equals(byPosition.id);
   }

   private void finishResourceReturn() {
      SetupFarmTask.ResourceReturnMode completedMode = this.resourceReturnMode;
      FarmTaskReason primaryFailure = this.pendingResourceFailure;
      SetupFarmTask.ResourcePurpose completedPurpose = this.resourcePurpose;
      if (this.resourceReturnStatus == FarmReturnStatus.NOT_REQUIRED) {
         this.resourceReturnStatus = FarmReturnStatus.RETURNED;
      }

      this.resourceReturnMode = SetupFarmTask.ResourceReturnMode.NONE;
      this.resourceReturnChild = null;
      this.resourceReturnTicks = 0;
      this.pendingResourceFailure = FarmTaskReason.NONE;
      this.releaseAcquisitionProtection();
      if (completedMode == SetupFarmTask.ResourceReturnMode.FINALIZE_FAILURE
         && this.resourceReturnStatus == FarmReturnStatus.RETURNED
         && completedPurpose == SetupFarmTask.ResourcePurpose.REPAIR_PREFLIGHT
         && primaryFailure == FarmTaskReason.REPAIR_TOOL_UNAVAILABLE
         && this.repairToolFailureBecameStale()) {
         this.resourcePurpose = null;
         this.requiredRepairDirt = 0;
         this.requiredRepairTools = null;
         this.repairToolCheckpoint = new FarmRepairToolAcquisitionTask.Checkpoint();
         this.repairPlan = null;
         this.repairBreakIndex = 0;
         this.repairFillIndex = 0;
         this.enterPhase(FarmTaskPhase.REPAIR);
      } else if (completedMode != SetupFarmTask.ResourceReturnMode.CONTINUE_SETUP || this.resourceReturnStatus != FarmReturnStatus.RETURNED) {
         FarmTaskReason reason = primaryFailure == FarmTaskReason.NONE ? FarmTaskReason.RESOURCE_RETURN_FAILED : primaryFailure;
         this.finalizeFailure(reason);
      } else if (this.worldView == null) {
         this.finalizeFailure(FarmTaskReason.STORE_UNAVAILABLE);
      } else {
         this.resourcePurpose = null;
         if (completedPurpose == SetupFarmTask.ResourcePurpose.PREPARE_SITE) {
            this.enterPhase(FarmTaskPhase.REVALIDATE);
         } else if (completedPurpose != SetupFarmTask.ResourcePurpose.COMPLETE_SETUP
            && completedPurpose != SetupFarmTask.ResourcePurpose.REPLACE_HOE
            && completedPurpose != SetupFarmTask.ResourcePurpose.REPAIR_PREFLIGHT) {
            this.finalizeFailure(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
         } else {
            this.resumeTillAfterResources = false;
            this.requiredRepairDirt = 0;
            this.requiredRepairTools = null;
            this.repairToolCheckpoint = new FarmRepairToolAcquisitionTask.Checkpoint();
            this.repairPlan = null;
            this.repairBreakIndex = 0;
            this.repairFillIndex = 0;
            this.enterPhase(FarmTaskPhase.REPAIR);
         }
      }
   }

   private boolean repairToolFailureBecameStale() {
      if (this.worldView != null && this.requiredRepairTools != null && this.sitePlan != null) {
         FarmPlotRepairPlan.ScanResult refreshed;
         try {
            refreshed = FarmPlotRepairPlan.scan(this.worldView, this.sitePlan.center());
         } catch (RuntimeException unavailable) {
            return false;
         }

         if (!refreshed.ready()) {
            return false;
         }

         FarmRepairToolPlan liveTools = refreshed.plan().toolPlan();
         return repairToolFailureSnapshotIsStale(
            this.requiredRepairTools.identity(), liveTools.identity(), liveTools.isSatisfied(this.controller.getInventory())
         );
      } else {
         return false;
      }
   }

   static boolean repairToolFailureSnapshotIsStale(String failedPlanIdentity, String livePlanIdentity, boolean livePlanSatisfied) {
      return !Objects.equals(failedPlanIdentity, livePlanIdentity) || livePlanSatisfied;
   }

   private Task tickRevalidation() {
      if (!this.siteChunksLoaded()) {
         this.fail(FarmTaskReason.CHUNK_UNLOADED);
         return null;
      }

      try {
         FarmSitePlanner.RevalidationResult result = FarmSitePlanner.revalidate(this.sitePlan, this.worldView);
         if (!result.valid()) {
            this.fail(FarmTaskReason.MANIFEST_DRIFT);
            return null;
         }
      } catch (RuntimeException unavailable) {
         this.fail(FarmTaskReason.MANIFEST_DRIFT);
         return null;
      }

      this.enterPhase(FarmTaskPhase.CLEAR);
      return null;
   }

   private Task tickClear(boolean centerOnly) {
      List<FarmSitePlan.ClearAction> actions = this.sitePlan.clearActions();
      if (this.mutationChild != null) {
         VerifiedBreakBlockTask child = (VerifiedBreakBlockTask)this.mutationChild;
         if (!child.isFinished()) {
            if (this.watchdog.mutationStalled()) {
               this.fail(FarmTaskReason.INTERACTION_DENIED);
               return null;
            } else {
               return child;
            }
         } else {
            if (!child.isSuccessful()) {
               this.fail(child.reason());
               return null;
            }

            this.mutationChild = null;
            if (!this.incrementCleared()) {
               return null;
            }

            this.watchdog.markProgress();
            if (centerOnly) {
               this.clearIndex = actions.size();
            } else {
               this.clearIndex++;
            }

            return null;
         }
      } else if (!centerOnly) {
         while (this.clearIndex < actions.size()) {
            FarmSitePlan.ClearAction action = actions.get(this.clearIndex);
            if (action.purpose() == FarmSitePlan.ClearPurpose.OPEN_CENTER) {
               this.clearIndex++;
            } else {
               if (!this.controller.getWorld().getBlockState(action.target()).isAir()) {
                  if (!action.expectedStateFingerprint().equals(stateFingerprint(this.controller.getWorld().getBlockState(action.target())))) {
                     this.fail(FarmTaskReason.MANIFEST_DRIFT);
                     return null;
                  }

                  if (!this.requireOpenStance(action.stance())) {
                     return null;
                  }

                  this.mutationChild = new VerifiedBreakBlockTask(action.target(), action.stance());
                  this.watchdog.markProgress();
                  return this.mutationChild;
               }

               this.clearIndex++;
               this.watchdog.markProgress();
            }
         }

         this.enterPhase(FarmTaskPhase.FILL);
         return null;
      } else {
         FarmSitePlan.ClearAction centerAction = null;

         for (FarmSitePlan.ClearAction action : actions) {
            if (action.purpose() == FarmSitePlan.ClearPurpose.OPEN_CENTER) {
               centerAction = action;
               break;
            }
         }

         if (centerAction != null
            && !this.controller.getWorld().getBlockState(centerAction.target()).isAir()
            && !FarmInteractionPostconditions.isStandaloneWaterSource(this.controller.getWorld().getBlockState(centerAction.target()))) {
            if (!centerAction.expectedStateFingerprint().equals(stateFingerprint(this.controller.getWorld().getBlockState(centerAction.target())))) {
               this.fail(FarmTaskReason.MANIFEST_DRIFT);
               return null;
            }

            if (!this.requireOpenStance(centerAction.stance())) {
               return null;
            }

            this.mutationChild = new VerifiedBreakBlockTask(centerAction.target(), centerAction.stance());
            this.watchdog.markProgress();
            return this.mutationChild;
         } else {
            this.enterPhase(FarmTaskPhase.REGISTER_PREPARED);
            return null;
         }
      }
   }

   private Task tickFill() {
      List<FarmSitePlan.FillAction> actions = this.sitePlan.fillActions();
      if (this.mutationChild != null) {
         PlaceFarmDirtTask child = (PlaceFarmDirtTask)this.mutationChild;
         if (!child.isFinished()) {
            if (this.watchdog.mutationStalled()) {
               this.fail(FarmTaskReason.INTERACTION_DENIED);
               return null;
            } else {
               return child;
            }
         } else {
            if (!child.isSuccessful()) {
               this.fail(child.reason());
               return null;
            }

            this.mutationChild = null;
            if (!this.incrementFilled()) {
               return null;
            }

            this.fillIndex++;
            this.watchdog.markProgress();
            return null;
         }
      } else {
         while (this.fillIndex < actions.size()) {
            FarmSitePlan.FillAction action = actions.get(this.fillIndex);
            BlockState state = this.controller.getWorld().getBlockState(action.target());
            if (!state.is(Blocks.DIRT) && !state.is(Blocks.GRASS_BLOCK) && !state.is(Blocks.FARMLAND)) {
               if (!state.isAir()) {
                  this.fail(FarmTaskReason.MANIFEST_DRIFT);
                  return null;
               }

               if (!this.requireOpenStance(action.stance())) {
                  return null;
               }

               this.mutationChild = new PlaceFarmDirtTask(action.target(), action.stance());
               this.watchdog.markProgress();
               return this.mutationChild;
            }

            this.fillIndex++;
            this.watchdog.markProgress();
         }

         this.enterPhase(this.sitePlan.waterRequired() ? FarmTaskPhase.OPEN_CENTER : FarmTaskPhase.REGISTER_PREPARED);
         return null;
      }
   }

   private Task tickRegisterPrepared() {
      if (this.preparedCommitAttempted) {
         this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
         return null;
      }

      this.preparedCommitAttempted = true;

      FarmPlotRepairPlan.ScanResult preparedShape;
      try {
         preparedShape = FarmPlotRepairPlan.scan(this.worldView, this.sitePlan.center());
      } catch (RuntimeException scanFailure) {
         this.fail(FarmTaskReason.MANIFEST_DRIFT);
         return null;
      }

      if (preparedShape.ready() && preparedShape.plan().clearActions().isEmpty() && preparedShape.plan().fillActions().isEmpty()) {
         FarmTaskReason registrationFailure = this.registerPreparedCheckpoint(this.sitePlan.center());
         if (registrationFailure != FarmTaskReason.NONE) {
            this.fail(registrationFailure);
            return null;
         } else {
            this.protectSiteForAcquisition();
            this.resourcePurpose = SetupFarmTask.ResourcePurpose.COMPLETE_SETUP;
            boolean waterMissing = !FarmInteractionPostconditions.isStandaloneWaterSource(this.controller.getWorld().getBlockState(this.sitePlan.center()));
            this.resourceStage = initialResourceStage(waterMissing);
            this.watchdog.markProgress();
            this.enterPhase(FarmTaskPhase.ACQUIRE_RESOURCES);
            return null;
         }
      } else {
         this.fail(preparedShape.reason() == FarmPlotRepairPlan.UnsafeReason.UNREADABLE ? FarmTaskReason.CHUNK_UNLOADED : FarmTaskReason.MANIFEST_DRIFT);
         return null;
      }
   }

   private FarmTaskReason registerPreparedCheckpoint(BlockPos center) {
      FarmObservationResult scan;
      try {
         scan = FarmCropScanner.observePrepared(this.controller.getWorld(), center);
      } catch (RuntimeException scannerFailure) {
         return FarmTaskReason.INTERNAL_CONTRACT_FAILURE;
      }

      if (scan.status() == FarmObservationStatus.OBSERVED && scan.observation() != null) {
         WaypointMutationResult prepared;
         try {
            prepared = FarmWaypointService.registerPrepared(scan.observation(), "bot_placed");
         } catch (UncheckedIOException jsonFailure) {
            return FarmTaskReason.WAYPOINT_JSON_FAILURE;
         } catch (RuntimeException internalFailure) {
            return FarmTaskReason.INTERNAL_CONTRACT_FAILURE;
         }

         this.waypointResult = prepared;
         if (prepared == null) {
            return FarmTaskReason.INTERNAL_CONTRACT_FAILURE;
         }

         FarmTaskReason mutationFailure = failureForMutation(prepared.status());
         if (mutationFailure != FarmTaskReason.NONE) {
            return mutationFailure;
         }

         WaypointRecord current = prepared.current();
         BlockPos currentCenter = current == null ? null : current.canonicalBlockPos();
         if (current != null
            && current.stale
            && "farm".equals(current.type)
            && Objects.equals(this.commandDimension, current.dimension)
            && center.equals(currentCenter)
            && current.id != null
            && current.id.equals(WaypointRecord.idFor(this.commandDimension, center))) {
            this.preparedFarmHandle = new SetupFarmTask.PreparedFarmHandle(current.id, current.dimension, currentCenter);
            if (this.resolvePreparedFarmWaypointAtExactPosition() == null) {
               return FarmTaskReason.FARM_NOT_FOUND;
            }

            if ((prepared.status() == WaypointMutationStatus.COMMITTED_INDEX_DEGRADED || prepared.status() == WaypointMutationStatus.NO_CHANGE_INDEX_DEGRADED)
               && this.runState != null) {
               this.runState.setWaypointDegraded(AgenticRunRegistry.DegradationLevel.PARTIAL, "farm_waypoint_index_degraded");
            }

            return FarmTaskReason.NONE;
         } else {
            return FarmTaskReason.INTERNAL_CONTRACT_FAILURE;
         }
      } else {
         return scan.status() == FarmObservationStatus.UNLOADED ? FarmTaskReason.CHUNK_UNLOADED : FarmTaskReason.MANIFEST_DRIFT;
      }
   }

   private Task tickRepair() {
      if (this.preparedFarmHandle == null) {
         this.fail(FarmTaskReason.FARM_NOT_FOUND);
         return null;
      }

      if (this.mutationChild != null) {
         if (this.mutationChild instanceof VerifiedBreakBlockTask child) {
            if (!child.isFinished()) {
               if (this.watchdog.mutationStalled()) {
                  this.fail(FarmTaskReason.INTERACTION_DENIED);
                  return null;
               } else {
                  return child;
               }
            } else if (!child.isSuccessful()) {
               if (child.reason() == FarmTaskReason.MANIFEST_DRIFT && child.attempts() == 0) {
                  this.mutationChild = null;
                  this.repairPlan = null;
                  this.watchdog.markProgress();
                  return null;
               } else if (child.reason() == FarmTaskReason.REPAIR_TOOL_UNAVAILABLE) {
                  this.mutationChild = null;
                  this.repairPlan = null;
                  this.repairBreakIndex = 0;
                  this.repairFillIndex = 0;
                  this.watchdog.markProgress();
                  return null;
               } else {
                  this.fail(child.reason());
                  return null;
               }
            } else {
               this.mutationChild = null;
               if (!this.incrementCleared()) {
                  return null;
               }

               this.repairBreakIndex++;
               this.watchdog.markProgress();
               return null;
            }
         } else if (this.mutationChild instanceof PlaceFarmDirtTask child) {
            if (!child.isFinished()) {
               if (this.watchdog.mutationStalled()) {
                  this.fail(FarmTaskReason.INTERACTION_DENIED);
                  return null;
               } else {
                  return child;
               }
            } else if (!child.isSuccessful()) {
               if (child.reason() == FarmTaskReason.MANIFEST_DRIFT && child.attempts() == 0) {
                  this.mutationChild = null;
                  this.repairPlan = null;
                  this.watchdog.markProgress();
                  return null;
               } else {
                  this.fail(child.reason());
                  return null;
               }
            } else {
               this.mutationChild = null;
               if (!this.incrementFilled()) {
                  return null;
               }

               this.repairFillIndex++;
               this.watchdog.markProgress();
               return null;
            }
         } else {
            this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
            return null;
         }
      } else {
         if (this.getSub() != null) {
            return null;
         }

         if (this.repairPlan == null) {
            FarmTaskReason orphanFailure = this.retireOrphanedFarmProtection(this.sitePlan.center());
            if (orphanFailure != FarmTaskReason.NONE) {
               this.fail(orphanFailure);
               return null;
            }

            FarmPlotRepairPlan.ScanResult result;
            try {
               result = FarmPlotRepairPlan.scan(this.worldView, this.sitePlan.center());
            } catch (RuntimeException scanFailure) {
               this.fail(FarmTaskReason.MANIFEST_DRIFT);
               return null;
            }

            if (!result.ready()) {
               this.fail(result.reason() == FarmPlotRepairPlan.UnsafeReason.UNREADABLE ? FarmTaskReason.CHUNK_UNLOADED : FarmTaskReason.MANIFEST_DRIFT);
               return null;
            }

            this.repairPlan = result.plan();
            this.repairBreakIndex = 0;
            this.repairFillIndex = 0;
            this.clearTotal = Math.max(this.clearTotal, this.cleared + this.repairPlan.clearActions().size());
            this.fillTotal = Math.max(this.fillTotal, this.filled + this.repairPlan.fillActions().size());
            FarmRepairToolPlan toolPlan = this.repairPlan.toolPlan();
            if (toolPlan.hasUnsatisfiedCustom(this.controller.getInventory())) {
               this.fail(FarmTaskReason.REPAIR_TOOL_UNAVAILABLE);
               return null;
            }

            boolean toolsMissing = !toolPlan.isSatisfied(this.controller.getInventory());
            boolean dirtMissing = this.count(Items.DIRT) < this.repairPlan.requiredDirt();
            if (toolsMissing || dirtMissing) {
               if (this.repairResourceTrips >= 2) {
                  this.fail(toolsMissing ? FarmTaskReason.REPAIR_TOOL_UNAVAILABLE : FarmTaskReason.DIRT_UNAVAILABLE);
                  return null;
               }

               this.repairResourceTrips++;
               this.requiredRepairDirt = this.repairPlan.requiredDirt();
               this.requiredRepairTools = toolPlan;
               this.repairToolCheckpoint = new FarmRepairToolAcquisitionTask.Checkpoint();
               this.repairPlan = null;
               this.protectSiteForAcquisition();
               this.resourcePurpose = SetupFarmTask.ResourcePurpose.REPAIR_PREFLIGHT;
               this.resourceStage = toolsMissing ? SetupFarmTask.ResourceStage.ENSURE_REPAIR_TOOLS : SetupFarmTask.ResourceStage.ENSURE_REPAIR_DIRT;
               this.resourceChild = null;
               this.enterPhase(FarmTaskPhase.ACQUIRE_RESOURCES);
               return null;
            }

            boolean needsMutations = !this.repairPlan.clearActions().isEmpty() || !this.repairPlan.fillActions().isEmpty();
            if (!needsMutations) {
               this.activeTillTargets = this.repairPlan.tillActions().stream().map(FarmPlotRepairPlan.TillAction::target).toList();
               this.tillPlanReady = true;
               this.tillIndex = 0;
               this.tillTotal = this.tilled + this.activeTillTargets.size();
               if (!this.repairPlan.centerHasWaterSource()) {
                  if (this.count(Items.WATER_BUCKET) < 1) {
                     this.repairPlan = null;
                     this.protectSiteForAcquisition();
                     this.resourcePurpose = SetupFarmTask.ResourcePurpose.COMPLETE_SETUP;
                     this.resourceStage = SetupFarmTask.ResourceStage.ENSURE_BUCKET;
                     this.resourceChild = null;
                     this.enterPhase(FarmTaskPhase.ACQUIRE_RESOURCES);
                  } else if (this.remainingHoeDurability() < this.activeTillTargets.size()) {
                     this.beginHoeReacquisition();
                  } else {
                     this.enterPhase(FarmTaskPhase.PLACE_WATER);
                  }
               } else if (this.remainingHoeDurability() < this.activeTillTargets.size()) {
                  this.beginHoeReacquisition();
               } else {
                  this.enterPhase(FarmTaskPhase.TILL);
               }

               return null;
            }

            if (this.repairMutationPasses >= 3) {
               this.fail(FarmTaskReason.MANIFEST_DRIFT);
               return null;
            }

            this.repairMutationPasses++;
         }

         List<FarmPlotRepairPlan.ClearAction> breaks = this.repairPlan.clearActions();

         while (this.repairBreakIndex < breaks.size()) {
            FarmPlotRepairPlan.ClearAction action = breaks.get(this.repairBreakIndex);
            BlockState state = this.controller.getWorld().getBlockState(action.target());
            if (!state.isAir()) {
               if (action.expectedStateFingerprint().equals(stateFingerprint(state)) && this.isLiveOpenMutationStance(action.stance())) {
                  this.mutationChild = VerifiedBreakBlockTask.forFarmRepair(
                     action.target(), action.stance(), action.expectedStateFingerprint(), action.breakToolRequirement()
                  );
                  this.watchdog.markProgress();
                  return this.mutationChild;
               }

               this.repairPlan = null;
               this.watchdog.markProgress();
               return null;
            }

            this.repairBreakIndex++;
            this.watchdog.markProgress();
         }

         SetupFarmTask.RepairPassTransition passTransition = repairPassTransition(breaks.size(), this.repairBreakIndex, this.repairFillIndex);
         this.repairBreakIndex = passTransition.nextBreakIndex();
         this.repairFillIndex = passTransition.nextFillIndex();
         if (passTransition.action() == SetupFarmTask.RepairPassAction.RESCAN_BEFORE_FILL) {
            this.repairPlan = null;
            this.watchdog.markProgress();
            return null;
         }

         if (passTransition.action() != SetupFarmTask.RepairPassAction.CONTINUE_FILLS) {
            throw new IllegalStateException("repair clear loop exited before completion");
         }

         List<FarmPlotRepairPlan.FillAction> fills = this.repairPlan.fillActions();

         while (this.repairFillIndex < fills.size()) {
            FarmPlotRepairPlan.FillAction action = fills.get(this.repairFillIndex);
            BlockState state = this.controller.getWorld().getBlockState(action.target());
            if (!state.is(Blocks.DIRT) && !state.is(Blocks.GRASS_BLOCK) && !state.is(Blocks.FARMLAND)) {
               if (state.isAir() && action.expectedStateFingerprint().equals(stateFingerprint(state)) && this.isLiveOpenMutationStance(action.stance())) {
                  this.mutationChild = PlaceFarmDirtTask.forFarmRepair(action.target(), action.stance());
                  this.watchdog.markProgress();
                  return this.mutationChild;
               }

               this.repairPlan = null;
               this.watchdog.markProgress();
               return null;
            }

            this.repairFillIndex++;
            this.watchdog.markProgress();
         }

         this.repairPlan = null;
         this.repairBreakIndex = 0;
         this.repairFillIndex = 0;
         this.watchdog.markProgress();
         return null;
      }
   }

   static SetupFarmTask.RepairPassTransition repairPassTransition(int plannedBreaks, int completedBreaks, int currentFillIndex) {
      if (plannedBreaks < 0 || completedBreaks < 0 || completedBreaks > plannedBreaks || currentFillIndex < 0) {
         throw new IllegalArgumentException("invalid repair-break progress");
      } else if (completedBreaks < plannedBreaks) {
         return new SetupFarmTask.RepairPassTransition(SetupFarmTask.RepairPassAction.CONTINUE_BREAKS, completedBreaks, currentFillIndex);
      } else {
         return plannedBreaks > 0
            ? new SetupFarmTask.RepairPassTransition(SetupFarmTask.RepairPassAction.RESCAN_BEFORE_FILL, 0, 0)
            : new SetupFarmTask.RepairPassTransition(SetupFarmTask.RepairPassAction.CONTINUE_FILLS, completedBreaks, currentFillIndex);
      }
   }

   private Task tickPlaceWater() {
      BlockState centerState = this.controller.getWorld().getBlockState(this.sitePlan.center());
      if (FarmInteractionPostconditions.isStandaloneWaterSource(centerState)) {
         if (this.mutationChild instanceof PlaceFarmWaterTask child) {
            if (!child.settlePendingTransition() || !child.isFinished() || !child.isSuccessful()) {
               this.fail(child.reason() == FarmTaskReason.NONE ? FarmTaskReason.INTERNAL_CONTRACT_FAILURE : child.reason());
               return null;
            }

            if (!this.consumeFinishedMutation(child)) {
               return null;
            }

            this.watchdog.markProgress();
            this.enterPhase(FarmTaskPhase.TILL);
            return null;
         } else {
            this.enterPhase(FarmTaskPhase.TILL);
            return null;
         }
      } else if (this.mutationChild == null) {
         FarmSitePlan.WaterAction action = this.sitePlan.waterAction();
         if (!centerState.isAir()) {
            this.repairPlan = null;
            this.enterPhase(FarmTaskPhase.REPAIR);
            return null;
         }

         if (this.count(Items.WATER_BUCKET) < 1) {
            this.protectSiteForAcquisition();
            this.resourcePurpose = SetupFarmTask.ResourcePurpose.COMPLETE_SETUP;
            this.resourceStage = SetupFarmTask.ResourceStage.ENSURE_BUCKET;
            this.enterPhase(FarmTaskPhase.ACQUIRE_RESOURCES);
            return null;
         }

         BlockPos preferred = action == null ? null : action.stance();
         BlockPos stance = this.chooseOpenMutationStance(this.sitePlan.center(), preferred);
         if (stance == null) {
            this.fail(FarmTaskReason.MANIFEST_DRIFT);
            return null;
         }

         if (!this.requireOpenStance(stance)) {
            return null;
         }

         this.mutationChild = new PlaceFarmWaterTask(this.sitePlan.center(), this.sitePlan.center().below(), stance);
         this.watchdog.markProgress();
         return this.mutationChild;
      } else {
         PlaceFarmWaterTask child = (PlaceFarmWaterTask)this.mutationChild;
         if (!child.isFinished()) {
            if (this.watchdog.mutationStalled()) {
               this.fail(FarmTaskReason.INTERACTION_DENIED);
               return null;
            } else {
               return child;
            }
         } else if (!child.isSuccessful()) {
            this.fail(child.reason());
            return null;
         } else {
            this.mutationChild = null;
            this.sourcePlaced = true;
            this.watchdog.markProgress();
            this.enterPhase(FarmTaskPhase.TILL);
            return null;
         }
      }
   }

   private BlockPos chooseOpenMutationStance(BlockPos target, BlockPos preferred) {
      ArrayList<BlockPos> candidates = new ArrayList<>();
      if (preferred != null) {
         candidates.add(preferred.immutable());
      }

      for (BlockPos candidate : FarmPlotGeometry.stanceCandidates(this.sitePlan.center(), target)) {
         if (!candidates.contains(candidate)) {
            candidates.add(candidate);
         }
      }

      for (BlockPos candidate : candidates) {
         if (this.isLiveOpenMutationStance(candidate)) {
            return candidate.immutable();
         }
      }

      return null;
   }

   private boolean isLiveOpenMutationStance(BlockPos stance) {
      if (!FarmStanceNavigation.hasPlannerSafeLiveGeometry(this.controller, stance)) {
         return false;
      }

      BlockPos supportPos = stance.below();
      PlayerPlacedBlockStore store = PlayerPlacedBlockStore.get();
      return store != null
         && !FarmStanceNavigation.stanceProtectionDenied(
            store.contains(this.commandDimension, supportPos),
            store.contains(this.commandDimension, stance),
            FarmStanceNavigation.isPassableCropStance(this.controller, stance),
            store.contains(this.commandDimension, stance.above())
         );
   }

   private Task tickTill() {
      if (!this.tillPlanReady) {
         this.repairPlan = null;
         this.enterPhase(FarmTaskPhase.REPAIR);
         return null;
      }

      List<BlockPos> actions = this.activeTillTargets;
      if (this.mutationChild != null) {
         TillFarmBlockTask child = (TillFarmBlockTask)this.mutationChild;
         if (!child.isFinished()) {
            if (this.watchdog.mutationStalled()) {
               this.fail(FarmTaskReason.INTERACTION_DENIED);
               return null;
            } else {
               return child;
            }
         } else if (!child.isSuccessful()) {
            if (child.reason() == FarmTaskReason.HOE_UNAVAILABLE) {
               this.mutationChild = null;
               this.beginHoeReacquisition();
               return null;
            } else if (child.reason() == FarmTaskReason.MANIFEST_DRIFT && child.attempts() == 0) {
               this.mutationChild = null;
               this.repairPlan = null;
               this.enterPhase(FarmTaskPhase.REPAIR);
               return null;
            } else {
               this.fail(child.reason());
               return null;
            }
         } else {
            if (child.freshTill() && !this.incrementTilled()) {
               return null;
            }

            this.mutationChild = null;
            this.tillIndex++;
            this.watchdog.markProgress();
            return null;
         }
      } else {
         while (this.tillIndex < actions.size()) {
            BlockPos target = actions.get(this.tillIndex);
            BlockState targetState = this.controller.getWorld().getBlockState(target);
            if (!targetState.is(Blocks.FARMLAND)) {
               if ((targetState.is(Blocks.DIRT) || targetState.is(Blocks.GRASS_BLOCK))
                  && this.controller.getWorld().getBlockState(target.above()).isAir()) {
                  if (this.remainingHoeDurability() < 1) {
                     this.beginHoeReacquisition();
                     return null;
                  }

                  BlockPos stance = this.chooseOpenMutationStance(target, null);
                  if (stance == null) {
                     this.fail(FarmTaskReason.MANIFEST_DRIFT);
                     return null;
                  }

                  if (!this.requireOpenStance(stance)) {
                     return null;
                  }

                  this.mutationChild = new TillFarmBlockTask(target, stance);
                  this.watchdog.markProgress();
                  return this.mutationChild;
               }

               this.repairPlan = null;
               this.enterPhase(FarmTaskPhase.REPAIR);
               return null;
            }

            this.tillIndex++;
            this.watchdog.markProgress();
         }

         this.enterPhase(FarmTaskPhase.VERIFY);
         return null;
      }
   }

   private void beginHoeReacquisition() {
      this.protectSiteForAcquisition();
      this.resumeTillAfterResources = true;
      this.resourcePurpose = SetupFarmTask.ResourcePurpose.REPLACE_HOE;
      this.resourceStage = SetupFarmTask.ResourceStage.REACQUIRE_HOE;
      this.resourceChild = null;
      this.enterPhase(FarmTaskPhase.ACQUIRE_RESOURCES);
   }

   private Task tickVerify() {
      FarmPlotRepairPlan.ScanResult finalShape;
      try {
         finalShape = FarmPlotRepairPlan.scan(this.worldView, this.sitePlan.center());
      } catch (RuntimeException scanFailure) {
         this.fail(FarmTaskReason.MANIFEST_DRIFT);
         return null;
      }

      if (!finalShape.ready()) {
         this.fail(finalShape.reason() == FarmPlotRepairPlan.UnsafeReason.UNREADABLE ? FarmTaskReason.CHUNK_UNLOADED : FarmTaskReason.MANIFEST_DRIFT);
         return null;
      }

      FarmPlotRepairPlan verifiedShape = finalShape.plan();
      if (verifiedShape.centerHasWaterSource()
         && verifiedShape.clearActions().isEmpty()
         && verifiedShape.fillActions().isEmpty()
         && verifiedShape.tillActions().isEmpty()) {
         FarmObservationResult scan;
         try {
            WaypointRecord scanRecord = new WaypointRecord();
            scanRecord.id = WaypointRecord.idFor(this.commandDimension, this.sitePlan.center());
            scanRecord.type = "farm";
            scanRecord.dimension = this.commandDimension;
            BlockPos center = this.sitePlan.center();
            scanRecord.pos = new int[]{center.getX(), center.getY(), center.getZ()};
            scanRecord.data = new FarmWaypointData(4, 80, List.of());
            scan = FarmCropScanner.observe(this.controller.getWorld(), scanRecord);
         } catch (RuntimeException scannerFailure) {
            this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
            return null;
         }

         if (scan.status() != FarmObservationStatus.OBSERVED) {
            this.fail(scan.status() == FarmObservationStatus.UNLOADED ? FarmTaskReason.CHUNK_UNLOADED : FarmTaskReason.MANIFEST_DRIFT);
            return null;
         } else {
            this.verifiedObservation = scan.observation();
            this.enterPhase(FarmTaskPhase.COMMIT);
            return null;
         }
      } else {
         this.repairPlan = null;
         this.tillPlanReady = false;
         this.enterPhase(FarmTaskPhase.REPAIR);
         return null;
      }
   }

   private Task tickCommit() {
      if (this.commitAttempted || this.verifiedObservation == null) {
         this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
         return null;
      }

      if (this.resolvePreparedFarmWaypointAtExactPosition() == null) {
         this.fail(FarmTaskReason.FARM_NOT_FOUND);
         return null;
      }

      this.commitAttempted = true;

      try {
         this.waypointResult = FarmWaypointService.registerOrRefresh(this.verifiedObservation, "bot_placed");
      } catch (UncheckedIOException jsonFailure) {
         this.fail(FarmTaskReason.WAYPOINT_JSON_FAILURE);
         return null;
      } catch (RuntimeException internalFailure) {
         this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
         return null;
      }

      if (this.waypointResult == null) {
         this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
         return null;
      }

      FarmTaskReason mutationFailure = failureForMutation(this.waypointResult.status());
      if (mutationFailure != FarmTaskReason.NONE) {
         this.fail(mutationFailure);
         return null;
      }

      if ((
            this.waypointResult.status() == WaypointMutationStatus.COMMITTED_INDEX_DEGRADED
               || this.waypointResult.status() == WaypointMutationStatus.NO_CHANGE_INDEX_DEGRADED
         )
         && this.runState != null) {
         this.runState.setWaypointDegraded(AgenticRunRegistry.DegradationLevel.PARTIAL, "farm_waypoint_index_degraded");
      }

      this.succeed();
      return null;
   }

   static FarmTaskReason failureForMutation(WaypointMutationStatus status) {
      return switch ((WaypointMutationStatus)Objects.requireNonNull(status, "status")) {
         case COMMITTED, COMMITTED_INDEX_DEGRADED, NO_CHANGE, NO_CHANGE_INDEX_DEGRADED -> FarmTaskReason.NONE;
         case REJECTED_TYPE_CONFLICT, REJECTED_TARGET_CONFLICT -> FarmTaskReason.TYPE_CONFLICT;
         case FAILED_STORE_UNAVAILABLE -> FarmTaskReason.STORE_UNAVAILABLE;
         case FAILED_JSON_COMMIT -> FarmTaskReason.WAYPOINT_JSON_FAILURE;
         case NOT_FOUND, NOT_FOUND_INDEX_DEGRADED -> FarmTaskReason.INTERNAL_CONTRACT_FAILURE;
      };
   }

   static SetupFarmTask.ResourceStage initialResourceStage(boolean waterRequired) {
      return waterRequired ? SetupFarmTask.ResourceStage.ENSURE_BUCKET : SetupFarmTask.ResourceStage.ENSURE_HOE;
   }

   static boolean requiresPreparedWaypoint(FarmTaskPhase phase) {
      return switch ((FarmTaskPhase)Objects.requireNonNull(phase, "phase")) {
         case REPAIR, PLACE_WATER, TILL, VERIFY, COMMIT -> true;
         default -> false;
      };
   }

   static SetupFarmTask.ResourceStage nextSatisfiedResourceStage(SetupFarmTask.ResourceStage stage) {
      return switch ((SetupFarmTask.ResourceStage)Objects.requireNonNull(stage, "stage")) {
         case ENSURE_BUILD_DIRT, ENSURE_HOE, ENSURE_REPAIR_TOOLS, ENSURE_REPAIR_DIRT, REACQUIRE_HOE, COMPLETE -> SetupFarmTask.ResourceStage.COMPLETE;
         case ENSURE_BUCKET -> SetupFarmTask.ResourceStage.ACQUIRE_WATER;
         case ACQUIRE_WATER -> SetupFarmTask.ResourceStage.ENSURE_HOE;
      };
   }

   static FarmTaskReason resourceTerminalReason(SetupFarmTask.ResourceStage stage, boolean childStopped, boolean requirementMet, int resourceTicks) {
      Objects.requireNonNull(stage, "stage");
      if (childStopped && !requirementMet) {
         return resourceUnavailableReason(stage);
      } else {
         return resourceTicks >= 6000 && stage != SetupFarmTask.ResourceStage.COMPLETE && !requirementMet
            ? FarmTaskReason.RESOURCE_TIMEOUT
            : FarmTaskReason.NONE;
      }
   }

   static boolean withinCumulativeMutationBudget(int completed, int remaining, int maximum) {
      return completed >= 0 && remaining >= 0 && maximum >= 0 && completed <= maximum && remaining <= maximum - completed;
   }

   private boolean incrementCleared() {
      if (!withinCumulativeMutationBudget(this.cleared, 1, 1040)) {
         this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
         return false;
      } else {
         this.cleared++;
         return true;
      }
   }

   private boolean incrementFilled() {
      if (!withinCumulativeMutationBudget(this.filled, 1, 64)) {
         this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
         return false;
      } else {
         this.filled++;
         return true;
      }
   }

   private boolean incrementTilled() {
      if (!withinCumulativeMutationBudget(this.tilled, 1, 320)) {
         this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
         return false;
      } else {
         this.tilled++;
         return true;
      }
   }

   private static FarmTaskReason resourceUnavailableReason(SetupFarmTask.ResourceStage stage) {
      return switch (stage) {
         case ENSURE_BUILD_DIRT, ENSURE_REPAIR_DIRT -> FarmTaskReason.DIRT_UNAVAILABLE;
         case ENSURE_BUCKET -> FarmTaskReason.BUCKET_UNAVAILABLE;
         case ACQUIRE_WATER -> FarmTaskReason.WATER_SOURCE_NOT_FOUND;
         case ENSURE_HOE -> FarmTaskReason.HOE_UNAVAILABLE;
         case ENSURE_REPAIR_TOOLS -> FarmTaskReason.REPAIR_TOOL_UNAVAILABLE;
         case REACQUIRE_HOE -> FarmTaskReason.HOE_REACQUIRE_FAILED;
         case COMPLETE -> FarmTaskReason.INTERNAL_CONTRACT_FAILURE;
      };
   }

   private boolean currentResourceRequirementMet() {
      return switch (this.resourceStage) {
         case ENSURE_BUILD_DIRT -> this.count(Items.DIRT) >= this.sitePlan.requiredDirt();
         case ENSURE_BUCKET -> this.count(Items.WATER_BUCKET) >= 1 || this.count(Items.BUCKET) >= 1;
         case ACQUIRE_WATER -> this.count(Items.WATER_BUCKET) >= 1;
         case ENSURE_HOE -> this.remainingHoeDurability() >= this.remainingTillTargets();
         case ENSURE_REPAIR_TOOLS -> this.requiredRepairTools != null && this.requiredRepairTools.isSatisfied(this.controller.getInventory());
         case ENSURE_REPAIR_DIRT -> this.count(Items.DIRT) >= this.requiredRepairDirt;
         case REACQUIRE_HOE -> this.remainingHoeDurability() >= Math.max(1, this.remainingTillTargets());
         case COMPLETE -> true;
      };
   }

   private int remainingTillTargets() {
      if (this.sitePlan == null) {
         return 0;
      } else {
         return this.tillPlanReady ? Math.max(0, this.activeTillTargets.size() - this.tillIndex) : 80;
      }
   }

   private int remainingHoeDurability() {
      return FarmHoePolicy.remainingDurability(this.controller.getInventory());
   }

   private int count(Item item) {
      return this.controller.getItemStorage().getItemCount(item);
   }

   private static String stateFingerprint(BlockState state) {
      return FarmSiteWorldView.stateFingerprint(state);
   }

   private boolean requireOpenStance(BlockPos stance) {
      if (this.controller.getChunkTracker().isChunkLoaded(stance)
         && this.controller.getChunkTracker().isChunkLoaded(stance.above())
         && this.controller.getChunkTracker().isChunkLoaded(stance.below())) {
         BlockPos supportPos = stance.below();
         if (!FarmStanceNavigation.hasPlannerSafeLiveGeometry(this.controller, stance)) {
            this.fail(FarmTaskReason.MANIFEST_DRIFT);
            return false;
         }

         PlayerPlacedBlockStore store = PlayerPlacedBlockStore.get();
         if (store != null
            && !FarmStanceNavigation.stanceProtectionDenied(
               store.contains(this.commandDimension, supportPos),
               store.contains(this.commandDimension, stance),
               FarmStanceNavigation.isPassableCropStance(this.controller, stance),
               store.contains(this.commandDimension, stance.above())
            )) {
            return true;
         }

         this.fail(store == null ? FarmTaskReason.STORE_UNAVAILABLE : FarmTaskReason.MANIFEST_DRIFT);
         return false;
      } else {
         this.fail(FarmTaskReason.CHUNK_UNLOADED);
         return false;
      }
   }

   private boolean siteChunksLoaded() {
      if (this.sitePlan == null) {
         return false;
      }

      for (BlockPos position : this.sitePlan.snapshot().positions()) {
         if (!this.controller.getChunkTracker().isChunkLoaded(position)) {
            return false;
         }
      }

      return true;
   }

   private void protectSiteForAcquisition() {
      if (!this.acquisitionProtectionPushed) {
         Set<BlockPos> frozen = new HashSet<>(this.sitePlan.snapshot().positions());
         this.controller.getBehaviour().push();
         this.acquisitionProtectionPushed = true;
         this.controller.getBehaviour().avoidBlockBreaking(frozen::contains);
         this.controller.getBehaviour().avoidBlockPlacing(frozen::contains);
      }
   }

   private void releaseAcquisitionProtection() {
      if (this.acquisitionProtectionPushed && this.controller != null) {
         this.controller.getBehaviour().pop();
         this.acquisitionProtectionPushed = false;
      }
   }

   private void enterPhase(FarmTaskPhase next) {
      this.phase = Objects.requireNonNull(next, "next");
      this.watchdog.enterPhase(next);
      this.mutationChild = null;
   }

   private String currentDimension() {
      return this.controller.getWorld().dimension().location().toString();
   }

   private BlockPos resolvedCenter() {
      if (this.sitePlan != null) {
         return this.sitePlan.center();
      } else {
         return this.resumeCenter == null ? this.exactCenter : this.resumeCenter;
      }
   }

   private void succeed() {
      this.terminal = true;
      this.successful = true;
      this.failureReason = FarmTaskReason.NONE;
      this.phase = FarmTaskPhase.DONE;
      this.terminalOutcome = new FarmTaskOutcome(
         true,
         true,
         FarmTaskReason.NONE,
         FarmTaskPhase.DONE,
         this.commandDimension,
         this.sitePlan.center(),
         this.cleared,
         this.filled,
         this.tilled,
         this.sourcePlaced,
         this.waypointResult
      );
      this.updateProgress();
   }

   private void fail(FarmTaskReason reason) {
      if (!this.terminal) {
         FarmTaskReason requested = Objects.requireNonNull(reason, "reason");
         if (this.phase == FarmTaskPhase.ACQUIRE_RESOURCES
            && this.sitePlan != null
            && this.resourceReturnMode == SetupFarmTask.ResourceReturnMode.NONE
            && requested != FarmTaskReason.CANCELLED_OPERATOR) {
            if (this.controller != null && Objects.equals(this.commandDimension, this.currentDimension()) && this.sitePlan.acquisitionReturnStance() != null) {
               this.beginResourceReturn(SetupFarmTask.ResourceReturnMode.FINALIZE_FAILURE, requested);
               return;
            }

            this.resourceReturnStatus = FarmReturnStatus.UNAVAILABLE;
         }

         this.finalizeFailure(requested);
      }
   }

   private void finalizeFailure(FarmTaskReason reason) {
      if (!this.terminal) {
         FarmTaskReason requestedReason = Objects.requireNonNull(reason, "reason");
         FarmTaskReason receiptFailure = this.reconcileIssuedReceiptsForTerminalOutcome();
         this.terminal = true;
         this.successful = false;
         this.failureReason = receiptFailure == FarmTaskReason.NONE ? requestedReason : receiptFailure;
         FarmTaskPhase failurePhase = this.phase != FarmTaskPhase.DONE
               && (this.phase != FarmTaskPhase.PRECHECK || this.failureReason != FarmTaskReason.INTERNAL_CONTRACT_FAILURE)
            ? this.phase
            : FarmTaskPhase.FAILED;
         this.terminalOutcome = new FarmTaskOutcome(
            true,
            false,
            this.failureReason,
            failurePhase,
            this.commandDimension,
            this.resolvedCenter(),
            this.cleared,
            this.filled,
            this.tilled,
            this.sourcePlaced,
            this.waypointResult,
            this.resourceReturnStatus
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
   public boolean prepareForTransientResume(TaskSuspensionCause cause) {
      Objects.requireNonNull(cause, "cause");
      if (this.terminal) {
         return false;
      }

      if (!this.reconcileMutationReceiptBeforeSuspend()) {
         this.quiesceMovementForStop();
         return false;
      }

      if (this.sitePlan != null) {
         this.resumeCenter = this.sitePlan.center().immutable();
      }

      this.resumeStartPending = true;
      this.quiesceMovementForStop();
      return true;
   }

   @Override
   public void onTransientResumeAbandoned() {
      this.resumeStartPending = false;
      if (!this.terminal) {
         this.fail(FarmTaskReason.CANCELLED_OPERATOR);
      }

      this.quiesceMovementForStop();
   }

   private boolean reconcileMutationReceiptBeforeSuspend() {
      if (this.pendingMutationReceipt != null) {
         if (!this.settleMutationReceipt(this.pendingMutationReceipt)) {
            this.fail(receiptReason(this.pendingMutationReceipt));
            return false;
         }

         if (this.pendingMutationReceipt.isFinished() && !this.consumeFinishedMutation(this.pendingMutationReceipt)) {
            return false;
         }
      }

      if (this.mutationChild == null) {
         return true;
      }

      if (!this.settleMutationReceipt(this.mutationChild)) {
         this.fail(receiptReason(this.mutationChild));
         return false;
      }

      if (!this.mutationChild.isFinished()) {
         FarmMutationReceipt receipt = (FarmMutationReceipt)this.mutationChild;
         if (receipt.hasIssuedMutation()) {
            receipt.prepareForTransientDetach();
            this.pendingMutationReceipt = this.mutationChild;
            this.pendingMutationReceiptTicks = 0;
         }

         return true;
      } else {
         return this.consumeFinishedMutation(this.mutationChild);
      }
   }

   private boolean settleRetainedMutationReceiptOnResume() {
      Task retained = this.pendingMutationReceipt;
      if (retained == null) {
         return true;
      }

      if (!this.settleMutationReceipt(retained)) {
         this.fail(receiptReason(retained));
         this.pendingMutationReceipt = null;
         return false;
      }

      if (retained.isFinished()) {
         if (!this.consumeFinishedMutation(retained)) {
            return false;
         }

         this.pendingMutationReceiptTicks = 0;
         this.watchdog.markProgress();
         return true;
      } else {
         this.pendingMutationReceiptTicks++;
         if (this.pendingMutationReceiptTicks <= 3) {
            this.setDebugState("Settling farm mutation receipt after interruption");
            return false;
         } else {
            this.pendingMutationReceipt = null;
            this.pendingMutationReceiptTicks = 0;
            return true;
         }
      }
   }

   private boolean settleMutationReceipt(Task child) {
      return child instanceof FarmMutationReceipt receipt && receipt.settlePendingTransition();
   }

   private static FarmTaskReason receiptReason(Task child) {
      return child instanceof FarmMutationReceipt receipt && receipt.reason() != FarmTaskReason.NONE
         ? receipt.reason()
         : FarmTaskReason.INTERNAL_CONTRACT_FAILURE;
   }

   private boolean consumeFinishedMutation(Task child) {
      FarmTaskReason accountingFailure = this.accountFinishedMutation(child);
      if (accountingFailure != FarmTaskReason.NONE) {
         this.fail(accountingFailure);
         return false;
      } else {
         return true;
      }
   }

   private FarmTaskReason accountFinishedMutation(Task child) {
      FarmTaskReason result = FarmTaskReason.NONE;
      if (!(child instanceof FarmMutationReceipt receipt && child.isFinished() && receipt.isSuccessful())) {
         result = receiptReason(child);
      } else if (child instanceof VerifiedBreakBlockTask) {
         if (!withinCumulativeMutationBudget(this.cleared, 1, 1040)) {
            result = FarmTaskReason.INTERNAL_CONTRACT_FAILURE;
         } else {
            this.cleared++;
         }
      } else if (child instanceof PlaceFarmDirtTask) {
         if (!withinCumulativeMutationBudget(this.filled, 1, 64)) {
            result = FarmTaskReason.INTERNAL_CONTRACT_FAILURE;
         } else {
            this.filled++;
         }
      } else if (child instanceof PlaceFarmWaterTask) {
         this.sourcePlaced = true;
      } else if (child instanceof TillFarmBlockTask till) {
         if (till.freshTill()) {
            if (!withinCumulativeMutationBudget(this.tilled, 1, 320)) {
               result = FarmTaskReason.INTERNAL_CONTRACT_FAILURE;
            } else {
               this.tilled++;
            }
         }
      } else {
         result = FarmTaskReason.INTERNAL_CONTRACT_FAILURE;
      }

      if (this.mutationChild == child) {
         this.mutationChild = null;
      }

      if (this.pendingMutationReceipt == child) {
         this.pendingMutationReceipt = null;
      }

      return result;
   }

   private FarmTaskReason reconcileIssuedReceiptsForTerminalOutcome() {
      Task retained = this.pendingMutationReceipt;
      Task active = this.mutationChild;
      this.pendingMutationReceipt = null;
      this.pendingMutationReceiptTicks = 0;
      this.mutationChild = null;
      FarmTaskReason retainedFailure = this.settleIssuedReceiptForTerminal(retained);
      FarmTaskReason activeFailure = active == retained ? FarmTaskReason.NONE : this.settleIssuedReceiptForTerminal(active);
      return retainedFailure != FarmTaskReason.NONE ? retainedFailure : activeFailure;
   }

   private FarmTaskReason settleIssuedReceiptForTerminal(Task child) {
      if (!(child instanceof FarmMutationReceipt receipt && receipt.hasIssuedMutation())) {
         return FarmTaskReason.NONE;
      } else if (!receipt.settlePendingTransition()) {
         return receiptReason(child);
      } else {
         return child.isFinished() ? this.accountFinishedMutation(child) : FarmTaskReason.NONE;
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.resumeStartPending = false;
      if (!this.terminal) {
         this.fail(FarmTaskReason.CANCELLED_OPERATOR);
      }

      this.quiesceMovementForStop();
   }

   @Override
   public void afterChildrenStopped() {
      this.releaseAcquisitionProtection();
   }

   private void quiesceMovementForStop() {
      if (this.controller != null) {
         this.controller.getInputControls().release(Input.CLICK_LEFT);
         this.controller.getInputControls().release(Input.CLICK_RIGHT);
         this.controller.getBaritone().getPathingBehavior().forceCancel();
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

   public FarmTaskOutcome outcome() {
      return this.terminalOutcome != null
         ? this.terminalOutcome
         : new FarmTaskOutcome(
            false,
            false,
            FarmTaskReason.NONE,
            this.phase,
            this.commandDimension,
            this.resolvedCenter(),
            this.cleared,
            this.filled,
            this.tilled,
            this.sourcePlaced,
            this.waypointResult
         );
   }

   public SetupFarmTask.Progress progress() {
      return new SetupFarmTask.Progress(
         this.phase,
         this.resolvedCenter(),
         this.cleared,
         this.clearTotal,
         this.filled,
         this.fillTotal,
         this.tilled,
         this.tillTotal,
         this.resourceTicks,
         this.sitePlan != null && this.sitePlan.waterRequired()
      );
   }

   public String describeProgress() {
      SetupFarmTask.Progress current = this.progress();
      if (this.terminal && !this.successful) {
         return "farm setup failed: " + this.failureReason.controlledReason();
      }

      return switch (current.phase()) {
         case PRECHECK -> "checking farm prerequisites";
         case SELECT_SITE -> "selecting a safe farm site";
         case ACQUIRE_RESOURCES -> "acquiring farm resources";
         case REVALIDATE -> "revalidating the selected farm site";
         case CLEAR, OPEN_CENTER -> "clearing farm blocks " + current.cleared() + "/" + current.clearTotal();
         case FILL -> "filling farm gaps " + current.filled() + "/" + current.fillTotal();
         case REGISTER_PREPARED -> "saving the prepared farm return anchor";
         case REPAIR -> "validating and repairing the returned farm plot";
         case PLACE_WATER -> "placing the farm water source";
         case TILL -> "tilling farm soil " + current.tilled() + "/" + current.tillTotal();
         case VERIFY -> "verifying the completed farm";
         case COMMIT -> "refreshing the completed farm waypoint";
         case DONE -> "farm setup complete";
         case FAILED -> "farm setup failed";
         default -> "farm setup in progress";
      };
   }

   public FarmSitePlan sitePlan() {
      return this.sitePlan;
   }

   public BlockPos commandAnchor() {
      return this.commandAnchor;
   }

   public String commandDimension() {
      return this.commandDimension;
   }

   public BlockPos exactCenter() {
      return this.exactCenter;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof SetupFarmTask task
         && task.commandAnchor.equals(this.commandAnchor)
         && task.commandDimension.equals(this.commandDimension)
         && Objects.equals(task.exactCenter, this.exactCenter);
   }

   @Override
   protected String toDebugString() {
      return this.exactCenter == null
         ? "Set up deterministic farm near " + this.commandAnchor.toShortString()
         : "Set up deterministic farm at " + this.exactCenter.toShortString();
   }

   record ExactCenterResolution(SetupFarmTask.ExactCenterStatus status, SetupFarmTask.RecordedResumeResolution recorded) {
      ExactCenterResolution {
         Objects.requireNonNull(status, "status");
         if (status == SetupFarmTask.ExactCenterStatus.RECORDED != (recorded != null)) {
            throw new IllegalArgumentException("only an exact recorded farm may carry a durable identity");
         }
      }

      static SetupFarmTask.ExactCenterResolution status(SetupFarmTask.ExactCenterStatus status) {
         return new SetupFarmTask.ExactCenterResolution(status, null);
      }

      static SetupFarmTask.ExactCenterResolution recorded(WaypointRecord record, BlockPos center) {
         return new SetupFarmTask.ExactCenterResolution(
            SetupFarmTask.ExactCenterStatus.RECORDED, SetupFarmTask.RecordedResumeResolution.selected(record, center)
         );
      }
   }

   enum ExactCenterStatus {
      NEW_FORCED,
      RECORDED,
      UNLOADED,
      TYPE_CONFLICT,
      UNSUPPORTED_DATA;
   }

   private record PreparedFarmHandle(String id, String dimension, BlockPos center) {
      private PreparedFarmHandle(String id, String dimension, BlockPos center) {
         if (id != null && !id.isBlank() && dimension != null && !dimension.isBlank()) {
            center = Objects.requireNonNull(center, "center").immutable();
            this.id = id;
            this.dimension = dimension;
            this.center = center;
         } else {
            throw new IllegalArgumentException("prepared farm identity cannot be blank");
         }
      }
   }

   public record Progress(
      FarmTaskPhase phase,
      BlockPos center,
      int cleared,
      int clearTotal,
      int filled,
      int fillTotal,
      int tilled,
      int tillTotal,
      int resourceTicks,
      boolean waterRequired
   ) {
      public Progress {
         Objects.requireNonNull(phase, "phase");
         if (center != null) {
            center = center.immutable();
         }
      }
   }

   private record RecordedFarmCandidate(WaypointRecord record, BlockPos center, long spatialDistanceSquared) {
      private RecordedFarmCandidate {
         Objects.requireNonNull(record, "record");
         center = Objects.requireNonNull(center, "center").immutable();
      }
   }

   record RecordedResumeResolution(SetupFarmTask.RecordedResumeStatus status, String id, String dimension, BlockPos center) {
      RecordedResumeResolution(SetupFarmTask.RecordedResumeStatus status, String id, String dimension, BlockPos center) {
         Objects.requireNonNull(status, "status");
         if (status == SetupFarmTask.RecordedResumeStatus.SELECTED) {
            if (id == null || id.isBlank() || dimension == null || dimension.isBlank()) {
               throw new IllegalArgumentException("selected recorded farm identity is required");
            }

            center = Objects.requireNonNull(center, "center").immutable();
         } else if (id != null || dimension != null || center != null) {
            throw new IllegalArgumentException("non-selected recorded farm cannot carry an identity");
         }

         this.status = status;
         this.id = id;
         this.dimension = dimension;
         this.center = center;
      }

      static SetupFarmTask.RecordedResumeResolution none() {
         return new SetupFarmTask.RecordedResumeResolution(SetupFarmTask.RecordedResumeStatus.NONE, null, null, null);
      }

      static SetupFarmTask.RecordedResumeResolution unloaded() {
         return new SetupFarmTask.RecordedResumeResolution(SetupFarmTask.RecordedResumeStatus.UNLOADED, null, null, null);
      }

      static SetupFarmTask.RecordedResumeResolution selected(WaypointRecord record, BlockPos center) {
         return new SetupFarmTask.RecordedResumeResolution(SetupFarmTask.RecordedResumeStatus.SELECTED, record.id, record.dimension, center);
      }
   }

   enum RecordedResumeStatus {
      NONE,
      SELECTED,
      UNLOADED;
   }

   enum RepairPassAction {
      CONTINUE_BREAKS,
      RESCAN_BEFORE_FILL,
      CONTINUE_FILLS;
   }

   record RepairPassTransition(SetupFarmTask.RepairPassAction action, int nextBreakIndex, int nextFillIndex) {
   }

   private enum ResourcePurpose {
      PREPARE_SITE,
      COMPLETE_SETUP,
      REPLACE_HOE,
      REPAIR_PREFLIGHT;
   }

   private enum ResourceReturnMode {
      NONE,
      CONTINUE_SETUP,
      FINALIZE_FAILURE;
   }

   enum ResourceStage {
      ENSURE_BUILD_DIRT,
      ENSURE_BUCKET,
      ACQUIRE_WATER,
      ENSURE_HOE,
      ENSURE_REPAIR_TOOLS,
      ENSURE_REPAIR_DIRT,
      REACQUIRE_HOE,
      COMPLETE;
   }
}
