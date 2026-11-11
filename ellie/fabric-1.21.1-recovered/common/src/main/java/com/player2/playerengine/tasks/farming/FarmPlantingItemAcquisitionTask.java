package com.player2.playerengine.tasks.farming;

import com.player2.playerengine.automaton.api.utils.input.Input;
import com.player2.playerengine.containeraccess.ScanReportFormatter;
import com.player2.playerengine.structureprotection.PlayerPlacedBlockStore;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.container.BoundedContainerTransferTask;
import com.player2.playerengine.tasks.crafting.DescribesProgress;
import com.player2.playerengine.tasks.movement.GetToBlockTask;
import com.player2.playerengine.tasks.movement.GetToEntityTask;
import com.player2.playerengine.tasks.movement.TimeoutWanderTask;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.core.registries.BuiltInRegistries;

public final class FarmPlantingItemAcquisitionTask extends Task implements DescribesProgress {
   public static final int WHOLE_TIMEOUT_TICKS = 18000;
   public static final int MAX_WORLD_ROUNDS = 16;
   public static final int WORLD_ROUND_WANDER_TICKS = 600;
   public static final long WORLD_ROUND_WANDER_MS = 30000L;
   public static final int PICKUP_TIMEOUT_TICKS = 60;
   public static final double PICKUP_RADIUS_BLOCKS = 8.0;
   public static final double DROP_OWNERSHIP_RADIUS_BLOCKS = 2.0;
   public static final int MAX_PREEXISTING_DROP_IDS = 64;
   public static final int MAX_OWNED_DROP_IDS = 16;
   private final Item plantingItem;
   private final int absoluteTargetCount;
   private final BlockPos frozenOrigin;
   private final String dimensionId;
   private final FarmPlantingItemAcquisitionTask.Checkpoint checkpoint;
   private BoundedContainerTransferTask transferChild;
   private VerifiedBreakBlockTask breakChild;
   private TimeoutWanderTask wanderChild;
   private boolean detachedCancelled;

   public FarmPlantingItemAcquisitionTask(Item plantingItem, int absoluteTargetCount, BlockPos frozenOrigin, String dimensionId) {
      this(plantingItem, absoluteTargetCount, frozenOrigin, dimensionId, new FarmPlantingItemAcquisitionTask.Checkpoint());
   }

   public FarmPlantingItemAcquisitionTask(
      Item plantingItem, int absoluteTargetCount, BlockPos frozenOrigin, String dimensionId, FarmPlantingItemAcquisitionTask.Checkpoint checkpoint
   ) {
      this.plantingItem = Objects.requireNonNull(plantingItem, "plantingItem");
      if (absoluteTargetCount >= 1 && absoluteTargetCount <= 80) {
         this.absoluteTargetCount = absoluteTargetCount;
         this.frozenOrigin = Objects.requireNonNull(frozenOrigin, "frozenOrigin").immutable();
         if (dimensionId != null && !dimensionId.isBlank()) {
            this.dimensionId = dimensionId;
            this.checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
            checkpoint.bind(plantingItem, absoluteTargetCount, this.frozenOrigin, dimensionId, 18000);
         } else {
            throw new IllegalArgumentException("dimensionId cannot be blank");
         }
      } else {
         throw new IllegalArgumentException("absoluteTargetCount must be within the farm soil-cell bound");
      }
   }

   public FarmPlantingItemAcquisitionTask.Checkpoint checkpoint() {
      return this.checkpoint;
   }

   public FarmPlantingItemAcquisitionTask.Phase phase() {
      return this.checkpoint.phase;
   }

   public FarmPlantingItemAcquisitionTask.Outcome outcome() {
      return this.detachedCancelled && this.checkpoint.outcome == FarmPlantingItemAcquisitionTask.Outcome.RUNNING
         ? FarmPlantingItemAcquisitionTask.Outcome.CANCELLED
         : this.checkpoint.outcome;
   }

   public boolean isSuccessful() {
      return this.checkpoint.outcome == FarmPlantingItemAcquisitionTask.Outcome.ACQUIRED;
   }

   public int acquiredCount() {
      return this.checkpoint.lastObservedInventoryCount;
   }

   public int remainingCount() {
      return Math.max(0, this.absoluteTargetCount - this.acquiredCount());
   }

   public String controlledReason() {
      return switch (this.outcome()) {
         case RUNNING -> "planting-item acquisition is still running";
         case ACQUIRED -> "the requested planting items were acquired";
         case SOURCE_EXHAUSTED -> "the bounded planting-item sources were exhausted";
         case WORLD_GUARDS_UNAVAILABLE -> "world sources were skipped because protection data was unavailable";
         case SOURCE_DISCOVERY_FAILED -> "planting-item source discovery failed";
         case DEADLINE_EXCEEDED -> "planting-item acquisition reached its whole-operation deadline";
         case DIMENSION_CHANGED -> "planting-item acquisition stopped after a dimension change";
         case CANCELLED -> "planting-item acquisition was cancelled";
      };
   }

   @Override
   public boolean isFinished() {
      return this.checkpoint.outcome != FarmPlantingItemAcquisitionTask.Outcome.RUNNING || this.detachedCancelled;
   }

   @Override
   protected void onStart() {
      this.transferChild = null;
      this.breakChild = null;
      this.wanderChild = null;
      this.detachedCancelled = false;
      this.setDebugState(this.checkpoint.phase.name());
   }

