package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.automaton.api.process.IBuilderProcess;
import com.player2.playerengine.multiversion.ToolMaterialVer;
import com.player2.playerengine.multiversion.blockpos.BlockPosVer;
import com.player2.playerengine.tasks.AbstractDoToClosestObjectTask;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.construction.DestroyBlockTask;
import com.player2.playerengine.tasks.construction.build_structure.VillageSiteGuard;
import com.player2.playerengine.tasks.movement.PickupDroppedItemTask;
import com.player2.playerengine.tasks.movement.TimeoutWanderTask;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.MiningRequirement;
import com.player2.playerengine.util.helpers.AutomaticMiningSourcePolicy;
import com.player2.playerengine.util.helpers.MaterialAvailability;
import com.player2.playerengine.util.helpers.StorageHelper;
import com.player2.playerengine.util.helpers.WorldHelper;
import com.player2.playerengine.util.progresscheck.MovementProgressChecker;
import com.player2.playerengine.util.slots.CursorSlot;
import com.player2.playerengine.util.slots.PlayerSlot;
import com.player2.playerengine.util.time.TimerGame;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Tuple;

public class MineAndCollectTask extends ResourceTask {
   private final Block[] blocksToMine;
   private final MiningRequirement requirement;
   private final TimerGame cursorStackTimer = new TimerGame(3.0);
   private final MineAndCollectTask.MineOrCollectTask subtask;
   private SatisfyMiningRequirementTask satisfyTask;
   private boolean toolAcquisitionFailed;
   private String toolFailureHuman;
   private String toolFailureMachine;
   private boolean toolFailureReportedToPlayer;

   public MineAndCollectTask(ItemTarget[] itemTargets, Block[] blocksToMine, MiningRequirement requirement) {
      super(itemTargets);
      this.requirement = requirement;
      this.blocksToMine = blocksToMine;
      this.subtask = new MineAndCollectTask.MineOrCollectTask(this.blocksToMine, itemTargets);
   }

   public MineAndCollectTask(ItemTarget[] blocksToMine, MiningRequirement requirement) {
      this(blocksToMine, itemTargetToBlockList(blocksToMine), requirement);
   }

   public MineAndCollectTask(ItemTarget target, Block[] blocksToMine, MiningRequirement requirement) {
      this(new ItemTarget[]{target}, blocksToMine, requirement);
   }

   public MineAndCollectTask(Item item, int count, Block[] blocksToMine, MiningRequirement requirement) {
      this(new ItemTarget(item, count), blocksToMine, requirement);
   }

   public static Block[] itemTargetToBlockList(ItemTarget[] targets) {
      return AutomaticMiningSourcePolicy.blocksFor(targets);
   }

   @Override
   protected void onResourceStart(PlayerEngineController mod) {
      mod.getBehaviour().push();
      if (AutomaticMiningSourcePolicy.containsProtectedAcquisitionItem(this.itemTargets)) {
         mod.getBehaviour().avoidBlockBreaking(pos -> mod.getWorld().getBlockState(pos).is(Blocks.CHEST));
      }

      mod.getBehaviour().addProtectedItems(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE);
      this.subtask.resetSearch();
      this.subtask.resetBreakCancelGuard();
   }

   @Override
   protected boolean shouldAvoidPickingUp(PlayerEngineController mod) {
      return true;
   }

