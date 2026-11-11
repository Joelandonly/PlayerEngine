package com.player2.playerengine.tasks.agentic;

import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.agentic.AgenticRunRegistry;
import com.player2.playerengine.agentic.MaterialReservationService;
import com.player2.playerengine.containeraccess.ScanReportFormatter;
import com.player2.playerengine.containeraccess.StorageItemArgs;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.container.BoundedContainerTransferTask;
import com.player2.playerengine.tasks.resources.CollectPlanksTask;
import com.player2.playerengine.trackers.storage.ContainerCache;
import com.player2.playerengine.util.MiningRequirement;
import com.player2.playerengine.util.helpers.ItemHelper;
import com.player2.playerengine.util.helpers.StorageHelper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.BuiltInRegistries;

public final class FuelGatherTask extends Task {
   private final FuelGatherParams params;
   private final AgenticRunRegistry.AgenticRunState runState;
   private final MaterialReservationService ledger;
   private final Item targetFuelItem;
   private final int neededCount;
   private FuelGatherTask.Phase phase = FuelGatherTask.Phase.CHECK_INVENTORY;
   private boolean finished;
   private final Set<BlockPos> triedContainers = new HashSet<>();
   private final Deque<BlockPos> markedCandidates = new ArrayDeque<>();
   private final Deque<BlockPos> unmarkedCandidates = new ArrayDeque<>();
   private BlockPos activeWithdrawPos;
   private BoundedContainerTransferTask withdrawChild;
   private int markedAttempts;
   private int unmarkedAttempts;
   private Task climbChild;
   private boolean climbStarted;

   public FuelGatherTask(FuelGatherParams params, AgenticRunRegistry.AgenticRunState runState, MaterialReservationService ledger) {
      this.params = params;
      this.runState = runState;
      this.ledger = ledger;
      this.targetFuelItem = params.targetFuelItem();
      this.neededCount = Math.max(1, params.neededCount());
   }

   @Override
   public boolean isFinished() {
      return this.controller == null ? false : this.heldInventoryOnly() >= this.neededCount;
   }

   @Override
   protected void onStart() {
      this.phase = this.isFinished() ? FuelGatherTask.Phase.DONE : FuelGatherTask.Phase.CHECK_INVENTORY;
      this.setDebugState(this.phase.name());
   }

   @Override
   protected Task onTick() {
      if (this.isFinished()) {
         this.phase = FuelGatherTask.Phase.DONE;
         this.setDebugState(this.phase.name());
         return null;
      }

      switch (this.phase) {
         case CHECK_INVENTORY:
            this.phase = FuelGatherTask.Phase.SEARCH_MARKED;
            this.report("looking for " + this.fuelWord() + " in marked storage...", false);
            this.setDebugState(this.phase.name());
            return null;
         case SEARCH_MARKED:
            return this.searchMarked();
         case WITHDRAW_MARKED:
            return this.driveWithdraw(FuelGatherTask.Phase.SEARCH_MARKED, this.markedCandidates);
         case SEARCH_UNMARKED:
            return this.searchUnmarked();
         case WITHDRAW_UNMARKED:
            return this.driveWithdraw(FuelGatherTask.Phase.SEARCH_UNMARKED, this.unmarkedCandidates);
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
         Vec3 origin = this.controller.getPlayer().position();
         String dimensionId = this.dimensionId();

         for (BlockPos pos : MarkedChestToolLocator.candidateCoordinates(this.controller, this.targetFuelItem, origin, this.params.radius(), dimensionId)) {
            if (!this.triedContainers.contains(pos)) {
               this.markedCandidates.add(pos);
            }
         }

         if (this.markedCandidates.isEmpty()) {
            this.phase = FuelGatherTask.Phase.SEARCH_UNMARKED;
            this.report("nothing marked; checking nearby chests for " + this.fuelWord() + "...", false);
            this.setDebugState(this.phase.name());
            return null;
         }
      }

      this.phase = FuelGatherTask.Phase.WITHDRAW_MARKED;
      this.setDebugState(this.phase.name());
      return null;
   }

   private Task searchUnmarked() {
      if (this.unmarkedCandidates.isEmpty()) {
         Vec3 origin = this.controller.getPlayer().position();
         double radiusSq = this.params.radius() * this.params.radius();
         List<BlockPos> chests = new ArrayList<>();
         chests.addAll(this.controller.getBlockScanner().getKnownLocations(Blocks.CHEST));
         chests.addAll(this.controller.getBlockScanner().getKnownLocations(Blocks.TRAPPED_CHEST));
         chests.sort(Comparator.comparingDouble(p -> distSq(p, origin)));

         for (BlockPos pos : chests) {
            if (!this.triedContainers.contains(pos) && !(distSq(pos, origin) > radiusSq)) {
               Optional<ContainerCache> cache = this.controller.getItemStorage().getContainerAtPosition(pos);
               if (!cache.isEmpty() && cache.get().hasItem(this.targetFuelItem)) {
                  this.unmarkedCandidates.add(pos);
               }
            }
         }

         if (this.unmarkedCandidates.isEmpty()) {
            this.phase = FuelGatherTask.Phase.CLIMB_CHAIN;
            this.report("no chest has " + this.fuelWord() + "; gathering it from the world...", false);
            this.setDebugState(this.phase.name());
            return null;
         }
      }

      this.phase = FuelGatherTask.Phase.WITHDRAW_UNMARKED;
      this.setDebugState(this.phase.name());
      return null;
   }