   @Override
   protected Task onTick() {
      if (this.checkpoint.outcome != FarmPlantingItemAcquisitionTask.Outcome.RUNNING) {
         return null;
      }

      this.checkpoint.wholeTicks++;
      this.settleCompletedMutationChildren();
      if (!this.dimensionId.equals(this.currentDimension())) {
         if (this.checkpoint.terminalRequest == null) {
            this.beginTerminalSettlement(FarmPlantingItemAcquisitionTask.Outcome.DIMENSION_CHANGED);
         } else {
            this.fail(FarmPlantingItemAcquisitionTask.Outcome.DIMENSION_CHANGED);
         }

         return null;
      } else {
         Task receiptNavigation = this.reconcilePendingReceipts();
         this.updateObservedInventory();
         if (this.checkpoint.terminalRequest != null) {
            if (receiptNavigation != null) {
               if (this.checkpoint.terminalRequest == FarmPlantingItemAcquisitionTask.Outcome.ACQUIRED
                  && this.checkpoint.wholeTicks < this.checkpoint.wholeTickBudget) {
                  return receiptNavigation;
               }

               this.fail(
                  this.checkpoint.terminalRequest == FarmPlantingItemAcquisitionTask.Outcome.ACQUIRED
                     ? FarmPlantingItemAcquisitionTask.Outcome.DEADLINE_EXCEEDED
                     : this.checkpoint.terminalRequest
               );
               return null;
            } else {
               this.finalizeTerminalSettlement();
               return null;
            }
         } else {
            if (this.checkpoint.lastObservedInventoryCount >= this.absoluteTargetCount) {
               this.beginTerminalSettlement(FarmPlantingItemAcquisitionTask.Outcome.ACQUIRED);
               return null;
            }

            if (this.checkpoint.wholeTicks >= this.checkpoint.wholeTickBudget) {
               this.beginTerminalSettlement(FarmPlantingItemAcquisitionTask.Outcome.DEADLINE_EXCEEDED);
               return null;
            }

            if (receiptNavigation != null) {
               return receiptNavigation;
            }

            if (this.checkpoint.outcome != FarmPlantingItemAcquisitionTask.Outcome.RUNNING) {
               return null;
            }

            this.setDebugState(this.checkpoint.phase.name());

            return switch (this.checkpoint.phase) {
               case CHECK_INVENTORY -> this.advanceFromInventory();
               case FREEZE_SOURCES -> this.freezeSources();
               case MARKED_CHESTS -> this.processMarkedChests();
               case DISCOVER_WORLD_ROUND -> this.discoverWorldRound();
               case NATURAL_GRASS -> this.processNaturalGrass();
               case VILLAGE_CROPS -> this.processVillageCrops();
               case VILLAGE_CHESTS -> this.processVillageChests();
               case PICKUP_DROPS -> this.processPickup();
               case WANDER_BETWEEN_ROUNDS -> this.processWorldRoundWander();
               case DONE, FAILED -> null;
            };
         }
      }
   }

   private Task advanceFromInventory() {
      this.checkpoint.phase = FarmPlantingItemAcquisitionTask.Phase.FREEZE_SOURCES;
      return null;
   }

   private Task freezeSources() {
      if (!this.checkpoint.candidatesFrozen) {
         try {
            this.checkpoint.freezeMarked(PlantableSourcePlanner.freezeMarked(this.controller, this.plantingItem, this.frozenOrigin, this.dimensionId));
         } catch (RuntimeException discoveryFailure) {
            this.fail(FarmPlantingItemAcquisitionTask.Outcome.SOURCE_DISCOVERY_FAILED);
            return null;
         }
      }

      this.checkpoint.phase = FarmPlantingItemAcquisitionTask.Phase.MARKED_CHESTS;
      return null;
   }

   private Task processMarkedChests() {
      if (this.transferChild != null && this.checkpoint.pendingTransfer != null) {
         return this.processTransfer(FarmPlantingItemAcquisitionTask.Phase.MARKED_CHESTS, this.checkpoint.pendingTransfer.position());
      } else {
         List<BlockPos> candidates = this.checkpoint.markedManifest.markedChests();
         if (this.checkpoint.markedCursor >= candidates.size()) {
            this.checkpoint.phase = FarmPlantingItemAcquisitionTask.Phase.DISCOVER_WORLD_ROUND;
            return null;
         } else {
            return this.processTransfer(FarmPlantingItemAcquisitionTask.Phase.MARKED_CHESTS, candidates.get(this.checkpoint.markedCursor));
         }
      }
   }

   private Task discoverWorldRound() {
      if (this.checkpoint.worldRoundManifest != null) {
         this.fail(FarmPlantingItemAcquisitionTask.Outcome.SOURCE_DISCOVERY_FAILED);
         return null;
      }

      if (this.checkpoint.worldRoundsDiscovered >= 16) {
         this.fail(FarmPlantingItemAcquisitionTask.Outcome.SOURCE_EXHAUSTED);
         return null;
      }

      try {
         BlockPos scanOrigin = this.controller.getEntity().blockPosition().immutable();
         PlantableSourcePlanner.WorldRoundManifest round = PlantableSourcePlanner.freezeWorldRound(
            this.controller,
            this.plantingItem,
            scanOrigin,
            this.dimensionId,
            this.checkpoint.attemptedNaturalGrass,
            this.checkpoint.attemptedVillageCrops,
            this.checkpoint.attemptedVillageChests,
            this.checkpoint.naturalScanCursor,
            this.checkpoint.cropScanCursor,
            this.checkpoint.villageChestScanCursor
         );
         if (!round.worldGuardsAvailable()) {
            this.fail(FarmPlantingItemAcquisitionTask.Outcome.WORLD_GUARDS_UNAVAILABLE);
            return null;
         }

         this.checkpoint.freezeWorldRound(round);
         this.checkpoint.phase = this.plantingItem == Items.WHEAT_SEEDS
            ? FarmPlantingItemAcquisitionTask.Phase.NATURAL_GRASS
            : FarmPlantingItemAcquisitionTask.Phase.VILLAGE_CROPS;
      } catch (RuntimeException discoveryFailure) {
         this.fail(FarmPlantingItemAcquisitionTask.Outcome.SOURCE_DISCOVERY_FAILED);
      }

      return null;
   }

