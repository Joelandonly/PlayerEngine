package com.player2.playerengine.util.sign;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.HangingSignItem;
import org.jetbrains.annotations.Nullable;

public final class SignScanSupport {
   public static final int MAX_SIGNS = 48;
   public static final int MAX_LINE_LEN = 192;

   private SignScanSupport() {
   }

   public static boolean isSignBlockEntity(BlockEntity be) {
      return be instanceof SignBlockEntity;
   }

   public static BlockHitResult blockHitForSignUse(BlockPos anchor, Direction faceTowardAir) {
      return blockHitForSignUse(anchor, faceTowardAir, null);
   }

   public static BlockHitResult blockHitForSignUse(BlockPos anchor, Direction faceTowardAir, @Nullable Item signItem) {
      BlockPos airPos = anchor.relative(faceTowardAir);
      Direction towardSolid = faceTowardAir.getOpposite();
      double hx = airPos.getX() + 0.5 + towardSolid.getStepX() * 0.5;
      double hy = airPos.getY() + 0.5 + towardSolid.getStepY() * 0.5;
      double hz = airPos.getZ() + 0.5 + towardSolid.getStepZ() * 0.5;
      if (signItem instanceof HangingSignItem && faceTowardAir.getAxis().isHorizontal()) {
         hy = anchor.getY() + 1.0 - 0.09375;
         hx = Mth.clamp(hx, anchor.getX() + 0.0625, anchor.getX() + 0.9375);
         hz = Mth.clamp(hz, anchor.getZ() + 0.0625, anchor.getZ() + 0.9375);
      }

      Vec3 hitPos = new Vec3(hx, hy, hz);
      return new BlockHitResult(hitPos, faceTowardAir, anchor, false);
   }

   public static BlockPos expectedSignBlockPos(BlockPos anchor, Direction faceTowardAir) {
      if (faceTowardAir == Direction.UP) {
         return anchor.above();
      } else {
         return faceTowardAir == Direction.DOWN ? anchor.below() : anchor.relative(faceTowardAir);
      }
   }

   public static boolean shouldSneakToPlaceAgainst(BlockState supportState) {
      Block b = supportState.getBlock();
      return b == Blocks.CHEST
         || b == Blocks.TRAPPED_CHEST
         || b instanceof EnderChestBlock
         || b instanceof BarrelBlock
         || b instanceof ShulkerBoxBlock
         || b instanceof CraftingTableBlock
         || b instanceof DoorBlock
         || b instanceof TrapDoorBlock
         || b instanceof FenceGateBlock
         || b == Blocks.ANVIL
         || b == Blocks.CHIPPED_ANVIL
         || b == Blocks.DAMAGED_ANVIL
         || b == Blocks.ENCHANTING_TABLE
         || b == Blocks.GRINDSTONE
         || b == Blocks.STONECUTTER
         || b == Blocks.LOOM
         || b == Blocks.CARTOGRAPHY_TABLE
         || b == Blocks.SMITHING_TABLE
         || b == Blocks.FLETCHING_TABLE
         || b == Blocks.BELL
         || b == Blocks.LEVER
         || b == Blocks.REPEATER
         || b == Blocks.COMPARATOR
         || b == Blocks.NOTE_BLOCK
         || b == Blocks.REDSTONE_WIRE;
   }

   public static String truncate(String s, int max) {
      if (s == null) {
         return "";
      } else {
         return s.length() <= max ? s : s.substring(0, max) + "…";
      }
   }

   public static String cardinalFromYaw(float yRotDegrees) {
      float y = yRotDegrees % 360.0F;
      if (y < 0.0F) {
         y += 360.0F;
      }

      if (y >= 315.0F || y < 45.0F) {
         return "south";
      } else if (y < 135.0F) {
         return "west";
      } else {
         return y < 225.0F ? "north" : "east";
      }
   }
}
