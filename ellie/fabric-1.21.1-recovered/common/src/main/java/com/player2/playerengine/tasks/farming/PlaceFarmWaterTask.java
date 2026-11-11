package com.player2.playerengine.tasks.farming;

import com.player2.playerengine.automaton.api.utils.Rotation;
import com.player2.playerengine.automaton.api.utils.input.Input;
import com.player2.playerengine.structureprotection.PlayerPlacedBlockStore;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.movement.GetToBlockTask;
import com.player2.playerengine.util.helpers.LookHelper;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext.Fluid;

public final class PlaceFarmWaterTask extends Task implements FarmMutationReceipt {
   private static final int RETRY_DELAY_TICKS = 10;
   private final BlockPos center;
   private final BlockPos support;
   private final BlockPos stance;
   private String dimension;
   private BlockState expectedCenter;
   private BlockState expectedSupport;
   private boolean behaviourPushed;
   private boolean terminal;
   private boolean successful;
   private FarmTaskReason reason = FarmTaskReason.NONE;
   private int noProgressTicks;
   private int attempts;
   private int ticksSinceAttempt;
   private int emptyBucketsBefore;
   private int waterBucketsBefore;
   private boolean inventoryBaselineCaptured;
   private boolean transientReceiptDetached;

   public PlaceFarmWaterTask(BlockPos center, BlockPos stance) {
      this(center, center == null ? null : center.below(), stance);
   }

   public PlaceFarmWaterTask(BlockPos center, BlockPos support, BlockPos stance) {
      this.center = Objects.requireNonNull(center, "center").immutable();
      this.support = Objects.requireNonNull(support, "support").immutable();
      this.stance = Objects.requireNonNull(stance, "stance").immutable();
      if (!this.support.equals(this.center.below())) {
         throw new IllegalArgumentException("farm water support must be directly below center");
      }
   }

