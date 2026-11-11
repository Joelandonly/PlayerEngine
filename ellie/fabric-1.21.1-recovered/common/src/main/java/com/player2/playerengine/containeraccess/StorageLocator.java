package com.player2.playerengine.containeraccess;

import com.player2.playerengine.PlayerEngineController;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;

public final class StorageLocator {
   public static final double LOOK_REACH_BLOCKS = 20.0;
   private static final Block[] SUPPORTED_BLOCKS = new Block[]{
      Blocks.CHEST,
      Blocks.TRAPPED_CHEST,
      Blocks.BARREL,
      Blocks.SHULKER_BOX,
      Blocks.WHITE_SHULKER_BOX,
      Blocks.ORANGE_SHULKER_BOX,
      Blocks.MAGENTA_SHULKER_BOX,
      Blocks.LIGHT_BLUE_SHULKER_BOX,
      Blocks.YELLOW_SHULKER_BOX,
      Blocks.LIME_SHULKER_BOX,
      Blocks.PINK_SHULKER_BOX,
      Blocks.GRAY_SHULKER_BOX,
      Blocks.LIGHT_GRAY_SHULKER_BOX,
      Blocks.CYAN_SHULKER_BOX,
      Blocks.PURPLE_SHULKER_BOX,
      Blocks.BLUE_SHULKER_BOX,
      Blocks.BROWN_SHULKER_BOX,
      Blocks.GREEN_SHULKER_BOX,
      Blocks.RED_SHULKER_BOX,
      Blocks.BLACK_SHULKER_BOX
   };

   private StorageLocator() {
   }

   public static Optional<StorageLocator.Located> findNearestContainer(PlayerEngineController mod, Vec3 origin, double maxRadiusBlocks) {
      ServerLevel level = mod.getWorld();
      if (level != null && origin != null) {
         double capSq = maxRadiusBlocks * maxRadiusBlocks;
         List<BlockPos> candidates = mod.getBlockScanner().getKnownLocations(SUPPORTED_BLOCKS);
         candidates.sort(Comparator.comparingDouble(p -> origin.distanceToSqr(Vec3.atCenterOf(p))));

         for (BlockPos candidate : candidates) {
            if (origin.distanceToSqr(Vec3.atCenterOf(candidate)) > capSq) {
               break;
            }

            Optional<StorageLocator.Located> located = resolveSupported(level, candidate);
            if (located.isPresent()) {
               return located;
            }
         }

         return Optional.empty();
      } else {
         return Optional.empty();
      }
   }

   public static Optional<StorageLocator.Located> findLookedAtContainer(ServerLevel level, ServerPlayer player, double reachBlocks) {
      if (level != null && player != null) {
         HitResult hit = player.pick(reachBlocks, 1.0F, false);
         return hit.getType() == Type.BLOCK && hit instanceof BlockHitResult blockHit
            ? resolveSupported(level, blockHit.getBlockPos())
            : Optional.empty();
      } else {
         return Optional.empty();
      }
   }

   private static Optional<StorageLocator.Located> resolveSupported(ServerLevel level, BlockPos pos) {
      ContainerResolver.Resolution resolution = ContainerResolver.resolve(level, pos);
      if (!resolution.ok()) {
         return Optional.empty();
      }

      ResolvedContainer resolved = resolution.resolved();
      return resolved.kind() == ContainerKind.GENERIC ? Optional.empty() : Optional.of(new StorageLocator.Located(resolved.canonicalPos(), resolved.kind()));
   }

   public record Located(BlockPos canonicalPos, ContainerKind kind) {
   }
}
