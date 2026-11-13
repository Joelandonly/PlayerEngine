package com.player2.playerengine.tasks.construction;

import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.pathing.goals.GoalBlock;
import com.player2.playerengine.automaton.api.utils.RayTraceUtils;
import com.player2.playerengine.automaton.api.utils.RotationUtils;
import com.player2.playerengine.util.helpers.LookHelper;
import com.player2.playerengine.automaton.api.schematic.AbstractSchematic;
import com.player2.playerengine.automaton.process.BuilderProcess;
import com.player2.playerengine.observatory.ObservatoryTelemetry;
import com.player2.playerengine.reliability.SharedTargetRecovery;
import com.player2.playerengine.safezone.SafeZoneService;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.base.TaskSuspensionCause;
import com.player2.playerengine.tasks.base.TransientlyResumableTask;
import com.player2.playerengine.tasks.base.TrackedTaskOutcomeProvider;
import com.player2.playerengine.tasks.construction.build_structure.VillageSiteGuard;
import com.player2.playerengine.tasks.movement.CustomBaritoneGoalTask;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;

public final class NetherBridgeTask extends Task implements TrackedTaskOutcomeProvider, TransientlyResumableTask {
   private static final BlockState FLOOR = Blocks.NETHERRACK.defaultBlockState();
   private static final BlockState AIR = Blocks.AIR.defaultBlockState();
   private static final int ACTION_TIMEOUT = 600;
   private static final int MAX_ROWS_BEHIND_FRONT = 1;
   public static final int MAX_LENGTH = 256;
   private final BlockPos origin;
   private final Direction direction;
   private final int length;
   private int row = 1;
   private Task acquire;
   private int materialBefore;
   private int actionTicks;
   private boolean atFront;
   private int footingRecoveries;
   private BridgeCell active;
   private BlockPos target;
   private BlockState desired;
   private String failure;
   private boolean finished;

   public NetherBridgeTask(BlockPos origin, Direction direction, int length) {
      this.origin = origin.immutable();
      this.direction = direction;
      this.length = length;
   }

   @Override protected void onStart() {
      this.actionTicks = 0;
      this.atFront = false;
      Direction side = this.direction.getClockWise();
      BlockPos first = this.origin.relative(side, -1);
      BlockPos last = this.origin.relative(this.direction, this.length).relative(side);
      VillageSiteGuard.register(this, this.controller, Math.min(first.getX(), last.getX()), Math.min(first.getZ(), last.getZ()),
         Math.abs(first.getX() - last.getX()) + 1, Math.abs(first.getZ() - last.getZ()) + 1);
   }

