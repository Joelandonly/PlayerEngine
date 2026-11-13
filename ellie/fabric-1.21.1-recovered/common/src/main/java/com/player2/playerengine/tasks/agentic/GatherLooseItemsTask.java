package com.player2.playerengine.tasks.agentic;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.agentic.AgenticRunRegistry;
import com.player2.playerengine.agentic.steps.GatherLooseItemsParams;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.crafting.DescribesProgress;
import com.player2.playerengine.tasks.movement.PickupDroppedItemTask;
import com.player2.playerengine.tasks.slot.EnsureFreeInventorySlotTask;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class GatherLooseItemsTask extends Task implements DescribesProgress {
   private final GatherLooseItemsParams params;
   private final AgenticRunRegistry.AgenticRunState runState;
   private Vec3 origin;
   private GatherLooseItemsTask.Phase phase = GatherLooseItemsTask.Phase.SCANNING;
   private boolean finished;
   private int gatheredItems;
   private int skippedUnreachable;
   private long startMs;
   private long inventoryRecoveryStartedMs=-1L;
   private long settleStartMs = -1L;
   private PickupDroppedItemTask pickupChild;
   private ItemEntity currentTarget;
   private final Set<Integer> blacklistedEntityIds = new HashSet<>();
   private int inventoryBaselineCount;
   private boolean triedFreeInventory;

   public GatherLooseItemsTask(GatherLooseItemsParams params, AgenticRunRegistry.AgenticRunState runState) {
      this.params = params;
      this.runState = runState;
   }

   @Override
   public boolean isFinished() {
      return this.finished;
   }

   @Override
   public String describeProgress() {
      int remaining = this.countEligibleDrops();
      String target = this.currentTarget != null && !this.currentTarget.getItem().isEmpty()
         ? ItemHelper.stripItemName(this.currentTarget.getItem().getItem())
         : "none";
      return String.format(
         Locale.ROOT,
         "phase=%s gathered=%d skipped=%d remaining=%d target=%s",
         this.phase.name().toLowerCase(Locale.ROOT),
         this.gatheredItems,
         this.skippedUnreachable,
         remaining,
         target
      );
   }

   @Override
   protected void onStart() {
      this.origin = this.controller.getPlayer().position();
      this.startMs = System.currentTimeMillis();
      this.inventoryBaselineCount = countInventoryItems(this.controller);
      this.phase = GatherLooseItemsTask.Phase.SCANNING;
      this.updateRunProgress();
      this.report("gathering loose " + this.describeFilter(), false);
   }

   @Override
   protected Task onTick() {
      if(inventoryRecoveryStartedMs<0)detectGatheredFromInventory();
      Task inventoryRecovery=com.player2.playerengine.agentic.AutoInventoryCleanup.beforeResourceTick(this);
      if(inventoryRecovery!=null){
         if(inventoryRecoveryStartedMs<0)inventoryRecoveryStartedMs=System.currentTimeMillis();
         return inventoryRecovery;
      }
      if(inventoryRecoveryStartedMs>=0){
         long elapsed=System.currentTimeMillis()-inventoryRecoveryStartedMs;
         this.startMs+=elapsed;if(this.settleStartMs>=0)this.settleStartMs+=elapsed;
         this.inventoryBaselineCount=countInventoryItems(this.controller);inventoryRecoveryStartedMs=-1L;
      }
      if (this.finished) {
         return null;
      }

      double elapsedSec = (System.currentTimeMillis() - this.startMs) / 1000.0;
      if (elapsedSec >= this.params.timeoutSeconds()) {
         if (this.gatheredItems > 0) {
            this.finishRun(GatherLooseItemsTask.Phase.PARTIAL, "timeout after partial gather");
         } else {
            this.finishRun(GatherLooseItemsTask.Phase.FAILED, "timeout with drops still nearby");
         }

         return null;
      } else {
         if (this.gatheredItems >= this.params.maxItems()) {
            this.finishRun(GatherLooseItemsTask.Phase.DONE, "reached maxItems");
            return null;
         }

         List<ItemEntity> drops = this.eligibleDrops();
         if (drops.isEmpty()) {
            this.phase = GatherLooseItemsTask.Phase.SETTLING;
            if (this.settleStartMs < 0L) {
               this.settleStartMs = System.currentTimeMillis();
            }

            double settleElapsed = (System.currentTimeMillis() - this.settleStartMs) / 1000.0;
            if (settleElapsed >= this.params.settleSeconds()) {
               if (this.gatheredItems == 0) {
                  this.finishRun(GatherLooseItemsTask.Phase.DONE, "no eligible drops nearby");
               } else {
                  this.finishRun(GatherLooseItemsTask.Phase.DONE, "gather complete");
               }
            }

            this.updateRunProgress();
            return null;
         } else {
            this.settleStartMs = -1L;
            if (!hasFreeInventorySlot(this.controller)) {
               if (this.params.freeInventoryIfFull() && !this.triedFreeInventory) {
                  this.phase = GatherLooseItemsTask.Phase.FREEING_INVENTORY;
                  this.triedFreeInventory = true;
                  this.updateRunProgress();
                  this.report("inventory full; freeing a slot", false);
                  return new EnsureFreeInventorySlotTask();
               }

               if (this.gatheredItems > 0) {
                  this.finishRun(GatherLooseItemsTask.Phase.PARTIAL, "inventory full");
               } else {
                  this.finishRun(GatherLooseItemsTask.Phase.FAILED, "inventory full");
               }

               return null;
            } else {
               this.triedFreeInventory = false;
               ItemEntity nearest = this.pickNearest(drops);
               if (nearest == null) {
                  this.finishRun(this.gatheredItems > 0 ? GatherLooseItemsTask.Phase.PARTIAL : GatherLooseItemsTask.Phase.DONE, "no reachable drops");
                  return null;
               }

               if (this.pickupChild == null
                  || this.currentTarget == null
                  || this.currentTarget.getId() != nearest.getId()
                  || this.pickupChild.stopped()) {
                  this.currentTarget = nearest;
                  Item item = nearest.getItem().getItem();
                  int count = Math.max(1, nearest.getItem().getCount());
                  this.pickupChild = new PickupDroppedItemTask(new ItemTarget(item, count), this.params.freeInventoryIfFull());
                  this.phase = GatherLooseItemsTask.Phase.MOVING_TO_DROP;
                  this.report("moving to " + ItemHelper.stripItemName(item) + " drop", false);
               }

               int before = this.gatheredItems;
               this.detectGatheredFromInventory();
               if (this.gatheredItems > before) {
                  this.pickupChild = null;
                  this.currentTarget = null;
                  this.updateRunProgress();
                  return null;
               } else if (!this.currentTarget.isRemoved() && !this.currentTarget.getItem().isEmpty()) {
                  this.updateRunProgress();
                  return this.pickupChild;
               } else {
                  this.gatheredItems++;
                  this.pickupChild = null;
                  this.currentTarget = null;
                  this.updateRunProgress();
                  return null;
               }
            }
         }
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      if (!this.finished && !this.isTerminalPhase()) {
         this.finishRun(GatherLooseItemsTask.Phase.FAILED, "interrupted");
      }
   }

   private boolean isTerminalPhase() {
      return this.phase == GatherLooseItemsTask.Phase.DONE
         || this.phase == GatherLooseItemsTask.Phase.PARTIAL
         || this.phase == GatherLooseItemsTask.Phase.FAILED;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof GatherLooseItemsTask task && task.params.equals(this.params);
   }

   @Override
   protected String toDebugString() {
      return "GatherLooseItems";
   }

   private List<ItemEntity> eligibleDrops() {
      return this.controller
         .getEntityTracker()
         .getItemDropsWithin(
            this.origin,
            this.params.radius(),
            entity -> !entity.isRemoved() && !this.blacklistedEntityIds.contains(entity.getId()) && this.matchesFilter(entity)
         );
   }

   private int countEligibleDrops() {
      return this.eligibleDrops().size();
   }

   private boolean matchesFilter(ItemEntity entity) {
      if (this.params.itemIdFilters().isEmpty()) {
         return true;
      }

      String name = ItemHelper.stripItemName(entity.getItem().getItem()).toLowerCase(Locale.ROOT);

      for (String filter : this.params.itemIdFilters()) {
         if (name.contains(filter) || filter.contains(name)) {
            return true;
         }
      }

      return false;
   }

   private ItemEntity pickNearest(List<ItemEntity> drops) {
      Vec3 pos = this.controller.getPlayer().position();
      return drops.stream()
         .filter(e -> this.controller.getEntityTracker().isEntityReachable(e))
         .min(Comparator.comparingDouble(e -> e.distanceToSqr(pos)))
         .orElseGet(() -> {
            if (!drops.isEmpty()) {
               ItemEntity blocked = drops.get(0);
               this.blacklistedEntityIds.add(blocked.getId());
               this.controller.getEntityTracker().requestEntityUnreachable(blocked);
               this.skippedUnreachable++;
            }

            return null;
         });
   }

   private void detectGatheredFromInventory() {
      int now = countInventoryItems(this.controller);
      int delta = now - this.inventoryBaselineCount;
      if (delta > 0) {
         this.gatheredItems += delta;
         this.inventoryBaselineCount = now;
      }
   }

   private static int countInventoryItems(PlayerEngineController mod) {
      int total = 0;
      LivingEntityInventory inv = mod.getBaritone().getEntityContext().inventory();

      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack stack = inv.getItem(i);
         if (!stack.isEmpty()) {
            total += stack.getCount();
         }
      }

      return total;
   }

   private static boolean hasFreeInventorySlot(PlayerEngineController mod) {
      LivingEntityInventory inv = mod.getBaritone().getEntityContext().inventory();

      for (int i = 0; i < inv.getContainerSize(); i++) {
         if (inv.getItem(i).isEmpty()) {
            return true;
         }
      }

      return false;
   }

   private void finishRun(GatherLooseItemsTask.Phase endPhase, String message) {
      this.phase = endPhase;
      boolean failure = endPhase == GatherLooseItemsTask.Phase.FAILED;
      this.finished = !failure;
      this.setDebugState(endPhase.name());
      if (this.runState != null) {
         this.runState.setGatherProgress(this.describeProgress() + " — " + message);
         if (endPhase == GatherLooseItemsTask.Phase.PARTIAL) {
            this.runState.setGatherDegraded(AgenticRunRegistry.DegradationLevel.PARTIAL, message);
         }
      }

      this.controller.log("[Agentic] gather_loose_items: " + message + " (" + this.describeProgress() + ")");

      String prefix = switch (endPhase) {
         case PARTIAL -> "gather partial: ";
         case FAILED -> "gather failed: ";
         default -> "gathered " + this.gatheredItems + " item(s): ";
      };
      this.report(prefix + message, true);
      if (failure && !this.stopped()) {
         this.stop(this);
      }
   }

   private void updateRunProgress() {
      if (this.runState != null) {
         this.runState.setGatherProgress(this.describeProgress());
      }

      this.setDebugState(this.phase.name());
   }

   private String describeFilter() {
      return this.params.itemIdFilters().isEmpty() ? "items nearby" : String.join(", ", this.params.itemIdFilters());
   }

   private void report(String message, boolean milestone) {
      if (this.runState == null || !this.runState.isTerminal()) {
         this.controller.reportAgenticProgress(message, milestone);
      }
   }

   private enum Phase {
      SCANNING,
      MOVING_TO_DROP,
      FREEING_INVENTORY,
      SETTLING,
      DONE,
      PARTIAL,
      FAILED;
   }
}