   private Task driveWithdraw(FuelGatherTask.Phase searchPhaseToReturnTo, Deque<BlockPos> candidates) {
      if (this.withdrawChild != null) {
         if (!this.withdrawChild.isFinished() && !this.withdrawChild.stopped()) {
            return this.withdrawChild;
         }

         this.withdrawChild = null;
         this.activeWithdrawPos = null;
         if (this.isFinished()) {
            this.phase = FuelGatherTask.Phase.DONE;
            this.setDebugState(this.phase.name());
            return null;
         }
      }

      boolean marked = searchPhaseToReturnTo == FuelGatherTask.Phase.SEARCH_MARKED;
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
         int remaining = Math.max(1, this.neededCount - this.heldInventoryOnly());
         List<StorageItemArgs.ItemQuery> queries = List.of(
            new StorageItemArgs.ItemQuery(this.targetFuelItem, ItemHelper.stripItemName(this.targetFuelItem), remaining)
         );
         this.withdrawChild = new BoundedContainerTransferTask(ScanReportFormatter.TransferDirection.WITHDRAW, pos, queries);
         this.report(
            "withdrawing " + this.fuelWord() + " from a chest at " + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "...", false
         );
         this.setDebugState(this.phase.name() + "@" + pos.toShortString());
         return this.withdrawChild;
      } else {
         this.phase = this.nextStageAfterWithdraw(searchPhaseToReturnTo);
         this.setDebugState(this.phase.name());
         return null;
      }
   }

   private FuelGatherTask.Phase nextStageAfterWithdraw(FuelGatherTask.Phase searchPhase) {
      return searchPhase == FuelGatherTask.Phase.SEARCH_MARKED ? FuelGatherTask.Phase.SEARCH_UNMARKED : FuelGatherTask.Phase.CLIMB_CHAIN;
   }

   private Task climbChain() {
      if (this.climbChild != null) {
         if (!this.climbChild.isFinished() && !this.climbChild.stopped()) {
            return this.climbChild;
         } else {
            this.climbChild = null;
            if (this.isFinished()) {
               this.phase = FuelGatherTask.Phase.DONE;
               this.setDebugState(this.phase.name());
               return null;
            } else {
               this.terminateFailed("no_acquirable_fuel");
               return null;
            }
         }
      } else {
         if (this.climbStarted) {
            this.terminateFailed("no_acquirable_fuel");
            return null;
         }

         if (this.isCoal()) {
            if (!StorageHelper.miningRequirementMetInventory(this.controller, MiningRequirement.WOOD)) {
               this.terminateFailed("no_acquirable_fuel");
               return null;
            } else {
               Task child = TaskCatalogue.getItemTask(Items.COAL, this.neededCount);
               if (child == null) {
                  this.terminateFailed("no_acquirable_fuel");
                  return null;
               } else {
                  this.climbChild = child;
                  this.climbStarted = true;
                  this.report("mining coal for fuel...", false);
                  this.setDebugState(this.phase.name() + ":coal");
                  return this.climbChild;
               }
            }
         } else {
            this.climbChild = new CollectPlanksTask(this.neededCount);
            this.climbStarted = true;
            this.report("chopping wood for fuel...", false);
            this.setDebugState(this.phase.name() + ":planks");
            return this.climbChild;
         }
      }
   }

   private void terminateFailed(String reason) {
      this.phase = FuelGatherTask.Phase.FAILED;
      this.finished = false;
      this.setDebugState("FAILED:" + reason);
      this.stopChildren();
      this.controller.log("[Agentic] fuel_gather: " + reason + " (item=" + this.fuelWord() + " need=" + this.neededCount + ")");
      this.report("couldn't gather " + this.fuelWord() + ": " + reason, true);
      if (!this.stopped()) {
         this.stop(this);
      }
   }

   private void stopChildren() {
      if (this.withdrawChild != null && !this.withdrawChild.stopped()) {
         this.withdrawChild.stop(this);
      }

      if (this.climbChild != null && !this.climbChild.stopped()) {
         this.climbChild.stop(this);
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      if (!this.finished && this.phase != FuelGatherTask.Phase.DONE && this.phase != FuelGatherTask.Phase.FAILED && !this.isFinished()) {
         this.phase = FuelGatherTask.Phase.FAILED;
         this.setDebugState("FAILED:interrupted");
      }
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof FuelGatherTask task && task.targetFuelItem == this.targetFuelItem && task.neededCount == this.neededCount;
   }

   @Override
   protected String toDebugString() {
      return "FuelGather[" + this.fuelWord() + " x" + this.neededCount + "]";
   }

   private int heldInventoryOnly() {
      return this.controller.getItemStorage().getItemCountInventoryOnly(this.targetFuelItem);
   }

   private boolean isCoal() {
      return this.targetFuelItem == Items.COAL;
   }

   private int maxContainers() {
      return Math.max(1, this.params.maxContainers());
   }

   private String fuelWord() {
      try {
         return BuiltInRegistries.ITEM.getKey(this.targetFuelItem).getPath().replace('_', ' ');
      } catch (Exception e) {
         return "fuel";
      }
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
