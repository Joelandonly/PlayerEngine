package com.player2.playerengine.tasks.container;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.construction.PlaceBlockNearbyTask;
import com.player2.playerengine.trackers.storage.ContainerCache;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.WorldHelper;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class StoreInAnyContainerTask extends Task {
   private static final double OVERALL_TIMEOUT_SECONDS = 90.0;
   private static final double DEPOSIT_TIMEOUT_SECONDS = 45.0;
   private static final double DEPOSIT_TRAVEL_CAP_BLOCKS = 115.0;
   private final ItemTarget[] toStore;
   private final boolean getIfNotPresent;
   private boolean finished;
   private StoreInAnyContainerTask.Outcome outcome;
   private String reason = "";
   private long startMs;
   private BoundedContainerDepositTask depositCore;
   private BlockPos chosenContainer;
   private boolean everChoseContainer;
   private final Set<BlockPos> fullContainers = new HashSet<>();

   public StoreInAnyContainerTask(boolean getIfNotPresent, ItemTarget... toStore) {
      this.getIfNotPresent = getIfNotPresent;
      this.toStore = toStore;
   }

   public boolean succeeded() {
      return this.outcome == StoreInAnyContainerTask.Outcome.SUCCESS;
   }

   public StoreInAnyContainerTask.Outcome outcome() {
      return this.outcome;
   }

   public String reason() {
      return this.reason;
   }

   @Override
   protected void onStart() {
      this.startMs = System.currentTimeMillis();
   }

   @Override
   protected Task onTick() {
      if (this.finished) {
         return null;
      }

      ItemTarget[] itemsToStore = this.getItemsToStore(this.controller);
      if (itemsToStore.length == 0) {
         this.terminate(StoreInAnyContainerTask.Outcome.SUCCESS, "");
         return null;
      }

      if (this.elapsedSec() >= 90.0) {
         String why = "container_full".equals(this.reason) ? "container_full" : (this.everChoseContainer ? "timeout" : "no_container_available");
         this.terminate(StoreInAnyContainerTask.Outcome.FAILED, why);
         return null;
      }

      if (this.getIfNotPresent) {
         for (ItemTarget target : this.toStore) {
            if (this.controller.getItemStorage().getItemCount(target) < target.getTargetCount()) {
               this.setDebugState("Collecting " + target + " before storing.");
               return TaskCatalogue.getItemTask(target);
            }
         }
      }

      if (this.chosenContainer == null || this.depositCore == null) {
         Optional<BlockPos> closestContainer = this.controller
            .getBlockScanner()
            .getNearestBlock(this.buildContainerPredicate(), StoreInContainerTask.CONTAINER_BLOCKS);
         if (closestContainer.isPresent() && this.beyondTravelCap(closestContainer.get())) {
            closestContainer = Optional.empty();
         }

         if (closestContainer.isPresent()) {
            this.chosenContainer = closestContainer.get();
            this.everChoseContainer = true;
            this.depositCore = new BoundedContainerDepositTask(this.chosenContainer, 45.0, itemsToStore);
            this.setDebugState("Found a container; storing items.");
            return this.depositCore;
         }

         for (Block containerBlock : StoreInContainerTask.CONTAINER_BLOCKS) {
            if (this.controller.getItemStorage().hasItem(containerBlock.asItem())) {
               this.setDebugState("Placing a container nearby.");
               return new PlaceBlockNearbyTask(
                  pos -> !WorldHelper.isChest(this.controller, pos) || WorldHelper.isAir(this.controller, pos.above()), containerBlock
               );
            }
         }

         this.setDebugState("Obtaining a chest to store items.");
         return TaskCatalogue.getItemTask(Items.CHEST, 1);
      } else if (this.depositCore.isFinished()) {
         this.classifyDepositOutcome(itemsToStore);
         return null;
      } else {
         this.setDebugState("Depositing into chosen container " + this.chosenContainer.toShortString());
         return this.depositCore;
      }
   }

   private Predicate<BlockPos> buildContainerPredicate() {
      return pos -> {
         if (com.player2.playerengine.agentic.InventoryCleanupStorage.isProjectStorage(this.controller, pos)) return false;
         if (this.fullContainers.contains(pos)) {
            return false;
         } else if (WorldHelper.isChest(this.controller, pos)
            && WorldHelper.isSolidBlock(this.controller, pos.above())
            && !WorldHelper.canBreak(this.controller, pos.above())) {
            return false;
         } else {
            Optional<ContainerCache> cache = this.controller.getItemStorage().getContainerAtPosition(pos);
            if (cache.isPresent() && cache.get().isFull()) {
               return false;
            } else {
               return WorldHelper.isChest(this.controller, pos) && this.controller.getModSettings().shouldAvoidSearchingForDungeonChests()
                  ? !this.isDungeonChest(this.controller, pos)
                  : true;
            }
         }
      };
   }

   private void classifyDepositOutcome(ItemTarget[] itemsToStore) {
      switch (this.depositCore.outcome()) {
         case DEPOSITED:
         case NOTHING:
            this.terminate(StoreInAnyContainerTask.Outcome.SUCCESS, "");
            break;
         case PARTIAL_CONTAINER_FULL:
            if (this.chosenContainer != null) {
               this.fullContainers.add(this.chosenContainer);
            }

            this.reason = "container_full";
            this.chosenContainer = null;
            this.depositCore = null;
            Optional<BlockPos> next = this.controller
               .getBlockScanner()
               .getNearestBlock(this.buildContainerPredicate(), StoreInContainerTask.CONTAINER_BLOCKS);
            if (next.isPresent() && this.beyondTravelCap(next.get())) {
               next = Optional.empty();
            }

            if (next.isPresent()) {
               this.chosenContainer = next.get();
               this.everChoseContainer = true;
               this.depositCore = new BoundedContainerDepositTask(this.chosenContainer, 45.0, itemsToStore);
               this.setDebugState("Container full; storing into another container " + this.chosenContainer.toShortString());
            } else {
               this.terminate(StoreInAnyContainerTask.Outcome.FAILED, "container_full");
            }
            break;
         case PARTIAL_REMAINING:
            this.terminate(StoreInAnyContainerTask.Outcome.FAILED, "partial_remaining");
            break;
         case TIMEOUT:
            this.terminate(StoreInAnyContainerTask.Outcome.FAILED, "timeout");
      }
   }

   private void terminate(StoreInAnyContainerTask.Outcome result, String why) {
      this.outcome = result;
      this.reason = why;
      this.finished = true;
      if (this.depositCore != null && !this.depositCore.stopped()) {
         this.depositCore.stop(this);
      }

      this.depositCore = null;
   }

   @Override
   public boolean isFinished() {
      return this.finished;
   }

   private ItemTarget[] getItemsToStore(PlayerEngineController controller) {
      return Arrays.stream(this.toStore).filter(target -> controller.getItemStorage().hasItem(target.getMatches())).toArray(ItemTarget[]::new);
   }

   private boolean beyondTravelCap(BlockPos target) {
      double capSq = 13225.0;
      Vec3 origin = this.controller.getPlayer().position();
      boolean beyond = origin.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) > capSq;
      if (beyond) {
         this.controller.log("[deposit] ignoring container at " + target.toShortString() + " beyond travel cap 115 blocks; using a local chest instead");
      }

      return beyond;
   }

   private boolean isDungeonChest(PlayerEngineController controller, BlockPos pos) {
      int range = 6;

      for (int dx = -range; dx <= range; dx++) {
         for (int dz = -range; dz <= range; dz++) {
            if (controller.getWorld().getBlockState(pos.offset(dx, 0, dz)).is(Blocks.SPAWNER)) {
               return true;
            }
         }
      }

      return false;
   }

   private double elapsedSec() {
      return (System.currentTimeMillis() - this.startMs) / 1000.0;
   }

   @Override
   protected void onStop(Task interruptTask) {
      if (this.depositCore != null && !this.depositCore.stopped()) {
         this.depositCore.stop(interruptTask);
      }

      this.depositCore = null;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof StoreInAnyContainerTask task ? task.getIfNotPresent == this.getIfNotPresent && Arrays.equals(task.toStore, this.toStore) : false;
   }

   @Override
   protected String toDebugString() {
      return "Storing in any container: " + Arrays.toString(this.toStore);
   }

   public enum Outcome {
      SUCCESS,
      FAILED;
   }
}
