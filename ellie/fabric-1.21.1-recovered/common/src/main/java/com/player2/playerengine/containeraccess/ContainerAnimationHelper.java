package com.player2.playerengine.containeraccess;

import com.player2.playerengine.util.Debug;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.BarrelBlock;

public final class ContainerAnimationHelper {
   public static final int ANIMATION_MIN_HOLD_TICKS = 20;
   private static final float SOUND_VOLUME = 0.5F;
   private static final double VIEWER_RANGE_SQ = 64.0;

   private ContainerAnimationHelper() {
   }

   public static void open(ServerLevel level, ResolvedContainer resolved) {
      if (resolved.kind().isChest()) {
         openChest(level, resolved);
      } else if (resolved.kind() == ContainerKind.BARREL) {
         openBarrel(level, resolved.canonicalPos());
      } else if (resolved.kind() == ContainerKind.SHULKER_BOX) {
         openShulker(level, resolved.canonicalPos());
      }
   }

   public static void close(ServerLevel level, ResolvedContainer resolved) {
      if (resolved.kind().isChest()) {
         closeChest(level, resolved);
      } else if (resolved.kind() == ContainerKind.BARREL) {
         closeBarrel(level, resolved.canonicalPos());
      } else if (resolved.kind() == ContainerKind.SHULKER_BOX) {
         closeShulker(level, resolved.canonicalPos());
      }
   }

   private static void openChest(ServerLevel level, ResolvedContainer resolved) {
      BlockState state = level.getBlockState(resolved.canonicalPos());
      if (state.getBlock() instanceof ChestBlock) {
         level.blockEvent(resolved.canonicalPos(), state.getBlock(), 1, 1);
         sendChestEventToSecondaryHalf(level, resolved, 1);
         playChestSound(level, resolved.canonicalPos(), state, SoundEvents.CHEST_OPEN);
      }
   }

   private static void closeChest(ServerLevel level, ResolvedContainer resolved) {
      int viewers = ChestBlockEntity.getOpenCount(level, resolved.canonicalPos());
      if (resolved.secondaryPos() != null) {
         viewers += ChestBlockEntity.getOpenCount(level, resolved.secondaryPos());
      }

      if (viewers > 0) {
         Debug.logInternal(
            "StorageAccess: skipped chest close @ "
               + ContainerResolver.formatPos(resolved.canonicalPos())
               + " - "
               + viewers
               + " real viewer(s) hold it open (guarded close)"
         );
      } else {
         BlockState state = level.getBlockState(resolved.canonicalPos());
         if (state.getBlock() instanceof ChestBlock) {
            level.blockEvent(resolved.canonicalPos(), state.getBlock(), 1, 0);
            sendChestEventToSecondaryHalf(level, resolved, 0);
            playChestSound(level, resolved.canonicalPos(), state, SoundEvents.CHEST_CLOSE);
         }
      }
   }

   private static void sendChestEventToSecondaryHalf(ServerLevel level, ResolvedContainer resolved, int openParam) {
      if (resolved.secondaryPos() != null) {
         BlockState secondaryState = level.getBlockState(resolved.secondaryPos());
         if (secondaryState.getBlock() instanceof ChestBlock) {
            level.blockEvent(resolved.secondaryPos(), secondaryState.getBlock(), 1, openParam);
         }
      }
   }

   private static void playChestSound(ServerLevel level, BlockPos pos, BlockState state, SoundEvent sound) {
      ChestType type = (ChestType)state.getValue(ChestBlock.TYPE);
      if (type != ChestType.LEFT) {
         double x = pos.getX() + 0.5;
         double y = pos.getY() + 0.5;
         double z = pos.getZ() + 0.5;
         if (type == ChestType.RIGHT) {
            Direction direction = ChestBlock.getConnectedDirection(state);
            x += direction.getStepX() * 0.5;
            z += direction.getStepZ() * 0.5;
         }

         level.playSound(null, x, y, z, sound, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
      }
   }

   private static void openBarrel(ServerLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      if (state.getBlock() instanceof BarrelBlock) {
         if ((Boolean)state.getValue(BarrelBlock.OPEN)) {
            Debug.logInternal(
               "StorageAccess: barrel @ " + ContainerResolver.formatPos(pos) + " was already open (adopting stuck state; will close on session end)"
            );
         } else {
            level.setBlock(pos, (BlockState)state.setValue(BarrelBlock.OPEN, true), 3);
            playCenteredSound(level, pos, SoundEvents.BARREL_OPEN);
         }
      }
   }

   private static void closeBarrel(ServerLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      if (state.getBlock() instanceof BarrelBlock) {
         if ((Boolean)state.getValue(BarrelBlock.OPEN)) {
            level.setBlock(pos, (BlockState)state.setValue(BarrelBlock.OPEN, false), 3);
            playCenteredSound(level, pos, SoundEvents.BARREL_CLOSE);
         }
      }
   }

   private static void openShulker(ServerLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      if (state.getBlock() instanceof ShulkerBoxBlock && level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box) {
         if (!box.isClosed()) {
            Debug.logInternal(
               "StorageAccess: skipped shulker open @ " + ContainerResolver.formatPos(pos) + " - box is not closed (real viewer or mid-animation)"
            );
         } else {
            level.blockEvent(pos, state.getBlock(), 1, 1);
            playCenteredSound(level, pos, SoundEvents.SHULKER_BOX_OPEN);
         }
      }
   }

   private static void closeShulker(ServerLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      if (state.getBlock() instanceof ShulkerBoxBlock && level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box) {
         if (hasShulkerViewerNearby(level, pos)) {
            Debug.logInternal(
               "StorageAccess: skipped shulker close @ " + ContainerResolver.formatPos(pos) + " - a real viewer has a shulker GUI open nearby (guarded close)"
            );
         } else if (!box.isClosed()) {
            level.blockEvent(pos, state.getBlock(), 1, 0);
            playCenteredSound(level, pos, SoundEvents.SHULKER_BOX_CLOSE);
         }
      }
   }

   private static boolean hasShulkerViewerNearby(ServerLevel level, BlockPos pos) {
      for (ServerPlayer player : level.players()) {
         if (player.containerMenu instanceof ShulkerBoxMenu
            && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0) {
            return true;
         }
      }

      return false;
   }

   private static void playCenteredSound(ServerLevel level, BlockPos pos, SoundEvent sound) {
      level.playSound(
         null,
         pos.getX() + 0.5,
         pos.getY() + 0.5,
         pos.getZ() + 0.5,
         sound,
         SoundSource.BLOCKS,
         0.5F,
         level.random.nextFloat() * 0.1F + 0.9F
      );
   }
}
