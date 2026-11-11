package com.player2.playerengine.tasks.container;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.automaton.api.entity.IInventoryProvider;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.reliability.FoodAcquisition;
import com.player2.playerengine.reliability.FuelRecovery;
import com.player2.playerengine.reliability.ProductionHandoff;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.construction.PlaceBlockNearbyTask;
import com.player2.playerengine.tasks.cooking.FuelPlanner;
import com.player2.playerengine.tasks.movement.GetCloseToBlockTask;
import com.player2.playerengine.tasks.movement.TimeoutWanderTask;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.SmeltTarget;
import com.player2.playerengine.util.time.TimerGame;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

public class SmeltInFurnaceTask extends ResourceTask {
   private static final int FURNACE_COOK_TICKS = 200;
   private static final FuelPlanner FUEL_PLANNER = new FuelPlanner();
   private final SmeltTarget[] targets;
   private final TimerGame smeltTimer = new TimerGame(10.0);
   private BlockPos furnacePos = null;
   private boolean isSmelting = false;
   private boolean fuelGatherAttempted = false;
   private boolean fuelGatherRan = false;
   private boolean fuelGatherInProgress = false;
   private Task fuelGatherTask = null;
   private FuelPlanner.FuelPlan currentPlan = null;
   private PlaceBlockNearbyTask pendingFurnacePlaceTask = null;
   private Object ellieFuelRecovery;
   private Object ellieFoodOwner;

   public SmeltInFurnaceTask(SmeltTarget... targets) {
      super(extractItemTargets(targets));
      this.targets = targets;
   }

   public SmeltInFurnaceTask(SmeltTarget target) {
      this(new SmeltTarget[]{target});
   }

   private static ItemTarget[] extractItemTargets(SmeltTarget[] recipeTargets) {
      List<ItemTarget> result = new ArrayList<>(recipeTargets.length);

      for (SmeltTarget target : recipeTargets) {
         result.add(target.getItem());
      }

      return result.toArray(ItemTarget[]::new);
   }

   @Override
   protected boolean shouldAvoidPickingUp(PlayerEngineController controller) {
      return false;
   }

   @Override
   protected void onResourceStart(PlayerEngineController controller) {
      controller.getBehaviour().push();
      controller.getBehaviour().addProtectedItems(Items.FURNACE);

      for (SmeltTarget target : this.targets) {
         controller.getBehaviour().addProtectedItems(target.getMaterial().getMatches());
      }
   }

