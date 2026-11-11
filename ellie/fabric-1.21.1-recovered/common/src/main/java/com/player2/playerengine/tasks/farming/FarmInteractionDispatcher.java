package com.player2.playerengine.tasks.farming;

import com.player2.playerengine.PlayerEngineController;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;

final class FarmInteractionDispatcher {
   private FarmInteractionDispatcher() {
   }

   static boolean isExpectedBlockHit(HitResult hit, BlockPos expectedBlock) {
      return hit instanceof BlockHitResult blockHit && blockHit.getType() == Type.BLOCK && blockHit.getBlockPos().equals(expectedBlock);
   }

   static boolean isExpectedBlockFace(HitResult hit, BlockPos expectedBlock, Direction expectedFace) {
      return isExpectedBlockHit(hit, expectedBlock) && ((BlockHitResult)hit).getDirection() == expectedFace;
   }

   static Optional<InteractionResult> dispatchExactBlock(
      HitResult hit, BlockPos expectedBlock, Runnable beforeDispatch, Function<BlockHitResult, InteractionResult> interaction
   ) {
      return !isExpectedBlockHit(hit, expectedBlock) ? Optional.empty() : dispatch((BlockHitResult)hit, beforeDispatch, interaction);
   }

   static Optional<InteractionResult> dispatchExactFace(
      HitResult hit, BlockPos expectedBlock, Direction expectedFace, Runnable beforeDispatch, Function<BlockHitResult, InteractionResult> interaction
   ) {
      return !isExpectedBlockFace(hit, expectedBlock, expectedFace) ? Optional.empty() : dispatch((BlockHitResult)hit, beforeDispatch, interaction);
   }

   static Optional<InteractionResult> dispatchMainHandBlockThenItemAtBlock(
      PlayerEngineController controller, HitResult hit, BlockPos expectedBlock, Runnable beforeDispatch
   ) {
      return dispatchExactBlock(hit, expectedBlock, beforeDispatch, blockHit -> useMainHandBlockThenItem(controller, blockHit));
   }

   static Optional<InteractionResult> dispatchMainHandBlockUseAtFace(
      PlayerEngineController controller, HitResult hit, BlockPos expectedBlock, Direction expectedFace, Runnable beforeDispatch
   ) {
      return dispatchExactFace(hit, expectedBlock, expectedFace, beforeDispatch, blockHit -> useMainHandBlock(controller, blockHit));
   }

   static Optional<InteractionResult> dispatchMainHandBlockThenItemAtFace(
      PlayerEngineController controller, HitResult hit, BlockPos expectedBlock, Direction expectedFace, Runnable beforeDispatch
   ) {
      return dispatchExactFace(hit, expectedBlock, expectedFace, beforeDispatch, blockHit -> useMainHandBlockThenItem(controller, blockHit));
   }

   static InteractionResult useMainHandBlock(PlayerEngineController controller, BlockHitResult hit) {
      Objects.requireNonNull(controller, "controller");
      return Objects.requireNonNull(
         controller.getBaritone()
            .getEntityContext()
            .playerController()
            .processRightClickBlock(controller.getPlayer(), controller.getWorld(), InteractionHand.MAIN_HAND, hit),
         "block interaction result"
      );
   }

   static InteractionResult useMainHandBlockThenItem(PlayerEngineController controller, BlockHitResult hit) {
      return blockThenItemUse(
         hit,
         blockHit -> useMainHandBlock(controller, blockHit),
         () -> !controller.getPlayer().getMainHandItem().isEmpty(),
         () -> controller.getBaritone()
            .getEntityContext()
            .playerController()
            .processRightClick(controller.getPlayer(), controller.getWorld(), InteractionHand.MAIN_HAND)
      );
   }

   static InteractionResult blockThenItemUse(
      BlockHitResult hit, Function<BlockHitResult, InteractionResult> blockInteraction, BooleanSupplier hasHeldItem, Supplier<InteractionResult> itemInteraction
   ) {
      InteractionResult blockResult = Objects.requireNonNull(Objects.requireNonNull(blockInteraction, "blockInteraction").apply(hit), "block interaction result");
      return !blockResult.consumesAction() && Objects.requireNonNull(hasHeldItem, "hasHeldItem").getAsBoolean()
         ? Objects.requireNonNull(Objects.requireNonNull(itemInteraction, "itemInteraction").get(), "item interaction result")
         : blockResult;
   }

   private static Optional<InteractionResult> dispatch(BlockHitResult hit, Runnable beforeDispatch, Function<BlockHitResult, InteractionResult> interaction) {
      Objects.requireNonNull(beforeDispatch, "beforeDispatch").run();
      InteractionResult result = Objects.requireNonNull(Objects.requireNonNull(interaction, "interaction").apply(hit), "interaction result");
      return Optional.of(result);
   }
}
