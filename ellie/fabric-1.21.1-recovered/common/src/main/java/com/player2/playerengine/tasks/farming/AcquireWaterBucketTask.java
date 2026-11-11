package com.player2.playerengine.tasks.farming;

import com.player2.playerengine.agentic.elliegps.EllieGPSStore;
import com.player2.playerengine.automaton.api.utils.Rotation;
import com.player2.playerengine.automaton.api.utils.input.Input;
import com.player2.playerengine.structureprotection.PlayerPlacedBlockStore;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.movement.GetToBlockTask;
import com.player2.playerengine.tasks.movement.TimeoutWanderTask;
import com.player2.playerengine.util.helpers.LookHelper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.ClipContext.Fluid;

public final class AcquireWaterBucketTask extends Task {
   private static final int WANDER_RESERVED_TICKS_PER_LEG = 160;
   private static final long SEARCH_LEG_WALL_CLOCK_MS = 8000L;
   private static final int RETRY_DELAY_TICKS = 10;
   static final int LOCAL_SCAN_HORIZONTAL_RADIUS = 10;
   static final int LOCAL_SCAN_VERTICAL_RADIUS = 8;
   static final int SOURCE_SCAN_BLOCKS_PER_TICK = 52;
   static final int MAX_SCANNER_CANDIDATES_PER_WINDOW = 256;
   static final int MAX_WORLD_READS_PER_SCANNED_POSITION = 39;
   private static final int RETURN_TO_BOUND_RESERVE_TICKS = 100;
   private final BlockPos anchor;
   private final BlockPos surfaceReturnStance;
   private String dimension;
   private BlockPos source;
   private BlockPos stance;
   private BlockState expectedSource;
   private boolean behaviourPushed;
   private boolean terminal;
   private boolean successful;
   private FarmTaskReason reason = FarmTaskReason.NONE;
   private int searchLeg;
   private int searchTicksInLeg;
   private boolean wanderStartedInLeg;
   private boolean postWanderScan;
   private int interactionTicks;
   private int attempts;
   private int ticksSinceAttempt;
   private int emptyBucketsBefore;
   private int waterBucketsBefore;
   private boolean inventoryBaselineCaptured;
   private BlockPos sourceScanCenter;
   private int sourceScanIndex;
   private List<BlockPos> scannerCandidates = List.of();
   private int scannerCandidateIndex;

   public AcquireWaterBucketTask(BlockPos anchor) {
      this(anchor, Objects.requireNonNull(anchor, "anchor").above());
   }

   public AcquireWaterBucketTask(BlockPos anchor, BlockPos surfaceReturnStance) {
      this.anchor = Objects.requireNonNull(anchor, "anchor").immutable();
      this.surfaceReturnStance = Objects.requireNonNull(surfaceReturnStance, "surfaceReturnStance").immutable();
   }