   private Task processNaturalGrass() {
      PlantableSourcePlanner.WorldRoundManifest round = this.requireWorldRound();
      return round == null
         ? null
         : this.processBreakStage(
            round.naturalGrass(), FarmPlantingItemAcquisitionTask.Phase.NATURAL_GRASS, FarmPlantingItemAcquisitionTask.Phase.VILLAGE_CROPS
         );
   }

   private Task processVillageCrops() {
      PlantableSourcePlanner.WorldRoundManifest round = this.requireWorldRound();
      return round == null
         ? null
         : this.processBreakStage(
            round.villageCrops(), FarmPlantingItemAcquisitionTask.Phase.VILLAGE_CROPS, FarmPlantingItemAcquisitionTask.Phase.VILLAGE_CHESTS
         );
   }

   private Task processWorldRoundWander() {
      if (this.checkpoint.worldRoundManifest != null) {
         this.fail(FarmPlantingItemAcquisitionTask.Outcome.SOURCE_DISCOVERY_FAILED);
         return null;
      }

      if (this.checkpoint.wanderTicksInLeg >= 600) {
         this.finishWanderLeg();
         return null;
      }

      this.checkpoint.wanderTicksInLeg++;
      if (this.wanderChild != null) {
         if (!this.wanderChild.isFinished() && !this.wanderChild.stopped()) {
            return this.wanderChild;
         }

         this.finishWanderLeg();
         return null;
      } else {
         long remainingMs = Math.max(50L, (600 - this.checkpoint.wanderTicksInLeg + 1) * 50L);
         this.wanderChild = TimeoutWanderTask.bounded(Math.min(30000L, remainingMs));
         return this.wanderChild;
      }
   }

   private void finishWorldRound() {
      this.checkpoint.clearWorldRoundForWander();
      if (this.checkpoint.worldRoundsDiscovered >= 16) {
         this.fail(FarmPlantingItemAcquisitionTask.Outcome.SOURCE_EXHAUSTED);
      } else {
         this.checkpoint.phase = FarmPlantingItemAcquisitionTask.Phase.WANDER_BETWEEN_ROUNDS;
      }
   }

   private void finishWanderLeg() {
      this.stopExploreProcess();
      this.wanderChild = null;
      this.checkpoint.wanderTicksInLeg = 0;
      this.checkpoint.phase = FarmPlantingItemAcquisitionTask.Phase.DISCOVER_WORLD_ROUND;
   }

   private PlantableSourcePlanner.WorldRoundManifest requireWorldRound() {
      if (this.checkpoint.worldRoundManifest == null) {
         this.fail(FarmPlantingItemAcquisitionTask.Outcome.SOURCE_DISCOVERY_FAILED);
         return null;
      } else {
         return this.checkpoint.worldRoundManifest;
      }
   }

   private Task processVillageChests() {
      if (!this.worldSourcesPermitted()) {
         return null;
      } else {
         PlantableSourcePlanner.WorldRoundManifest round = this.requireWorldRound();
         if (round == null) {
            return null;
         } else if (this.transferChild != null && this.checkpoint.pendingTransfer != null) {
            return this.processTransfer(FarmPlantingItemAcquisitionTask.Phase.VILLAGE_CHESTS, this.checkpoint.pendingTransfer.position());
         } else {
            List<PlantableSourcePlanner.ChestCandidate> candidates = round.villageChests();
            if (this.checkpoint.villageChestCursor >= candidates.size()) {
               this.finishWorldRound();
               return null;
            } else {
               PlantableSourcePlanner.ChestCandidate candidate = candidates.get(this.checkpoint.villageChestCursor);
               if (!PlantableSourcePlanner.isLiveVillageChestCandidate(this.controller.getWorld(), this.dimensionId, candidate)) {
                  this.advanceVillageChestCursor(candidate.position());
                  return null;
               } else {
                  return this.processTransfer(FarmPlantingItemAcquisitionTask.Phase.VILLAGE_CHESTS, candidate.position());
               }
            }
         }
      }
   }

   private Task processTransfer(FarmPlantingItemAcquisitionTask.Phase phase, BlockPos position) {
      if (phase == FarmPlantingItemAcquisitionTask.Phase.VILLAGE_CHESTS && !this.isLiveVillageChest(position)) {
         this.captureTransferChild();
         this.transferChild = null;
         Task reconciliation = this.reconcilePendingReceipts();
         if (this.checkpoint.pendingTransfer == null
            && this.checkpoint.worldRoundManifest != null
            && this.checkpoint.villageChestCursor < this.checkpoint.worldRoundManifest.villageChests().size()) {
            PlantableSourcePlanner.ChestCandidate candidate = this.checkpoint.worldRoundManifest.villageChests().get(this.checkpoint.villageChestCursor);
            if (candidate.position().equals(position)) {
               this.advanceVillageChestCursor(position);
            }
         }

         return reconciliation;
      } else if (this.transferChild != null) {
         if (!this.transferChild.isFinished() && !this.transferChild.stopped()) {
            return this.transferChild;
         }

         this.captureTransferChild();
         this.transferChild = null;
         return this.reconcilePendingReceipts();
      } else {
         if (this.getSub() != null) {
            return null;
         }

         int remaining = Math.max(1, this.absoluteTargetCount - this.inventoryCount());
         this.checkpoint.pendingTransfer = new FarmPlantingItemAcquisitionTask.TransferReceipt(phase, position, this.inventoryCount(), false, 0);
         this.transferChild = BoundedContainerTransferTask.withdrawUpTo(position, this.plantingItem, remaining);
         return this.transferChild;
      }
   }