   @Override
   protected Task onResourceTick(PlayerEngineController mod) {
      if (StorageHelper.itemTargetsMetInventory(mod, this.itemTargets)) {
         this.setDebugState("Strict inventory gate met; finishing (no over-mining).");
         return null;
      }

      boolean sufficiencyViaDrops = StorageHelper.itemTargetsMetSufficiency(
         mod, mod.getPlayer().position(), mod.getModSettings().getAggregateCountDropRadius(), this.itemTargets
      );
      this.subtask.setCollectOnly(sufficiencyViaDrops);
      if (this.toolAcquisitionFailed) {
         this.setDebugState(this.toolFailureHuman != null ? this.toolFailureHuman : "Could not acquire the required tool.");
         if (!this.toolFailureReportedToPlayer && this.toolFailureHuman != null) {
            mod.reportAgenticProgress(this.toolFailureHuman, true);
            this.toolFailureReportedToPlayer = true;
         }

         this.stop();
         return null;
      } else if (!StorageHelper.miningRequirementMet(mod, this.requirement)) {
         this.subtask.cancelActiveBreakOnce(mod);
         this.setDebugState("Need a " + this.requirement + " tool before mining; acquiring it first.");
         if (this.satisfyTask == null) {
            this.satisfyTask = new SatisfyMiningRequirementTask(this.requirement);
         }

         if (this.satisfyTask.acquisitionFailed()) {
            this.toolAcquisitionFailed = true;
            this.toolFailureHuman = this.satisfyTask.humanFailureReason().orElse(null);
            this.toolFailureMachine = this.satisfyTask.machineFailureReason().orElse(null);
            this.setDebugState(this.toolFailureHuman != null ? this.toolFailureHuman : "Could not acquire the required tool.");
            return null;
         } else {
            return this.satisfyTask;
         }
      } else {
         this.subtask.resetBreakCancelGuard();
         if (this.subtask.isMining()) {
            this.makeSureToolIsEquipped(mod);
         }

         return this.subtask.wasWandering() && this.isInWrongDimension(mod) && !mod.getBlockScanner().anyFound(this.blocksToMine)
            ? this.getToCorrectDimensionTask(mod)
            : this.subtask;
      }
   }

   public Optional<String> toolAcquisitionMachineReason() {
      return this.toolAcquisitionFailed && this.toolFailureMachine != null ? Optional.of(this.toolFailureMachine) : Optional.empty();
   }

   public boolean toolAcquisitionFailed() {
      return this.toolAcquisitionFailed;
   }

   @Override
   protected void onResourceStop(PlayerEngineController mod, Task interruptTask) {
      mod.getBehaviour().pop();
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof MineAndCollectTask task ? Arrays.equals(task.blocksToMine, this.blocksToMine) : false;
   }

   @Override
   protected String toDebugStringName() {
      return "Mine And Collect";
   }

