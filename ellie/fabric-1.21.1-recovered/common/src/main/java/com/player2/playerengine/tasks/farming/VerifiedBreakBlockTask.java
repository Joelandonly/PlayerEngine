package com.player2.playerengine.tasks.farming;

import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.automaton.api.utils.Rotation;
import com.player2.playerengine.automaton.api.utils.input.Input;
import com.player2.playerengine.structureprotection.PlayerPlacedBlockStore;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.movement.GetToBlockTask;
import com.player2.playerengine.util.helpers.LookHelper;
import com.player2.playerengine.util.helpers.StorageHelper;
import com.player2.playerengine.util.slots.PlayerSlot;
import com.player2.playerengine.util.slots.Slot;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;

public final class VerifiedBreakBlockTask extends Task implements FarmMutationReceipt {
   private final BlockPos target;
   private final BlockPos stance;
   private final Predicate<BlockState> allowedAfter;
   private final Predicate<BlockState> requiredBefore;
   private final boolean allowProtectedTarget;
   private final boolean requirePlannerSafeStanceSupport;
   private final boolean protectStanceSupport;
   private final boolean protectTargetSupport;
   private final boolean requireUnmodifiedDropHand;
   private final String operationIdentity;
   private final FarmBreakToolRequirement repairToolRequirement;
   private final VerifiedBreakBlockTask.SourceGuard sourceGuard;
   private String dimension;
   private BlockState expectedBefore;
   private boolean terminal;
   private boolean successful;
   private FarmTaskReason reason = FarmTaskReason.NONE;
   private int noProgressTicks;
   private int attempts;
   private int ticksInAttempt;
   private int attemptTickBudget;
   private boolean clickHeld;
   private ItemStack selectedRepairStack;
   private boolean preAttemptTargetDrift;
   private boolean transientReceiptDetached;

   public VerifiedBreakBlockTask(BlockPos target, BlockPos stance) {
      this(target, stance, BlockStateBase::isAir);
   }

   public VerifiedBreakBlockTask(BlockPos target, BlockPos stance, Predicate<BlockState> allowedAfter) {
      this(target, stance, allowedAfter, state -> !allowedAfter.test(state), false, true, true, false, false, "ordinary", null, null);
   }

   private VerifiedBreakBlockTask(
      BlockPos target,
      BlockPos stance,
      Predicate<BlockState> allowedAfter,
      Predicate<BlockState> requiredBefore,
      boolean allowProtectedTarget,
      boolean requirePlannerSafeStanceSupport,
      boolean protectStanceSupport,
      boolean protectTargetSupport,
      boolean requireUnmodifiedDropHand,
      String operationIdentity,
      FarmBreakToolRequirement repairToolRequirement,
      VerifiedBreakBlockTask.SourceGuard sourceGuard
   ) {
      this.target = Objects.requireNonNull(target, "target").immutable();
      this.stance = Objects.requireNonNull(stance, "stance").immutable();
      this.allowedAfter = Objects.requireNonNull(allowedAfter, "allowedAfter");
      this.requiredBefore = Objects.requireNonNull(requiredBefore, "requiredBefore");
      this.allowProtectedTarget = allowProtectedTarget;
      this.requirePlannerSafeStanceSupport = requirePlannerSafeStanceSupport;
      this.protectStanceSupport = protectStanceSupport;
      this.protectTargetSupport = protectTargetSupport;
      this.requireUnmodifiedDropHand = requireUnmodifiedDropHand;
      this.operationIdentity = Objects.requireNonNull(operationIdentity, "operationIdentity");
      this.repairToolRequirement = repairToolRequirement;
      this.sourceGuard = sourceGuard;
   }

   static VerifiedBreakBlockTask forMatureCrop(BlockPos target, BlockPos stance) {
      return new VerifiedBreakBlockTask(
         target, stance, BlockStateBase::isAir, HarvestFarmTask::isActionable, true, false, false, false, false, "mature-crop", null, null
      );
   }