   private Task processBreakStage(
      List<PlantableSourcePlanner.BreakCandidate> candidates, FarmPlantingItemAcquisitionTask.Phase phase, FarmPlantingItemAcquisitionTask.Phase exhaustedPhase
   ) {
      if (!this.worldSourcesPermitted()) {
         return null;
      }

      if (this.breakChild != null) {
         this.refreshPendingBreakFromChild(false);
         if (!this.breakChild.isFinished() && !this.breakChild.stopped()) {
            return this.breakChild;
         }

         this.breakChild = null;
         return this.reconcilePendingReceipts();
      } else if (this.getSub() == null) {
         int cursor = phase == FarmPlantingItemAcquisitionTask.Phase.NATURAL_GRASS ? this.checkpoint.naturalCursor : this.checkpoint.cropCursor;
         if (cursor >= candidates.size()) {
            this.checkpoint.phase = exhaustedPhase;
            return null;
         } else {
            PlantableSourcePlanner.BreakCandidate candidate = candidates.get(cursor);
            if (!this.controller.getWorld().hasChunkAt(candidate.position())) {
               this.setDebugState(phase.name() + ":returning-to-frozen-source");
               return new GetToBlockTask(candidate.position());
            } else if (!PlantableSourcePlanner.isLiveBreakCandidate(this.controller.getWorld(), this.plantingItem, this.dimensionId, candidate)) {
               this.advanceBreakCursor(candidate);
               return null;
            } else {
               BlockPos stance = this.selectStance(candidate.position());
               if (stance == null) {
                  this.advanceBreakCursor(candidate);
                  return null;
               } else {
                  Set<UUID> preExisting = this.preExistingDropIds(candidate.position());
                  if (preExisting == null) {
                     this.advanceBreakCursor(candidate);
                     return null;
                  } else {
                     this.checkpoint.pendingBreak = new FarmPlantingItemAcquisitionTask.BreakReceipt(
                        candidate, this.inventoryCount(), preExisting, false, false, false
                     );
                     this.breakChild = VerifiedBreakBlockTask.forUnprotectedSource(
                        candidate.position(), stance, candidate.expectedState(), this.plantingItem, this.dimensionId, candidate.kind()
                     );
                     return this.breakChild;
                  }
               }
            }
         }
      } else {
         return !this.getSub().isFinished() && !this.getSub().stopped() ? this.getSub() : null;
      }
   }

   private Task processPickup() {
      FarmPlantingItemAcquisitionTask.PickupReceipt receipt = this.checkpoint.pickupReceipt;
      if (receipt == null) {
         this.fail(FarmPlantingItemAcquisitionTask.Outcome.SOURCE_DISCOVERY_FAILED);
         return null;
      } else if (receipt.ticks() >= 60) {
         this.checkpoint.pickupReceipt = null;
         this.checkpoint.phase = receipt.returnPhase();
         return null;
      } else {
         this.checkpoint.pickupReceipt = receipt.nextTick();
         ItemEntity drop = this.nearestOwnedDrop(receipt);
         return drop == null ? null : new GetToEntityTask(drop);
      }
   }

   private Task reconcilePendingReceipts() {
      FarmPlantingItemAcquisitionTask.TransferReceipt transfer = this.checkpoint.pendingTransfer;
      if (transfer != null && this.transferChild == null) {
         boolean inventoryRose = this.inventoryCount() > transfer.inventoryBaseline();
         this.checkpoint.pendingTransfer = null;
         this.checkpoint.phase = transfer.phase();
         if (transferReceiptAllowsAdvance(transfer.terminal(), transfer.moved(), inventoryRose)) {
            this.advanceTransferCursor(transfer.phase(), transfer.position());
         }
      }

      FarmPlantingItemAcquisitionTask.BreakReceipt broken = this.checkpoint.pendingBreak;
      if (broken != null && this.breakChild == null) {
         BlockPos position = broken.candidate().position();
         if (!this.controller.getWorld().hasChunkAt(position)) {
            this.setDebugState("reconciling-frozen-break-receipt");
            return new GetToBlockTask(position);
         }

         BlockState live = this.controller.getWorld().getBlockState(position);
         boolean inventoryRose = this.inventoryCount() > broken.inventoryBaseline();
         boolean worldChanged = !live.equals(broken.candidate().expectedState());
         boolean ownsMutation = breakReceiptOwnsMutation(broken.successful(), broken.issued(), live.isAir(), inventoryRose);
         this.checkpoint.pendingBreak = null;
         this.checkpoint.phase = phaseFor(broken.candidate().kind());
         if (broken.terminal()) {
            this.advanceBreakCursor(broken.candidate());
            if (ownsMutation) {
               this.beginPickup(broken);
            }
         } else if (ownsMutation) {
            this.advanceBreakCursor(broken.candidate());
            this.beginPickup(broken);
         } else if (worldChanged) {
            this.advanceBreakCursor(broken.candidate());
         }

         return null;
      } else {
         return null;
      }
   }

   static boolean transferReceiptAllowsAdvance(boolean terminal, int authoritativeMoved, boolean inventoryRose) {
      if (authoritativeMoved < 0) {
         throw new IllegalArgumentException("authoritativeMoved cannot be negative");
      } else {
         return terminal || authoritativeMoved > 0;
      }
   }