   private void makeSureToolIsEquipped(PlayerEngineController mod) {
      if (this.cursorStackTimer.elapsed() && !mod.getFoodChain().needsToEat()) {
         assert this.controller.getPlayer() != null;
         ItemStack cursorStack = StorageHelper.getItemStackInCursorSlot(this.controller);
         if (cursorStack != null && !cursorStack.isEmpty()) {
            Item item = cursorStack.getItem();
            if (item.isCorrectToolForDrops(cursorStack, mod.getWorld().getBlockState(this.subtask.miningPos()))) {
               Item currentlyEquipped = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot(mod.getInventory())).getItem();
               if (item instanceof DiggerItem) {
                  if (currentlyEquipped instanceof DiggerItem currentPick) {
                     DiggerItem swapPick = (DiggerItem)item;
                     if (ToolMaterialVer.getMiningLevel(swapPick) > ToolMaterialVer.getMiningLevel(currentPick)) {
                        mod.getSlotHandler().forceEquipSlot(this.controller, CursorSlot.SLOT);
                     }
                  } else {
                     mod.getSlotHandler().forceEquipSlot(this.controller, CursorSlot.SLOT);
                  }
               }
            }
         }

         this.cursorStackTimer.reset();
      }
   }

   public static class MineOrCollectTask extends AbstractDoToClosestObjectTask<Object> {
      private final Block[] blocks;
      private final ItemTarget[] targets;
      private final Set<BlockPos> blacklist = new HashSet<>();
      private final MovementProgressChecker progressChecker = new MovementProgressChecker();
      private final Task pickupTask;
      private BlockPos miningPos;
      private final TimerGame settleTimer = new TimerGame(1.0);
      private boolean collectOnly;
      private boolean breakCancelFired;
      private TimeoutWanderTask boundedWanderInstance;

      public MineOrCollectTask(Block[] blocks, ItemTarget[] targets) {
         this.blocks = blocks;
         this.targets = targets;
         this.pickupTask = new PickupDroppedItemTask(targets, true);
      }

      @Override
      protected Vec3 getPos(PlayerEngineController mod, Object obj) {
         if (obj instanceof BlockPos b) {
            return WorldHelper.toVec3d(b);
         } else if (obj instanceof ItemEntity item) {
            return item.position();
         } else {
            throw new UnsupportedOperationException(
               "Shouldn't try to get the position of object " + obj + " of type " + (obj != null ? obj.getClass().toString() : "(null object)")
            );
         }
      }

      @Override
      protected Optional<Object> getClosestTo(PlayerEngineController mod, Vec3 pos) {
         Tuple<Double, Optional<ItemEntity>> closestDrop = getClosestItemDrop(mod, pos, this.targets);
         if (this.collectOnly) {
            return ((Optional)closestDrop.getB()).map(Object.class::cast);
         } else {
            Tuple<Double, Optional<BlockPos>> closestBlock = getClosestBlock(mod, pos, this.blocks);
            double blockSq = (Double)closestBlock.getA();
            double dropSq = (Double)closestDrop.getA();
            if (mod.getExtraBaritoneSettings().isInteractionPaused()) {
               return ((Optional)closestDrop.getB()).map(Object.class::cast);
            } else {
               return dropSq <= blockSq
                  ? ((Optional)closestDrop.getB()).map(Object.class::cast)
                  : ((Optional)closestBlock.getB()).map(Object.class::cast);
            }
         }
      }

      public static Tuple<Double, Optional<ItemEntity>> getClosestItemDrop(PlayerEngineController mod, Vec3 pos, ItemTarget... items) {
         Optional<ItemEntity> closestDrop = Optional.empty();
         if (mod.getEntityTracker().itemDropped(items)) {
            closestDrop = mod.getEntityTracker().getClosestItemDrop(pos, items);
         }

         return new Tuple(closestDrop.<Double>map(itemEntity -> itemEntity.distanceToSqr(pos) + 10.0).orElse(Double.POSITIVE_INFINITY), closestDrop);
      }

      public static Tuple<Double, Optional<BlockPos>> getClosestBlock(PlayerEngineController mod, Vec3 pos, Block... blocks) {
         Optional<BlockPos> closestBlock = mod.getBlockScanner()
            .getNearestBlock(
               pos,
               check -> (mod.getBlockScanner().isUnreachable(check) ? false : WorldHelper.canBreak(mod, check))
                  & VillageSiteGuard.allowResourceMining(mod, check),
               blocks
            );
         return new Tuple(
            closestBlock.<Double>map(blockPos -> BlockPosVer.getSquaredDistance(blockPos, pos)).orElse(Double.POSITIVE_INFINITY), closestBlock
         );
      }

      @Override
      protected Vec3 getOriginPos(PlayerEngineController mod) {
         return mod.getPlayer().position();
      }

      @Override
      protected Task onTick() {
         PlayerEngineController mod = this.controller;
         if (mod.getBaritone().getPathingBehavior().isPathing()) {
            this.progressChecker.reset();
         }

         if (this.miningPos != null && !this.progressChecker.check(mod)) {
            mod.getBaritone().getPathingBehavior().forceCancel();
            Debug.logMessage("Failed to mine block. Suggesting it may be unreachable.");
            mod.getBlockScanner().requestBlockUnreachable(this.miningPos, 2);
            this.blacklist.add(this.miningPos);
            this.miningPos = null;
            this.progressChecker.reset();
         }

         if (this.miningPos != null && this.boundedWanderInstance != null && !this.boundedWanderInstance.isFinished()) {
            this.boundedWanderInstance.resetNoImprovementClock();
         }

         return super.onTick();
      }

      @Override
      protected Task getGoalTask(Object obj) {
         if (!(obj instanceof BlockPos newPos)) {
            if (obj instanceof ItemEntity) {
               this.miningPos = null;
               return this.pickupTask;
            } else {
               throw new UnsupportedOperationException(
                  "Shouldn't try to get the goal from object " + obj + " of type " + (obj != null ? obj.getClass().toString() : "(null object)")
               );
            }
         } else {
            if (this.miningPos == null || !this.miningPos.equals(newPos)) {
               this.progressChecker.reset();
            }

            this.miningPos = newPos;
            this.settleTimer.reset();
            return new DestroyBlockTask(this.miningPos);
         }
      }

      @Override
      protected boolean isValid(PlayerEngineController mod, Object obj) {
         if (obj instanceof BlockPos b) {
            return (mod.getBlockScanner().isBlockAtPosition(b, this.blocks) && WorldHelper.canBreak(this.controller, b))
               & VillageSiteGuard.allowResourceMining(mod, obj);
         } else if (!(obj instanceof ItemEntity drop)) {
            return false;
         } else {
            Item item = drop.getItem().getItem();
            if (this.targets != null) {
               for (ItemTarget target : this.targets) {
                  if (target.matches(item)) {
                     return true;
                  }
               }
            }

            return false;
         }
      }

      @Override
      protected void onStart() {
         this.progressChecker.reset();
         this.miningPos = null;
         this.breakCancelFired = false;
         this.boundedWanderInstance = null;
         this.settleTimer.setInterval(this.controller.getModSettings().getMineCollectSettleSeconds());
         this.settleTimer.forceElapse();
      }

      @Override
      protected Task getWanderTask(PlayerEngineController mod) {
         double deadlineSeconds = mod.getModSettings().getWanderBoundDefaultSeconds();
         long deadlineMs = (long)(deadlineSeconds * 1000.0);
         if (this.boundedWanderInstance == null || this.boundedWanderInstance.isFinished()) {
            this.boundedWanderInstance = deadlineMs > 0L ? TimeoutWanderTask.bounded(deadlineMs) : new TimeoutWanderTask(true);
         }

         Task boundedWander = this.boundedWanderInstance;
         if (this.targets == null || this.targets.length == 0) {
            return boundedWander;
         }

         if (!this.settleTimer.elapsed()) {
            this.setDebugState("Waiting for just-mined drop to settle before re-checking 'still short'.");
            return null;
         }

         Vec3 origin = mod.getPlayer().position();
         double dropRadius = mod.getModSettings().getAggregateCountDropRadius();
         double localSourceBlockRadius = Math.min(mod.getModSettings().getAggregateLocalSourceBlockRadius(), mod.getModSettings().getAgenticMaxTravelRadius());
         if (mod.getEntityTracker().itemDropped(this.targets) && mod.getEntityTracker().getClosestItemDrop(origin, this.targets).isPresent()) {
            this.setDebugState("Eligible nearby drops present; collecting before any new-source wander.");
            return this.pickupTask;
         }

         if (!this.collectOnly) {
            for (ItemTarget target : this.targets) {
               if (!ItemTarget.nullOrEmpty(target)
                  && MaterialAvailability.localSourceCanCoverRemainder(mod, target, origin, dropRadius, localSourceBlockRadius)) {
                  this.setDebugState("Local source can cover remainder; mining locally instead of wandering.");
                  return null;
               }
            }
         }

         this.setDebugState("No local source can cover remainder; bounded wander for a new source.");
         return boundedWander;
      }

      @Override
      protected void onStop(Task interruptTask) {
      }

      @Override
      protected boolean isEqual(Task other) {
         return other instanceof MineAndCollectTask.MineOrCollectTask task
            ? Arrays.equals(task.blocks, this.blocks) && Arrays.equals(task.targets, this.targets)
            : false;
      }

      @Override
      protected String toDebugString() {
         return "Mining or Collecting";
      }

      public boolean isMining() {
         return this.miningPos != null;
      }

      public void cancelActiveBreakOnce(PlayerEngineController mod) {
         if (!this.breakCancelFired) {
            this.breakCancelFired = true;
            IBuilderProcess builder = mod.getBaritone().getBuilderProcess();
            if (builder.isActive()) {
               builder.onLostControl();
            }

            this.miningPos = null;
            this.progressChecker.reset();
         }
      }

      public void resetBreakCancelGuard() {
         this.breakCancelFired = false;
      }

      public void setCollectOnly(boolean collectOnly) {
         this.collectOnly = collectOnly;
      }

      public BlockPos miningPos() {
         return this.miningPos;
      }
   }
}
