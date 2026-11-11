package com.player2.playerengine.tasks.agentic;

import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.agentic.AgenticRunRegistry;
import com.player2.playerengine.agentic.MaterialReservationService;
import com.player2.playerengine.agentic.steps.ResolveStorageChestParams;
import com.player2.playerengine.containeraccess.ScanReportFormatter;
import com.player2.playerengine.containeraccess.StorageItemArgs;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.container.BoundedContainerTransferTask;
import com.player2.playerengine.trackers.storage.ContainerCache;
import com.player2.playerengine.util.MaterialChain;
import com.player2.playerengine.util.MiningRequirement;
import com.player2.playerengine.util.helpers.ItemHelper;
import com.player2.playerengine.util.helpers.StorageHelper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class ToolAcquisitionTask extends Task {
   private final MiningRequirement requirement;
   private final MineBlockParams params;
   private final AgenticRunRegistry.AgenticRunState runState;
   private final MaterialReservationService ledger;
   private ToolAcquisitionTask.Phase phase = ToolAcquisitionTask.Phase.CHECK_INVENTORY;
   private boolean finished;
   private final Set<BlockPos> triedContainers = new HashSet<>();
   private final Deque<BlockPos> markedCandidates = new ArrayDeque<>();
   private final Deque<BlockPos> unmarkedCandidates = new ArrayDeque<>();
   private BlockPos activeWithdrawPos;
   private BoundedContainerTransferTask withdrawChild;
   private int markedAttempts;
   private int unmarkedAttempts;
   private int climbBudgetRemaining;
   private final Set<MiningRequirement> failedTiers = EnumSet.noneOf(MiningRequirement.class);
   private Task climbChild;

   public ToolAcquisitionTask(
      MiningRequirement requirement, MineBlockParams params, AgenticRunRegistry.AgenticRunState runState, MaterialReservationService ledger
   ) {
      this.requirement = requirement;
      this.params = params;
      this.runState = runState;
      this.ledger = ledger;
      this.climbBudgetRemaining = params != null ? Math.max(1, params.toolAcquireClimbBudget()) : 1;
   }

   @Override
   public boolean isFinished() {
      return this.controller == null ? false : StorageHelper.miningRequirementMetInventory(this.controller, this.requirement);
   }

   @Override
   protected void onStart() {
      if (this.requirement != MiningRequirement.HAND && !StorageHelper.miningRequirementMetInventory(this.controller, this.requirement)) {
         this.phase = ToolAcquisitionTask.Phase.CHECK_INVENTORY;
      } else {
         this.phase = ToolAcquisitionTask.Phase.DONE;
      }

      this.setDebugState(this.phase.name());
   }

   @Override
   protected Task onTick() {
      if (this.isFinished()) {
         this.phase = ToolAcquisitionTask.Phase.DONE;
         this.setDebugState(this.phase.name());
         return null;
      }

      switch (this.phase) {
         case CHECK_INVENTORY:
            this.phase = ToolAcquisitionTask.Phase.SEARCH_MARKED;
            this.report("looking for a " + this.tierWord() + " pickaxe in marked storage...", false);
            this.setDebugState(this.phase.name());
            return null;
         case SEARCH_MARKED:
            return this.searchMarked();
         case WITHDRAW_MARKED:
            return this.driveWithdraw(ToolAcquisitionTask.Phase.SEARCH_MARKED, this.markedCandidates);
         case SEARCH_UNMARKED:
            return this.searchUnmarked();
         case WITHDRAW_UNMARKED:
            return this.driveWithdraw(ToolAcquisitionTask.Phase.SEARCH_UNMARKED, this.unmarkedCandidates);
         case CLIMB_CHAIN:
            return this.climbChain();
         case DONE:
         case FAILED:
         default:
            return null;
      }
   }

   private Task searchMarked() {
      if (this.markedCandidates.isEmpty()) {
         Item pick = this.requirement.getMinimumPickaxe();
         Vec3 origin = this.controller.getPlayer().position();
         String dimensionId = this.dimensionId();

         for (BlockPos pos : MarkedChestToolLocator.candidateCoordinates(this.controller, pick, origin, this.params.radius(), dimensionId)) {
            if (!this.triedContainers.contains(pos)) {
               this.markedCandidates.add(pos);
            }
         }

         if (this.markedCandidates.isEmpty()) {
            this.phase = ToolAcquisitionTask.Phase.SEARCH_UNMARKED;
            this.report("nothing marked; checking nearby chests for a " + this.tierWord() + " pickaxe...", false);
            this.setDebugState(this.phase.name());
            return null;
         }
      }

      this.phase = ToolAcquisitionTask.Phase.WITHDRAW_MARKED;
      this.setDebugState(this.phase.name());
      return null;
   }

   private Task searchUnmarked() {
      if (this.unmarkedCandidates.isEmpty()) {
         Item pick = this.requirement.getMinimumPickaxe();
         ResolveStorageChestParams scanParams = new ResolveStorageChestParams(
            this.params.radius(), this.params.radius(), true, false, true, this.params.timeoutSeconds()
         );
         Vec3 origin = this.controller.getPlayer().position();
         double radiusSq = scanParams.searchRadius() * scanParams.searchRadius();
         List<BlockPos> chests = new ArrayList<>();
         chests.addAll(this.controller.getBlockScanner().getKnownLocations(Blocks.CHEST));
         chests.addAll(this.controller.getBlockScanner().getKnownLocations(Blocks.TRAPPED_CHEST));
         chests.sort(Comparator.comparingDouble(p -> distSq(p, origin)));

         for (BlockPos pos : chests) {
            if (!this.triedContainers.contains(pos) && !(distSq(pos, origin) > radiusSq)) {
               Optional<ContainerCache> cache = this.controller.getItemStorage().getContainerAtPosition(pos);
               if (!cache.isEmpty() && cache.get().hasItem(pick)) {
                  this.unmarkedCandidates.add(pos);
               }
            }
         }

         if (this.unmarkedCandidates.isEmpty()) {
            this.phase = ToolAcquisitionTask.Phase.CLIMB_CHAIN;
            this.report("no chest has a " + this.tierWord() + " pickaxe; trying to craft one...", false);
            this.setDebugState(this.phase.name());
            return null;
         }
      }

      this.phase = ToolAcquisitionTask.Phase.WITHDRAW_UNMARKED;
      this.setDebugState(this.phase.name());
      return null;
   }

   private Task driveWithdraw(ToolAcquisitionTask.Phase searchPhaseToReturnTo, Deque<BlockPos> candidates) {
      if (this.withdrawChild != null) {
         if (!this.withdrawChild.isFinished() && !this.withdrawChild.stopped()) {
            return this.withdrawChild;
         }

         this.reserveDrawnTool();
         this.withdrawChild = null;
         this.activeWithdrawPos = null;
         if (this.isFinished()) {
            this.phase = ToolAcquisitionTask.Phase.DONE;
            this.setDebugState(this.phase.name());
            return null;
         }
      }

      boolean marked = searchPhaseToReturnTo == ToolAcquisitionTask.Phase.SEARCH_MARKED;
      int stageAttempts = marked ? this.markedAttempts : this.unmarkedAttempts;
      if (stageAttempts < this.maxContainers() && !candidates.isEmpty()) {
         BlockPos pos = candidates.poll();
         this.triedContainers.add(pos);
         if (marked) {
            this.markedAttempts++;
         } else {
            this.unmarkedAttempts++;
         }

         this.activeWithdrawPos = pos;
         Item pick = this.requirement.getMinimumPickaxe();
         List<StorageItemArgs.ItemQuery> queries = List.of(new StorageItemArgs.ItemQuery(pick, ItemHelper.stripItemName(pick), 1));
         this.withdrawChild = new BoundedContainerTransferTask(ScanReportFormatter.TransferDirection.WITHDRAW, pos, queries);
         this.report(
            "withdrawing a " + this.tierWord() + " pickaxe from a chest at " + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "...",
            false
         );
         this.setDebugState(this.phase.name() + "@" + pos.toShortString());
         return this.withdrawChild;
      } else {
         this.phase = this.nextStageAfterWithdraw(searchPhaseToReturnTo);
         this.setDebugState(this.phase.name());
         return null;
      }
   }

   private ToolAcquisitionTask.Phase nextStageAfterWithdraw(ToolAcquisitionTask.Phase searchPhase) {
      return searchPhase == ToolAcquisitionTask.Phase.SEARCH_MARKED ? ToolAcquisitionTask.Phase.SEARCH_UNMARKED : ToolAcquisitionTask.Phase.CLIMB_CHAIN;
   }

   private Task climbChain() {
      if (this.climbChild != null) {
         if (!this.climbChild.isFinished() && !this.climbChild.stopped()) {
            return this.climbChild;
         }

         this.reserveDrawnTool();
         this.climbChild = null;
         if (this.isFinished()) {
            this.phase = ToolAcquisitionTask.Phase.DONE;
            this.setDebugState(this.phase.name());
            return null;
         }

         this.failedTiers.add(this.requirement);
         this.climbBudgetRemaining--;
         if (this.climbBudgetRemaining <= 0) {
            this.terminateFailed("no_tool_acquisition_path");
            return null;
         }
      }

      MaterialChain tier = MaterialChain.forRequirement(this.requirement);
      if (tier == null) {
         this.terminateFailed("no_tool_acquisition_path");
         return null;
      } else if (this.failedTiers.contains(this.requirement)) {
         this.terminateFailed("no_tool_acquisition_path");
         return null;
      } else {
         Item pick = this.requirement.getMinimumPickaxe();
         Task child = TaskCatalogue.getItemTask(pick, 1);
         if (child == null) {
            this.terminateFailed("no_tool_acquisition_path");
            return null;
         } else {
            this.climbChild = child;
            this.report("crafting a " + this.tierWord() + " pickaxe (gathering its materials)...", false);
            this.setDebugState(this.phase.name() + ":" + tier.name());
            return this.climbChild;
         }
      }
   }

   private void reserveDrawnTool() {
      if (this.ledger != null) {
         Item pick = this.requirement.getMinimumPickaxe();
         int free = this.ledger.free(this.controller, pick);
         if (free > 0) {
            this.ledger.reserve(this.controller, pick, 1);
         }
      }
   }

   private void terminateFailed(String reason) {
      this.phase = ToolAcquisitionTask.Phase.FAILED;
      this.finished = false;
      this.setDebugState("FAILED:" + reason);
      if (this.withdrawChild != null && !this.withdrawChild.stopped()) {
         this.withdrawChild.stop(this);
      }

      if (this.climbChild != null && !this.climbChild.stopped()) {
         this.climbChild.stop(this);
      }

      this.controller.log("[Agentic] tool_acquisition: " + reason + " (req=" + this.requirement + ")");
      this.report("couldn't get a " + this.tierWord() + " pickaxe: " + reason, true);
      if (!this.stopped()) {
         this.stop(this);
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      if (!this.finished && this.phase != ToolAcquisitionTask.Phase.DONE && this.phase != ToolAcquisitionTask.Phase.FAILED && !this.isFinished()) {
         this.phase = ToolAcquisitionTask.Phase.FAILED;
         this.setDebugState("FAILED:interrupted");
      }
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof ToolAcquisitionTask task && task.requirement == this.requirement && Objects.equals(task.params, this.params);
   }

   @Override
   protected String toDebugString() {
      return "ToolAcquisition[" + this.requirement + "]";
   }

   private int maxContainers() {
      return this.params != null ? Math.max(1, this.params.toolAcquireMaxContainers()) : 1;
   }

   private String tierWord() {
      return this.requirement.name().toLowerCase(Locale.ROOT);
   }

   private String dimensionId() {
      try {
         return this.controller.getWorld().dimension().location().toString();
      } catch (Exception e) {
         return null;
      }
   }

   private static double distSq(BlockPos pos, Vec3 origin) {
      double dx = pos.getX() + 0.5 - origin.x;
      double dy = pos.getY() + 0.5 - origin.y;
      double dz = pos.getZ() + 0.5 - origin.z;
      return dx * dx + dy * dy + dz * dz;
   }

   private void report(String message, boolean milestone) {
      if (this.runState == null || !this.runState.isTerminal()) {
         this.controller.reportAgenticProgress(message, milestone);
      }
   }

   enum Phase {
      CHECK_INVENTORY,
      SEARCH_MARKED,
      WITHDRAW_MARKED,
      SEARCH_UNMARKED,
      WITHDRAW_UNMARKED,
      CLIMB_CHAIN,
      DONE,
      FAILED;
   }
}