   static boolean breakReceiptOwnsMutation(boolean childSuccessful, boolean issued, boolean verifiedTargetRemoved, boolean inventoryRose) {
      return childSuccessful || issued && verifiedTargetRemoved;
   }

   private void beginPickup(FarmPlantingItemAcquisitionTask.BreakReceipt receipt) {
      FarmPlantingItemAcquisitionTask.Phase returnPhase = phaseFor(receipt.candidate().kind());
      Set<UUID> ownedDropIds = this.newlyOwnedDropIds(receipt);
      if (ownedDropIds != null && !ownedDropIds.isEmpty()) {
         this.checkpoint.pickupReceipt = new FarmPlantingItemAcquisitionTask.PickupReceipt(returnPhase, receipt.candidate().position(), ownedDropIds, 0);
         this.checkpoint.phase = FarmPlantingItemAcquisitionTask.Phase.PICKUP_DROPS;
      } else {
         this.checkpoint.pickupReceipt = null;
         this.checkpoint.phase = returnPhase;
      }
   }

   private void captureTransferChild() {
      FarmPlantingItemAcquisitionTask.TransferReceipt receipt = this.checkpoint.pendingTransfer;
      if (receipt != null && this.transferChild != null) {
         BoundedContainerTransferTask.Outcome transferOutcome = this.transferChild.outcome();
         int moved = this.transferChild.entryOutcomes().stream().mapToInt(ScanReportFormatter.EntryOutcome::moved).sum();
         this.checkpoint.pendingTransfer = new FarmPlantingItemAcquisitionTask.TransferReceipt(
            receipt.phase(), receipt.position(), receipt.inventoryBaseline(), transferOutcome != null || this.transferChild.isFinished(), moved
         );
      }
   }

   private void settleCompletedMutationChildren() {
      if (this.transferChild != null && (this.transferChild.isFinished() || this.transferChild.stopped())) {
         this.captureTransferChild();
         this.transferChild = null;
      }

      if (this.breakChild != null) {
         this.refreshPendingBreakFromChild(false);
         if (this.breakChild.isFinished() || this.breakChild.stopped()) {
            this.breakChild = null;
         }
      }
   }

   private void beginTerminalSettlement(FarmPlantingItemAcquisitionTask.Outcome requested) {
      if (requested == FarmPlantingItemAcquisitionTask.Outcome.RUNNING) {
         throw new IllegalArgumentException("terminal settlement outcome required");
      }

      this.checkpoint.terminalRequest = requested;
      this.captureTransferChild();
      this.refreshPendingBreakFromChild(true);
      this.transferChild = null;
      this.breakChild = null;
      this.wanderChild = null;
      this.stopExploreProcess();
      this.quiesce();
   }

   private void finalizeTerminalSettlement() {
      FarmPlantingItemAcquisitionTask.Outcome requested = this.checkpoint.terminalRequest;
      this.checkpoint.terminalRequest = null;
      this.updateObservedInventory();
      if ((requested == FarmPlantingItemAcquisitionTask.Outcome.ACQUIRED || requested == FarmPlantingItemAcquisitionTask.Outcome.DEADLINE_EXCEEDED)
         && this.checkpoint.lastObservedInventoryCount >= this.absoluteTargetCount) {
         this.succeed();
      } else if (requested == FarmPlantingItemAcquisitionTask.Outcome.ACQUIRED) {
         if (this.checkpoint.wholeTicks >= this.checkpoint.wholeTickBudget) {
            this.fail(FarmPlantingItemAcquisitionTask.Outcome.DEADLINE_EXCEEDED);
         }
      } else {
         this.fail(requested);
      }
   }

   private void refreshPendingBreakFromChild(boolean transientDetach) {
      FarmPlantingItemAcquisitionTask.BreakReceipt receipt = this.checkpoint.pendingBreak;
      if (receipt != null && this.breakChild != null) {
         if (transientDetach) {
            this.breakChild.prepareForTransientDetach();
         }

         this.breakChild.settlePendingTransition();
         this.checkpoint.pendingBreak = new FarmPlantingItemAcquisitionTask.BreakReceipt(
            receipt.candidate(),
            receipt.inventoryBaseline(),
            receipt.preExistingDropIds(),
            receipt.issued() || this.breakChild.hasIssuedMutation(),
            this.breakChild.isFinished(),
            this.breakChild.isSuccessful()
         );
      }
   }

   private boolean worldSourcesPermitted() {
      if (this.checkpoint.worldRoundManifest != null && this.checkpoint.worldRoundManifest.worldGuardsAvailable() && PlayerPlacedBlockStore.get() != null) {
         return true;
      }

      this.beginTerminalSettlement(FarmPlantingItemAcquisitionTask.Outcome.WORLD_GUARDS_UNAVAILABLE);
      return false;
   }

   private BlockPos selectStance(BlockPos target) {
      PlayerPlacedBlockStore playerPlaced = PlayerPlacedBlockStore.get();
      if (playerPlaced == null) {
         return null;
      }

      for (Direction direction : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
         BlockPos stance = target.relative(direction).immutable();
         if (FarmStanceNavigation.hasSafeLiveGeometry(this.controller, stance)
            && !playerPlaced.contains(this.dimensionId, stance.below())
            && !playerPlaced.contains(this.dimensionId, stance)
            && !playerPlaced.contains(this.dimensionId, stance.above())) {
            return stance;
         }
      }

      return null;
   }

