package com.player2.playerengine.tasks.farming;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class FarmPlotRepairPlan {
   public static final int VERTICAL_LAYER_COUNT = 5;
   public static final int OBSERVATION_COUNT = 405;
   public static final int MAX_CLEAR_ACTIONS = 260;
   private static final long MAX_ACTION_HORIZONTAL_DISTANCE_SQUARED = 9L;
   private final BlockPos center;
   private final boolean centerHasWaterSource;
   private final int repairColumns;
   private final List<BlockPos> returnStanceCandidates;
   private final List<FarmPlotRepairPlan.ClearAction> clearActions;
   private final FarmRepairToolPlan toolPlan;
   private final List<FarmPlotRepairPlan.FillAction> fillActions;
   private final List<FarmPlotRepairPlan.TillAction> tillActions;
   private final Set<BlockPos> preservedCrops;

   private FarmPlotRepairPlan(
      BlockPos center,
      boolean centerHasWaterSource,
      int repairColumns,
      List<BlockPos> returnStanceCandidates,
      List<FarmPlotRepairPlan.ClearAction> clearActions,
      List<FarmPlotRepairPlan.FillAction> fillActions,
      List<FarmPlotRepairPlan.TillAction> tillActions,
      Set<BlockPos> preservedCrops
   ) {
      this.center = immutable(center, "center");
      this.centerHasWaterSource = centerHasWaterSource;
      if (repairColumns >= 0 && repairColumns <= 16) {
         this.repairColumns = repairColumns;
         this.returnStanceCandidates = immutablePositions(returnStanceCandidates, "returnStanceCandidates");
         this.clearActions = List.copyOf(Objects.requireNonNull(clearActions, "clearActions"));
         this.toolPlan = FarmRepairToolPlan.fromClearActions(this.clearActions);
         this.fillActions = List.copyOf(Objects.requireNonNull(fillActions, "fillActions"));
         this.tillActions = List.copyOf(Objects.requireNonNull(tillActions, "tillActions"));
         Objects.requireNonNull(preservedCrops, "preservedCrops");
         LinkedHashSet<BlockPos> crops = new LinkedHashSet<>();

         for (BlockPos crop : preservedCrops) {
            crops.add(immutable(crop, "preserved crop"));
         }

         this.preservedCrops = Collections.unmodifiableSet(crops);
         if (this.returnStanceCandidates.isEmpty() || this.clearActions.size() > 260 || this.fillActions.size() > 16 || this.tillActions.size() > 80) {
            throw new IllegalArgumentException("repair manifest is outside fixed bounds");
         }
      } else {
         throw new IllegalArgumentException("repair columns exceed the fixed farm limit");
      }
   }

   public static FarmPlotRepairPlan.ScanResult scan(ServerLevel level, BlockPos center) {
      return scan(FarmSiteWorldView.live(Objects.requireNonNull(level, "level")), center);
   }

   public static FarmPlotRepairPlan.ScanResult scan(FarmSiteWorldView world, BlockPos requestedCenter) {
      Objects.requireNonNull(world, "world");
      BlockPos center = immutable(requestedCenter, "center");
      world.assertServerThread();
      LinkedHashMap<BlockPos, FarmSiteWorldView.CellObservation> observations = new LinkedHashMap<>();

      for (BlockPos position : scanEnvelope(center)) {
         observations.put(position, world.observe(position));
      }

      return derive(center, observations);
   }

   public static List<BlockPos> scanEnvelope(BlockPos requestedCenter) {
      BlockPos center = immutable(requestedCenter, "center");
      ArrayList<BlockPos> positions = new ArrayList<>(405);
      List<BlockPos> cells = FarmPlotGeometry.allCells(center);

      for (int dy = -1; dy <= 3; dy++) {
         for (BlockPos cell : cells) {
            positions.add(cell.offset(0, dy, 0).immutable());
         }
      }

      return List.copyOf(positions);
   }

   public static List<BlockPos> farmLevelReturnStanceCandidates(BlockPos requestedCenter) {
      BlockPos center = immutable(requestedCenter, "center");
      ArrayList<BlockPos> candidates = new ArrayList<>(80);

      for (BlockPos soil : FarmPlotGeometry.soilCells(center)) {
         candidates.add(soil.above().immutable());
      }

      return List.copyOf(candidates);
   }

   public static boolean isFarmLevelReturnStance(BlockPos center, BlockPos feet) {
      if (center != null && feet != null && feet.getY() == center.getY() + 1) {
         BlockPos support = feet.below();
         return !support.equals(center) && FarmPlotGeometry.contains(center, support);
      } else {
         return false;
      }
   }

   private static FarmPlotRepairPlan.ScanResult derive(BlockPos center, LinkedHashMap<BlockPos, FarmSiteWorldView.CellObservation> observations) {
      List<BlockPos> expected = scanEnvelope(center);
      if (observations.size() == 405 && new ArrayList<>(observations.keySet()).equals(expected)) {
         for (Entry<BlockPos, FarmSiteWorldView.CellObservation> entry : observations.entrySet()) {
            BlockPos position = entry.getKey();
            FarmSiteWorldView.CellObservation facts = entry.getValue();
            if (facts != null && facts.isReadable()) {
               if (!facts.blockEntity() && !facts.unbreakable() && !facts.fallingHazard()) {
                  boolean allowedCenterSource = position.equals(center) && facts.isStandaloneWaterSource();
                  if (facts.hasFluid() && !allowedCenterSource) {
                     return unsafe(FarmPlotRepairPlan.Status.UNSAFE, FarmPlotRepairPlan.UnsafeReason.NON_CENTER_FLUID);
                  }
                  continue;
               }

               return unsafe(FarmPlotRepairPlan.Status.UNSAFE, FarmPlotRepairPlan.UnsafeReason.UNSAFE_OBSTRUCTION);
            }

            return unsafe(FarmPlotRepairPlan.Status.UNSAFE, FarmPlotRepairPlan.UnsafeReason.UNREADABLE);
         }

         List<BlockPos> cells = FarmPlotGeometry.allCells(center);

         for (BlockPos surface : cells) {
            if (!safeFoundationSupport(observations.get(surface.below()))) {
               return unsafe(FarmPlotRepairPlan.Status.UNSAFE, FarmPlotRepairPlan.UnsafeReason.UNSAFE_SUPPORT);
            }
         }

         ArrayList<FarmPlotRepairPlan.ClearDraft> headroomClears = new ArrayList<>();
         ArrayList<FarmPlotRepairPlan.ClearDraft> surfaceClears = new ArrayList<>();
         LinkedHashSet<BlockPos> fillTargets = new LinkedHashSet<>();
         LinkedHashSet<BlockPos> tillTargets = new LinkedHashSet<>();
         LinkedHashSet<BlockPos> preservedCrops = new LinkedHashSet<>();
         int repairColumns = 0;
         boolean centerHasWater = false;

         for (int dy = 3; dy >= 1; dy--) {
            for (BlockPos surface : cells) {
               BlockPos target = surface.above(dy);
               FarmSiteWorldView.CellObservation facts = observations.get(target);
               if (isCrop(facts)) {
                  boolean canonicalLowerCell = dy == 1
                     && !surface.equals(center)
                     && observations.get(surface).blockKind() == FarmSiteWorldView.BlockKind.FARMLAND;
                  boolean supportedPitcherUpper = dy == 2
                     && isSupportedPitcherUpperPair(surface, center, observations.get(surface), observations.get(surface.above()), facts);
                  if (!canonicalLowerCell && !supportedPitcherUpper) {
                     return unsafe(FarmPlotRepairPlan.Status.UNSAFE, FarmPlotRepairPlan.UnsafeReason.CROP_CONFLICT);
                  }

                  preservedCrops.add(target);
               } else if (facts.blockKind() != FarmSiteWorldView.BlockKind.AIR) {
                  headroomClears.add(
                     new FarmPlotRepairPlan.ClearDraft(target, facts.stateFingerprint(), FarmPlotRepairPlan.ClearPurpose.HEADROOM, facts.breakToolRequirement())
                  );
               }
            }
         }

         for (BlockPos surface : cells) {
            FarmSiteWorldView.CellObservation facts = observations.get(surface);
            FarmSiteWorldView.BlockKind kind = facts.blockKind();
            if (surface.equals(center)) {
               if (isCrop(facts)) {
                  return unsafe(FarmPlotRepairPlan.Status.UNSAFE, FarmPlotRepairPlan.UnsafeReason.CROP_CONFLICT);
               }

               if (kind == FarmSiteWorldView.BlockKind.STANDALONE_WATER) {
                  centerHasWater = true;
               } else if (kind != FarmSiteWorldView.BlockKind.AIR) {
                  surfaceClears.add(
                     new FarmPlotRepairPlan.ClearDraft(
                        surface, facts.stateFingerprint(), FarmPlotRepairPlan.ClearPurpose.OPEN_CENTER, facts.breakToolRequirement()
                     )
                  );
                  if (kind != FarmSiteWorldView.BlockKind.GRASS_BLOCK
                     && kind != FarmSiteWorldView.BlockKind.DIRT
                     && kind != FarmSiteWorldView.BlockKind.FARMLAND) {
                     repairColumns++;
                  }
               }
            } else {
               if (isCrop(facts)) {
                  return unsafe(FarmPlotRepairPlan.Status.UNSAFE, FarmPlotRepairPlan.UnsafeReason.CROP_CONFLICT);
               }

               switch (kind) {
                  case GRASS_BLOCK:
                  case DIRT:
                     tillTargets.add(surface);
                  case FARMLAND:
                     break;
                  case AIR:
                     fillTargets.add(surface);
                     tillTargets.add(surface);
                     repairColumns++;
                     break;
                  case CROP:
                     return unsafe(FarmPlotRepairPlan.Status.UNSAFE, FarmPlotRepairPlan.UnsafeReason.CROP_CONFLICT);
                  default:
                     surfaceClears.add(
                        new FarmPlotRepairPlan.ClearDraft(
                           surface, facts.stateFingerprint(), FarmPlotRepairPlan.ClearPurpose.REPLACE_SURFACE, facts.breakToolRequirement()
                        )
                     );
                     fillTargets.add(surface);
                     tillTargets.add(surface);
                     repairColumns++;
               }
            }
         }

         if (repairColumns > 16) {
            return unsafe(FarmPlotRepairPlan.Status.REPAIR_LIMIT_EXCEEDED, FarmPlotRepairPlan.UnsafeReason.REPAIR_LIMIT_EXCEEDED);
         }

         List<BlockPos> returnStances = liveReturnStances(center, observations);
         if (returnStances.isEmpty()) {
            return unsafe(FarmPlotRepairPlan.Status.UNSAFE, FarmPlotRepairPlan.UnsafeReason.NO_RETURN_STANCE);
         }

         ArrayList<FarmPlotRepairPlan.ClearAction> clearActions = new ArrayList<>(headroomClears.size() + surfaceClears.size());

         for (FarmPlotRepairPlan.ClearDraft draft : concat(headroomClears, surfaceClears)) {
            BlockPos stance = chooseActionStance(center, draft.target(), returnStances);
            if (stance == null) {
               return unsafe(FarmPlotRepairPlan.Status.UNSAFE, FarmPlotRepairPlan.UnsafeReason.NO_ACTION_STANCE);
            }

            clearActions.add(new FarmPlotRepairPlan.ClearAction(draft.target(), stance, draft.fingerprint(), draft.purpose(), draft.breakToolRequirement()));
         }

         if (clearActions.size() > 260) {
            return unsafe(FarmPlotRepairPlan.Status.UNSAFE, FarmPlotRepairPlan.UnsafeReason.UNSAFE_OBSTRUCTION);
         }

         String airFingerprint = FarmSiteWorldView.stateFingerprint(Blocks.AIR.defaultBlockState());
         String dirtFingerprint = FarmSiteWorldView.stateFingerprint(Blocks.DIRT.defaultBlockState());
         ArrayList<FarmPlotRepairPlan.FillAction> fillActions = new ArrayList<>(fillTargets.size());

         for (BlockPos target : fillTargets) {
            BlockPos stance = chooseActionStance(center, target, returnStances);
            if (stance == null) {
               return unsafe(FarmPlotRepairPlan.Status.UNSAFE, FarmPlotRepairPlan.UnsafeReason.NO_ACTION_STANCE);
            }

            boolean brokenFirst = surfaceClears.stream().anyMatch(clear -> clear.target().equals(target));
            fillActions.add(new FarmPlotRepairPlan.FillAction(target, stance, brokenFirst ? airFingerprint : observations.get(target).stateFingerprint()));
         }

         ArrayList<FarmPlotRepairPlan.TillAction> tillActions = new ArrayList<>(tillTargets.size());

         for (BlockPos target : tillTargets) {
            BlockPos stance = chooseActionStance(center, target, returnStances);
            if (stance == null) {
               return unsafe(FarmPlotRepairPlan.Status.UNSAFE, FarmPlotRepairPlan.UnsafeReason.NO_ACTION_STANCE);
            }

            tillActions.add(
               new FarmPlotRepairPlan.TillAction(target, stance, fillTargets.contains(target) ? dirtFingerprint : observations.get(target).stateFingerprint())
            );
         }

         return new FarmPlotRepairPlan.ScanResult(
            FarmPlotRepairPlan.Status.READY,
            FarmPlotRepairPlan.UnsafeReason.NONE,
            405,
            new FarmPlotRepairPlan(center, centerHasWater, repairColumns, returnStances, clearActions, fillActions, tillActions, preservedCrops)
         );
      } else {
         throw new IllegalArgumentException("repair observations do not match the exact envelope");
      }
   }

   private static List<FarmPlotRepairPlan.ClearDraft> concat(List<FarmPlotRepairPlan.ClearDraft> first, List<FarmPlotRepairPlan.ClearDraft> second) {
      ArrayList<FarmPlotRepairPlan.ClearDraft> combined = new ArrayList<>(first.size() + second.size());
      combined.addAll(first);
      combined.addAll(second);
      return combined;
   }

   private static FarmPlotRepairPlan.ScanResult unsafe(FarmPlotRepairPlan.Status status, FarmPlotRepairPlan.UnsafeReason reason) {
      return new FarmPlotRepairPlan.ScanResult(status, reason, 405, null);
   }

   private static boolean safeFoundationSupport(FarmSiteWorldView.CellObservation facts) {
      return facts != null
         && facts.isReadable()
         && facts.solidTopSupport()
         && facts.nonWaterloggable()
         && !facts.hasFluid()
         && !facts.playerProtected()
         && !facts.blockEntity()
         && !facts.unbreakable()
         && !facts.fallingHazard();
   }

   private static List<BlockPos> liveReturnStances(BlockPos center, Map<BlockPos, FarmSiteWorldView.CellObservation> observations) {
      ArrayList<BlockPos> candidates = new ArrayList<>();

      for (BlockPos feet : farmLevelReturnStanceCandidates(center)) {
         FarmSiteWorldView.CellObservation support = observations.get(feet.below());
         FarmSiteWorldView.CellObservation feetFacts = observations.get(feet);
         FarmSiteWorldView.CellObservation headFacts = observations.get(feet.above());
         if (safeFarmSurfaceSupport(support) && safeFeetCell(feetFacts) && safeHeadCell(headFacts)) {
            candidates.add(feet.immutable());
         }
      }

      return List.copyOf(candidates);
   }

   private static boolean safeFarmSurfaceSupport(FarmSiteWorldView.CellObservation facts) {
      return facts != null
            && facts.isReadable()
            && !facts.playerProtected()
            && facts.nonWaterloggable()
            && !facts.hasFluid()
            && !facts.blockEntity()
            && !facts.unbreakable()
            && !facts.fallingHazard()
         ? facts.blockKind() == FarmSiteWorldView.BlockKind.GRASS_BLOCK
            || facts.blockKind() == FarmSiteWorldView.BlockKind.DIRT
            || facts.blockKind() == FarmSiteWorldView.BlockKind.FARMLAND
         : false;
   }

   private static boolean safeFeetCell(FarmSiteWorldView.CellObservation facts) {
      boolean crop = isCrop(facts);
      return facts != null
         && facts.isReadable()
         && (!facts.playerProtected() || crop)
         && !facts.hasFluid()
         && !facts.blockEntity()
         && !facts.unbreakable()
         && !facts.fallingHazard()
         && (facts.collisionEmpty() || crop && PitcherCropHarvestBehavior.isLowerFingerprint(facts.stateFingerprint()));
   }

   private static boolean safeHeadCell(FarmSiteWorldView.CellObservation facts) {
      return facts != null
         && facts.isReadable()
         && !facts.playerProtected()
         && !facts.hasFluid()
         && !facts.blockEntity()
         && !facts.unbreakable()
         && !facts.fallingHazard()
         && facts.collisionEmpty();
   }

   private static boolean isCrop(FarmSiteWorldView.CellObservation facts) {
      return facts != null && (facts.recognizedCrop() || facts.blockKind() == FarmSiteWorldView.BlockKind.CROP);
   }

   static boolean isSupportedPitcherUpperPair(
      BlockPos surface,
      BlockPos center,
      FarmSiteWorldView.CellObservation support,
      FarmSiteWorldView.CellObservation lower,
      FarmSiteWorldView.CellObservation upper
   ) {
      if (surface != null
         && center != null
         && !surface.equals(center)
         && support != null
         && support.blockKind() == FarmSiteWorldView.BlockKind.FARMLAND
         && isCrop(lower)
         && isCrop(upper)
         && PitcherCropHarvestBehavior.isLowerFingerprint(lower.stateFingerprint())
         && PitcherCropHarvestBehavior.isUpperFingerprint(upper.stateFingerprint())) {
         OptionalInt lowerAge = PitcherCropHarvestBehavior.ageFromFingerprint(lower.stateFingerprint());
         OptionalInt upperAge = PitcherCropHarvestBehavior.ageFromFingerprint(upper.stateFingerprint());
         return lowerAge.isPresent() && upperAge.isPresent() && lowerAge.getAsInt() >= 3 && lowerAge.getAsInt() == upperAge.getAsInt();
      } else {
         return false;
      }
   }

   private static BlockPos chooseActionStance(BlockPos center, BlockPos target, List<BlockPos> candidates) {
      BlockPos selected = null;
      long selectedDistance = Long.MAX_VALUE;
      BlockPos targetSurface = new BlockPos(target.getX(), center.getY(), target.getZ());

      for (BlockPos candidate : candidates) {
         if (!candidate.below().equals(targetSurface)) {
            long distance = FarmPlotGeometry.horizontalDistanceSquared(candidate, target);
            if (distance <= 9L && (selected == null || distance < selectedDistance)) {
               selected = candidate;
               selectedDistance = distance;
            }
         }
      }

      return selected == null ? null : selected.immutable();
   }

   public BlockPos center() {
      return this.center;
   }

   public boolean centerHasWaterSource() {
      return this.centerHasWaterSource;
   }

   public int repairColumns() {
      return this.repairColumns;
   }

   public int requiredDirt() {
      return this.fillActions.size();
   }

   public List<BlockPos> returnStanceCandidates() {
      return this.returnStanceCandidates;
   }

   public List<FarmPlotRepairPlan.ClearAction> clearActions() {
      return this.clearActions;
   }

   public FarmRepairToolPlan toolPlan() {
      return this.toolPlan;
   }

   public List<FarmPlotRepairPlan.FillAction> fillActions() {
      return this.fillActions;
   }

   public List<FarmPlotRepairPlan.TillAction> tillActions() {
      return this.tillActions;
   }

   public Set<BlockPos> preservedCrops() {
      return this.preservedCrops;
   }

   private static List<BlockPos> immutablePositions(List<BlockPos> positions, String name) {
      Objects.requireNonNull(positions, name);
      ArrayList<BlockPos> copy = new ArrayList<>(positions.size());

      for (BlockPos position : positions) {
         copy.add(immutable(position, name + " entry"));
      }

      return List.copyOf(copy);
   }

   private static BlockPos immutable(BlockPos position, String name) {
      return Objects.requireNonNull(position, name).immutable();
   }

   public record ClearAction(
      BlockPos target,
      BlockPos stance,
      String expectedStateFingerprint,
      FarmPlotRepairPlan.ClearPurpose purpose,
      FarmBreakToolRequirement breakToolRequirement
   ) {
      public ClearAction {
         target = FarmPlotRepairPlan.immutable(target, "target");
         stance = FarmPlotRepairPlan.immutable(stance, "stance");
         Objects.requireNonNull(expectedStateFingerprint, "expectedStateFingerprint");
         Objects.requireNonNull(purpose, "purpose");
         Objects.requireNonNull(breakToolRequirement, "breakToolRequirement");
      }
   }

   private record ClearDraft(BlockPos target, String fingerprint, FarmPlotRepairPlan.ClearPurpose purpose, FarmBreakToolRequirement breakToolRequirement) {
      private ClearDraft {
         target = FarmPlotRepairPlan.immutable(target, "target");
         Objects.requireNonNull(fingerprint, "fingerprint");
         Objects.requireNonNull(purpose, "purpose");
         Objects.requireNonNull(breakToolRequirement, "breakToolRequirement");
      }
   }

   public enum ClearPurpose {
      HEADROOM,
      REPLACE_SURFACE,
      OPEN_CENTER;
   }

   public record FillAction(BlockPos target, BlockPos stance, String expectedStateFingerprint) {
      public FillAction {
         target = FarmPlotRepairPlan.immutable(target, "target");
         stance = FarmPlotRepairPlan.immutable(stance, "stance");
         Objects.requireNonNull(expectedStateFingerprint, "expectedStateFingerprint");
      }
   }

   public record ScanResult(FarmPlotRepairPlan.Status status, FarmPlotRepairPlan.UnsafeReason reason, int reads, FarmPlotRepairPlan plan) {
      public ScanResult(FarmPlotRepairPlan.Status status, FarmPlotRepairPlan.UnsafeReason reason, int reads, FarmPlotRepairPlan plan) {
         Objects.requireNonNull(status, "status");
         Objects.requireNonNull(reason, "reason");
         if (reads != 405) {
            throw new IllegalArgumentException("repair scan must account for exactly 405 reads");
         }

         if (status == FarmPlotRepairPlan.Status.READY == (plan != null)
            && status == FarmPlotRepairPlan.Status.READY == (reason == FarmPlotRepairPlan.UnsafeReason.NONE)) {
            this.status = status;
            this.reason = reason;
            this.reads = reads;
            this.plan = plan;
         } else {
            throw new IllegalArgumentException("repair scan status, reason, and plan disagree");
         }
      }

      public boolean ready() {
         return this.status == FarmPlotRepairPlan.Status.READY;
      }
   }

   public enum Status {
      READY,
      UNSAFE,
      REPAIR_LIMIT_EXCEEDED;
   }

   public record TillAction(BlockPos target, BlockPos stance, String expectedStateFingerprint) {
      public TillAction {
         target = FarmPlotRepairPlan.immutable(target, "target");
         stance = FarmPlotRepairPlan.immutable(stance, "stance");
         Objects.requireNonNull(expectedStateFingerprint, "expectedStateFingerprint");
      }
   }

   public enum UnsafeReason {
      NONE,
      UNREADABLE,
      UNSAFE_SUPPORT,
      UNSAFE_OBSTRUCTION,
      NON_CENTER_FLUID,
      CROP_CONFLICT,
      NO_RETURN_STANCE,
      NO_ACTION_STANCE,
      REPAIR_LIMIT_EXCEEDED;
   }
}