   static VerifiedBreakBlockTask forUnprotectedSource(
      BlockPos target, BlockPos stance, BlockState expectedState, Item plantingItem, String dimensionId, PlantableSourcePlanner.SourceKind sourceKind
   ) {
      BlockState frozenExpected = Objects.requireNonNull(expectedState, "expectedState");
      VerifiedBreakBlockTask.SourceGuard guard = new VerifiedBreakBlockTask.SourceGuard(
         Objects.requireNonNull(plantingItem, "plantingItem"), dimensionId, Objects.requireNonNull(sourceKind, "sourceKind"), frozenExpected
      );
      return new VerifiedBreakBlockTask(
         target,
         stance,
         BlockStateBase::isAir,
         frozenExpected::equals,
         false,
         false,
         true,
         true,
         true,
         "unprotected-source:" + guard.identity(),
         null,
         guard
      );
   }

   static VerifiedBreakBlockTask forFarmRepair(
      BlockPos target, BlockPos stance, String expectedStateFingerprint, FarmBreakToolRequirement breakToolRequirement
   ) {
      String fingerprint = Objects.requireNonNull(expectedStateFingerprint, "expectedStateFingerprint");
      FarmBreakToolRequirement toolRequirement = Objects.requireNonNull(breakToolRequirement, "breakToolRequirement");
      BlockState expectedState = Objects.requireNonNull(toolRequirement.expectedState(), "breakToolRequirement.expectedState");
      if (!fingerprint.equals(FarmSiteWorldView.stateFingerprint(expectedState))) {
         throw new IllegalArgumentException("Repair tool requirement must describe the frozen target state");
      } else {
         return new VerifiedBreakBlockTask(
            target,
            stance,
            BlockStateBase::isAir,
            state -> fingerprint.equals(FarmSiteWorldView.stateFingerprint(state))
               && state.is(expectedState.getBlock())
               && HarvestBehaviorRegistry.DEFAULT.resolve(state).isEmpty(),
            true,
            true,
            true,
            false,
            false,
            "farm-repair:" + fingerprint + ":" + toolRequirement,
            toolRequirement,
            null
         );
      }
   }

