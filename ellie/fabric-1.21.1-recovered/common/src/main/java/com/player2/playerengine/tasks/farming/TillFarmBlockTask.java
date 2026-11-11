package com.player2.playerengine.tasks.farming;

import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.automaton.api.utils.Rotation;
import com.player2.playerengine.automaton.api.utils.input.Input;
import com.player2.playerengine.structureprotection.PlayerPlacedBlockStore;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.movement.GetToBlockTask;
import com.player2.playerengine.util.helpers.LookHelper;
import com.player2.playerengine.util.slots.PlayerSlot;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntSupplier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class TillFarmBlockTask extends Task implements FarmMutationReceipt {
   private static final int RETRY_DELAY_TICKS = 10;
   private final BlockPos target;
   private final BlockPos stance;
   private final Item requestedHoeItem;
   private String dimension;
   private BlockState expectedBefore;
   private ItemStack selectedHoe;
   private boolean terminal;
   private boolean successful;
   private FarmTaskReason reason = FarmTaskReason.NONE;
   private boolean alreadyFarmland;
   private boolean freshTill;
   private boolean durabilityApplied;
   private int noProgressTicks;
   private int attempts;
   private int ticksSinceAttempt;
   private int beforeDamage;
   private int maximumDamage;
   private boolean transientReceiptDetached;

   public TillFarmBlockTask(BlockPos target, BlockPos stance) {
      this(target, stance, null);
   }

   public TillFarmBlockTask(BlockPos target, BlockPos stance, Item requestedHoeItem) {
      this.target = Objects.requireNonNull(target, "target").immutable();
      this.stance = Objects.requireNonNull(stance, "stance").immutable();
      if (requestedHoeItem != null && !(requestedHoeItem instanceof HoeItem)) {
         throw new IllegalArgumentException("requested item must be a hoe");
      }

      this.requestedHoeItem = requestedHoeItem;
   }

   @Override
   protected void onStart() {
      this.dimension = this.currentDimension();
      this.selectedHoe = null;
      this.terminal = false;
      this.successful = false;
      this.reason = FarmTaskReason.NONE;
      this.alreadyFarmland = false;
      this.freshTill = false;
      this.durabilityApplied = false;
      this.noProgressTicks = 0;
      this.attempts = 0;
      this.ticksSinceAttempt = 10;
      this.transientReceiptDetached = false;
      if (PlayerPlacedBlockStore.get() == null) {
         this.fail(FarmTaskReason.STORE_UNAVAILABLE);
      } else if (this.loaded(this.target)
         && this.loaded(this.target.above())
         && this.loaded(this.stance)
         && this.loaded(this.stance.below())
         && this.loaded(this.stance.above())) {
         this.expectedBefore = this.controller.getWorld().getBlockState(this.target);
         if (this.expectedBefore.is(Blocks.FARMLAND)) {
            this.alreadyFarmland = true;
            this.succeed();
         } else if (!FarmStanceNavigation.hasPlannerSafeLiveGeometry(this.controller, this.stance)
            || tillProtectionDenied(
               this.isProtected(this.target),
               this.isProtected(this.target.above()),
               this.isProtected(this.stance.below()),
               this.isProtected(this.stance),
               FarmStanceNavigation.isPassableCropStance(this.controller, this.stance),
               this.isProtected(this.stance.above())
            )) {
            this.fail(FarmTaskReason.INTERACTION_DENIED);
         } else if (!this.controller.getWorld().getBlockState(this.target.above()).isAir()) {
            this.fail(FarmTaskReason.MANIFEST_DRIFT);
         } else {
            this.selectedHoe = this.chooseExactHoe();
            if (this.selectedHoe == null) {
               this.fail(FarmTaskReason.HOE_UNAVAILABLE);
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
         && this.loaded(this.stance)
         && this.loaded(this.stance.below())
         && this.loaded(this.stance.above())) {
         BlockState observed = this.controller.getWorld().getBlockState(this.target);
         if (!observed.equals(this.expectedBefore)) {
            this.completeOwnedTillTransition(observed);
            return null;
         }

         if (FarmStanceNavigation.hasPlannerSafeLiveGeometry(this.controller, this.stance)
            && !tillProtectionDenied(
               this.isProtected(this.target),
               this.isProtected(this.target.above()),
               this.isProtected(this.stance.below()),
               this.isProtected(this.stance),
               FarmStanceNavigation.isPassableCropStance(this.controller, this.stance),
               this.isProtected(this.stance.above())
            )) {
            this.noProgressTicks++;
            this.ticksSinceAttempt++;
            if (this.noProgressTicks > 300) {
               this.fail(FarmTaskReason.INTERACTION_DENIED);
               return null;
            }

            if (!this.controller.getWorld().getBlockState(this.target.above()).isAir()) {
               this.fail(FarmTaskReason.MANIFEST_DRIFT);
               return null;
            }

            if (!FarmStanceNavigation.isAtFrozenStance(this.controller, this.stance)) {
               this.setDebugState("Moving to fixed till stance");
               return new GetToBlockTask(this.stance);
            }

            if (this.attempts >= 3) {
               this.fail(FarmTaskReason.INTERACTION_DENIED);
               return null;
            }

            if (this.ticksSinceAttempt < 10) {
               return null;
            }

            if (!this.equipSelectedHoe()) {
               this.fail(FarmTaskReason.HOE_UNAVAILABLE);
               return null;
            }

            Optional<Rotation> reach = LookHelper.getReach(this.controller, this.target, Direction.UP);
            if (reach.isEmpty()) {
               this.fail(FarmTaskReason.INTERACTION_DENIED);
               return null;
            }

            if (!LookHelper.isLookingAt(this.controller, reach.get())) {
               LookHelper.lookAt(this.controller, reach.get());
               return null;
            }

            ItemStack held = this.controller.getPlayer().getMainHandItem();
            HitResult mouseOver = this.controller.getBaritone().getEntityContext().objectMouseOver();
            Optional<InteractionResult> interaction = this.dispatchTillAttempt(
               mouseOver, held::getDamageValue, held::getMaxDamage, hit -> FarmInteractionDispatcher.useMainHandBlock(this.controller, hit)
            );
            if (interaction.isEmpty()) {
               LookHelper.lookAt(this.controller, reach.get());
               return null;
            }

            this.setDebugState("Tilling and verifying target (attempt " + this.attempts + "/3)");
            BlockState afterInteraction = this.controller.getWorld().getBlockState(this.target);
            if (shouldSettleImmediatelyAfterDispatch(this.attempts, this.durabilityApplied, this.expectedBefore, afterInteraction)) {
               this.completeOwnedTillTransition(afterInteraction);
            }

            return null;
         } else {
            this.fail(FarmTaskReason.INTERACTION_DENIED);
            return null;
         }
      } else {
         this.fail(FarmTaskReason.CHUNK_UNLOADED);
         return null;
      }
   }

   static boolean isExpectedTargetTopHit(HitResult hit, BlockPos expectedTarget) {
      return FarmInteractionDispatcher.isExpectedBlockFace(hit, expectedTarget, Direction.UP);
   }

   Optional<InteractionResult> dispatchTillAttempt(
      HitResult hit, IntSupplier damageSupplier, IntSupplier maximumDamageSupplier, Function<BlockHitResult, InteractionResult> interaction
   ) {
      return FarmInteractionDispatcher.dispatchExactFace(hit, this.target, Direction.UP, () -> {
         this.beforeDamage = Objects.requireNonNull(damageSupplier, "damageSupplier").getAsInt();
         this.maximumDamage = Objects.requireNonNull(maximumDamageSupplier, "maximumDamageSupplier").getAsInt();
         this.attempts++;
         this.ticksSinceAttempt = 0;
      }, interaction);
   }

   private ItemStack chooseExactHoe() {
      LivingEntityInventory inventory = this.controller.getInventory();
      ItemStack best = null;
      int bestRemaining = -1;

      for (ItemStack candidate : inventory.main) {
         if (!candidate.isEmpty()
            && candidate.getItem() instanceof HoeItem
            && (this.requestedHoeItem == null || candidate.getItem() == this.requestedHoeItem)
            && FarmHoePolicy.isAccepted(candidate)) {
            int remaining = candidate.getMaxDamage() - candidate.getDamageValue();
            if (remaining > bestRemaining) {
               best = candidate;
               bestRemaining = remaining;
            }
         }
      }

      return bestRemaining >= 1 ? best : null;
   }

   static boolean ownsFreshTillTransition(int issuedAttempts) {
      return issuedAttempts > 0;
   }

   static boolean needsOwnedTransitionSettlement(int issuedAttempts, boolean damageAlreadyApplied, BlockState expected, BlockState observed) {
      return issuedAttempts > 0 && !damageAlreadyApplied && expected != null && observed != null && !observed.equals(expected);
   }

   static boolean shouldSettleImmediatelyAfterDispatch(int issuedAttempts, boolean damageAlreadyApplied, BlockState expected, BlockState observed) {
      return needsOwnedTransitionSettlement(issuedAttempts, damageAlreadyApplied, expected, observed);
   }

   @Override
   public boolean settlePendingTransition() {
      if (this.terminal) {
         return this.successful;
      }

      if (this.controller != null && this.expectedBefore != null) {
         if (!Objects.equals(this.dimension, this.currentDimension())) {
            this.fail(FarmTaskReason.DIMENSION_CHANGED);
            return false;
         } else if (!this.loaded(this.target)) {
            this.fail(FarmTaskReason.CHUNK_UNLOADED);
            return false;
         } else {
            BlockState observed = this.controller.getWorld().getBlockState(this.target);
            return !needsOwnedTransitionSettlement(this.attempts, this.durabilityApplied, this.expectedBefore, observed)
               ? true
               : this.completeOwnedTillTransition(observed);
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

   private boolean completeOwnedTillTransition(BlockState observed) {
      if (!FarmInteractionPostconditions.farmlandTransition(this.expectedBefore, observed)) {
         this.fail(FarmTaskReason.MANIFEST_DRIFT);
         return false;
      } else if (!ownsFreshTillTransition(this.attempts)) {
         this.fail(FarmTaskReason.MANIFEST_DRIFT);
         return false;
      } else {
         this.freshTill = true;
         if (this.durabilityApplied) {
            this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
            return false;
         } else {
            ItemStack ownedHoe = this.selectedHoe;
            if (ownedHoe == null
               || ownedHoe.isEmpty()
               || !this.inventoryContainsIdentity(ownedHoe)
               || ownedHoe.getDamageValue() != this.beforeDamage
               || ownedHoe.getMaxDamage() != this.maximumDamage) {
               this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
               return false;
            } else if (this.controller.getPlayer().getMainHandItem() != ownedHoe && !this.equipSelectedHoe()) {
               this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
               return false;
            } else {
               this.controller.getEntity().swing(InteractionHand.MAIN_HAND);
               this.durabilityApplied = HoeDurabilityCompat.damageOnce(ownedHoe, this.controller.getEntity());
               if (!this.durabilityApplied) {
                  this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
                  return false;
               } else {
                  this.succeed();
                  return true;
               }
            }
         }
      }
   }

   private boolean inventoryContainsIdentity(ItemStack expected) {
      for (ItemStack stack : this.controller.getInventory().main) {
         if (stack == expected) {
            return true;
         }
      }

      return false;
   }

   static boolean isStoneOrBetter(HoeItem hoe) {
      return FarmHoePolicy.isStoneOrBetter(hoe);
   }

   static boolean tillProtectionDenied(
      boolean targetProtected,
      boolean targetHeadProtected,
      boolean stanceSupportProtected,
      boolean stanceProtected,
      boolean stanceIsPassableCrop,
      boolean stanceHeadProtected
   ) {
      return targetProtected
         || targetHeadProtected
         || FarmStanceNavigation.stanceProtectionDenied(stanceSupportProtected, stanceProtected, stanceIsPassableCrop, stanceHeadProtected);
   }

   private boolean equipSelectedHoe() {
      if (this.selectedHoe != null && !this.selectedHoe.isEmpty()) {
         LivingEntityInventory inventory = this.controller.getInventory();
         int exactSlot = -1;

         for (int i = 0; i < inventory.main.size(); i++) {
            if (inventory.main.get(i) == this.selectedHoe) {
               exactSlot = i;
               break;
            }
         }

         if (exactSlot < 0) {
            return false;
         }

         this.controller.getSlotHandler().forceEquipSlot(this.controller, PlayerSlot.getMainSlot(inventory, exactSlot));
         return this.controller.getPlayer().getMainHandItem() == this.selectedHoe && this.selectedHoe.getItem() instanceof HoeItem;
      } else {
         return false;
      }
   }

   private boolean loaded(BlockPos pos) {
      return this.controller.getChunkTracker().isChunkLoaded(pos);
   }

   private boolean isProtected(BlockPos pos) {
      PlayerPlacedBlockStore store = PlayerPlacedBlockStore.get();
      return store == null || store.contains(this.dimension, pos);
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

   public BlockPos stance() {
      return this.stance;
   }

   public Item selectedHoeItem() {
      return this.selectedHoe == null ? null : this.selectedHoe.getItem();
   }

   public boolean alreadyFarmland() {
      return this.alreadyFarmland;
   }

   public boolean freshTill() {
      return this.freshTill;
   }

   public boolean durabilityApplied() {
      return this.durabilityApplied;
   }

   public int attempts() {
      return this.attempts;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof TillFarmBlockTask task
         && task.target.equals(this.target)
         && task.stance.equals(this.stance)
         && task.requestedHoeItem == this.requestedHoeItem;
   }

   @Override
   protected String toDebugString() {
      return "Till verified farm block at " + this.target.toShortString();
   }
}