   private Set<UUID> preExistingDropIds(BlockPos sourcePosition) {
      List<ItemEntity> drops = this.boundedSourceDrops(sourcePosition, entity -> entity.isAlive() && entity.getItem().is(this.plantingItem));
      if (drops.size() > 64) {
         return null;
      }

      LinkedHashSet<UUID> ids = new LinkedHashSet<>();
      drops.stream().sorted(Comparator.comparing(entity -> entity.getUUID().toString())).forEach(entity -> ids.add(entity.getUUID()));
      return Set.copyOf(ids);
   }

   private ItemEntity nearestOwnedDrop(FarmPlantingItemAcquisitionTask.PickupReceipt receipt) {
      Vec3 player = this.controller.getEntity().position();
      List<ItemEntity> candidates = this.boundedSourceDrops(
         receipt.sourcePosition(),
         entity -> entity.isAlive() && entity.getItem().is(this.plantingItem) && receipt.ownedDropIds().contains(entity.getUUID())
      );
      return candidates.size() > 64
         ? null
         : candidates.stream()
            .min(
               Comparator.<ItemEntity>comparingDouble(entity -> entity.position().distanceToSqr(player))
                  .thenComparing(entity -> entity.getUUID().toString())
            )
            .orElse(null);
   }

   private Set<UUID> newlyOwnedDropIds(FarmPlantingItemAcquisitionTask.BreakReceipt receipt) {
      Vec3 center = Vec3.atCenterOf(receipt.candidate().position());
      double ownershipRadiusSq = 4.0;
      List<UUID> ids = this.boundedSourceDrops(
            receipt.candidate().position(),
            entity -> entity.isAlive()
               && entity.getItem().is(this.plantingItem)
               && !receipt.preExistingDropIds().contains(entity.getUUID())
               && entity.position().distanceToSqr(center) <= ownershipRadiusSq
         )
         .stream()
         .<UUID>map(Entity::getUUID)
         .sorted(Comparator.comparing(UUID::toString))
         .toList();
      return ids.size() > 16 ? null : Set.copyOf(ids);
   }

   private List<ItemEntity> boundedSourceDrops(BlockPos sourcePosition, Predicate<ItemEntity> predicate) {
      Vec3 center = Vec3.atCenterOf(sourcePosition);
      AABB bounds = AABB.ofSize(center, 16.0, 16.0, 16.0);
      ArrayList<ItemEntity> drops = new ArrayList<>(65);
      double radiusSq = 64.0;
      this.controller
         .getWorld()
         .getEntities(
            EntityTypeTest.forClass(ItemEntity.class),
            bounds,
            entity -> entity.position().distanceToSqr(center) <= radiusSq && predicate.test(entity),
            drops,
            65
         );
      return drops;
   }

   private void advanceTransferCursor(FarmPlantingItemAcquisitionTask.Phase phase, BlockPos position) {
      if (phase == FarmPlantingItemAcquisitionTask.Phase.MARKED_CHESTS) {
         this.checkpoint.markedCursor++;
      } else {
         if (phase != FarmPlantingItemAcquisitionTask.Phase.VILLAGE_CHESTS) {
            throw new IllegalArgumentException("not a transfer phase: " + phase);
         }

         this.advanceVillageChestCursor(position);
      }
   }

   private void advanceVillageChestCursor(BlockPos position) {
      this.checkpoint.attemptedVillageChests.add(Objects.requireNonNull(position, "position").immutable());
      this.checkpoint.villageChestCursor++;
   }

   private void advanceBreakCursor(PlantableSourcePlanner.BreakCandidate candidate) {
      if (candidate.kind() == PlantableSourcePlanner.SourceKind.NATURAL_GRASS) {
         this.checkpoint.attemptedNaturalGrass.add(candidate.position());
         this.checkpoint.naturalCursor++;
      } else {
         if (candidate.kind() != PlantableSourcePlanner.SourceKind.VILLAGE_CROP) {
            throw new IllegalArgumentException("not a break source: " + candidate.kind());
         }

         this.checkpoint.attemptedVillageCrops.add(candidate.position());
         this.checkpoint.cropCursor++;
      }
   }

   private boolean isLiveVillageChest(BlockPos position) {
      PlantableSourcePlanner.WorldRoundManifest round = this.checkpoint.worldRoundManifest;
      if (round != null && this.checkpoint.villageChestCursor < round.villageChests().size()) {
         PlantableSourcePlanner.ChestCandidate candidate = round.villageChests().get(this.checkpoint.villageChestCursor);
         if (!candidate.position().equals(position)) {
            return false;
         }

         double distanceSq = this.controller
            .getEntity()
            .position()
            .distanceToSqr(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5);
         return distanceSq > 64.0 || PlantableSourcePlanner.isLiveVillageChestCandidate(this.controller.getWorld(), this.dimensionId, candidate);
      } else {
         return false;
      }
   }

   private static FarmPlantingItemAcquisitionTask.Phase phaseFor(PlantableSourcePlanner.SourceKind kind) {
      return kind == PlantableSourcePlanner.SourceKind.NATURAL_GRASS
         ? FarmPlantingItemAcquisitionTask.Phase.NATURAL_GRASS
         : FarmPlantingItemAcquisitionTask.Phase.VILLAGE_CROPS;
   }

   private int inventoryCount() {
      return this.controller == null
         ? this.checkpoint.lastObservedInventoryCount
         : this.controller.getItemStorage().getItemCountInventoryOnly(this.plantingItem);
   }

   private void updateObservedInventory() {
      this.checkpoint.lastObservedInventoryCount = this.inventoryCount();
   }

   private String currentDimension() {
      return this.controller != null && this.controller.getWorld() != null ? this.controller.getWorld().dimension().location().toString() : "";
   }

