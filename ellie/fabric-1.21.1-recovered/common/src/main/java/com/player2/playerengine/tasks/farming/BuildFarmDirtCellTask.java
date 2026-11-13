package com.player2.playerengine.tasks.farming;

import com.player2.playerengine.agentic.InventoryCleanupStorage;
import com.player2.playerengine.automaton.api.schematic.AbstractSchematic;
import com.player2.playerengine.automaton.process.BuilderProcess;
import com.player2.playerengine.structureprotection.PlayerPlacedBlockStore;
import com.player2.playerengine.tasks.base.Task;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;

/** Places one planned dirt cell and verifies the block and the consumed item. */
final class BuildFarmDirtCellTask extends Task implements FarmMutationReceipt {
   private final BlockPos target;
   private final FarmSiteWorldView view;
   private final AbstractSchematic cell = new AbstractSchematic(1, 1, 1) {
      @Override
      public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> available) {
         return current.isAir() ? Blocks.DIRT.defaultBlockState() : current;
      }
   };
   private String dimension;
   private BlockState before;
   private int dirtBefore;
   private boolean submitted;
   private boolean success;
   private boolean policy;
   private FarmTaskReason failure = FarmTaskReason.NONE;

   BuildFarmDirtCellTask(BlockPos target, FarmSiteWorldView view) {
      this.target = target.immutable();
      this.view = view;
   }

   @Override
   protected void onStart() {
      this.dimension = this.controller.getWorld().dimension().location().toString();
   }

   @Override
   protected Task onTick() {
      if (!this.settlePendingTransition() || this.success) return null;
      var world = this.controller.getWorld();
      BlockPos support = this.target.below();
      if (!world.hasChunkAt(this.target) || !world.hasChunkAt(support)) {
         this.failure = FarmTaskReason.CHUNK_UNLOADED;
         return null;
      }
      var store = PlayerPlacedBlockStore.get();
      BlockState ground = world.getBlockState(support);
      BlockState current = world.getBlockState(this.target);
      if (store == null || store.contains(this.dimension, this.target) || store.contains(this.dimension, support)
         || this.view.isConstructionProtected(this.target) || this.view.isConstructionProtected(support)
         || InventoryCleanupStorage.isProjectStorage(this.controller, this.target)
         || !current.isAir() || !current.getFluidState().isEmpty() || !ground.getFluidState().isEmpty()
         || ground.hasBlockEntity() || ground.getBlock() instanceof Fallable || ground.getBlock() instanceof LiquidBlockContainer
         || !ground.isFaceSturdy(world, support, Direction.UP)) {
         this.failure = FarmTaskReason.MANIFEST_DRIFT;
         return null;
      }
      if (!this.submitted && this.controller.getBaritone().getBuilderProcess() instanceof BuilderProcess builder && !builder.isActive()) {
         this.dirtBefore = this.countDirt();
         if (this.dirtBefore == 0) {
            this.failure = FarmTaskReason.DIRT_UNAVAILABLE;
            return null;
         }
         this.before = current;
         this.controller.getBehaviour().push();
         this.policy = true;
         this.controller.getBehaviour().avoidBlockBreaking(position -> true);
         this.controller.getBehaviour().avoidBlockPlacing(position -> !position.equals(this.target));
         this.controller.getBehaviour().addProtectedItems(Items.DIRT);
         builder.buildSingleCell("farm dirt foundation and soil", this.cell, this.target);
         this.submitted = true;
      }
      return null;
   }

   @Override
   public boolean settlePendingTransition() {
      if (this.isFinished() || !this.submitted) return this.failure == FarmTaskReason.NONE;
      var world = this.controller.getWorld();
      if (!world.dimension().location().toString().equals(this.dimension)) this.failure = FarmTaskReason.DIMENSION_CHANGED;
      else if (!world.hasChunkAt(this.target)) this.failure = FarmTaskReason.CHUNK_UNLOADED;
      else {
         var result = PlaceFarmDirtTask.receiptDisposition(1, true, this.before, world.getBlockState(this.target), this.dirtBefore, this.countDirt());
         if (result == PlaceFarmDirtTask.ReceiptDisposition.SUCCESS) this.success = true;
         if (result == PlaceFarmDirtTask.ReceiptDisposition.INVALID) this.failure = FarmTaskReason.MANIFEST_DRIFT;
      }
      return this.failure == FarmTaskReason.NONE;
   }

   private int countDirt() {
      return this.controller.getItemStorage().getItemCount(Items.DIRT);
   }

   @Override
   protected void onStop(Task next) {
      if (this.controller.getBaritone().getBuilderProcess() instanceof BuilderProcess builder && builder.ownsSchematic(this.cell)) {
         builder.onLostControl();
         this.controller.getBaritone().getPathingBehavior().forceCancel();
      }
      if (this.policy) {
         this.controller.getBehaviour().pop();
         this.policy = false;
      }
   }

   @Override public boolean hasIssuedMutation() { return this.submitted; }
   @Override public void prepareForTransientDetach() {}
   @Override public boolean isFinished() { return this.success || this.failure != FarmTaskReason.NONE; }
   @Override public boolean isSuccessful() { return this.success; }
   @Override public FarmTaskReason reason() { return this.failure; }
   @Override protected boolean isEqual(Task other) { return this == other; }
   @Override protected String toDebugString() { return "Build farm dirt at " + this.target.toShortString(); }
}
