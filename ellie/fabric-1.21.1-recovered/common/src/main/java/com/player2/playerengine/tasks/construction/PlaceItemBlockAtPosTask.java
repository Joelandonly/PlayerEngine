package com.player2.playerengine.tasks.construction;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.automaton.api.entity.IInteractionManagerProvider;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.movement.ApproachNearBlockPosTask;
import com.player2.playerengine.util.helpers.WorldHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;

public class PlaceItemBlockAtPosTask extends Task {
   private static final int APPROACH_RADIUS = 4;
   private static final double REACH_SQ = 39.0625;
   private static final int SETTLE_TICKS = 4;
   private static final int MAX_CLICK_ATTEMPTS = 8;
   private final BlockPos placePos;
   private final Block expectedBlock;
   private final Item placeItem;
   private int clickAttempts;
   private int settleTicks;

   public PlaceItemBlockAtPosTask(BlockPos placePos, Block expectedBlock) {
      this.placePos = placePos.immutable();
      this.expectedBlock = expectedBlock;
      this.placeItem = expectedBlock.asItem();
   }

   @Override
   public boolean isFinished() {
      return WorldHelper.isBlock(this.controller, this.placePos, this.expectedBlock);
   }

   @Override
   protected void onStart() {
      this.clickAttempts = 0;
      this.settleTicks = 0;
   }

   @Override
   protected Task onTick() {
      if (this.isFinished()) {
         return null;
      }

      PlayerEngineController mod = this.controller;
      BlockPos support = this.placePos.below();
      if (!WorldHelper.isSolidBlock(mod, support)) {
         this.fail("no_support");
         return null;
      }

      if (!WorldHelper.isAir(mod, this.placePos) && !this.isFinished()) {
         this.fail("occupied");
         return null;
      }

      if (!mod.getItemStorage().hasItem(this.placeItem)) {
         this.fail("missing_item");
         return null;
      }

      if (!withinReach(mod, this.placePos)) {
         this.setDebugState("Approaching placement site");
         return new ApproachNearBlockPosTask(this.placePos, 4);
      }

      if (!mod.getSlotHandler().forceEquipItem(this.placeItem)) {
         this.setDebugState("Equipping place item");
         return null;
      }

      if (this.settleTicks > 0) {
         this.settleTicks--;
         this.setDebugState("Settling after place");
         return null;
      }

      if (this.clickAttempts >= 8) {
         this.fail("place_click_exhausted");
         return null;
      }

      this.clickAttempts++;
      this.setDebugState("Placing at " + this.placePos.toShortString());
      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(support).add(0.0, 0.5, 0.0), Direction.UP, support, false);
      aimAt(mod, hit.getLocation());
      InteractionHand hand = InteractionHand.MAIN_HAND;
      InteractionResult result = ((IInteractionManagerProvider)mod.getEntity())
         .getInteractionManager()
         .interactBlock(mod.getPlayer(), mod.getWorld(), mod.getPlayer().getMainHandItem(), hand, hit);
      if (result.consumesAction() || this.isFinished()) {
         mod.getPlayer().swing(hand);
         this.settleTicks = 4;
         if (this.isFinished()) {
            mod.getBlockScanner().addBlock(this.expectedBlock, this.placePos);
         }
      }

      return null;
   }

   private static void aimAt(PlayerEngineController mod, Vec3 target) {
      Vec3 look = target.subtract(mod.getPlayer().getEyePosition(1.0F)).normalize();
      double yaw = Math.toDegrees(Math.atan2(-look.x, look.z));
      double horizDist = Math.sqrt(look.x * look.x + look.z * look.z);
      double pitch = Math.toDegrees(-Math.atan2(look.y, horizDist));
      mod.getPlayer().setYRot((float)yaw);
      mod.getPlayer().setXRot((float)pitch);
   }

   private static boolean withinReach(PlayerEngineController mod, BlockPos pos) {
      return mod.getPlayer().distanceToSqr(Vec3.atCenterOf(pos)) <= 39.0625;
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof PlaceItemBlockAtPosTask task && task.placePos.equals(this.placePos) && task.expectedBlock == this.expectedBlock;
   }

   @Override
   protected String toDebugString() {
      return "PlaceItemBlockAtPos " + this.expectedBlock + " @ " + this.placePos.toShortString();
   }
}