   private void succeed() {
      this.updateObservedInventory();
      this.checkpoint.terminalRequest = null;
      this.checkpoint.outcome = FarmPlantingItemAcquisitionTask.Outcome.ACQUIRED;
      this.checkpoint.phase = FarmPlantingItemAcquisitionTask.Phase.DONE;
      this.checkpoint.pendingTransfer = null;
      this.checkpoint.pendingBreak = null;
      this.checkpoint.pickupReceipt = null;
      this.wanderChild = null;
      this.stopExploreProcess();
      this.setDebugState("DONE");
   }

   private void fail(FarmPlantingItemAcquisitionTask.Outcome failure) {
      if (failure != FarmPlantingItemAcquisitionTask.Outcome.RUNNING && failure != FarmPlantingItemAcquisitionTask.Outcome.ACQUIRED) {
         this.updateObservedInventory();
         this.checkpoint.terminalRequest = null;
         this.checkpoint.outcome = failure;
         this.checkpoint.phase = FarmPlantingItemAcquisitionTask.Phase.FAILED;
         this.checkpoint.pendingTransfer = null;
         this.checkpoint.pendingBreak = null;
         this.checkpoint.pickupReceipt = null;
         this.wanderChild = null;
         this.stopExploreProcess();
         this.setDebugState("FAILED:" + failure.name());
      } else {
         throw new IllegalArgumentException("failure outcome required");
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.captureTransferChild();
      this.refreshPendingBreakFromChild(interruptTask != null);
      this.quiesce();
      this.transferChild = null;
      this.breakChild = null;
      this.wanderChild = null;
      this.detachedCancelled = interruptTask == null && this.checkpoint.outcome == FarmPlantingItemAcquisitionTask.Outcome.RUNNING;
   }

   private void quiesce() {
      if (this.controller != null) {
         this.controller.getInputControls().release(Input.CLICK_LEFT);
         this.controller.getInputControls().release(Input.CLICK_RIGHT);
         this.controller.getBaritone().getPathingBehavior().forceCancel();
         if (this.controller.getBaritone().getCustomGoalProcess().isActive()) {
            this.controller.getBaritone().getCustomGoalProcess().onLostControl();
         }

         this.stopExploreProcess();
      }
   }

   private void stopExploreProcess() {
      if (this.controller != null && this.controller.getBaritone().getExploreProcess().isActive()) {
         this.controller.getBaritone().getExploreProcess().onLostControl();
      }
   }

   @Override
   public String describeProgress() {
      return "planting-item-acquisition item="
         + this.checkpoint.targetItemId
         + " phase="
         + this.checkpoint.phase.name().toLowerCase(Locale.ROOT)
         + " have="
         + this.acquiredCount()
         + " target="
         + this.absoluteTargetCount
         + " ticks="
         + this.checkpoint.wholeTicks;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof FarmPlantingItemAcquisitionTask task
         && task.plantingItem == this.plantingItem
         && task.absoluteTargetCount == this.absoluteTargetCount
         && task.frozenOrigin.equals(this.frozenOrigin)
         && task.dimensionId.equals(this.dimensionId)
         && task.checkpoint == this.checkpoint;
   }

   @Override
   protected String toDebugString() {
      return "FarmPlantingItemAcquisition[" + this.checkpoint.targetItemId + " -> " + this.absoluteTargetCount + "]";
   }

   private record BreakReceipt(
      PlantableSourcePlanner.BreakCandidate candidate,
      int inventoryBaseline,
      Set<UUID> preExistingDropIds,
      boolean issued,
      boolean terminal,
      boolean successful
   ) {
      private BreakReceipt {
         candidate = Objects.requireNonNull(candidate, "candidate");
         preExistingDropIds = Set.copyOf(preExistingDropIds == null ? Set.of() : preExistingDropIds);
      }
   }

   public static final class Checkpoint {
      private String targetItemId;
      private int absoluteTargetCount = -1;
      private BlockPos frozenOrigin;
      private String dimensionId;
      private int wholeTickBudget;
      private int wholeTicks;
      private int lastObservedInventoryCount;
      private boolean candidatesFrozen;
      private PlantableSourcePlanner.MarkedManifest markedManifest;
      private PlantableSourcePlanner.WorldRoundManifest worldRoundManifest;
      private FarmPlantingItemAcquisitionTask.Phase phase = FarmPlantingItemAcquisitionTask.Phase.CHECK_INVENTORY;
      private FarmPlantingItemAcquisitionTask.Outcome outcome = FarmPlantingItemAcquisitionTask.Outcome.RUNNING;
      private FarmPlantingItemAcquisitionTask.Outcome terminalRequest;
      private int markedCursor;
      private int naturalCursor;
      private int cropCursor;
      private int villageChestCursor;
      private int worldRoundsDiscovered;
      private int wanderTicksInLeg;
      private int naturalScanCursor;
      private int cropScanCursor;
      private int villageChestScanCursor;
      private final LinkedHashSet<BlockPos> attemptedNaturalGrass = new LinkedHashSet<>();
      private final LinkedHashSet<BlockPos> attemptedVillageCrops = new LinkedHashSet<>();
      private final LinkedHashSet<BlockPos> attemptedVillageChests = new LinkedHashSet<>();
      private FarmPlantingItemAcquisitionTask.TransferReceipt pendingTransfer;
      private FarmPlantingItemAcquisitionTask.BreakReceipt pendingBreak;
      private FarmPlantingItemAcquisitionTask.PickupReceipt pickupReceipt;

      public boolean isBound() {
         return this.absoluteTargetCount > 0;
      }

      public String targetItemId() {
         return this.targetItemId;
      }

      public int absoluteTargetCount() {
         return this.absoluteTargetCount;
      }

      public BlockPos frozenOrigin() {
         return this.frozenOrigin;
      }

      public String dimensionId() {
         return this.dimensionId;
      }

      public boolean candidatesFrozen() {
         return this.candidatesFrozen;
      }

      public int wholeTicks() {
         return this.wholeTicks;
      }

      public int markedCursor() {
         return this.markedCursor;
      }

      public int naturalCursor() {
         return this.naturalCursor;
      }

      public int cropCursor() {
         return this.cropCursor;
      }

      public int villageChestCursor() {
         return this.villageChestCursor;
      }

      public int worldRoundsDiscovered() {
         return this.worldRoundsDiscovered;
      }

      public int wanderTicksInLeg() {
         return this.wanderTicksInLeg;
      }

      public int attemptedNaturalGrassCount() {
         return this.attemptedNaturalGrass.size();
      }

      public int attemptedVillageCropCount() {
         return this.attemptedVillageCrops.size();
      }

      public int attemptedVillageChestCount() {
         return this.attemptedVillageChests.size();
      }

      public int naturalScanCursor() {
         return this.naturalScanCursor;
      }

      public int cropScanCursor() {
         return this.cropScanCursor;
      }

      public int villageChestScanCursor() {
         return this.villageChestScanCursor;
      }

      private void bind(Item item, int targetCount, BlockPos origin, String dimension, int tickBudget) {
         ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
         if (key == null) {
            throw new IllegalArgumentException("planting item must have a registry id");
         }

         String itemId = key.toString();
         if (!this.isBound()) {
            this.targetItemId = itemId;
            this.absoluteTargetCount = targetCount;
            this.frozenOrigin = origin.immutable();
            this.dimensionId = dimension;
            this.wholeTickBudget = tickBudget;
         } else if (!this.targetItemId.equals(itemId)
            || this.absoluteTargetCount != targetCount
            || !this.frozenOrigin.equals(origin)
            || !this.dimensionId.equals(dimension)
            || this.wholeTickBudget != tickBudget) {
            throw new IllegalArgumentException("planting acquisition checkpoint cannot be rebound");
         }
      }

      private void freezeMarked(PlantableSourcePlanner.MarkedManifest frozenManifest) {
         if (!this.candidatesFrozen) {
            this.markedManifest = Objects.requireNonNull(frozenManifest, "frozenManifest");
            this.candidatesFrozen = true;
         }
      }

      private void freezeWorldRound(PlantableSourcePlanner.WorldRoundManifest frozenManifest) {
         if (this.worldRoundManifest != null) {
            throw new IllegalStateException("world round is already frozen");
         }

         this.worldRoundManifest = Objects.requireNonNull(frozenManifest, "frozenManifest");
         this.naturalCursor = 0;
         this.cropCursor = 0;
         this.villageChestCursor = 0;
         this.naturalScanCursor = frozenManifest.nextNaturalScanCursor();
         this.cropScanCursor = frozenManifest.nextCropScanCursor();
         this.villageChestScanCursor = frozenManifest.nextVillageChestScanCursor();
         this.worldRoundsDiscovered++;
      }

      private void clearWorldRoundForWander() {
         this.worldRoundManifest = null;
         this.naturalCursor = 0;
         this.cropCursor = 0;
         this.villageChestCursor = 0;
         this.wanderTicksInLeg = 0;
      }
   }

   public enum Outcome {
      RUNNING,
      ACQUIRED,
      SOURCE_EXHAUSTED,
      WORLD_GUARDS_UNAVAILABLE,
      SOURCE_DISCOVERY_FAILED,
      DEADLINE_EXCEEDED,
      DIMENSION_CHANGED,
      CANCELLED;
   }

   public enum Phase {
      CHECK_INVENTORY,
      FREEZE_SOURCES,
      MARKED_CHESTS,
      DISCOVER_WORLD_ROUND,
      NATURAL_GRASS,
      VILLAGE_CROPS,
      VILLAGE_CHESTS,
      PICKUP_DROPS,
      WANDER_BETWEEN_ROUNDS,
      DONE,
      FAILED;
   }

   private record PickupReceipt(FarmPlantingItemAcquisitionTask.Phase returnPhase, BlockPos sourcePosition, Set<UUID> ownedDropIds, int ticks) {
      private PickupReceipt {
         if (returnPhase != FarmPlantingItemAcquisitionTask.Phase.NATURAL_GRASS && returnPhase != FarmPlantingItemAcquisitionTask.Phase.VILLAGE_CROPS) {
            throw new IllegalArgumentException("pickup return phase must be a break phase");
         }

         sourcePosition = Objects.requireNonNull(sourcePosition, "sourcePosition").immutable();
         ownedDropIds = Set.copyOf(ownedDropIds == null ? Set.of() : ownedDropIds);
         if (ticks < 0) {
            throw new IllegalArgumentException("pickup ticks cannot be negative");
         }
      }

      private FarmPlantingItemAcquisitionTask.PickupReceipt nextTick() {
         return new FarmPlantingItemAcquisitionTask.PickupReceipt(this.returnPhase, this.sourcePosition, this.ownedDropIds, this.ticks + 1);
      }
   }

   private record TransferReceipt(FarmPlantingItemAcquisitionTask.Phase phase, BlockPos position, int inventoryBaseline, boolean terminal, int moved) {
      private TransferReceipt {
         if (phase != FarmPlantingItemAcquisitionTask.Phase.MARKED_CHESTS && phase != FarmPlantingItemAcquisitionTask.Phase.VILLAGE_CHESTS) {
            throw new IllegalArgumentException("transfer receipt phase must be a chest phase");
         }

         position = Objects.requireNonNull(position, "position").immutable();
      }
   }
}