   @Override
   protected Task onResourceTick(PlayerEngineController controller) {
      Task var10000 = FuelRecovery.before(this, controller);
      if (var10000 != null) {
         ProductionHandoff.invalidate(controller);
         return var10000;
      }

      if (this.stopped()) {
         ProductionHandoff.invalidate(controller);
         return null;
      }

      ProductionHandoff.beforeSmeltTick(this, controller);
      boolean allDone = Arrays.stream(this.targets)
         .allMatch(target -> controller.getItemStorage().getItemCount(target.getItem()) >= target.getItem().getTargetCount());
      if (allDone) {
         this.setDebugState("Done smelting.");
         ProductionHandoff.invalidate(controller);
         return null;
      }

      SmeltTarget currentTarget = null;

      for (SmeltTarget target : this.targets) {
         if (controller.getItemStorage().getItemCount(target.getItem()) < target.getItem().getTargetCount()) {
            currentTarget = target;
            break;
         }
      }

      if (currentTarget == null) {
         Debug.logWarning("Smelting task is running, but all targets are met. This should not happen.");
         ProductionHandoff.invalidate(controller);
         return null;
      }

      this.smeltTimer.setInterval(10 * currentTarget.getItem().getTargetCount());
      int batchCount = FoodAcquisition.remainingBatch(currentTarget, this);
      Task furnaceAcquire = this.findOrPlaceFurnace(controller);
      if (furnaceAcquire != null) {
         ProductionHandoff.invalidate(controller);
         return furnaceAcquire;
      }

      if (!this.isSmelting) {
         if (controller.getItemStorage().getItemCount(currentTarget.getMaterial()) < currentTarget.getMaterial().getTargetCount()) {
            this.setDebugState("Collecting materials for smelting: " + currentTarget.getMaterial());
            var10000 = TaskCatalogue.getItemTask(currentTarget.getMaterial());
            ProductionHandoff.invalidate(controller);
            return var10000;
         }

         boolean planResolved = false;
         Optional<FuelPlanner.FuelPlan> plan = FUEL_PLANNER.plan(batchCount, 200, controller, null);
         if (plan.isPresent()) {
            this.currentPlan = plan.get();
            planResolved = true;
         } else if (this.fuelGatherInProgress) {
            if (this.fuelGatherTask != null && this.fuelGatherTask.isFinished()) {
               this.fuelGatherInProgress = false;
               this.fuelGatherTask = null;
               Optional<FuelPlanner.FuelPlan> rePlan = FUEL_PLANNER.plan(batchCount, 200, controller, null);
               if (rePlan.isPresent()) {
                  this.currentPlan = rePlan.get();
                  planResolved = true;
               }
            } else {
               if (this.fuelGatherTask == null || !this.fuelGatherTask.stopped()) {
                  this.setDebugState("Collecting fuel...");
                  var10000 = this.fuelGatherTask;
                  ProductionHandoff.invalidate(controller);
                  return var10000;
               }

               this.fuelGatherInProgress = false;
               this.fuelGatherTask = null;
            }
         }

         if (!planResolved) {
            if (!this.fuelGatherAttempted) {
               this.fuelGatherAttempted = true;
               Optional<FuelPlanner.DeficitCandidate> deficit = FUEL_PLANNER.deficitFor(batchCount, 200, controller, null);
               if (deficit.isPresent()) {
                  FuelPlanner.DeficitCandidate cand = deficit.get();
                  Task gather = FuelRecovery.issued(cand, this);
                  if (gather != null) {
                     this.fuelGatherRan = true;
                     this.fuelGatherInProgress = true;
                     this.fuelGatherTask = gather;
                     this.setDebugState("Collecting fuel: " + cand.item());
                     ProductionHandoff.invalidate(controller);
                     return gather;
                  }
               }
            }

            int smelted = controller.getItemStorage().getItemCount(currentTarget.getItem());
            String itemName = currentTarget.getItem().getMatches()[0].getDescription().getString();
            Component playerTailComp = this.fuelGatherRan
               ? Component.translatable("message.playerengine.furnace.partial_tail_gathered")
               : Component.translatable("message.playerengine.furnace.partial_tail_nofuel");
            String modelTail = this.fuelGatherRan
               ? ", gathered fuel but still short"
               : ", no acquirable fuel type (coal needs a pickaxe; no logs/planks reachable)";
            controller.reportAgenticProgress(
               Component.translatable("message.playerengine.furnace.partial", new Object[]{smelted, batchCount, itemName, playerTailComp}), true
            );
            this.recordFailureReason("smelt incomplete: smelted " + smelted + "/" + batchCount + " " + itemName + modelTail);
            this.stop(this);
            ProductionHandoff.invalidate(controller);
            return null;
         }
      }

      if (!this.furnacePos
         .closerThan(
            new Vec3i(
               (int)controller.getEntity().position().x,
               (int)controller.getEntity().position().y,
               (int)controller.getEntity().position().z
            ),
            4.5
         )) {
         this.setDebugState("Going to furnace.");
         var10000 = new GetCloseToBlockTask(this.furnacePos);
         ProductionHandoff.invalidate(controller);
         return var10000;
      } else if (!(controller.getWorld().getBlockEntity(this.furnacePos) instanceof AbstractFurnaceBlockEntity furnace)) {
         Debug.logWarning("Block at furnace position is not a furnace BE. Resetting.");
         this.furnacePos = null;
         var10000 = new TimeoutWanderTask(1.0F);
         ProductionHandoff.invalidate(controller);
         return var10000;
      } else {
         ItemStack outputStack = furnace.getItem(2);
         if (!outputStack.isEmpty()) {
            this.setDebugState("Taking smelted items.");
            LivingEntityInventory playerInv = ((IInventoryProvider)controller.getEntity()).getLivingInventory();
            if (!playerInv.insertStack(outputStack)) {
               this.setDebugState("Inventory is full, cannot take smelted items.");
               ProductionHandoff.invalidate(controller);
               return null;
            }

            furnace.setItem(2, ItemStack.EMPTY);
            furnace.setChanged();
         }

         if (this.isSmelting) {
            this.setDebugState("Waiting for items to smelt...");
            if (this.smeltTimer.elapsed()) {
               this.isSmelting = false;
            }

            ProductionHandoff.invalidate(controller);
            return null;
         } else {
            ItemStack materialSlot = furnace.getItem(0);
            ItemStack fuelSlot = furnace.getItem(1);
            LivingEntityInventory playerInv = ((IInventoryProvider)controller.getEntity()).getLivingInventory();
            if (fuelSlot.isEmpty() && this.currentPlan != null) {
               this.setDebugState("Adding fuel: " + this.currentPlan.fuelItem());
               Item fuelItem = this.currentPlan.fuelItem();
               int want = Math.min(this.currentPlan.fuelCount(), 64);
               ItemStack loaded = ItemStack.EMPTY;

               while (want > 0) {
                  int fuelSlotIndex = playerInv.getSlotWithStack(new ItemStack(fuelItem));
                  if (fuelSlotIndex == -1) {
                     break;
                  }

                  ItemStack pulled = playerInv.removeItem(fuelSlotIndex, want);
                  if (pulled.isEmpty()) {
                     break;
                  }

                  if (loaded.isEmpty()) {
                     loaded = pulled;
                  } else {
                     loaded.grow(pulled.getCount());
                  }

                  want -= pulled.getCount();
               }

               if (!loaded.isEmpty()) {
                  furnace.setItem(1, loaded);
                  furnace.setChanged();
               }
            }

            if (materialSlot.isEmpty()) {
               this.setDebugState("Adding material.");
               Item materialItem = currentTarget.getMaterial().getMatches()[0];
               int materialSlotIndex = playerInv.getSlotWithStack(new ItemStack(materialItem));
               if (materialSlotIndex != -1) {
                  furnace.setItem(0, playerInv.removeItem(materialSlotIndex, currentTarget.getMaterial().getTargetCount()));
                  this.isSmelting = true;
                  this.smeltTimer.reset();
                  furnace.setChanged();
                  ProductionHandoff.invalidate(controller);
                  return null;
               }
            }

            this.isSmelting = true;
            this.smeltTimer.reset();
            this.setDebugState("Waiting for furnace...");
            ProductionHandoff.invalidate(controller);
            return null;
         }
      }
   }