   @Override protected Task onTick() {
      var world = this.controller.getWorld();
      if (!world.dimension().equals(Level.NETHER)) return block("Ellie left the Nether", this.origin);
      if (this.acquire != null) {
         if (materialCount() > this.materialBefore && (this.acquire.isFinished() || this.acquire.stopped())) {
            this.acquire = null;
            this.atFront = false;
            this.actionTicks = 0;
         } else if (this.acquire.stopped() || this.acquire.isFinished() || ++this.actionTicks > 2400) {
            return block("Could not obtain more netherrack", this.origin.relative(this.direction, this.row));
         } else {
            return this.acquire;
         }
      }

      if (this.active != null) {
         if (!world.hasChunkAt(this.target)) return block("Current construction cell unloaded", this.target);
         BlockState current = world.getBlockState(this.target);
         if (matches(current, this.desired) || current != this.active.before) {
            if (matches(current, this.desired)) {
               SharedTargetRecovery.verifiedWorldOutput(this.controller, "Netherbridge verified cell " + this.target.toShortString());
            }
            releaseBuilder();
            this.actionTicks = 0;
         } else {
            var ellie = this.controller.getEntity();
            BlockPos workFront = this.origin.relative(this.direction, this.row - 1);
            if (this.atFront && ellie.blockPosition().getY() < this.origin.getY()) {
               if (++this.footingRecoveries > 2) return block("Ellie repeatedly lost her footing below the deck", ellie.blockPosition());
               releaseBuilder();
               this.atFront = false;
               this.actionTicks = 0;
               ObservatoryTelemetry.event("NETHERBRIDGE_RECOVERY", "Returning to the deck at row " + this.row);
               return new WalkToFront(workFront);
            }
            if (lavaToSeal(this.target, this.desired) != null) {
               releaseBuilder();
               return null;
            }
            String problem = obstacle(this.target, this.desired);
            if (problem != null) return block(problem, this.target);
            if (++this.actionTicks > ACTION_TIMEOUT) {
               return block("Builder could not " + (this.desired.isAir() ? "clear " : "place netherrack at ")
                  + this.target.toShortString() + " from Ellie’s position", this.controller.getEntity().blockPosition());
            }
            if (this.actionTicks % 40 == 0) startBuilder();
            return null;
         }
      }

      if (this.row > this.length) {
         this.finished = true;
         this.controller.reportAgenticProgress("Netherbridge verified all " + this.length + " rows.", true);
         return null;
      }

      BlockPos front = this.origin.relative(this.direction, this.row - 1);
      BlockPos support = front.below();
      if (!world.hasChunkAt(support)) {
         if (++this.actionTicks > ACTION_TIMEOUT) return block("Bridge footing chunk is unloaded", support);
         return new WalkToFront(front);
      }
      if (!world.getBlockState(support).isFaceSturdy(world, support, Direction.UP)) {
         BlockState before = world.getBlockState(support);
         if (!canFillFloor(before)) return block("Bridge footing is obstructed", support);
         String problem = obstacle(support, FLOOR);
         if (problem != null) return block(problem, support);
         boolean anchored = false;
         for (Direction face : Direction.values()) {
            if (face == Direction.UP) continue;
            BlockPos adjacent = support.relative(face);
            if (world.hasChunkAt(adjacent) && !SafeZoneService.protectedAt(world, adjacent)
               && world.getBlockState(adjacent).isFaceSturdy(world, adjacent, face.getOpposite())) {
               anchored = true;
               break;
            }
         }
         if (!anchored) return block("Bridge footing needs a neighboring solid block", support);
         if (materialCount() == 0) return acquireFloor(support);
         this.atFront = false;
         return beginCell(support, before, FLOOR, "Repairing bridge footing");
      }
      List<BlockPos> cells = rowCells(this.origin, this.direction, this.row);
      for (int index = 0; index < cells.size(); index++) {
         BlockPos pos = cells.get(index);
         if (!world.isInWorldBounds(pos) || !world.getWorldBorder().isWithinBounds(pos)) return block("Outside world bounds", pos);
         if (!world.hasChunkAt(pos)) {
            this.setDebugState("Waiting for chunk at " + pos.toShortString());
            if (++this.actionTicks > ACTION_TIMEOUT) return block("Next row is in an unloaded chunk", pos);
            return new WalkToFront(front);
         }
         if (!matches(world.getBlockState(pos), expected(index))) {
            String problem = obstacle(pos, expected(index));
            if (problem != null) return block(problem, pos);
         }
      }

      BlockPos firePending = null;
      for (int index = 0; index < cells.size(); index++) {
         BlockPos pos = cells.get(index);
         BlockState needed = expected(index);
         BlockState current = world.getBlockState(pos);
         BlockPos lava = lavaToSeal(pos, needed);
         if (matches(current, needed) && lava == null) continue;
         if (!canWorkFromDeck(front)) {
            this.atFront = false;
            this.setDebugState("Moving up to verified bridge row " + (this.row - 1) + " at " + front.toShortString());
            if (++this.actionTicks > ACTION_TIMEOUT) return block("No route back to the verified bridge", front);
            return new WalkToFront(front);
         }
         if (!this.atFront) {
            this.atFront = true;
            this.actionTicks = 0;
         }
         if (lava != null) {
            String problem = obstacle(lava, FLOOR);
            if (problem != null) return block(problem, lava);
            if (materialCount() == 0) return acquireFloor(lava);
            return beginCell(lava, world.getBlockState(lava), FLOOR, "Sealing lava");
         }
         if (current.getBlock() instanceof BaseFireBlock) {
            if (firePending == null) firePending = pos;
            continue;
         }
         if (needed == FLOOR && materialCount() == 0) return acquireFloor(pos);
         // Replace an existing non-netherrack floor in two verified steps.
         if (needed == FLOOR && !canFillFloor(current)) {
            var feet = this.controller.getEntity().blockPosition();
            if (feet.below().equals(pos)) {
               if (++this.actionTicks > ACTION_TIMEOUT) return block("Cannot step off the floor cell safely", pos);
               return new WalkToFront(front);
            }
            needed = AIR;
         }
         return beginCell(pos, current, needed, "Netherbridge row " + this.row + "/" + this.length);
      }

      if (firePending != null) {
         if (extinguish(firePending)) {
            this.actionTicks = 0;
            return null;
         }
         if (++this.actionTicks > ACTION_TIMEOUT) return block("Cannot reach fire from the bridge", firePending);
         return this.controller.getEntity().blockPosition().equals(front) ? null : new WalkToFront(front);
      }
      SharedTargetRecovery.verifiedWorldOutput(this.controller, "Netherbridge verified row " + this.row);
      if (this.row == this.length || this.row % 8 == 0) {
         ObservatoryTelemetry.event("NETHERBRIDGE_PROGRESS", "Verified " + this.row + "/" + this.length + " rows");
      }
      this.row++;
      this.footingRecoveries = 0;
      this.atFront = false;
      this.actionTicks = 0;
      return null;
   }