   @Override
   protected void onStart() {
      this.dimension = this.currentDimension();
      this.terminal = false;
      this.successful = false;
      this.reason = FarmTaskReason.NONE;
      this.noProgressTicks = 0;
      this.attempts = 0;
      this.ticksInAttempt = 0;
      this.attemptTickBudget = 0;
      this.clickHeld = false;
      this.selectedRepairStack = null;
      this.preAttemptTargetDrift = false;
      this.transientReceiptDetached = false;
      if (PlayerPlacedBlockStore.get() == null) {
         this.fail(FarmTaskReason.STORE_UNAVAILABLE);
      } else if (this.loaded(this.target) && this.loaded(this.stance) && this.loaded(this.stance.below()) && this.loaded(this.stance.above())) {
         this.expectedBefore = this.controller.getWorld().getBlockState(this.target);
         boolean requiredBeforeSatisfied = this.requiredBefore.test(this.expectedBefore);
         if (requiredBeforeSatisfied && !this.allowedAfter.test(this.expectedBefore)) {
            if (targetProtectionDenied(this.isProtected(this.target), this.allowProtectedTarget, requiredBeforeSatisfied)
               || this.protectTargetSupport && this.isProtected(this.target.below())
               || this.liveStanceUnsafe()
               || !this.sourceGuardPermits()) {
               this.fail(FarmTaskReason.MANIFEST_DRIFT);
            }
         } else {
            this.preAttemptTargetDrift = true;
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

      if (this.loaded(this.target) && this.loaded(this.stance) && this.loaded(this.stance.below()) && this.loaded(this.stance.above())) {
         BlockState observed = this.controller.getWorld().getBlockState(this.target);
         VerifiedBreakBlockTask.ObservedTransition transition = observedTransition(this.expectedBefore, observed, this.allowedAfter, this.attempts);
         if (transition != VerifiedBreakBlockTask.ObservedTransition.UNCHANGED) {
            if (transition == VerifiedBreakBlockTask.ObservedTransition.SUCCESS) {
               this.succeed(transition);
            } else {
               this.preAttemptTargetDrift = transition == VerifiedBreakBlockTask.ObservedTransition.PRE_ATTEMPT_DRIFT;
               this.fail(FarmTaskReason.MANIFEST_DRIFT);
            }

            return null;
         } else {
            if (targetProtectionDenied(this.isProtected(this.target), this.allowProtectedTarget, this.requiredBefore.test(this.expectedBefore))
               || this.protectTargetSupport && this.isProtected(this.target.below())
               || this.liveStanceUnsafe()) {
               this.fail(FarmTaskReason.INTERACTION_DENIED);
               return null;
            }

            if (this.clickHeld && this.repairToolRequirement != null && this.repairToolRequirement.requiresTool() && !this.selectedRepairToolStillAvailable()) {
               this.fail(FarmTaskReason.REPAIR_TOOL_UNAVAILABLE);
               return null;
            }

            this.noProgressTicks++;
            if (this.noProgressTicks > 300) {
               this.fail(FarmTaskReason.INTERACTION_DENIED);
               return null;
            }

            if (!FarmStanceNavigation.isAtFrozenStance(this.controller, this.stance)) {
               this.stopClick();
               this.setDebugState("Moving to fixed break stance");
               return new GetToBlockTask(this.stance);
            }

            Optional<Rotation> reach = LookHelper.getReach(this.controller, this.target);
            if (reach.isEmpty()) {
               this.stopClick();
               this.fail(FarmTaskReason.INTERACTION_DENIED);
               return null;
            }

            if (!LookHelper.isLookingAt(this.controller, reach.get())) {
               this.stopClick();
               LookHelper.lookAt(this.controller, reach.get());
               return null;
            }

            if (!this.sourceGuardPermits()) {
               this.stopClick();
               this.fail(FarmTaskReason.INTERACTION_DENIED);
               return null;
            }

            boolean repairHandPrepared = false;
            if (!this.clickHeld) {
               if (this.attempts >= 3) {
                  this.fail(FarmTaskReason.INTERACTION_DENIED);
                  return null;
               }

               if (this.repairToolRequirement != null) {
                  if (!this.requiredBefore.test(observed)) {
                     this.preAttemptTargetDrift = true;
                     this.fail(FarmTaskReason.MANIFEST_DRIFT);
                     return null;
                  }

                  if (!this.equipFarmRepairHand()) {
                     this.fail(FarmTaskReason.REPAIR_TOOL_UNAVAILABLE);
                     return null;
                  }

                  repairHandPrepared = true;
               } else if (this.requireUnmodifiedDropHand) {
                  if (!this.equipUnmodifiedDropHand()) {
                     this.fail(FarmTaskReason.INTERACTION_DENIED);
                     return null;
                  }
               } else {
                  this.equipBestTool(this.expectedBefore);
               }

               this.attempts++;
               this.ticksInAttempt = 0;
               float destroyProgress = this.controller.getPlayer() instanceof Player player
                  ? observed.getDestroyProgress(player, this.controller.getWorld(), this.target)
                  : 0.0F;
               this.attemptTickBudget = continuousClickBudget(destroyProgress);
               this.clickHeld = true;
            }

            if (this.repairToolRequirement != null && !repairHandPrepared && !this.reequipSelectedRepairHand()) {
               this.fail(FarmTaskReason.REPAIR_TOOL_UNAVAILABLE);
               return null;
            }

            if (this.requireUnmodifiedDropHand && !this.equipUnmodifiedDropHand()) {
               this.fail(FarmTaskReason.INTERACTION_DENIED);
               return null;
            }

            this.controller.getInputControls().hold(Input.CLICK_LEFT);
            this.ticksInAttempt++;
            if (this.ticksInAttempt >= this.attemptTickBudget) {
               this.stopClick();
            }

            this.setDebugState("Breaking and verifying target (attempt " + this.attempts + "/3)");
            return null;
         }
      } else {
         this.fail(FarmTaskReason.CHUNK_UNLOADED);
         return null;
      }
   }

   private void equipBestTool(BlockState state) {
      Optional<Slot> best = StorageHelper.getBestToolSlot(this.controller, state);
      if (!best.isEmpty()) {
         Item exactItem = StorageHelper.getItemStackInSlot(best.get()).getItem();
         this.controller.getSlotHandler().forceEquipItem(exactItem);
      }
   }

   private boolean equipUnmodifiedDropHand() {
      LivingEntityInventory inventory = this.controller.getInventory();
      int selected = -1;

      for (int i = 0; i < inventory.main.size(); i++) {
         if (((ItemStack)inventory.main.get(i)).isEmpty()) {
            selected = i;
            break;
         }
      }

      if (selected < 0) {
         for (int i = 0; i < inventory.main.size(); i++) {
            if (safeUnmodifiedDropHand((ItemStack)inventory.main.get(i))) {
               selected = i;
               break;
            }
         }
      }

      if (selected < 0) {
         return false;
      }

      this.controller.getSlotHandler().forceEquipSlot(this.controller, PlayerSlot.getMainSlot(inventory, selected));
      return safeUnmodifiedDropHand(this.controller.getPlayer().getMainHandItem());
   }

   static boolean safeUnmodifiedDropHand(ItemStack stack) {
      ItemStack checked = Objects.requireNonNull(stack, "stack");
      return checked.isEmpty() || !checked.isDamageableItem() && !(checked.getItem() instanceof ShearsItem);
   }

   private boolean equipFarmRepairHand() {
      LivingEntityInventory inventory = this.controller.getInventory();
      int exactSlot = FarmRepairToolPlan.bestSlot(inventory, this.repairToolRequirement, 1);
      if (exactSlot >= 0 && exactSlot < inventory.main.size()) {
         ItemStack exactStack = (ItemStack)inventory.main.get(exactSlot);
         if (!this.repairToolRequirement.requiresTool() || !exactStack.isEmpty() && this.repairToolRequirement.accepts(exactStack)) {
            this.selectedRepairStack = exactStack;
            return this.equipExactRepairSlot(inventory, exactSlot, exactStack);
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean reequipSelectedRepairHand() {
      LivingEntityInventory inventory = this.controller.getInventory();
      int exactSlot = findIdentitySlot(inventory, this.selectedRepairStack);
      return exactSlot >= 0
         ? this.equipExactRepairSlot(inventory, exactSlot, this.selectedRepairStack)
         : !this.repairToolRequirement.requiresTool() && this.equipFarmRepairHand();
   }

   private boolean equipExactRepairSlot(LivingEntityInventory inventory, int exactSlot, ItemStack exactStack) {
      if (!this.repairToolRequirement.requiresTool() || !exactStack.isEmpty() && this.repairToolRequirement.accepts(exactStack)) {
         this.controller.getSlotHandler().forceEquipSlot(this.controller, PlayerSlot.getMainSlot(inventory, exactSlot));
         ItemStack mainHand = this.controller.getPlayer().getMainHandItem();
         return mainHand != exactStack ? false : !this.repairToolRequirement.requiresTool() || this.repairToolRequirement.accepts(mainHand);
      } else {
         return false;
      }
   }

   private boolean selectedRepairToolStillAvailable() {
      return this.selectedRepairStack != null
         && findIdentitySlot(this.controller.getInventory(), this.selectedRepairStack) >= 0
         && !this.selectedRepairStack.isEmpty()
         && this.repairToolRequirement.accepts(this.selectedRepairStack);
   }

   private static int findIdentitySlot(LivingEntityInventory inventory, ItemStack expected) {
      if (expected == null) {
         return -1;
      }

      for (int i = 0; i < inventory.main.size(); i++) {
         if (inventory.main.get(i) == expected) {
            return i;
         }
      }

      return -1;
   }

   static boolean safeNoToolRepairHand(ItemStack stack) {
      Objects.requireNonNull(stack, "stack");
      return stack.isEmpty() || !stack.isDamageableItem() || !(stack.getItem() instanceof HoeItem);
   }

   static int continuousClickBudget(float destroyProgress) {
      int ceiling = 300;
      if (Float.isFinite(destroyProgress) && !(destroyProgress <= 0.0F)) {
         double rawTicks = Math.ceil(1.0 / destroyProgress);
         if (rawTicks >= ceiling) {
            return ceiling;
         }

         int expectedTicks = Math.max(1, (int)rawTicks);
         int safetyMargin = Math.max(5, Math.min(40, (int)Math.ceil(expectedTicks * 0.2)));
         return Math.min(ceiling, expectedTicks + safetyMargin);
      } else {
         return ceiling;
      }
   }

   private boolean loaded(BlockPos pos) {
      return this.controller.getChunkTracker().isChunkLoaded(pos);
   }

   private boolean liveStanceUnsafe() {
      boolean geometrySafe = this.requirePlannerSafeStanceSupport
         ? FarmStanceNavigation.hasPlannerSafeLiveGeometry(this.controller, this.stance)
         : FarmStanceNavigation.hasSafeLiveGeometry(this.controller, this.stance);
      return !geometrySafe
         || FarmStanceNavigation.stanceProtectionDenied(
            this.protectStanceSupport && this.isProtected(this.stance.below()),
            this.isProtected(this.stance),
            FarmStanceNavigation.isPassableCropStance(this.controller, this.stance),
            this.isProtected(this.stance.above())
         );
   }

   private boolean isProtected(BlockPos pos) {
      PlayerPlacedBlockStore store = PlayerPlacedBlockStore.get();
      return store == null || store.contains(this.dimension, pos);
   }

   private boolean sourceGuardPermits() {
      return this.sourceGuard == null || this.sourceGuard.permits(this.controller.getWorld(), this.target);
   }

   boolean requiresUnprotectedStanceSupport() {
      return this.protectStanceSupport;
   }

   boolean requiresPlannerSafeStanceSupport() {
      return this.requirePlannerSafeStanceSupport;
   }

   static boolean targetProtectionDenied(boolean protectedTarget, boolean allowProtectedTarget, boolean requiredBeforeSatisfied) {
      return protectedTarget && (!allowProtectedTarget || !requiredBeforeSatisfied);
   }

   static boolean shouldRetireTargetProtection(boolean allowProtectedTarget, boolean protectedTarget, VerifiedBreakBlockTask.ObservedTransition transition) {
      return allowProtectedTarget && protectedTarget && transition == VerifiedBreakBlockTask.ObservedTransition.SUCCESS;
   }

   static VerifiedBreakBlockTask.ObservedTransition observedTransition(
      BlockState expectedBefore, BlockState observed, Predicate<BlockState> allowedAfter, int attempts
   ) {
      Objects.requireNonNull(expectedBefore, "expectedBefore");
      Objects.requireNonNull(observed, "observed");
      Objects.requireNonNull(allowedAfter, "allowedAfter");
      if (attempts < 0) {
         throw new IllegalArgumentException("attempts cannot be negative");
      } else if (observed.equals(expectedBefore)) {
         return VerifiedBreakBlockTask.ObservedTransition.UNCHANGED;
      } else if (attempts == 0) {
         return VerifiedBreakBlockTask.ObservedTransition.PRE_ATTEMPT_DRIFT;
      } else {
         return FarmInteractionPostconditions.verifiedBreakTransition(expectedBefore, observed, allowedAfter)
            ? VerifiedBreakBlockTask.ObservedTransition.SUCCESS
            : VerifiedBreakBlockTask.ObservedTransition.INVALID_DRIFT;
      }
   }

   @Override
   public boolean settlePendingTransition() {
      if (this.terminal) {
         return this.successful;
      }

      if (this.controller != null && this.expectedBefore != null && this.attempts > 0) {
         if (!Objects.equals(this.dimension, this.currentDimension())) {
            this.fail(FarmTaskReason.DIMENSION_CHANGED);
            return false;
         } else if (!this.loaded(this.target)) {
            this.fail(FarmTaskReason.CHUNK_UNLOADED);
            return false;
         } else {
            BlockState observed = this.controller.getWorld().getBlockState(this.target);
            VerifiedBreakBlockTask.ObservedTransition transition = observedTransition(this.expectedBefore, observed, this.allowedAfter, this.attempts);
            if (transition == VerifiedBreakBlockTask.ObservedTransition.UNCHANGED) {
               return true;
            } else if (transition == VerifiedBreakBlockTask.ObservedTransition.SUCCESS) {
               this.succeed(transition);
               return true;
            } else {
               this.preAttemptTargetDrift = transition == VerifiedBreakBlockTask.ObservedTransition.PRE_ATTEMPT_DRIFT;
               this.fail(FarmTaskReason.MANIFEST_DRIFT);
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

   private String currentDimension() {
      return this.controller.getWorld().dimension().location().toString();
   }

   private void succeed(VerifiedBreakBlockTask.ObservedTransition transition) {
      this.stopClick();
      PlayerPlacedBlockStore store = PlayerPlacedBlockStore.get();
      boolean targetProtected = store != null && store.contains(this.dimension, this.target);
      if (shouldRetireTargetProtection(this.allowProtectedTarget, targetProtected, transition)) {
         store.remove(this.dimension, this.target);
      }

      this.terminal = true;
      this.successful = true;
      this.reason = FarmTaskReason.NONE;
   }

   private void fail(FarmTaskReason failure) {
      this.stopClick();
      this.terminal = true;
      this.successful = false;
      this.reason = Objects.requireNonNull(failure, "failure");
   }

   private void stopClick() {
      if (this.controller != null) {
         this.controller.getInputControls().release(Input.CLICK_LEFT);
      }

      this.clickHeld = false;
      this.ticksInAttempt = 0;
      this.attemptTickBudget = 0;
      this.selectedRepairStack = null;
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.stopClick();
      if (this.controller != null) {
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

   public BlockState expectedBefore() {
      return this.expectedBefore;
   }

   public int attempts() {
      return this.attempts;
   }

   public boolean wasPreAttemptTargetDrift() {
      return this.preAttemptTargetDrift;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof VerifiedBreakBlockTask task
         && task.target.equals(this.target)
         && task.stance.equals(this.stance)
         && task.operationIdentity.equals(this.operationIdentity);
   }

   @Override
   protected String toDebugString() {
      return "Verified break at " + this.target.toShortString();
   }

   enum ObservedTransition {
      UNCHANGED,
      SUCCESS,
      PRE_ATTEMPT_DRIFT,
      INVALID_DRIFT;
   }

   private record SourceGuard(Item plantingItem, String dimensionId, PlantableSourcePlanner.SourceKind sourceKind, BlockState expectedState) {
      private SourceGuard {
         plantingItem = Objects.requireNonNull(plantingItem, "plantingItem");
         if (dimensionId != null && !dimensionId.isBlank()) {
            sourceKind = Objects.requireNonNull(sourceKind, "sourceKind");
            if (sourceKind != PlantableSourcePlanner.SourceKind.NATURAL_GRASS && sourceKind != PlantableSourcePlanner.SourceKind.VILLAGE_CROP) {
               throw new IllegalArgumentException("source guard requires a break source");
            }

            expectedState = Objects.requireNonNull(expectedState, "expectedState");
         } else {
            throw new IllegalArgumentException("dimensionId cannot be blank");
         }
      }

      private boolean permits(ServerLevel level, BlockPos target) {
         return PlantableSourcePlanner.isLiveBreakCandidate(
            level, this.plantingItem, this.dimensionId, new PlantableSourcePlanner.BreakCandidate(this.sourceKind, target, this.expectedState)
         );
      }

      private String identity() {
         return this.sourceKind.name() + ":" + this.dimensionId + ":" + this.plantingItem + ":" + FarmSiteWorldView.stateFingerprint(this.expectedState);
      }
   }
}
