package com.player2.playerengine.tasks.farming;

import com.player2.playerengine.automaton.api.utils.Rotation;
import com.player2.playerengine.automaton.api.utils.input.Input;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.movement.GetToBlockTask;
import com.player2.playerengine.trackers.storage.SurvivalConsumptionLedger;
import com.player2.playerengine.util.helpers.LookHelper;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.block.state.BlockState;

public final class PlantFarmBlockTask extends Task implements FarmMutationReceipt {
   private static final int RETRY_DELAY_TICKS = 10;
   private final BlockPos target;
   private final BlockPos stance;
   private final FarmPlantingItemResolver.Descriptor descriptor;
   private String dimension;
   private BlockState expectedTarget;
   private boolean terminal;
   private boolean successful;
   private FarmTaskReason reason = FarmTaskReason.NONE;
   private boolean inventoryBaselineCaptured;
   private int itemCountBefore;
   private SurvivalConsumptionLedger.Snapshot survivalConsumptionBaseline;
   private int attempts;
   private int noProgressTicks;
   private int ticksSinceAttempt;
   private boolean transientReceiptDetached;

   public PlantFarmBlockTask(BlockPos target, BlockPos stance, FarmPlantingItemResolver.Descriptor descriptor) {
      this.target = Objects.requireNonNull(target, "target").immutable();
      this.stance = Objects.requireNonNull(stance, "stance").immutable();
      this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
   }