   private Task findOrPlaceFurnace(PlayerEngineController controller) {
      if (this.pendingFurnacePlaceTask != null) {
         BlockPos placed = this.pendingFurnacePlaceTask.getPlaced();
         if (placed != null) {
            if (controller.getWorld().getBlockState(placed).is(Blocks.FURNACE)) {
               this.furnacePos = placed;
            }

            this.pendingFurnacePlaceTask = null;
         }
      }

      if (this.furnacePos != null && controller.getWorld().getBlockState(this.furnacePos).is(Blocks.FURNACE)) {
         return null;
      }

      Optional<BlockPos> nearestFurnace = controller.getBlockScanner().getNearestBlock(Blocks.FURNACE);
      if (nearestFurnace.isPresent()
         && !nearestFurnace.get()
            .closerThan(
               new Vec3i(
                  (int)controller.getEntity().position().x,
                  (int)controller.getEntity().position().y,
                  (int)controller.getEntity().position().z
               ),
               100.0
            )) {
         nearestFurnace = Optional.empty();
      }

      if (!nearestFurnace.isPresent()) {
         this.furnacePos = null;
         if (controller.getItemStorage().hasItem(Items.FURNACE)) {
            this.setDebugState("Placing furnace.");
            if (this.pendingFurnacePlaceTask == null) {
               this.pendingFurnacePlaceTask = new PlaceBlockNearbyTask(Blocks.FURNACE);
            }

            return this.pendingFurnacePlaceTask;
         } else {
            this.setDebugState("Obtaining furnace.");
            return TaskCatalogue.getItemTask(Items.FURNACE, 1);
         }
      } else {
         this.furnacePos = nearestFurnace.get();
         return null;
      }
   }

   @Override
   protected void onResourceStop(PlayerEngineController controller, Task interruptTask) {
      controller.getBehaviour().pop();
      this.fuelGatherAttempted = false;
      this.fuelGatherRan = false;
      this.fuelGatherInProgress = false;
      this.fuelGatherTask = null;
      this.currentPlan = null;
      this.pendingFurnacePlaceTask = null;
      this.furnacePos = null;
      this.isSmelting = false;
      FuelRecovery.stopped(this);
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof SmeltInFurnaceTask task ? Arrays.equals(task.targets, this.targets) : false;
   }

   @Override
   protected String toDebugStringName() {
      return "Smelting in Furnace";
   }
}