   @Override
   protected void onStart() {
      this.dimension = this.currentDimension();
      this.source = null;
      this.stance = null;
      this.expectedSource = null;
      this.terminal = false;
      this.successful = false;
      this.reason = FarmTaskReason.NONE;
      this.searchLeg = 0;
      this.searchTicksInLeg = 0;
      this.wanderStartedInLeg = false;
      this.postWanderScan = false;
      this.interactionTicks = 0;
      this.attempts = 0;
      this.ticksSinceAttempt = 10;
      this.inventoryBaselineCaptured = false;
      this.behaviourPushed = false;
      this.resetSourceScan();
      if (this.controller.getItemStorage().getItemCount(Items.WATER_BUCKET) > 0) {
         this.succeed();
      } else if (this.controller.getItemStorage().getItemCount(Items.BUCKET) < 1) {
         this.fail(FarmTaskReason.BUCKET_UNAVAILABLE);
      } else if (EllieGPSStore.get() != null && PlayerPlacedBlockStore.get() != null) {
         this.controller.getBehaviour().push();
         this.behaviourPushed = true;
         this.controller.getBehaviour().setRayTracingFluidHandling(Fluid.SOURCE_ONLY);
      } else {
         this.fail(FarmTaskReason.STORE_UNAVAILABLE);
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

      if (EllieGPSStore.get() == null || PlayerPlacedBlockStore.get() == null) {
         this.fail(FarmTaskReason.STORE_UNAVAILABLE);
         return null;
      }

      if (this.source == null) {
         if (this.searchLeg >= 3) {
            this.fail(FarmTaskReason.WATER_SOURCE_NOT_FOUND);
            return null;
         }

         this.searchTicksInLeg++;
         if (this.searchTicksInLeg >= 600) {
            this.advanceSearchLeg();
            return null;
         }

         Task child = this.getSub();
         if (!this.isPlayerAtLiveSurface()) {
            if (this.wanderStartedInLeg && !this.postWanderScan) {
               this.postWanderScan = true;
            }

            this.resetSourceScan();
            this.setDebugState("Returning to the selected farm surface before water search");
            return new GetToBlockTask(this.surfaceReturnStance);
         } else if (this.wanderStartedInLeg && !this.postWanderScan && child instanceof TimeoutWanderTask wander) {
            if (!wander.isFinished() && this.searchTicksInLeg < 600 - postScanReserveTicks()) {
               return wander;
            }

            this.postWanderScan = true;
            this.resetSourceScan();
            return null;
         } else {
            if (!this.withinSearchRadius(this.controller.getPlayer().blockPosition())) {
               this.setDebugState("Returning inside bounded water search");
               return new GetToBlockTask(this.surfaceReturnStance);
            }

            AcquireWaterBucketTask.SourceSearchBatch batch = this.findSourceBatch();
            if (batch.selected().isPresent()) {
               AcquireWaterBucketTask.SourceAndStance pair = batch.selected().get();
               this.source = pair.source();
               this.stance = pair.stance();
               this.expectedSource = this.controller.getWorld().getBlockState(this.source);
               this.interactionTicks = 0;
               this.setDebugState("Moving to verified water source");
               return new GetToBlockTask(this.stance);
            }

            if (!batch.complete()) {
               this.setDebugState("Scanning a bounded local area for water (leg " + (this.searchLeg + 1) + "/3)");
               return null;
            }

            return switch (windowCompletion(this.wanderStartedInLeg, this.postWanderScan)) {
               case START_WANDER -> {
                  this.wanderStartedInLeg = true;
                  float wanderDistance = boundedWanderDistance(this.anchor, this.controller.getPlayer().blockPosition(), 32.0F + this.searchLeg * 16.0F, 96.0);
                  if (wanderDistance < 1.0F) {
                     this.postWanderScan = true;
                     this.resetSourceScan();
                     yield null;
                  } else {
                     this.setDebugState("Searching for water (leg " + (this.searchLeg + 1) + "/3)");
                     yield new TimeoutWanderTask(wanderDistance, false, 8000L);
                  }
               }
               case ADVANCE_LEG -> {
                  this.advanceSearchLeg();
                  yield null;
               }
               case INVALID -> {
                  this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
                  yield null;
               }
            };
         }
      } else {
         if (this.inventoryBaselineCaptured) {
            int emptyAfter = this.count(Items.BUCKET);
            int waterAfter = this.count(Items.WATER_BUCKET);
            if (FarmInteractionPostconditions.waterPickupInventoryDelta(this.emptyBucketsBefore, emptyAfter, this.waterBucketsBefore, waterAfter)) {
               this.succeed();
               return null;
            }

            if (emptyAfter != this.emptyBucketsBefore || waterAfter != this.waterBucketsBefore) {
               this.fail(FarmTaskReason.INTERNAL_CONTRACT_FAILURE);
               return null;
            }
         }

         if (!this.controller.getChunkTracker().isChunkLoaded(this.source) || !this.controller.getChunkTracker().isChunkLoaded(this.stance)) {
            this.fail(FarmTaskReason.CHUNK_UNLOADED);
            return null;
         }

         if (!FarmStanceNavigation.isAtFrozenStance(this.controller, this.stance)) {
            this.setDebugState("Moving to fixed water stance");
            return new GetToBlockTask(this.stance);
         }

         this.interactionTicks = advanceInteractionTicks(this.interactionTicks, true);
         this.ticksSinceAttempt++;
         if (this.interactionTicks > 300) {
            this.fail(FarmTaskReason.INTERACTION_DENIED);
            return null;
         }

         if (!this.isSafeSource(this.source) || !this.controller.getWorld().getBlockState(this.source).equals(this.expectedSource)) {
            this.fail(FarmTaskReason.MANIFEST_DRIFT);
            return null;
         }

         if (this.attempts >= 3) {
            this.fail(FarmTaskReason.INTERACTION_DENIED);
            return null;
         }

         if (this.ticksSinceAttempt < 10) {
            return null;
         }

         if (this.controller.getSlotHandler().forceEquipItem(Items.BUCKET)
            && this.controller.getPlayer().getMainHandItem().is(Items.BUCKET)) {
            Optional<Rotation> reach = LookHelper.getReach(this.controller, this.source);
            if (reach.isEmpty()) {
               this.fail(FarmTaskReason.INTERACTION_DENIED);
               return null;
            } else if (!LookHelper.isLookingAt(this.controller, reach.get())) {
               LookHelper.lookAt(this.controller, reach.get());
               return null;
            } else {
               HitResult mouseOver = this.controller.getBaritone().getEntityContext().objectMouseOver();
               Optional<InteractionResult> interaction = FarmInteractionDispatcher.dispatchMainHandBlockThenItemAtBlock(
                  this.controller, mouseOver, this.source, () -> {
                     if (!this.inventoryBaselineCaptured) {
                        this.emptyBucketsBefore = this.count(Items.BUCKET);
                        this.waterBucketsBefore = this.count(Items.WATER_BUCKET);
                        this.inventoryBaselineCaptured = true;
                     }

                     this.attempts++;
                     this.ticksSinceAttempt = 0;
                  }
               );
               if (interaction.isEmpty()) {
                  LookHelper.lookAt(this.controller, reach.get());
                  return null;
               } else {
                  this.setDebugState("Picking up verified water source");
                  return null;
               }
            }
         } else {
            this.fail(FarmTaskReason.BUCKET_UNAVAILABLE);
            return null;
         }
      }
   }

   private AcquireWaterBucketTask.SourceSearchBatch findSourceBatch() {
      BlockPos playerCenter = this.controller.getPlayer().blockPosition().immutable();
      if (this.sourceScanCenter == null) {
         this.sourceScanCenter = freezeSourceScanCenter(null, surfaceScanCenter(playerCenter, this.surfaceHeight(playerCenter)));
         this.sourceScanIndex = 0;
         this.scannerCandidateIndex = 0;
         ArrayList<BlockPos> boundedKnown = new ArrayList<>(this.controller.getBlockScanner().getKnownLocationsBounded(256, Blocks.WATER));
         boundedKnown.removeIf(
            candidatex -> !this.withinSearchRadius(candidatex)
               || !this.controller.getChunkTracker().isChunkLoaded(candidatex)
               || !this.isExposedSurfaceSource(candidatex)
         );
         boundedKnown.sort(sourcePriorityComparator(this.anchor));
         this.scannerCandidates = List.copyOf(boundedKnown);
      }

      int visited = 0;
      int volume = localScanVolume();

      while ((this.scannerCandidateIndex < this.scannerCandidates.size() || this.sourceScanIndex < volume) && visited < 52) {
         BlockPos candidate = this.scannerCandidateIndex < this.scannerCandidates.size()
            ? this.scannerCandidates.get(this.scannerCandidateIndex++)
            : localScanPosition(this.sourceScanCenter, this.sourceScanIndex++);
         visited++;
         if (this.withinSearchRadius(candidate) && this.controller.getChunkTracker().isChunkLoaded(candidate) && this.isExposedSurfaceSource(candidate)) {
            BlockState state = this.controller.getWorld().getBlockState(candidate);
            if (state.is(Blocks.WATER)
               && state.getFluidState().isSource()
               && state.getBlock() instanceof BucketPickup
               && this.isSafeSource(candidate, state)) {
               BlockPos selectedStance = this.chooseStance(candidate);
               if (selectedStance != null) {
                  return new AcquireWaterBucketTask.SourceSearchBatch(
                     Optional.of(new AcquireWaterBucketTask.SourceAndStance(candidate.immutable(), selectedStance)),
                     this.scannerCandidateIndex >= this.scannerCandidates.size() && this.sourceScanIndex >= volume
                  );
               }
            }
         }
      }

      return new AcquireWaterBucketTask.SourceSearchBatch(
         Optional.empty(), this.scannerCandidateIndex >= this.scannerCandidates.size() && this.sourceScanIndex >= volume
      );
   }

   private boolean isSafeSource(BlockPos candidate) {
      if (this.controller.getChunkTracker().isChunkLoaded(candidate) && !this.isKnownFarmCenter(candidate)) {
         BlockState state = this.controller.getWorld().getBlockState(candidate);
         return this.isSafeSource(candidate, state);
      } else {
         return false;
      }
   }

   private boolean isSafeSource(BlockPos candidate, BlockState state) {
      if (this.isKnownFarmCenter(candidate) || !this.isExposedSurfaceSource(candidate)) {
         return false;
      }

      if (state.is(Blocks.WATER) && state.getFluidState().isSource() && state.getBlock() instanceof BucketPickup) {
         for (BlockPos envelope : this.sourceEnvelope(candidate)) {
            if (!this.controller.getChunkTracker().isChunkLoaded(envelope)
               || this.isProtected(envelope)
               || this.controller.getWorld().getBlockState(envelope).hasBlockEntity()
               || this.controller.getWorld().getBlockEntity(envelope) != null) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private void advanceSearchLeg() {
      this.searchLeg++;
      this.searchTicksInLeg = 0;
      this.wanderStartedInLeg = false;
      this.postWanderScan = false;
      this.resetSourceScan();
   }

   private void resetSourceScan() {
      this.sourceScanCenter = null;
      this.sourceScanIndex = 0;
      this.scannerCandidates = List.of();
      this.scannerCandidateIndex = 0;
   }

   static int localScanVolume() {
      int width = 21;
      int height = 17;
      return width * width * height;
   }

   static BlockPos freezeSourceScanCenter(BlockPos existing, BlockPos current) {
      Objects.requireNonNull(current, "current");
      return existing == null ? current.immutable() : existing;
   }

   static int wanderReservedTicksPerLeg() {
      return 160;
   }

   static int postScanReserveTicks() {
      int scanCandidates = localScanVolume() + 256;
      int scanTicks = (scanCandidates + 52 - 1) / 52;
      return scanTicks + 100 + 2;
   }

   static int returnToBoundReserveTicks() {
      return 100;
   }

   static int latestReservedPostScanWorkTick() {
      int scanTicks = postScanReserveTicks() - 100 - 2;
      int detachTick = 600 - postScanReserveTicks();
      return detachTick + 100 + scanTicks;
   }

   static float boundedWanderDistance(BlockPos anchor, BlockPos current, float desired, double maxAnchorDistance) {
      Objects.requireNonNull(anchor, "anchor");
      Objects.requireNonNull(current, "current");
      if (!(desired < 0.0F) && !(maxAnchorDistance <= 0.0)) {
         double currentDistance = Math.sqrt(current.distSqr(anchor));
         double available = Math.max(0.0, maxAnchorDistance - currentDistance - 4.0);
         return (float)Math.min(desired, available);
      } else {
         throw new IllegalArgumentException("water-search wander bounds are invalid");
      }
   }

   static AcquireWaterBucketTask.SearchWindowCompletion windowCompletion(boolean wanderStartedInLeg, boolean postWanderScan) {
      if (!wanderStartedInLeg && !postWanderScan) {
         return AcquireWaterBucketTask.SearchWindowCompletion.START_WANDER;
      } else {
         return wanderStartedInLeg && postWanderScan
            ? AcquireWaterBucketTask.SearchWindowCompletion.ADVANCE_LEG
            : AcquireWaterBucketTask.SearchWindowCompletion.INVALID;
      }
   }

   static BlockPos localScanPosition(BlockPos center, int index) {
      Objects.requireNonNull(center, "center");
      int volume = localScanVolume();
      if (index >= 0 && index < volume) {
         int width = 21;
         int plane = width * width;
         int dy = centerOutVerticalOffset(index / plane);
         int withinPlane = index % plane;
         int dz = withinPlane / width - 10;
         int dx = withinPlane % width - 10;
         return center.offset(dx, dy, dz).immutable();
      } else {
         throw new IllegalArgumentException("local water scan index is out of range");
      }
   }

   static int centerOutVerticalOffset(int planeIndex) {
      int planes = 17;
      if (planeIndex < 0 || planeIndex >= planes) {
         throw new IllegalArgumentException("local water scan plane is out of range");
      }

      if (planeIndex == 0) {
         return 0;
      }

      int magnitude = (planeIndex + 1) / 2;
      return (planeIndex & 1) == 1 ? -magnitude : magnitude;
   }

   static BlockPos surfaceScanCenter(BlockPos horizontalCenter, int surfaceHeight) {
      Objects.requireNonNull(horizontalCenter, "horizontalCenter");
      return new BlockPos(horizontalCenter.getX(), surfaceHeight - 1, horizontalCenter.getZ());
   }

   static boolean isExposedSurfaceSource(int sourceY, int surfaceHeight) {
      return sourceY + 1 == surfaceHeight;
   }

   static boolean isAtLiveSurface(int feetY, int surfaceHeight) {
      return Math.abs(feetY - surfaceHeight) <= 1;
   }

   static int advanceInteractionTicks(int currentTicks, boolean atFrozenStance) {
      if (currentTicks < 0) {
         throw new IllegalArgumentException("interaction ticks cannot be negative");
      } else {
         return atFrozenStance ? Math.addExact(currentTicks, 1) : currentTicks;
      }
   }

   static Comparator<BlockPos> sourcePriorityComparator(BlockPos anchor) {
      Objects.requireNonNull(anchor, "anchor");
      return Comparator.<BlockPos>comparingInt(candidate -> Math.abs(candidate.getY() - anchor.getY()))
         .thenComparingLong(candidate -> FarmPlotGeometry.horizontalDistanceSquared(candidate, anchor))
         .thenComparingInt(Vec3i::getX)
         .thenComparingInt(Vec3i::getY)
         .thenComparingInt(Vec3i::getZ);
   }

   private BlockPos chooseStance(BlockPos candidate) {
      for (Direction direction : Plane.HORIZONTAL) {
         BlockPos neighbor = candidate.relative(direction);
         if (this.isStandable(neighbor)) {
            return neighbor.immutable();
         }

         BlockPos bankTop = neighbor.above();
         if (this.isStandable(bankTop)) {
            return bankTop.immutable();
         }
      }

      return null;
   }

   private boolean isStandable(BlockPos position) {
      Level level = this.controller.getWorld();
      return this.controller.getChunkTracker().isChunkLoaded(position)
         && this.controller.getChunkTracker().isChunkLoaded(position.below())
         && level.getBlockState(position).getCollisionShape(level, position).isEmpty()
         && level.getBlockState(position.above()).getCollisionShape(level, position.above()).isEmpty()
         && level.getBlockState(position.below()).isCollisionShapeFullBlock(level, position.below())
         && !this.isProtected(position)
         && !this.isProtected(position.above())
         && !this.isProtected(position.below());
   }

   private boolean isKnownFarmCenter(BlockPos candidate) {
      EllieGPSStore store = EllieGPSStore.get();
      return store == null || store.hasTypeAtPosition("farm", this.dimension, candidate);
   }

   private List<BlockPos> sourceEnvelope(BlockPos candidate) {
      ArrayList<BlockPos> result = new ArrayList<>(7);
      result.add(candidate);
      result.add(candidate.above());
      result.add(candidate.below());

      for (Direction direction : Plane.HORIZONTAL) {
         result.add(candidate.relative(direction));
      }

      return result;
   }

   private boolean withinSearchRadius(BlockPos position) {
      double max = 96.0;
      return position.distSqr(this.anchor) <= max * max;
   }

   private int surfaceHeight(BlockPos position) {
      return this.controller.getWorld().getHeight(Types.MOTION_BLOCKING_NO_LEAVES, position.getX(), position.getZ());
   }

   private boolean isExposedSurfaceSource(BlockPos candidate) {
      return isExposedSurfaceSource(candidate.getY(), this.surfaceHeight(candidate));
   }

   private boolean isPlayerAtLiveSurface() {
      BlockPos feet = this.controller.getBaritone().getEntityContext().feetPos();
      return feet.equals(this.surfaceReturnStance) || isAtLiveSurface(feet.getY(), this.surfaceHeight(feet));
   }

   private boolean isProtected(BlockPos pos) {
      PlayerPlacedBlockStore store = PlayerPlacedBlockStore.get();
      return store == null || store.contains(this.dimension, pos);
   }

   private int count(Item item) {
      return this.controller.getItemStorage().getItemCount(item);
   }

   private String currentDimension() {
      return this.controller.getWorld().dimension().location().toString();
   }

   private void succeed() {
      this.terminal = true;
      this.successful = true;
      this.reason = FarmTaskReason.NONE;
   }

   private void fail(FarmTaskReason failure) {
      this.terminal = true;
      this.successful = false;
      this.reason = Objects.requireNonNull(failure, "failure");
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.releaseControls();
      if (!this.terminal && interruptTask == null) {
         this.fail(FarmTaskReason.CANCELLED_OPERATOR);
      }
   }

   private void releaseControls() {
      if (this.controller != null) {
         this.controller.getInputControls().release(Input.CLICK_RIGHT);
         this.controller.getBaritone().getPathingBehavior().forceCancel();
         if (this.controller.getBaritone().getCustomGoalProcess().isActive()) {
            this.controller.getBaritone().getCustomGoalProcess().onLostControl();
         }

         if (this.controller.getBaritone().getExploreProcess().isActive()) {
            this.controller.getBaritone().getExploreProcess().onLostControl();
         }

         if (this.behaviourPushed) {
            this.controller.getBehaviour().pop();
            this.behaviourPushed = false;
         }
      }
   }

   @Override
   public boolean isFinished() {
      return this.terminal;
   }

   public boolean isSuccessful() {
      return this.terminal && this.successful;
   }

   public FarmTaskReason reason() {
      return this.reason;
   }

   public String controlledReason() {
      return this.reason.controlledReason();
   }

   public BlockPos anchor() {
      return this.anchor;
   }

   public BlockPos surfaceReturnStance() {
      return this.surfaceReturnStance;
   }

   public BlockPos source() {
      return this.source;
   }

   public BlockPos stance() {
      return this.stance;
   }

   public int attempts() {
      return this.attempts;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof AcquireWaterBucketTask task && task.anchor.equals(this.anchor) && task.surfaceReturnStance.equals(this.surfaceReturnStance);
   }

   @Override
   protected String toDebugString() {
      return "Acquire verified water bucket from anchor " + this.anchor.toShortString();
   }

   enum SearchWindowCompletion {
      START_WANDER,
      ADVANCE_LEG,
      INVALID;
   }

   private record SourceAndStance(BlockPos source, BlockPos stance) {
   }

   private record SourceSearchBatch(Optional<AcquireWaterBucketTask.SourceAndStance> selected, boolean complete) {
      private SourceSearchBatch {
         Objects.requireNonNull(selected, "selected");
      }
   }
}
