package com.player2.playerengine.tasks.farming;

import com.player2.playerengine.PlayerEngineController;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.core.Direction.Axis;

final class FarmStanceNavigation {
   private static final double MAX_STEP_SAFE_CROP_COLLISION_Y = 0.5;

   private FarmStanceNavigation() {
   }

   static boolean isAtFrozenStance(PlayerEngineController controller, BlockPos frozenStance) {
      return controller != null && isAtFrozenStance(controller.getBaritone().getEntityContext().feetPos(), frozenStance);
   }

   static boolean isAtFrozenStance(BlockPos logicalFeetPosition, BlockPos frozenStance) {
      return frozenStance != null && frozenStance.equals(logicalFeetPosition);
   }

   static boolean isPassableCropStance(PlayerEngineController controller, BlockPos stance) {
      if (controller != null && stance != null && controller.getChunkTracker().isChunkLoaded(stance)) {
         Level level = controller.getWorld();
         BlockState state = level.getBlockState(stance);
         HarvestBehavior behavior = HarvestBehaviorRegistry.DEFAULT.resolve(state).orElse(null);
         if (behavior == null) {
            return false;
         }

         VoxelShape collision = state.getCollisionShape(level, stance);
         return cropStanceGeometryAllowed(
            true,
            behavior.allowsLowCollisionStance(state),
            collision.isEmpty(),
            collision.isEmpty() ? 0.0 : collision.max(Axis.Y),
            state.getFluidState().isEmpty(),
            state.hasBlockEntity(),
            state.getDestroySpeed(level, stance)
         );
      } else {
         return false;
      }
   }

   static boolean hasSafeLiveGeometry(PlayerEngineController controller, BlockPos stance) {
      return hasSafeLiveGeometry(controller, stance, false);
   }

   static boolean hasPlannerSafeLiveGeometry(PlayerEngineController controller, BlockPos stance) {
      return hasSafeLiveGeometry(controller, stance, true);
   }

   private static boolean hasSafeLiveGeometry(PlayerEngineController controller, BlockPos stance, boolean requirePlannerSafeSupport) {
      if (controller != null
         && stance != null
         && controller.getChunkTracker().isChunkLoaded(stance.below())
         && controller.getChunkTracker().isChunkLoaded(stance)
         && controller.getChunkTracker().isChunkLoaded(stance.above())) {
         Level level = controller.getWorld();
         BlockPos supportPos = stance.below();
         BlockState support = level.getBlockState(supportPos);
         boolean stableTop = support.isFaceSturdy(level, supportPos, Direction.UP);
         return supportGeometryAllowed(
               support,
               stableTop,
               support.getCollisionShape(level, supportPos).isEmpty(),
               support.getFluidState().isEmpty(),
               support.getDestroySpeed(level, supportPos),
               requirePlannerSafeSupport
            )
            && isOpenStanceCell(controller, stance, true)
            && isOpenStanceCell(controller, stance.above(), false);
      } else {
         return false;
      }
   }

   static boolean supportGeometryAllowed(
      BlockState support, boolean sturdyTop, boolean collisionEmpty, boolean fluidEmpty, float destroySpeed, boolean requirePlannerSafeSupport
   ) {
      if (support == null || support.hasBlockEntity() || support.getBlock() instanceof Fallable || collisionEmpty || !fluidEmpty || destroySpeed < 0.0F) {
         return false;
      }

      if (!requirePlannerSafeSupport) {
         return true;
      }

      boolean normalizableFarmSupport = support.is(Blocks.GRASS_BLOCK)
         || support.is(Blocks.DIRT)
         || support.is(Blocks.STONE)
         || support.is(Blocks.FARMLAND);
      return !(support.getBlock() instanceof LiquidBlockContainer) && (sturdyTop || normalizableFarmSupport);
   }

   private static boolean isOpenStanceCell(PlayerEngineController controller, BlockPos position, boolean allowLowCropCollision) {
      Level level = controller.getWorld();
      BlockState state = level.getBlockState(position);
      if (state.isAir()) {
         return true;
      }

      HarvestBehavior behavior = HarvestBehaviorRegistry.DEFAULT.resolve(state).orElse(null);
      VoxelShape collision = state.getCollisionShape(level, position);
      return behavior != null && allowLowCropCollision
         ? cropStanceGeometryAllowed(
            true,
            behavior.allowsLowCollisionStance(state),
            collision.isEmpty(),
            collision.isEmpty() ? 0.0 : collision.max(Axis.Y),
            state.getFluidState().isEmpty(),
            state.hasBlockEntity(),
            state.getDestroySpeed(level, position)
         )
         : (behavior != null || FarmSiteWorldView.isClearableGroundCover(state, state.getFluidState()))
            && collision.isEmpty()
            && state.getFluidState().isEmpty()
            && !state.hasBlockEntity()
            && state.getDestroySpeed(level, position) >= 0.0F;
   }

   static boolean cropStanceGeometryAllowed(
      boolean recognizedCrop,
      boolean behaviorAllowsLowCollision,
      boolean collisionEmpty,
      double collisionMaxY,
      boolean fluidEmpty,
      boolean blockEntity,
      float destroySpeed
   ) {
      if (!recognizedCrop || !fluidEmpty || blockEntity || destroySpeed < 0.0F) {
         return false;
      } else {
         return collisionEmpty ? true : behaviorAllowsLowCollision && Double.isFinite(collisionMaxY) && collisionMaxY >= 0.0 && collisionMaxY <= 0.5;
      }
   }

   static boolean stanceProtectionDenied(boolean supportProtected, boolean feetProtected, boolean feetIsPassableCrop, boolean headProtected) {
      return supportProtected || feetProtected && !feetIsPassableCrop || headProtected;
   }
}