   @Override
   protected void onStart() {
      this.dimension = this.currentDimension();
      this.terminal = false;
      this.successful = false;
      this.reason = FarmTaskReason.NONE;
      this.inventoryBaselineCaptured = false;
      this.itemCountBefore = 0;
      this.survivalConsumptionBaseline = null;
      this.attempts = 0;
      this.noProgressTicks = 0;
      this.ticksSinceAttempt = 10;
      this.transientReceiptDetached = false;
      if (this.loaded(this.target)
         && this.loaded(this.target.below())
         && this.loaded(this.stance)
         && this.loaded(this.stance.below())
         && this.loaded(this.stance.above())) {
         this.expectedTarget = this.controller.getWorld().getBlockState(this.target);
         if (this.expectedTarget.isAir() && this.controller.getWorld().getBlockState(this.target.below()).is(Blocks.FARMLAND)) {
            if (!FarmStanceNavigation.hasPlannerSafeLiveGeometry(this.controller, this.stance)) {
               this.fail(FarmTaskReason.INTERACTION_DENIED);
            }
         } else {
            this.fail(FarmTaskReason.MANIFEST_DRIFT);
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
         && this.loaded(this.target.below())
         && this.loaded(this.stance)
         && this.loaded(this.stance.below())
         && this.loaded(this.stance.above())) {
         FarmPlantingReceiptClassifier.Disposition receipt = this.receiptDisposition();
         if (receipt == FarmPlantingReceiptClassifier.Disposition.SUCCESS) {
            this.succeed();
            return null;
         }

         if (receipt == FarmPlantingReceiptClassifier.Disposition.INVALID) {
            this.fail(FarmTaskReason.MANIFEST_DRIFT);
            return null;
         }

         this.noProgressTicks++;
         this.ticksSinceAttempt++;
         if (this.noProgressTicks >= 300 || this.attempts >= 3) {
            this.fail(FarmTaskReason.INTERACTION_DENIED);
            return null;
         }

         if (!this.validPrecondition() || !FarmStanceNavigation.hasPlannerSafeLiveGeometry(this.controller, this.stance)) {
            this.fail(FarmTaskReason.MANIFEST_DRIFT);
            return null;
         }

         if (!FarmStanceNavigation.isAtFrozenStance(this.controller, this.stance)) {
            this.setDebugState("Moving to fixed planting stance");
            return new GetToBlockTask(this.stance);
         }

         if (this.ticksSinceAttempt < 10) {
            return null;
         }

         Item item = this.descriptor.item();
         if (this.countRequestedItem() >= 1
            && this.controller.getSlotHandler().forceEquipItem(item)
            && this.controller.getPlayer().getMainHandItem().is(item)) {
            BlockPos support = this.target.below();
            Optional<Rotation> reach = LookHelper.getReach(this.controller, support, Direction.UP);
            if (reach.isEmpty()) {
               this.fail(FarmTaskReason.INTERACTION_DENIED);
               return null;
            } else if (!LookHelper.isLookingAt(this.controller, reach.get())) {
               LookHelper.lookAt(this.controller, reach.get());
               return null;
            } else {
               HitResult mouseOver = this.controller.getBaritone().getEntityContext().objectMouseOver();
               Optional<InteractionResult> interaction = FarmInteractionDispatcher.dispatchMainHandBlockUseAtFace(
                  this.controller, mouseOver, support, Direction.UP, () -> {
                     if (!this.inventoryBaselineCaptured) {
                        this.itemCountBefore = this.countRequestedItem();
                        this.survivalConsumptionBaseline = this.controller.getSurvivalConsumptionLedger().snapshot();
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
                  this.setDebugState("Planting and verifying crop (attempt " + this.attempts + "/3)");
                  return null;
               }
            }
         } else {
            this.fail(FarmTaskReason.INTERACTION_DENIED);
            return null;
         }
      } else {
         this.fail(FarmTaskReason.CHUNK_UNLOADED);
         return null;
      }
   }

   private FarmPlantingReceiptClassifier.Disposition receiptDisposition() {
      BlockState observed = this.controller.getWorld().getBlockState(this.target);
      String observedCanonical = HarvestBehaviorRegistry.DEFAULT
         .resolve(observed)
         .flatMap(behavior -> behavior.canonicalCropId(observed))
         .map(Object::toString)
         .orElse(null);
      int confirmedSurvivalConsumptions = this.inventoryBaselineCaptured
         ? this.controller.getSurvivalConsumptionLedger().consumedSince(this.descriptor.item(), this.survivalConsumptionBaseline).orElse(-1)
         : 0;
      return FarmPlantingReceiptClassifier.classify(
         this.attempts,
         this.inventoryBaselineCaptured,
         observed.equals(this.expectedTarget),
         this.controller.getWorld().getBlockState(this.target.below()).is(Blocks.FARMLAND),
         this.descriptor.canonicalCropId().toString(),
         observedCanonical,
         confirmedSurvivalConsumptions,
         this.itemCountBefore,
         this.countRequestedItem()
      );
   }

   private boolean validPrecondition() {
      return this.controller.getWorld().getBlockState(this.target).equals(this.expectedTarget)
         && this.expectedTarget.isAir()
         && this.controller.getWorld().getBlockState(this.target.below()).is(Blocks.FARMLAND)
         && this.descriptor.placementBlock().defaultBlockState().canSurvive(this.controller.getWorld(), this.target);
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
         }

         if (this.loaded(this.target) && this.loaded(this.target.below())) {
            FarmPlantingReceiptClassifier.Disposition receipt = this.receiptDisposition();
            if (receipt == FarmPlantingReceiptClassifier.Disposition.SUCCESS) {
               this.succeed();
               return true;
            } else if (receipt == FarmPlantingReceiptClassifier.Disposition.INVALID) {
               this.fail(FarmTaskReason.MANIFEST_DRIFT);
               return false;
            } else {
               return true;
            }
         } else {
            this.fail(FarmTaskReason.CHUNK_UNLOADED);
            return false;
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

   @Override
   protected void onStop(Task interruptTask) {
      this.quiesce();
      if (!this.terminal && interruptTask == null && !this.transientReceiptDetached) {
         this.fail(FarmTaskReason.CANCELLED_OPERATOR);
      }
   }

   private int countRequestedItem() {
      return this.controller.getItemStorage().getItemCountInventoryOnly(this.descriptor.item());
   }

   private boolean loaded(BlockPos position) {
      return this.controller.getChunkTracker().isChunkLoaded(position);
   }

   private String currentDimension() {
      return this.controller.getWorld().dimension().location().toString();
   }

   private void quiesce() {
      if (this.controller != null) {
         this.controller.getInputControls().release(Input.CLICK_RIGHT);
         this.controller.getBaritone().getPathingBehavior().forceCancel();
      }
   }

   private void succeed() {
      this.terminal = true;
      this.successful = true;
      this.reason = FarmTaskReason.NONE;
      this.controller.getEntity().swing(InteractionHand.MAIN_HAND);
   }

   private void fail(FarmTaskReason failure) {
      this.terminal = true;
      this.successful = false;
      this.reason = Objects.requireNonNull(failure, "failure");
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

   public BlockPos target() {
      return this.target;
   }

   public BlockPos stance() {
      return this.stance;
   }

   public FarmPlantingItemResolver.Descriptor descriptor() {
      return this.descriptor;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof PlantFarmBlockTask task
         && task.target.equals(this.target)
         && task.stance.equals(this.stance)
         && task.descriptor.item() == this.descriptor.item();
   }

   @Override
   protected String toDebugString() {
      return "Plant verified crop " + this.descriptor.plantingItemId() + " at " + this.target.toShortString();
   }
}
