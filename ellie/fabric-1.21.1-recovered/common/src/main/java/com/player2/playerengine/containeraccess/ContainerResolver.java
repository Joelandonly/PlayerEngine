package com.player2.playerengine.containeraccess;

import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ContainerResolver {
   public static final double STORAGE_TRAVEL_CAP_BLOCKS = 115.0;
   public static final long NAVIGATE_TIMEOUT_MS = 45000L;

   private ContainerResolver() {
   }

   public static ContainerResolver.Resolution resolve(ServerLevel level, BlockPos pos) {
      ChunkPos chunk = new ChunkPos(pos);
      if (!level.getChunkSource().hasChunk(chunk.x, chunk.z)) {
         return ContainerResolver.Resolution.failure(StorageAccessCode.CONTAINER_UNREACHABLE, "chunk not loaded at " + formatPos(pos));
      } else {
         BlockState state = level.getBlockState(pos);
         if (state.getBlock() instanceof EnderChestBlock) {
            return ContainerResolver.Resolution.failure(
               StorageAccessCode.CONTAINER_UNSUPPORTED, "ender chest at " + formatPos(pos) + " is per-player storage and cannot be accessed"
            );
         } else if (state.getBlock() instanceof ChestBlock chestBlock) {
            return resolveChest(level, pos, state, chestBlock);
         } else if (level.getBlockEntity(pos) instanceof Container container) {
            ContainerKind kind = ContainerKind.GENERIC;
            if (state.getBlock() instanceof BarrelBlock) {
               kind = ContainerKind.BARREL;
            } else if (state.getBlock() instanceof ShulkerBoxBlock) {
               kind = ContainerKind.SHULKER_BOX;
            }

            return ContainerResolver.Resolution.success(new ResolvedContainer(container, kind, pos, null, container.getContainerSize()));
         } else {
            return ContainerResolver.Resolution.failure(
               StorageAccessCode.CONTAINER_MISSING,
               "block at " + formatPos(pos) + " is " + BuiltInRegistries.BLOCK.getKey(state.getBlock()) + ", not a container"
            );
         }
      }
   }

   private static ContainerResolver.Resolution resolveChest(ServerLevel level, BlockPos pos, BlockState state, ChestBlock chestBlock) {
      Container container = ChestBlock.getContainer(chestBlock, state, level, pos, true);
      if (container == null) {
         return ContainerResolver.Resolution.failure(StorageAccessCode.CONTAINER_MISSING, "chest block at " + formatPos(pos) + " has no live block entity");
      }

      boolean trapped = state.getBlock() instanceof TrappedChestBlock;
      ChestType type = (ChestType)state.getValue(ChestBlock.TYPE);
      BlockPos canonicalPos = pos;
      BlockPos secondaryPos = null;
      ContainerKind kind = trapped ? ContainerKind.TRAPPED_CHEST : ContainerKind.CHEST;
      if (container instanceof CompoundContainer && type != ChestType.SINGLE) {
         BlockPos otherHalf = pos.relative(ChestBlock.getConnectedDirection(state));
         if (type == ChestType.RIGHT) {
            canonicalPos = pos;
            secondaryPos = otherHalf;
         } else {
            canonicalPos = otherHalf;
            secondaryPos = pos;
         }

         kind = trapped ? ContainerKind.DOUBLE_TRAPPED_CHEST : ContainerKind.DOUBLE_CHEST;
      }

      return ContainerResolver.Resolution.success(new ResolvedContainer(container, kind, canonicalPos, secondaryPos, container.getContainerSize()));
   }

   public static String formatPos(BlockPos pos) {
      return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
   }

   public record Resolution(ResolvedContainer resolved, StorageAccessCode code, String detail) {
      public boolean ok() {
         return this.code == StorageAccessCode.OK;
      }

      public static ContainerResolver.Resolution success(ResolvedContainer resolved) {
         return new ContainerResolver.Resolution(resolved, StorageAccessCode.OK, "");
      }

      public static ContainerResolver.Resolution failure(StorageAccessCode code, String detail) {
         return new ContainerResolver.Resolution(null, code, detail);
      }
   }
}
