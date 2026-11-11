package com.player2.playerengine.tasks.farming;

import com.player2.playerengine.automaton.api.utils.Rotation;
import com.player2.playerengine.automaton.api.utils.input.Input;
import com.player2.playerengine.structureprotection.PlayerPlacedBlockStore;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.movement.GetToBlockTask;
import com.player2.playerengine.util.helpers.LookHelper;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntSupplier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class PlaceFarmDirtTask extends Task implements FarmMutationReceipt {
   private static final int RETRY_DELAY_TICKS = 10;
   private final BlockPos target;
   private final BlockPos support;
   private final BlockPos stance;
   private final boolean allowProtectedTarget;
   private String dimension;
   private BlockState expectedTarget;
   private BlockState expectedSupport;
   private BlockState expectedTargetHead;
   private BlockState expectedStance;
   private BlockState expectedStanceHead;
   private boolean expectedStancePassableCrop;
   private boolean terminal;
   private boolean successful;
   private FarmTaskReason reason = FarmTaskReason.NONE;
   private int noProgressTicks;
   private int attempts;
   private int ticksSinceAttempt;
   private int dirtBefore;
   private boolean inventoryBaselineCaptured;
   private boolean transientReceiptDetached;

   public PlaceFarmDirtTask(BlockPos target, BlockPos stance) {
      this(target, target == null ? null : target.below(), stance);
   }

   public PlaceFarmDirtTask(BlockPos target, BlockPos support, BlockPos stance) {
      this(target, support, stance, false);
   }

   private PlaceFarmDirtTask(BlockPos target, BlockPos support, BlockPos stance, boolean allowProtectedTarget) {
      this.target = Objects.requireNonNull(target, "target").immutable();
      this.support = Objects.requireNonNull(support, "support").immutable();
      this.stance = Objects.requireNonNull(stance, "stance").immutable();
      this.allowProtectedTarget = allowProtectedTarget;
      if (!this.support.equals(this.target.below())) {
         throw new IllegalArgumentException("farm dirt support must be directly below target");
      }
   }

   static PlaceFarmDirtTask forFarmRepair(BlockPos target, BlockPos stance) {
      return new PlaceFarmDirtTask(target, target.below(), stance, true);
   }

   @Override
   protected void onStart() {
      this.dimension = this.currentDimension();
      this.terminal = false;
      this.successful = false;
      this.reason = FarmTaskReason.NONE;
      this.noProgressTicks = 0;
      this.attempts = 0;
      this.ticksSinceAttempt = 10;
      this.inventoryBaselineCaptured = false;
      this.transientReceiptDetached = false;
      if (PlayerPlacedBlockStore.get() == null) {
         this.fail(FarmTaskReason.STORE_UNAVAILABLE);
      } else if (this.loaded(this.target)
         && this.loaded(this.target.above())
         && this.loaded(this.support)
         && this.loaded(this.stance)
         && this.loaded(this.stance.below())
         && this.loaded(this.stance.above())) {
         this.expectedTarget = this.controller.getWorld().getBlockState(this.target);
         this.expectedSupport = this.controller.getWorld().getBlockState(this.support);
         this.expectedTargetHead = this.controller.getWorld().getBlockState(this.target.above());
         this.expectedStance = this.controller.getWorld().getBlockState(this.stance);
         this.expectedStanceHead = this.controller.getWorld().getBlockState(this.stance.above());
         this.expectedStancePassableCrop = FarmStanceNavigation.isPassableCropStance(this.controller, this.stance);
         if (!this.validPrecondition()) {
            this.fail(FarmTaskReason.MANIFEST_DRIFT);
         } else {
            if (this.countDirt() < 1) {
               this.fail(FarmTaskReason.DIRT_UNAVAILABLE);
            }
         }
      } else {
         this.fail(FarmTaskReason.CHUNK_UNLOADED);
      }
   }

   @Override
   protected Task onTick() {
      if (this.terminal) {
         return null;
      }

      if (!Objects.equals(this.dimension, this.currentDimension())) {
         this.fail(FarmTaskReason.DIMENSION_CHANGED);
         return null;
      }

      if (this.loaded(this.target)
         && this.loaded(this.target.above())
         && this.loaded(this.support)
         && this.loaded(this.stance)
         && this.loaded(this.stance.below())
         && this.loaded(this.stance.above())) {
         this.noProgressTicks++;
         this.ticksSinceAttempt++;
         if (this.noProgressTicks >= 300) {
            this.fail(FarmTaskReason.INTERACTION_DENIED);
            return null;
         }

         BlockState observedTarget = this.controller.getWorld().getBlockState(this.target);
         int dirtAfter = this.countDirt();
         if (this.inventoryBaselineCaptured) {
            if (placementCompleted(this.expectedTarget, observedTarget, this.dirtBefore, dirtAfter)) {
               this.succeed();
               return null;
            }

            if (!observedTarget.equals(this.expectedTarget) || dirtAfter != this.dirtBefore) {
               this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
               return null;
            }
         }

         if (!this.validPrecondition()) {
            this.fail(FarmTaskReason.MANIFEST_DRIFT);
            return null;
         }

         if (!FarmStanceNavigation.isAtFrozenStance(this.controller, this.stance)) {
            this.setDebugState("Moving to fixed dirt-placement stance");
            return new GetToBlockTask(this.stance);
         }

         if (this.attempts >= 3) {
            this.fail(FarmTaskReason.INTERACTION_DENIED);
            return null;
         }

         if (this.ticksSinceAttempt < 10) {
            return null;
         }

         if (this.controller.getSlotHandler().forceEquipItem(Items.DIRT)
            && this.controller.getPlayer().getMainHandItem().is(Items.DIRT)) {
            Optional<Rotation> reach = LookHelper.getReach(this.controller, this.support, Direction.UP);
            if (reach.isEmpty()) {
               this.fail(FarmTaskReason.INTERACTION_DENIED);
               return null;
            }

            if (!LookHelper.isLookingAt(this.controller, reach.get())) {
               LookHelper.lookAt(this.controller, reach.get());
               return null;
            }

            HitResult mouseOver = this.controller.getBaritone().getEntityContext().objectMouseOver();
            Optional<InteractionResult> interaction = this.dispatchPlacementAttempt(
               mouseOver, this::countDirt, hit -> FarmInteractionDispatcher.useMainHandBlock(this.controller, hit)
            );
            if (interaction.isEmpty()) {
               LookHelper.lookAt(this.controller, reach.get());
               return null;
            }

            if (interaction.get().consumesAction()) {
               this.controller.getPlayer().swing(InteractionHand.MAIN_HAND);
            }

            this.setDebugState("Placing and verifying farm dirt (attempt " + this.attempts + "/3)");
            return null;
         } else {
            this.fail(FarmTaskReason.DIRT_UNAVAILABLE);
            return null;
         }
      } else {
         this.fail(FarmTaskReason.CHUNK_UNLOADED);
         return null;
      }
   }

   static boolean placementCompleted(BlockState expectedTarget, BlockState observedTarget, int dirtBefore, int dirtAfter) {
      return expectedTarget != null
         && expectedTarget.isAir()
         && observedTarget != null
         && observedTarget.is(Blocks.DIRT)
         && dirtBefore >= 1
         && dirtAfter == dirtBefore - 1;
   }

   static PlaceFarmDirtTask.ReceiptDisposition receiptDisposition(
      int issuedAttempts, boolean baselineCaptured, BlockState expectedTarget, BlockState observedTarget, int dirtBefore, int dirtAfter
   ) {
      if (issuedAttempts <= 0) {
         return PlaceFarmDirtTask.ReceiptDisposition.PENDING;
      } else if (!baselineCaptured || expectedTarget == null || observedTarget == null) {
         return PlaceFarmDirtTask.ReceiptDisposition.INVALID;
      } else if (placementCompleted(expectedTarget, observedTarget, dirtBefore, dirtAfter)) {
         return PlaceFarmDirtTask.ReceiptDisposition.SUCCESS;
      } else {
         return observedTarget.equals(expectedTarget) && dirtAfter == dirtBefore
            ? PlaceFarmDirtTask.ReceiptDisposition.PENDING
            : PlaceFarmDirtTask.ReceiptDisposition.INVALID;
      }
   }

   static boolean isExpectedSupportTopHit(HitResult hit, BlockPos expectedSupport) {
      return FarmInteractionDispatcher.isExpectedBlockFace(hit, expectedSupport, Direction.UP);
   }

   Optional<InteractionResult> dispatchPlacementAttempt(HitResult hit, IntSupplier dirtCounter, Function<BlockHitResult, InteractionResult> interaction) {
      return FarmInteractionDispatcher.dispatchExactFace(hit, this.support, Direction.UP, () -> {
         if (!this.inventoryBaselineCaptured) {
            this.dirtBefore = Objects.requireNonNull(dirtCounter, "dirtCounter").getAsInt();
            this.inventoryBaselineCaptured = true;
         }

         this.attempts++;
         this.ticksSinceAttempt = 0;
      }, interaction);
   }

   boolean inventoryBaselineCaptured() {
      return this.inventoryBaselineCaptured;
   }

   @Override
   public boolean settlePendingTransition() {
      if (this.terminal) {
         return this.successful;
      }

      if (this.controller != null && this.expectedTarget != null && this.attempts > 0) {
         if (!Objects.equals(this.dimension, this.currentDimension())) {
            this.fail(FarmTaskReason.DIMENSION_CHANGED);
            return false;
         } else if (!this.loaded(this.target)) {
            this.fail(FarmTaskReason.CHUNK_UNLOADED);
            return false;
         } else {
            PlaceFarmDirtTask.ReceiptDisposition disposition = receiptDisposition(
               this.attempts,
               this.inventoryBaselineCaptured,
               this.expectedTarget,
               this.controller.getWorld().getBlockState(this.target),
               this.dirtBefore,
               this.countDirt()
            );
            if (disposition == PlaceFarmDirtTask.ReceiptDisposition.PENDING) {
               return true;
            } else if (disposition == PlaceFarmDirtTask.ReceiptDisposition.SUCCESS) {
               this.succeed();
               return true;
            } else {
               this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
               return false;
            }
         }
      } else {
         return true;
      }
   }

   @Override
   public boolean hasIssuedMutation() {
      return this.attempts > 0;
   }

   @Override
   public void prepareForTransientDetach() {
      this.transientReceiptDetached = true;
   }

   private boolean validPrecondition() {
      Level level = this.controller.getWorld();
      BlockState observedStance = level.getBlockState(this.stance);
      boolean observedStancePassableCrop = FarmStanceNavigation.isPassableCropStance(this.controller, this.stance);
      return FarmStanceNavigation.hasPlannerSafeLiveGeometry(this.controller, this.stance)
         && level.getBlockState(this.target).equals(this.expectedTarget)
         && this.expectedTarget.isAir()
         && level.getBlockState(this.target.above()).equals(this.expectedTargetHead)
         && this.expectedTargetHead.isAir()
         && level.getBlockState(this.support).equals(this.expectedSupport)
         && this.expectedSupport.isFaceSturdy(level, this.support, Direction.UP)
         && !(this.expectedSupport.getBlock() instanceof LiquidBlockContainer)
         && stanceStateStillValid(this.expectedStance, observedStance, this.expectedStancePassableCrop, observedStancePassableCrop)
         && level.getBlockState(this.stance.above()).equals(this.expectedStanceHead)
         && observedStance.getCollisionShape(level, this.stance).isEmpty()
         && this.expectedStanceHead.getCollisionShape(level, this.stance.above()).isEmpty()
         && (this.allowProtectedTarget || !this.isProtected(this.target))
         && !this.isProtected(this.target.above())
         && !this.isProtected(this.support)
         && !FarmStanceNavigation.stanceProtectionDenied(
            this.isProtected(this.stance.below()),
            this.isProtected(this.stance),
            observedStancePassableCrop,
            this.isProtected(this.stance.above())
         );
   }

   static boolean stanceStateStillValid(BlockState expected, BlockState observed, boolean expectedPassableCrop, boolean observedPassableCrop) {
      return expected != null
         && observed != null
         && (expected.equals(observed) || expectedPassableCrop && observedPassableCrop && expected.getBlock() == observed.getBlock());
   }

   private boolean loaded(BlockPos position) {
      return this.controller.getChunkTracker().isChunkLoaded(position);
   }

   private boolean isProtected(BlockPos position) {
      PlayerPlacedBlockStore store = PlayerPlacedBlockStore.get();
      return store == null || store.contains(this.dimension, position);
   }

   private int countDirt() {
      return this.controller.getItemStorage().getItemCount(Items.DIRT);
   }

   private String currentDimension() {
      return this.controller.getWorld().dimension().location().toString();
   }

   private void succeed() {
      this.terminal = true;
      this.successful = true;
      this.reason = FarmTaskReason.NONE;
   }

   private void fail(FarmTaskReason failure) {
      this.terminal = true;
      this.successful = false;
      this.reason = Objects.requireNonNull(failure, "failure");
   }

   @Override
   protected void onStop(Task interruptTask) {
      if (this.controller != null) {
         this.controller.getInputControls().release(Input.CLICK_RIGHT);
         this.controller.getBaritone().getPathingBehavior().forceCancel();
         if (this.controller.getBaritone().getCustomGoalProcess().isActive()) {
            this.controller.getBaritone().getCustomGoalProcess().onLostControl();
         }
      }

      if (!this.terminal && interruptTask == null && !this.transientReceiptDetached) {
         this.fail(FarmTaskReason.CANCELLED_OPERATOR);
      }
   }

   @Override
   public boolean isFinished() {
      return this.terminal;
   }

   @Override
   public boolean isSuccessful() {
      return this.terminal && this.successful;
   }

   @Override
   public FarmTaskReason reason() {
      return this.reason;
   }

   public String controlledReason() {
      return this.reason.controlledReason();
   }

   public BlockPos target() {
      return this.target;
   }

   public BlockPos support() {
      return this.support;
   }

   public BlockPos stance() {
      return this.stance;
   }

   public int attempts() {
      return this.attempts;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof PlaceFarmDirtTask task
         && task.target.equals(this.target)
         && task.support.equals(this.support)
         && task.stance.equals(this.stance)
         && task.allowProtectedTarget == this.allowProtectedTarget;
   }

   @Override
   protected String toDebugString() {
      return "Place verified farm dirt at " + this.target.toShortString();
   }

   enum ReceiptDisposition {
      PENDING,
      SUCCESS,
      INVALID;
   }
}