   @Override
   protected void onStart() {
      this.dimension = this.currentDimension();
      this.behaviourPushed = false;
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
      } else if (this.loaded(this.center)
         && this.loaded(this.support)
         && this.loaded(this.stance)
         && this.loaded(this.stance.below())
         && this.loaded(this.stance.above())) {
         this.expectedCenter = this.controller.getWorld().getBlockState(this.center);
         this.expectedSupport = this.controller.getWorld().getBlockState(this.support);
         if (!this.isValidPrecondition()) {
            this.fail(FarmTaskReason.MANIFEST_DRIFT);
         } else if (this.count(Items.WATER_BUCKET) < 1) {
            this.fail(FarmTaskReason.BUCKET_UNAVAILABLE);
         } else {
            this.controller.getBehaviour().push();
            this.behaviourPushed = true;
            this.controller.getBehaviour().setRayTracingFluidHandling(Fluid.NONE);
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

      if (this.loaded(this.center)
         && this.loaded(this.support)
         && this.loaded(this.stance)
         && this.loaded(this.stance.below())
         && this.loaded(this.stance.above())) {
         this.noProgressTicks++;
         this.ticksSinceAttempt++;
         if (this.noProgressTicks > 300) {
            this.fail(FarmTaskReason.INTERACTION_DENIED);
            return null;
         }

         BlockState observedCenter = this.controller.getWorld().getBlockState(this.center);
         if (this.inventoryBaselineCaptured) {
            int emptyAfter = this.count(Items.BUCKET);
            int waterAfter = this.count(Items.WATER_BUCKET);
            if (FarmInteractionPostconditions.waterPlacement(observedCenter, this.emptyBucketsBefore, emptyAfter, this.waterBucketsBefore, waterAfter)) {
               if (((Biome)this.controller.getWorld().getBiome(this.center).value()).shouldFreeze(this.controller.getWorld(), this.center)) {
                  this.fail(FarmTaskReason.FREEZE_RISK);
                  return null;
               }

               this.succeed();
               return null;
            }

            if (!observedCenter.equals(this.expectedCenter) || emptyAfter != this.emptyBucketsBefore || waterAfter != this.waterBucketsBefore) {
               this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
               return null;
            }
         }

         if (!this.isValidPrecondition()) {
            this.fail(FarmTaskReason.MANIFEST_DRIFT);
            return null;
         }

         if (!FarmStanceNavigation.isAtFrozenStance(this.controller, this.stance)) {
            this.setDebugState("Moving to fixed farm-water stance");
            return new GetToBlockTask(this.stance);
         }

         if (this.attempts >= 3) {
            this.fail(FarmTaskReason.INTERACTION_DENIED);
            return null;
         }

         if (this.ticksSinceAttempt < 10) {
            return null;
         }

         if (this.controller.getSlotHandler().forceEquipItem(Items.WATER_BUCKET)
            && this.controller.getPlayer().getMainHandItem().is(Items.WATER_BUCKET)) {
            Optional<Rotation> reach = LookHelper.getReach(this.controller, this.support, Direction.UP);
            if (reach.isEmpty()) {
               this.fail(FarmTaskReason.INTERACTION_DENIED);
               return null;
            } else if (!LookHelper.isLookingAt(this.controller, reach.get())) {
               LookHelper.lookAt(this.controller, reach.get());
               return null;
            } else {
               HitResult mouseOver = this.controller.getBaritone().getEntityContext().objectMouseOver();
               if (!isExpectedSupportTopHit(mouseOver, this.support)) {
                  LookHelper.lookAt(this.controller, reach.get());
                  return null;
               } else {
                  Optional<InteractionResult> interaction = FarmInteractionDispatcher.dispatchMainHandBlockThenItemAtFace(
                     this.controller, mouseOver, this.support, Direction.UP, () -> {
                        if (!this.inventoryBaselineCaptured) {
                           this.emptyBucketsBefore = this.count(Items.BUCKET);
                           this.waterBucketsBefore = this.count(Items.WATER_BUCKET);
                           this.inventoryBaselineCaptured = true;
                        }

                        this.attempts++;
                        this.ticksSinceAttempt = 0;
                     }
                  );
                  if (interaction.isEmpty()) {
                     LookHelper.lookAt(this.controller, reach.get());
                     return null;
                  } else {
                     this.setDebugState("Placing and verifying farm water");
                     return null;
                  }
               }
            }
         } else {
            this.fail(FarmTaskReason.BUCKET_UNAVAILABLE);
            return null;
         }
      } else {
         this.fail(FarmTaskReason.CHUNK_UNLOADED);
         return null;
      }
   }

   static boolean isExpectedSupportTopHit(HitResult hit, BlockPos expectedSupport) {
      return FarmInteractionDispatcher.isExpectedBlockFace(hit, expectedSupport, Direction.UP);
   }

   static PlaceFarmWaterTask.ReceiptDisposition receiptDisposition(
      int issuedAttempts,
      boolean baselineCaptured,
      BlockState expectedCenter,
      BlockState observedCenter,
      int emptyBucketsBefore,
      int emptyBucketsAfter,
      int waterBucketsBefore,
      int waterBucketsAfter
   ) {
      if (issuedAttempts <= 0) {
         return PlaceFarmWaterTask.ReceiptDisposition.PENDING;
      } else if (!baselineCaptured || expectedCenter == null || observedCenter == null) {
         return PlaceFarmWaterTask.ReceiptDisposition.INVALID;
      } else if (FarmInteractionPostconditions.waterPlacement(observedCenter, emptyBucketsBefore, emptyBucketsAfter, waterBucketsBefore, waterBucketsAfter)) {
         return PlaceFarmWaterTask.ReceiptDisposition.SUCCESS;
      } else {
         return observedCenter.equals(expectedCenter) && emptyBucketsAfter == emptyBucketsBefore && waterBucketsAfter == waterBucketsBefore
            ? PlaceFarmWaterTask.ReceiptDisposition.PENDING
            : PlaceFarmWaterTask.ReceiptDisposition.INVALID;
      }
   }

   @Override
   public boolean settlePendingTransition() {
      if (this.terminal) {
         return this.successful;
      }

      if (this.controller != null && this.expectedCenter != null && this.attempts > 0) {
         if (!Objects.equals(this.dimension, this.currentDimension())) {
            this.fail(FarmTaskReason.DIMENSION_CHANGED);
            return false;
         } else if (!this.loaded(this.center)) {
            this.fail(FarmTaskReason.CHUNK_UNLOADED);
            return false;
         } else {
            PlaceFarmWaterTask.ReceiptDisposition disposition = receiptDisposition(
               this.attempts,
               this.inventoryBaselineCaptured,
               this.expectedCenter,
               this.controller.getWorld().getBlockState(this.center),
               this.emptyBucketsBefore,
               this.count(Items.BUCKET),
               this.waterBucketsBefore,
               this.count(Items.WATER_BUCKET)
            );
            if (disposition == PlaceFarmWaterTask.ReceiptDisposition.PENDING) {
               return true;
            } else if (disposition == PlaceFarmWaterTask.ReceiptDisposition.INVALID) {
               this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
               return false;
            } else if (((Biome)this.controller.getWorld().getBiome(this.center).value()).shouldFreeze(this.controller.getWorld(), this.center)) {
               this.fail(FarmTaskReason.FREEZE_RISK);
               return false;
            } else {
               this.succeed();
               return true;
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

   private boolean isValidPrecondition() {
      Level level = this.controller.getWorld();
      return this.loaded(this.center)
         && this.loaded(this.support)
         && this.loaded(this.stance)
         && this.loaded(this.stance.below())
         && this.loaded(this.stance.above())
         && FarmStanceNavigation.hasPlannerSafeLiveGeometry(this.controller, this.stance)
         && level.getBlockState(this.center).equals(this.expectedCenter)
         && this.expectedCenter.isAir()
         && level.getBlockState(this.support).equals(this.expectedSupport)
         && this.expectedSupport.isFaceSturdy(level, this.support, Direction.UP)
         && !(this.expectedSupport.getBlock() instanceof LiquidBlockContainer)
         && !this.isProtected(this.center)
         && !this.isProtected(this.support)
         && !FarmStanceNavigation.stanceProtectionDenied(
            this.isProtected(this.stance.below()),
            this.isProtected(this.stance),
            FarmStanceNavigation.isPassableCropStance(this.controller, this.stance),
            this.isProtected(this.stance.above())
         );
   }

   private boolean loaded(BlockPos pos) {
      return this.controller.getChunkTracker().isChunkLoaded(pos);
   }

   private boolean isProtected(BlockPos pos) {
      PlayerPlacedBlockStore store = PlayerPlacedBlockStore.get();
      return store == null || store.contains(this.dimension, pos);
   }

   private int count(Item item) {
      return this.controller.getItemStorage().getItemCount(item);
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
      this.releaseControls();
      if (!this.terminal && interruptTask == null && !this.transientReceiptDetached) {
         this.fail(FarmTaskReason.CANCELLED_OPERATOR);
      }
   }

   private void releaseControls() {
      if (this.controller != null) {
         this.controller.getInputControls().release(Input.CLICK_RIGHT);
         this.controller.getBaritone().getPathingBehavior().forceCancel();
         if (this.controller.getBaritone().getCustomGoalProcess().isActive()) {
            this.controller.getBaritone().getCustomGoalProcess().onLostControl();
         }

         if (this.behaviourPushed) {
            this.controller.getBehaviour().pop();
            this.behaviourPushed = false;
         }
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

   public BlockPos center() {
      return this.center;
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
      return other instanceof PlaceFarmWaterTask task
         && task.center.equals(this.center)
         && task.support.equals(this.support)
         && task.stance.equals(this.stance);
   }

   @Override
   protected String toDebugString() {
      return "Place verified farm water at " + this.center.toShortString();
   }

   enum ReceiptDisposition {
      PENDING,
      SUCCESS,
      INVALID;
   }
}
