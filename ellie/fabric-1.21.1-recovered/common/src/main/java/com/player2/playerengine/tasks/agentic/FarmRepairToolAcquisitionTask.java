package com.player2.playerengine.tasks.agentic;

import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.containeraccess.ScanReportFormatter;
import com.player2.playerengine.containeraccess.StorageItemArgs;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.container.BoundedContainerTransferTask;
import com.player2.playerengine.tasks.farming.FarmBreakToolRequirement;
import com.player2.playerengine.tasks.farming.FarmRepairToolPlan;
import com.player2.playerengine.tasks.farming.FarmTaskReason;
import com.player2.playerengine.util.helpers.ItemHelper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class FarmRepairToolAcquisitionTask extends Task {
   private final FarmRepairToolPlan plan;
   private final String planIdentity;
   private final BlockPos frozenCenter;
   private final String dimensionId;
   private final FarmRepairToolAcquisitionTask.Checkpoint checkpoint;
   private FarmRepairToolAcquisitionTask.Phase phase = FarmRepairToolAcquisitionTask.Phase.CHECK_INVENTORY;
   private FarmRepairToolAcquisitionTask.Outcome outcome = FarmRepairToolAcquisitionTask.Outcome.RUNNING;
   private FarmTaskReason reason = FarmTaskReason.NONE;
   private BoundedContainerTransferTask withdrawChild;
   private FarmRepairToolPlan.Requirement activeCraftRequirement;
   private Task craftChild;

   public FarmRepairToolAcquisitionTask(
      FarmRepairToolPlan plan, BlockPos frozenCenter, String dimensionId, FarmRepairToolAcquisitionTask.Checkpoint checkpoint
   ) {
      this.plan = Objects.requireNonNull(plan, "plan");
      this.planIdentity = Objects.requireNonNull(plan.identity(), "plan.identity()");
      if (this.planIdentity.isBlank()) {
         throw new IllegalArgumentException("plan identity cannot be blank");
      }

      this.frozenCenter = Objects.requireNonNull(frozenCenter, "frozenCenter").immutable();
      if (dimensionId != null && !dimensionId.isBlank()) {
         this.dimensionId = dimensionId;
         this.checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
         this.checkpoint.bind(this.planIdentity, this.frozenCenter, dimensionId);
      } else {
         throw new IllegalArgumentException("dimensionId cannot be blank");
      }
   }

   public FarmRepairToolAcquisitionTask(FarmRepairToolPlan plan, BlockPos frozenCenter, String dimensionId) {
      this(plan, frozenCenter, dimensionId, new FarmRepairToolAcquisitionTask.Checkpoint());
   }

   @Override
   public boolean isFinished() {
      return this.outcome != FarmRepairToolAcquisitionTask.Outcome.RUNNING;
   }

   public boolean isSuccessful() {
      return this.outcome == FarmRepairToolAcquisitionTask.Outcome.SATISFIED_FROM_INVENTORY
         || this.outcome == FarmRepairToolAcquisitionTask.Outcome.ACQUIRED_FROM_MARKED_STORAGE
         || this.outcome == FarmRepairToolAcquisitionTask.Outcome.ACQUIRED_FROM_CRAFTING;
   }

   public FarmRepairToolAcquisitionTask.Outcome outcome() {
      return this.outcome;
   }

   public FarmTaskReason reason() {
      return this.reason;
   }

   public FarmRepairToolAcquisitionTask.Checkpoint checkpoint() {
      return this.checkpoint;
   }

   @Override
   protected void onStart() {
      this.phase = FarmRepairToolAcquisitionTask.Phase.CHECK_INVENTORY;
      this.withdrawChild = null;
      this.activeCraftRequirement = null;
      this.craftChild = null;
      this.setDebugState(this.phase.name());
   }

   @Override
   protected Task onTick() {
      if (this.isFinished()) {
         return null;
      }

      LivingEntityInventory inventory = this.liveInventory();
      if (inventory == null) {
         this.failUnavailable();
         return null;
      }

      if (this.plan.isSatisfied(inventory)) {
         this.succeed(this.observedSuccessOutcome());
         return null;
      }

      if (this.plan.hasUnsatisfiedCustom(inventory)) {
         this.failUnavailable();
         return null;
      }

      return switch (this.phase) {
         case CHECK_INVENTORY -> {
            this.phase = this.checkpoint.candidatesFrozen
               ? FarmRepairToolAcquisitionTask.Phase.WITHDRAW_MARKED
               : FarmRepairToolAcquisitionTask.Phase.SEARCH_MARKED;
            this.setDebugState(this.phase.name());
            yield null;
         }
         case SEARCH_MARKED -> this.searchMarked(inventory);
         case WITHDRAW_MARKED -> this.withdrawMarked(inventory);
         case CRAFT_FALLBACK -> this.craftFallback(inventory);
         case DONE, FAILED -> null;
      };
   }

   private Task searchMarked(LivingEntityInventory inventory) {
      if (!this.checkpoint.candidatesFrozen) {
         List<FarmRepairToolPlan.Requirement> missing = this.missingStandardRequirements(inventory);
         if (missing == null) {
            this.failUnavailable();
            return null;
         }

         this.checkpoint.freezeCandidates(this.discoverCandidates(missing));
      }

      this.phase = FarmRepairToolAcquisitionTask.Phase.WITHDRAW_MARKED;
      this.setDebugState(this.phase.name());
      return null;
   }

   private List<FarmRepairToolAcquisitionTask.Candidate> discoverCandidates(List<FarmRepairToolPlan.Requirement> requirements) {
      Vec3 origin = new Vec3(this.frozenCenter.getX() + 0.5, this.frozenCenter.getY() + 0.5, this.frozenCenter.getZ() + 0.5);
      ArrayList<FarmRepairToolAcquisitionTask.Candidate> discovered = new ArrayList<>();
      Set<FarmRepairToolAcquisitionTask.CandidateKey> seen = new HashSet<>();

      for (int requirementPriority = 0; requirementPriority < requirements.size(); requirementPriority++) {
         FarmRepairToolPlan.Requirement requirement = requirements.get(requirementPriority);
         if (requirement != null) {
            FarmBreakToolRequirement.Family family = requirement.family();
            List<Item> items = requirement.markedStorageItems();
            if (items != null) {
               for (int itemPriority = 0; itemPriority < items.size(); itemPriority++) {
                  Item item = items.get(itemPriority);
                  if (item != null) {
                     for (BlockPos chest : MarkedChestToolLocator.candidateCoordinates(this.controller, item, origin, 96.0, this.dimensionId)) {
                        FarmRepairToolAcquisitionTask.CandidateKey key = new FarmRepairToolAcquisitionTask.CandidateKey(family, item, chest);
                        if (seen.add(key)) {
                           discovered.add(new FarmRepairToolAcquisitionTask.Candidate(family, item, chest, requirement, requirementPriority, itemPriority));
                        }
                     }
                  }
               }
            }
         }
      }

      discovered.sort(
         Comparator.<FarmRepairToolAcquisitionTask.Candidate>comparingLong(candidate -> distanceSquared(candidate.chest(), this.frozenCenter))
            .thenComparingInt(FarmRepairToolAcquisitionTask.Candidate::requirementPriority)
            .thenComparingInt(FarmRepairToolAcquisitionTask.Candidate::itemPriority)
            .thenComparingInt(candidate -> candidate.chest().getX())
            .thenComparingInt(candidate -> candidate.chest().getY())
            .thenComparingInt(candidate -> candidate.chest().getZ())
            .thenComparing(FarmRepairToolAcquisitionTask.Candidate::family)
      );
      return discovered;
   }

   private Task withdrawMarked(LivingEntityInventory inventory) {
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
         if (this.plan.isSatisfied(inventory)) {
            this.succeed(FarmRepairToolAcquisitionTask.Outcome.ACQUIRED_FROM_MARKED_STORAGE);
         }

         return null;
      } else {
         if (this.getSub() != null) {
            return null;
         }

         List<FarmRepairToolPlan.Requirement> missing = this.missingStandardRequirements(inventory);
         if (missing == null) {
            this.failUnavailable();
            return null;
         }

         Set<FarmBreakToolRequirement.Family> missingFamilies = new HashSet<>();

         for (FarmRepairToolPlan.Requirement requirement : missing) {
            if (requirement != null) {
               missingFamilies.add(requirement.family());
            }
         }

         FarmRepairToolAcquisitionTask.Candidate candidate = this.checkpoint.claimNextCandidate(missingFamilies);
         if (candidate == null) {
            this.phase = FarmRepairToolAcquisitionTask.Phase.CRAFT_FALLBACK;
            this.setDebugState(this.phase.name());
            return null;
         } else {
            List<StorageItemArgs.ItemQuery> query = List.of(new StorageItemArgs.ItemQuery(candidate.item(), ItemHelper.stripItemName(candidate.item()), 1));
            this.withdrawChild = new BoundedContainerTransferTask(ScanReportFormatter.TransferDirection.WITHDRAW, candidate.chest(), query);
            this.setDebugState(this.phase.name() + ":" + candidate.family().name());
            return this.withdrawChild;
         }
      }
   }

   private Task craftFallback(LivingEntityInventory inventory) {
      if (this.checkpoint.fallbackTerminalFailure) {
         this.failUnavailable();
         return null;
      }

      if (this.craftChild == null) {
         if (this.getSub() != null) {
            return null;
         }

         List<FarmRepairToolPlan.Requirement> missing = this.missingStandardRequirements(inventory);
         if (missing == null) {
            this.failUnavailable();
            return null;
         }

         FarmRepairToolPlan.Requirement requirement = firstMissing(missing);
         if (requirement == null) {
            if (this.plan.isSatisfied(inventory)) {
               this.succeed(this.observedSuccessOutcome());
            } else {
               this.failUnavailable();
            }

            return null;
         } else {
            FarmBreakToolRequirement.Family family = requirement.family();
            Item fallback = requirement.craftFallbackItem();
            if (fallback == null) {
               this.checkpoint.fallbackTerminalFailure = true;
               this.failUnavailable();
               return null;
            }

            try {
               FarmRepairToolAcquisitionTask.CraftTarget target = this.checkpoint
                  .craftTarget(family, fallback, this.controller.getItemStorage().getItemCount(fallback));
               this.craftChild = TaskCatalogue.getItemTask(fallback, target.absoluteCount());
            } catch (RuntimeException invalidFallback) {
               this.checkpoint.fallbackTerminalFailure = true;
               this.failUnavailable();
               return null;
            }

            if (this.craftChild == null) {
               this.checkpoint.fallbackTerminalFailure = true;
               this.failUnavailable();
               return null;
            } else {
               this.activeCraftRequirement = requirement;
               this.setDebugState(this.phase.name() + ":" + family.name());
               return this.craftChild;
            }
         }
      } else {
         if (!this.craftChild.isFinished() && !this.craftChild.stopped()) {
            return this.craftChild;
         }

         this.craftChild = null;
         FarmBreakToolRequirement.Family completedFamily = this.activeCraftRequirement == null ? null : this.activeCraftRequirement.family();
         this.activeCraftRequirement = null;
         List<FarmRepairToolPlan.Requirement> missing = this.missingStandardRequirements(inventory);
         boolean acquired = missing != null
            && completedFamily != null
            && missing.stream().noneMatch(requirementx -> requirementx != null && requirementx.family() == completedFamily);
         if (!acquired) {
            this.checkpoint.fallbackTerminalFailure = true;
            this.failUnavailable();
         }

         return null;
      }
   }

   private List<FarmRepairToolPlan.Requirement> missingStandardRequirements(LivingEntityInventory inventory) {
      try {
         return this.plan.missingStandardRequirements(inventory);
      } catch (RuntimeException invalidPlan) {
         return null;
      }
   }

   private static FarmRepairToolPlan.Requirement firstMissing(List<FarmRepairToolPlan.Requirement> requirements) {
      for (FarmRepairToolPlan.Requirement requirement : requirements) {
         if (requirement != null) {
            return requirement;
         }
      }

      return null;
   }

   private LivingEntityInventory liveInventory() {
      return this.controller == null ? null : this.controller.getInventory();
   }

   private FarmRepairToolAcquisitionTask.Outcome observedSuccessOutcome() {
      if (this.checkpoint.fallbackCommitted()) {
         return FarmRepairToolAcquisitionTask.Outcome.ACQUIRED_FROM_CRAFTING;
      } else {
         return this.checkpoint.markedTransferMoved
            ? FarmRepairToolAcquisitionTask.Outcome.ACQUIRED_FROM_MARKED_STORAGE
            : FarmRepairToolAcquisitionTask.Outcome.SATISFIED_FROM_INVENTORY;
      }
   }

   private void succeed(FarmRepairToolAcquisitionTask.Outcome successfulOutcome) {
      this.outcome = successfulOutcome;
      this.reason = FarmTaskReason.NONE;
      this.phase = FarmRepairToolAcquisitionTask.Phase.DONE;
      this.setDebugState(this.phase.name() + ":" + successfulOutcome.name());
   }

   private void failUnavailable() {
      this.outcome = FarmRepairToolAcquisitionTask.Outcome.UNAVAILABLE;
      this.reason = FarmTaskReason.REPAIR_TOOL_UNAVAILABLE;
      this.phase = FarmRepairToolAcquisitionTask.Phase.FAILED;
      this.setDebugState(this.phase.name() + ":" + this.reason.controlledReason());
   }

   @Override
   protected void onStop(Task interruptTask) {
      if (this.withdrawChild != null && this.withdrawChild.outcome() != null) {
         BoundedContainerTransferTask.Outcome transferOutcome = this.withdrawChild.outcome();
         boolean moved = transferOutcome == BoundedContainerTransferTask.Outcome.MOVED_ALL
            || transferOutcome == BoundedContainerTransferTask.Outcome.MOVED_PARTIAL;
         this.checkpoint.completeTransferAttempt(moved);
      }

      this.withdrawChild = null;
      this.activeCraftRequirement = null;
      this.craftChild = null;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof FarmRepairToolAcquisitionTask task
         && Objects.equals(task.planIdentity, this.planIdentity)
         && Objects.equals(task.frozenCenter, this.frozenCenter)
         && Objects.equals(task.dimensionId, this.dimensionId)
         && task.checkpoint == this.checkpoint;
   }

   @Override
   protected String toDebugString() {
      return "FarmRepairToolAcquisition";
   }

   private static long distanceSquared(BlockPos first, BlockPos second) {
      long dx = (long)first.getX() - second.getX();
      long dy = (long)first.getY() - second.getY();
      long dz = (long)first.getZ() - second.getZ();
      return dx * dx + dy * dy + dz * dz;
   }

   private record Candidate(
      FarmBreakToolRequirement.Family family,
      Item item,
      BlockPos chest,
      FarmRepairToolPlan.Requirement requirement,
      int requirementPriority,
      int itemPriority
   ) {
      private Candidate {
         Objects.requireNonNull(family, "family");
         Objects.requireNonNull(item, "item");
         chest = Objects.requireNonNull(chest, "chest").immutable();
         Objects.requireNonNull(requirement, "requirement");
      }
   }

   private record CandidateKey(FarmBreakToolRequirement.Family family, Item item, BlockPos chest) {
   }

   public static final class Checkpoint {
      private String planIdentity;
      private BlockPos frozenCenter;
      private String dimensionId;
      private boolean candidatesFrozen;
      private List<FarmRepairToolAcquisitionTask.Candidate> candidates = List.of();
      private int candidateCursor;
      private final Map<FarmBreakToolRequirement.Family, Integer> markedAttemptsByFamily = new LinkedHashMap<>();
      private final Map<FarmRepairToolAcquisitionTask.CandidateKey, Integer> markedAttemptsByCandidate = new LinkedHashMap<>();
      private FarmRepairToolAcquisitionTask.Candidate inFlightCandidate;
      private boolean transferAttemptInProgress;
      private final Map<FarmBreakToolRequirement.Family, FarmRepairToolAcquisitionTask.CraftTarget> craftTargetsByFamily = new LinkedHashMap<>();
      private boolean fallbackTerminalFailure;
      private boolean markedTransferMoved;

      public boolean isBound() {
         return this.planIdentity != null;
      }

      public String planIdentity() {
         return this.planIdentity;
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

      public int attemptedMarkedTransfers(FarmBreakToolRequirement.Family family) {
         return family == null ? 0 : this.markedAttemptsByFamily.getOrDefault(family, 0);
      }

      public boolean fallbackCommitted() {
         return !this.craftTargetsByFamily.isEmpty();
      }

      private void bind(String identity, BlockPos center, String dimension) {
         Objects.requireNonNull(identity, "identity");
         Objects.requireNonNull(center, "center");
         Objects.requireNonNull(dimension, "dimension");
         if (!this.isBound()) {
            this.planIdentity = identity;
            this.frozenCenter = center.immutable();
            this.dimensionId = dimension;
         } else if (!Objects.equals(this.planIdentity, identity) || !Objects.equals(this.frozenCenter, center) || !Objects.equals(this.dimensionId, dimension)) {
            throw new IllegalArgumentException("farm repair tool checkpoint cannot be rebound to another plan");
         }
      }

      private void freezeCandidates(List<FarmRepairToolAcquisitionTask.Candidate> discovered) {
         if (!this.candidatesFrozen) {
            this.candidates = List.copyOf(discovered);
            this.candidatesFrozen = true;
         }
      }

      private FarmRepairToolAcquisitionTask.Candidate claimNextCandidate(Set<FarmBreakToolRequirement.Family> missingFamilies) {
         if (!this.candidatesFrozen) {
            return null;
         }

         Objects.requireNonNull(missingFamilies, "missingFamilies");

         while (true) {
            while (this.inFlightCandidate == null) {
               if (this.candidateCursor >= this.candidates.size()) {
                  return null;
               }

               FarmRepairToolAcquisitionTask.Candidate candidate = this.candidates.get(this.candidateCursor++);
               if (missingFamilies.contains(candidate.family()) && this.attemptedMarkedTransfers(candidate.family()) < 8) {
                  this.inFlightCandidate = candidate;
               }
            }

            if (!missingFamilies.contains(this.inFlightCandidate.family())) {
               this.inFlightCandidate = null;
               this.transferAttemptInProgress = false;
            } else {
               if (this.transferAttemptInProgress) {
                  return this.inFlightCandidate;
               }

               int attempts = this.attemptedMarkedTransfers(this.inFlightCandidate.family());
               FarmRepairToolAcquisitionTask.CandidateKey key = keyOf(this.inFlightCandidate);
               int candidateAttempts = this.markedAttemptsByCandidate.getOrDefault(key, 0);
               if (attempts < 8 && candidateAttempts < 2) {
                  this.markedAttemptsByFamily.put(this.inFlightCandidate.family(), attempts + 1);
                  this.markedAttemptsByCandidate.put(key, candidateAttempts + 1);
                  this.transferAttemptInProgress = true;
                  return this.inFlightCandidate;
               }

               this.inFlightCandidate = null;
            }
         }
      }

      private void completeTransferAttempt(boolean moved) {
         this.transferAttemptInProgress = false;
         if (moved) {
            this.markedTransferMoved = true;
         }

         if (!moved || this.inFlightCandidate == null || this.markedAttemptsByCandidate.getOrDefault(keyOf(this.inFlightCandidate), 0) >= 2) {
            this.inFlightCandidate = null;
         }
      }

      private static FarmRepairToolAcquisitionTask.CandidateKey keyOf(FarmRepairToolAcquisitionTask.Candidate candidate) {
         return new FarmRepairToolAcquisitionTask.CandidateKey(candidate.family(), candidate.item(), candidate.chest());
      }

      private void retryStoppedTransfer() {
         this.transferAttemptInProgress = false;
      }

      private FarmRepairToolAcquisitionTask.CraftTarget craftTarget(FarmBreakToolRequirement.Family family, Item item, int currentExactCount) {
         FarmRepairToolAcquisitionTask.CraftTarget existing = this.craftTargetsByFamily.get(family);
         if (existing != null) {
            if (existing.item() != item) {
               throw new IllegalArgumentException("farm repair fallback item changed for " + family);
            } else {
               return existing;
            }
         } else {
            FarmRepairToolAcquisitionTask.CraftTarget created = new FarmRepairToolAcquisitionTask.CraftTarget(item, Math.addExact(currentExactCount, 1));
            this.craftTargetsByFamily.put(family, created);
            return created;
         }
      }
   }

   private record CraftTarget(Item item, int absoluteCount) {
      private CraftTarget {
         Objects.requireNonNull(item, "item");
         if (absoluteCount < 1) {
            throw new IllegalArgumentException("absolute craft target must be positive");
         }
      }
   }

   public enum Outcome {
      RUNNING,
      SATISFIED_FROM_INVENTORY,
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