   private boolean canWorkFromDeck(BlockPos front) {
      var ellie = this.controller.getEntity();
      BlockPos feet = ellie.blockPosition();
      if (!ellie.onGround() || feet.getY() != this.origin.getY()) return false;
      double reach = this.controller.getBaritone().getEntityContext().playerController().getBlockReachDistance();
      return nearFront(feet, front, this.direction)
         && ellie.getEyePosition().distanceToSqr(Vec3.atCenterOf(front.relative(this.direction))) <= reach * reach;
   }

   static boolean nearFront(BlockPos feet, BlockPos front, Direction direction) {
      int dx = feet.getX() - front.getX();
      int dz = feet.getZ() - front.getZ();
      int forward = dx * direction.getStepX() + dz * direction.getStepZ();
      int sideways = dx * direction.getStepZ() - dz * direction.getStepX();
      return feet.getY() == front.getY() && forward <= 0 && forward >= -MAX_ROWS_BEHIND_FRONT
         && Math.abs(sideways) <= 1;
   }

   private boolean extinguish(BlockPos pos) {
      var world = this.controller.getWorld();
      var ellie = this.controller.getEntity();
      var path = this.controller.getBaritone().getPathingBehavior();
      if (!ellie.onGround() || !path.isSafeToCancel() || SafeZoneService.protectedAt(world, pos)) return false;
      var reach = this.controller.getBaritone().getEntityContext().playerController().getBlockReachDistance();
      var rotation = RotationUtils.reachable(ellie, pos, reach);
      if (rotation.isEmpty()) return false;
      var hit = RayTraceUtils.rayTraceTowards(ellie, rotation.get(), reach);
      if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK
         || !isFireHit(pos, block.getBlockPos(), block.getDirection())) return false;
      if (!(world.getBlockState(pos).getBlock() instanceof BaseFireBlock) || !world.removeBlock(pos, false)) return false;
      LookHelper.lookAt(this.controller, rotation.get());
      ellie.swing(InteractionHand.MAIN_HAND);
      world.levelEvent(null, LevelEvent.SOUND_EXTINGUISH_FIRE, pos, 0);
      world.gameEvent(ellie, GameEvent.BLOCK_DESTROY, pos);
      return true;
   }

   static boolean isFireHit(BlockPos fire, BlockPos hit, Direction side) {
      return fire.equals(hit) || side == Direction.UP && fire.below().equals(hit);
   }

   private Task acquireFloor(BlockPos pos) {
      this.materialBefore = materialCount();
      this.acquire = TaskCatalogue.getItemTask(Items.NETHERRACK, Math.min(32, 3 * (this.length - this.row + 1)));
      if (this.acquire == null) return block("No netherrack acquisition task is available", pos);
      this.actionTicks = 0;
      return this.acquire;
   }

   private Task beginCell(BlockPos pos, BlockState current, BlockState needed, String action) {
      this.target = pos;
      this.desired = needed;
      this.actionTicks = 0;
      this.active = new BridgeCell(current, needed, this.origin.getY());
      startBuilder();
      this.setDebugState(action + " at " + pos.toShortString());
      return null;
   }

   static List<BlockPos> rowCells(BlockPos origin, Direction direction, int row) {
      BlockPos center = origin.relative(direction, row);
      List<BlockPos> cells = new ArrayList<>(12);
      for (int height = 2; height >= 0; height--) {
         for (int offset : new int[]{0, -1, 1}) cells.add(center.relative(direction.getClockWise(), offset).above(height));
      }
      for (int offset : new int[]{0, -1, 1}) cells.add(center.relative(direction.getClockWise(), offset).below());
      return cells;
   }

   private static BlockState expected(int index) { return index < 9 ? AIR : FLOOR; }
   private static boolean matches(BlockState state, BlockState expected) {
      return expected.isAir() ? state.isAir() : state.is(expected.getBlock());
   }

   private int materialCount() { return this.controller.getItemStorage().getItemCountInventoryOnly(Items.NETHERRACK); }

   static boolean canFillFloor(BlockState state) {
      return state.isAir() || state.is(Blocks.LAVA) && state.canBeReplaced();
   }

   private BlockPos lavaToSeal(BlockPos pos, BlockState needed) {
      var world = this.controller.getWorld();
      BlockState state = world.getBlockState(pos);
      if (needed == FLOOR && (canFillFloor(state) || matches(state, needed))) return null;
      for (Direction side : Direction.values()) {
         if (!canFlowIntoCell(side)) continue;
         BlockPos adjacent = pos.relative(side);
         if (world.hasChunkAt(adjacent) && world.getBlockState(adjacent).is(Blocks.LAVA)) return adjacent;
      }
      return needed.isAir() && state.is(Blocks.LAVA) ? pos : null;
   }

   static boolean canFlowIntoCell(Direction neighborSide) {
      return neighborSide != Direction.DOWN;
   }

   private String obstacle(BlockPos pos, BlockState needed) {
      var world = this.controller.getWorld();
      if (SafeZoneService.protectedAt(world, pos)) return "Owner Safe Zone blocks the row";
      BlockState state = world.getBlockState(pos);
      if (needed == FLOOR && canFillFloor(state) || needed.isAir() && state.is(Blocks.LAVA)) return null;
      if (state.hasBlockEntity() || state.is(Blocks.NETHER_PORTAL) || state.getDestroySpeed(world, pos) < 0) {
         return "A container, portal, or unbreakable block obstructs the row";
      }
      if (!state.getFluidState().isEmpty()) return "Fluid occupies the row";
      if (!state.isAir()) {
         for (Direction side : Direction.values()) {
            BlockPos adjacent = pos.relative(side);
            if (!world.hasChunkAt(adjacent)) return "Cannot check neighboring chunk at " + adjacent.toShortString();
            var fluid = world.getFluidState(adjacent);
            if (canFlowIntoCell(side) && !fluid.isEmpty()
               && !(fluid.is(FluidTags.LAVA) && world.getBlockState(adjacent).is(Blocks.LAVA))) {
               return "Clearing this cell could release " + fluid.getType() + " from " + adjacent.toShortString();
            }
            if (side == Direction.UP && world.getBlockState(adjacent).getBlock() instanceof net.minecraft.world.level.block.Fallable) {
               return "Clearing this cell could drop a falling block from " + adjacent.toShortString();
            }
         }
      }
      return null;
   }

   private void startBuilder() {
      if (this.controller.getBaritone().getBuilderProcess() instanceof BuilderProcess builder
         && (!builder.isActive() || builder.ownsSchematic(this.active) && builder.isPaused())) {
         builder.buildSingleCell("Netherbridge", this.active, this.target);
      }
   }

   private void releaseBuilder() {
      if (this.controller.getBaritone().getBuilderProcess() instanceof BuilderProcess builder
         && builder.ownsSchematic(this.active)) {
         builder.onLostControl();
         this.controller.getBaritone().getPathingBehavior().forceCancel();
         this.controller.getBaritone().getInputOverrideHandler().clearAllKeys();
      }
      this.active = null;
   }

   private Task block(String reason, BlockPos pos) {
      this.failure = reason + " at " + pos.toShortString() + " (row " + this.row + "/" + this.length + ")";
      releaseBuilder();
      return null;
   }

   @Override protected void onStop(Task next) {
      releaseBuilder();
      VillageSiteGuard.clear(this, this.controller);
   }
   @Override public boolean prepareForTransientResume(TaskSuspensionCause cause) {
      releaseBuilder();
      this.acquire = null;
      return !isFinished();
   }
   @Override public void onTransientResumeAbandoned() { VillageSiteGuard.clear(this, this.controller); }
   @Override public boolean isFinished() { return this.finished || this.failure != null; }
   @Override public boolean isTerminal() { return isFinished(); }
   @Override public boolean isSuccessful() { return this.finished; }
   @Override public String controlledReason() { return this.failure; }
   @Override protected boolean isEqual(Task other) { return this == other; }
   @Override protected String toDebugString() { return "Netherbridge " + this.direction + " from " + this.origin.toShortString(); }

   private static final class WalkToFront extends CustomBaritoneGoalTask {
      private final BlockPos front;

      WalkToFront(BlockPos front) {
         super(false);
         this.front = front;
      }

      @Override protected Goal newGoal(PlayerEngineController mod) { return new GoalBlock(this.front); }
      @Override protected boolean isEqual(Task other) { return other instanceof WalkToFront walk && this.front.equals(walk.front); }
      @Override protected String toDebugString() { return "Walking to verified bridge at " + this.front.toShortString(); }
   }

   private static final class BridgeCell extends AbstractSchematic {
      private final BlockState before;
      private final BlockState desired;
      private final int deckY;
      BridgeCell(BlockState before, BlockState desired, int deckY) {
         super(1, 1, 1);
         this.before = before;
         this.desired = desired;
         this.deckY = deckY;
      }
      @Override public int minimumPlacementStanceY(int targetY) { return this.deckY; }
      @Override public boolean restrictEditsToFootprint() { return true; }
      @Override public boolean placeFromBelow() { return true; }
      @Override public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> available) {
         return current == this.before ? this.desired : current;
      }
   }
}
