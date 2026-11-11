package com.player2.playerengine.tasks.agentic;

import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.containeraccess.ScanReportFormatter;
import com.player2.playerengine.containeraccess.StorageItemArgs;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.container.BoundedContainerTransferTask;
import com.player2.playerengine.tasks.farming.FarmHoePolicy;
import com.player2.playerengine.tasks.farming.FarmTaskReason;
import com.player2.playerengine.util.helpers.ItemHelper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class FarmHoeAcquisitionTask extends Task {
   private final int requiredDurability;
   private final BlockPos frozenCenter;
   private final String dimensionId;
   private final FarmHoeAcquisitionTask.Checkpoint checkpoint;
   private FarmHoeAcquisitionTask.Phase phase = FarmHoeAcquisitionTask.Phase.CHECK_INVENTORY;
   private FarmHoeAcquisitionTask.Outcome outcome = FarmHoeAcquisitionTask.Outcome.RUNNING;
   private FarmTaskReason reason = FarmTaskReason.NONE;
   private BoundedContainerTransferTask withdrawChild;
   private Task craftChild;

   public FarmHoeAcquisitionTask(int requiredDurability, BlockPos frozenCenter, String dimensionId, FarmHoeAcquisitionTask.Checkpoint checkpoint) {
      if (requiredDurability >= 1 && requiredDurability <= 80) {
         this.requiredDurability = requiredDurability;
         this.frozenCenter = Objects.requireNonNull(frozenCenter, "frozenCenter").immutable();
         if (dimensionId != null && !dimensionId.isBlank()) {
            this.dimensionId = dimensionId;
            this.checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
            this.checkpoint.bind(requiredDurability, this.frozenCenter, dimensionId);
         } else {
            throw new IllegalArgumentException("dimensionId cannot be blank");
         }
      } else {
         throw new IllegalArgumentException("requiredDurability must be within the farm soil bound");
      }
   }

   public FarmHoeAcquisitionTask(int requiredDurability, BlockPos frozenCenter, String dimensionId) {
      this(requiredDurability, frozenCenter, dimensionId, new FarmHoeAcquisitionTask.Checkpoint());
   }

   @Override
   public boolean isFinished() {
      return this.outcome != FarmHoeAcquisitionTask.Outcome.RUNNING;
   }

   public boolean isSuccessful() {
      return this.outcome == FarmHoeAcquisitionTask.Outcome.ACQUIRED_FROM_INVENTORY
         || this.outcome == FarmHoeAcquisitionTask.Outcome.ACQUIRED_FROM_MARKED_STORAGE
         || this.outcome == FarmHoeAcquisitionTask.Outcome.ACQUIRED_FROM_CRAFTING;
   }

   public FarmHoeAcquisitionTask.Outcome outcome() {
      return this.outcome;
   }

   public FarmTaskReason reason() {
      return this.reason;
   }

   public FarmHoeAcquisitionTask.Checkpoint checkpoint() {
      return this.checkpoint;
   }

   @Override
   protected void onStart() {
      this.phase = FarmHoeAcquisitionTask.Phase.CHECK_INVENTORY;
      this.setDebugState(this.phase.name());
   }

   @Override
   protected Task onTick() {
      if (this.isFinished()) {
         return null;
      }

      if (this.hasRequiredDurability()) {
         this.succeed(this.observedSuccessSource());
         return null;
      }

      return switch (this.phase) {
         case CHECK_INVENTORY -> {
            this.phase = this.checkpoint.candidatesFrozen ? FarmHoeAcquisitionTask.Phase.WITHDRAW_MARKED : FarmHoeAcquisitionTask.Phase.SEARCH_MARKED;
            this.setDebugState(this.phase.name());
            yield null;
         }
         case SEARCH_MARKED -> this.searchMarked();
         case WITHDRAW_MARKED -> this.withdrawMarked();
         case CRAFT_FALLBACK -> this.craftFallback();
         case DONE, FAILED -> null;
      };
   }

   private Task searchMarked() {
      if (!this.checkpoint.candidatesFrozen) {
         this.checkpoint.freezeCandidates(this.discoverCandidates());
      }

      this.phase = FarmHoeAcquisitionTask.Phase.WITHDRAW_MARKED;
      this.setDebugState(this.phase.name());
      return null;
   }

   private List<FarmHoeAcquisitionTask.Candidate> discoverCandidates() {
      Vec3 origin = new Vec3(this.frozenCenter.getX() + 0.5, this.frozenCenter.getY() + 0.5, this.frozenCenter.getZ() + 0.5);
      List<FarmHoeAcquisitionTask.Candidate> discovered = new ArrayList<>();
      Set<FarmHoeAcquisitionTask.CandidateKey> seen = new HashSet<>();
      List<Item> items = FarmHoePolicy.markedStorageItems();

      for (int priority = 0; priority < items.size(); priority++) {
         Item item = items.get(priority);

         for (BlockPos position : MarkedChestToolLocator.candidateCoordinates(this.controller, item, origin, 96.0, this.dimensionId)) {
            FarmHoeAcquisitionTask.CandidateKey key = new FarmHoeAcquisitionTask.CandidateKey(position, item);
            if (seen.add(key)) {
               discovered.add(new FarmHoeAcquisitionTask.Candidate(position, item, priority));
            }
         }
      }

      discovered.sort(
         Comparator.<FarmHoeAcquisitionTask.Candidate>comparingLong(c -> distanceSquared(c.position(), this.frozenCenter))
            .thenComparingInt(FarmHoeAcquisitionTask.Candidate::itemPriority)
            .thenComparingInt(c -> c.position().getX())
            .thenComparingInt(c -> c.position().getY())
            .thenComparingInt(c -> c.position().getZ())
      );
      return discovered;
   }

   private Task withdrawMarked() {
      if (this.withdrawChild != null) {
         if (!this.withdrawChild.isFinished() && !this.withdrawChild.stopped()) {
            return this.withdrawChild;
         }

         BoundedContainerTransferTask.Outcome transferOutcome = this.withdrawChild.outcome();
         boolean moved = transferOutcome == BoundedContainerTransferTask.Outcome.MOVED_ALL
            || transferOutcome == BoundedContainerTransferTask.Outcome.MOVED_PARTIAL;
         if (transferOutcome != null) {
            this.checkpoint.completeTransferAttempt(moved);
         } else if (this.withdrawChild.stopped()) {
            this.checkpoint.retryStoppedTransfer();
         }

         this.withdrawChild = null;
         if (this.hasRequiredDurability()) {
            this.succeed(FarmHoeAcquisitionTask.Outcome.ACQUIRED_FROM_MARKED_STORAGE);
            return null;
         } else {
            return null;
         }
      } else if (this.getSub() != null) {
         return null;
      } else {
         FarmHoeAcquisitionTask.Candidate candidate = this.checkpoint.claimNextCandidate();
         if (candidate == null) {
            this.phase = FarmHoeAcquisitionTask.Phase.CRAFT_FALLBACK;
            this.setDebugState(this.phase.name());
            return null;
         } else {
            List<StorageItemArgs.ItemQuery> query = List.of(new StorageItemArgs.ItemQuery(candidate.item(), ItemHelper.stripItemName(candidate.item()), 1));
            this.withdrawChild = new BoundedContainerTransferTask(ScanReportFormatter.TransferDirection.WITHDRAW, candidate.position(), query);
            this.setDebugState(this.phase.name() + "@" + candidate.position().toShortString());
            return this.withdrawChild;
         }
      }
   }

   private Task craftFallback() {
      if (this.checkpoint.fallbackTerminalFailure) {
         this.failUnavailable();
         return null;
      }

      if (this.craftChild != null) {
         if (!this.craftChild.isFinished() && !this.craftChild.stopped()) {
            return this.craftChild;
         }

         this.craftChild = null;
         if (this.hasRequiredDurability()) {
            this.succeed(FarmHoeAcquisitionTask.Outcome.ACQUIRED_FROM_CRAFTING);
         } else {
            this.checkpoint.fallbackTerminalFailure = true;
            this.failUnavailable();
         }

         return null;
      } else {
         if (this.getSub() != null) {
            return null;
         }

         int target = this.checkpoint.fallbackTargetStoneHoeCount;
         if (target <= 0) {
            target = nextStoneHoeTarget(this.controller.getItemStorage().getItemCount(Items.STONE_HOE));
            this.checkpoint.commitFallbackTarget(target);
         }

         try {
            this.craftChild = TaskCatalogue.getItemTask(Items.STONE_HOE, target);
         } catch (RuntimeException catalogueFailure) {
            this.checkpoint.fallbackTerminalFailure = true;
            this.failUnavailable();
            return null;
         }

         if (this.craftChild == null) {
            this.checkpoint.fallbackTerminalFailure = true;
            this.failUnavailable();
            return null;
         } else {
            return this.craftChild;
         }
      }
   }

   private boolean hasRequiredDurability() {
      return this.controller != null && FarmHoePolicy.hasDurability(this.controller.getInventory(), this.requiredDurability);
   }

   private FarmHoeAcquisitionTask.Outcome observedSuccessSource() {
      if (this.checkpoint.fallbackCommitted()) {
         return FarmHoeAcquisitionTask.Outcome.ACQUIRED_FROM_CRAFTING;
      } else {
         return this.checkpoint.attemptedMarkedTransfers() > 0
            ? FarmHoeAcquisitionTask.Outcome.ACQUIRED_FROM_MARKED_STORAGE
            : FarmHoeAcquisitionTask.Outcome.ACQUIRED_FROM_INVENTORY;
      }
   }

   private void succeed(FarmHoeAcquisitionTask.Outcome successfulOutcome) {
      this.outcome = successfulOutcome;
      this.reason = FarmTaskReason.NONE;
      this.phase = FarmHoeAcquisitionTask.Phase.DONE;
      this.setDebugState(this.phase.name() + ":" + successfulOutcome.name());
   }

   private void failUnavailable() {
      this.outcome = FarmHoeAcquisitionTask.Outcome.UNAVAILABLE;
      this.reason = FarmTaskReason.HOE_UNAVAILABLE;
      this.phase = FarmHoeAcquisitionTask.Phase.FAILED;
      this.setDebugState(this.phase.name() + ":" + this.reason.controlledReason());
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.withdrawChild = null;
      this.craftChild = null;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof FarmHoeAcquisitionTask task
         && task.requiredDurability == this.requiredDurability
         && Objects.equals(task.frozenCenter, this.frozenCenter)
         && Objects.equals(task.dimensionId, this.dimensionId)
         && task.checkpoint == this.checkpoint;
   }

   @Override
   protected String toDebugString() {
      return "FarmHoeAcquisition[durability=" + this.requiredDurability + "]";
   }

   private static long distanceSquared(BlockPos first, BlockPos second) {
      long dx = (long)first.getX() - second.getX();
      long dy = (long)first.getY() - second.getY();
      long dz = (long)first.getZ() - second.getZ();
      return dx * dx + dy * dy + dz * dz;
   }

   public static int nextStoneHoeTarget(int currentStoneHoeCount) {
      if (currentStoneHoeCount < 0) {
         throw new IllegalArgumentException("current stone-hoe count cannot be negative");
      } else {
         return Math.addExact(currentStoneHoeCount, 1);
      }
   }

   private record Candidate(BlockPos position, Item item, int itemPriority) {
      private Candidate {
         position = position.immutable();
         Objects.requireNonNull(item, "item");
      }
   }

   private record CandidateKey(BlockPos position, Item item) {
   }

   public static final class Checkpoint {
      private int requiredDurability = -1;
      private BlockPos frozenCenter;
      private String dimensionId;
      private boolean candidatesFrozen;
      private List<FarmHoeAcquisitionTask.Candidate> candidates = List.of();
      private int candidateCursor;
      private int markedTransferAttempts;
      private FarmHoeAcquisitionTask.Candidate inFlightCandidate;
      private boolean transferAttemptInProgress;
      private int fallbackTargetStoneHoeCount;
      private boolean fallbackTerminalFailure;

      public boolean isBound() {
         return this.requiredDurability >= 0;
      }

      public int requiredDurability() {
         return this.requiredDurability;
      }

      public BlockPos frozenCenter() {
         return this.frozenCenter;
      }

      public String dimensionId() {
         return this.dimensionId;
      }

      public boolean candidatesFrozen() {
         return this.candidatesFrozen;
      }

      public int attemptedMarkedTransfers() {
         return this.markedTransferAttempts;
      }

      public boolean fallbackCommitted() {
         return this.fallbackTargetStoneHoeCount > 0;
      }

      private void bind(int required, BlockPos center, String dimension) {
         if (!this.isBound()) {
            this.requiredDurability = required;
            this.frozenCenter = center.immutable();
            this.dimensionId = dimension;
         } else if (this.requiredDurability != required || !Objects.equals(this.frozenCenter, center) || !Objects.equals(this.dimensionId, dimension)) {
            throw new IllegalArgumentException("farm hoe checkpoint cannot be rebound to a different acquisition");
         }
      }

      private void freezeCandidates(List<FarmHoeAcquisitionTask.Candidate> discovered) {
         if (!this.candidatesFrozen) {
            this.candidates = List.copyOf(discovered);
            this.candidatesFrozen = true;
         }
      }

      private FarmHoeAcquisitionTask.Candidate claimNextCandidate() {
         if (!this.candidatesFrozen) {
            return null;
         }

         if (this.inFlightCandidate == null) {
            if (this.candidateCursor >= this.candidates.size()) {
               return null;
            }

            this.inFlightCandidate = this.candidates.get(this.candidateCursor++);
         }

         if (!this.transferAttemptInProgress) {
            if (this.markedTransferAttempts >= 8) {
               return null;
            }

            this.markedTransferAttempts++;
            this.transferAttemptInProgress = true;
         }

         return this.inFlightCandidate;
      }

      private void completeTransferAttempt(boolean retainCandidate) {
         this.transferAttemptInProgress = false;
         if (!retainCandidate) {
            this.inFlightCandidate = null;
         }
      }

      private void retryStoppedTransfer() {
         this.transferAttemptInProgress = false;
      }

      private void commitFallbackTarget(int absoluteStoneHoeCount) {
         if (this.fallbackTargetStoneHoeCount <= 0) {
            this.fallbackTargetStoneHoeCount = absoluteStoneHoeCount;
         }
      }
   }

   public enum Outcome {
      RUNNING,
      ACQUIRED_FROM_INVENTORY,
      ACQUIRED_FROM_MARKED_STORAGE,
      ACQUIRED_FROM_CRAFTING,
      UNAVAILABLE;
   }

   private enum Phase {
      CHECK_INVENTORY,
      SEARCH_MARKED,
      WITHDRAW_MARKED,
      CRAFT_FALLBACK,
      DONE,
      FAILED;
   }
}
