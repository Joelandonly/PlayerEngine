package com.player2.playerengine.tasks.farming;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.PitcherCropBlock;

public final class FarmPlotRepairPlanSelfTest {
   private static final BlockPos CENTER = new BlockPos(23, 70, -31);

   private FarmPlotRepairPlanSelfTest() {
   }

   public static void runAll() {
      envelopeIsExactlyCurrentFarm();
      surfaceAndCenterClassificationIsExact();
      cropsArePreservedOnlyOnCanonicalFarmland();
      pairedPitcherHalvesArePreservedExactly();
      hazardsAndRepairBoundsAreRejected();
      largeDirtFoundationBuildsFromGroundUp();
      clearOrderIsTopDownBeforeSurfaceRepair();
      toolFactsComeFromTheFrozenRepairRead();
      returnStancesStayOnSafeNoncenterSoil();
   }

   private static void envelopeIsExactlyCurrentFarm() {
      List<BlockPos> envelope = FarmPlotRepairPlan.scanEnvelope(CENTER);
      require(envelope.size() == 405, "repair envelope contains exactly 405 observations");
      require(new HashSet<>(envelope).size() == envelope.size(), "repair envelope positions are unique");

      for (int dy = -1; dy <= 3; dy++) {
         int layerY = CENTER.getY() + dy;
         require(envelope.stream().filter(pos -> pos.getY() == layerY).count() == 81L, "repair envelope has 81 observations at dy=" + dy);
      }

      require(
         envelope.stream()
            .allMatch(pos -> Math.abs(pos.getX() - CENTER.getX()) <= 4 && Math.abs(pos.getZ() - CENTER.getZ()) <= 4),
         "repair envelope never leaves the current 9x9 farm"
      );
      require(envelope.equals(FarmPlotRepairPlan.scanEnvelope(CENTER)), "repair envelope order is deterministic");
      FarmPlotRepairPlanSelfTest.FakeWorldView flat = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());
      FarmPlotRepairPlan.ScanResult result = FarmPlotRepairPlan.scan(flat, CENTER);
      require(result.ready(), "flat repair plot is ready");
      require(result.reads() == 405 && flat.reads == 405, "repair planner performs exactly 405 view reads");
   }

   private static void surfaceAndCenterClassificationIsExact() {
      BlockPos gap = CENTER.offset(-3, 0, -3);
      BlockPos replacement = CENTER.offset(-2, 0, -3);
      BlockPos farmland = CENTER.offset(-1, 0, -3);
      BlockPos protectedReplacement = CENTER.offset(0, 0, -3);
      FarmPlotRepairPlanSelfTest.FakeWorldView view = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());
      view.set(gap, view.cell(FarmSiteWorldView.BlockKind.AIR));
      view.set(replacement, view.cell(FarmSiteWorldView.BlockKind.STONE));
      view.set(farmland, view.cell(FarmSiteWorldView.BlockKind.FARMLAND));
      view.set(protectedReplacement, view.withProtected(view.cell(FarmSiteWorldView.BlockKind.OTHER)));
      assertUnsafe(view, FarmPlotRepairPlan.UnsafeReason.UNSAFE_OBSTRUCTION, "player-placed surface blocks are protected");
      view.set(protectedReplacement, view.cell(FarmSiteWorldView.BlockKind.OTHER));
      FarmPlotRepairPlan.ScanResult result = FarmPlotRepairPlan.scan(view, CENTER);
      require(result.ready(), "safe surface repairs produce a manifest");
      FarmPlotRepairPlan plan = result.plan();
      require(plan.repairColumns() == 3, "gap and two replacements count as repair columns");
      require(plan.requiredDirt() == 3, "every repaired noncenter surface receives dirt");
      require(
         plan.fillActions().stream().map(FarmPlotRepairPlan.FillAction::target).toList().containsAll(List.of(gap, replacement, protectedReplacement)),
         "air and safe replacements are all filled"
      );
      require(
         plan.clearActions()
            .stream()
            .anyMatch(action -> action.target().equals(replacement) && action.purpose() == FarmPlotRepairPlan.ClearPurpose.REPLACE_SURFACE),
         "stone is broken before replacement"
      );
      require(
         plan.clearActions().stream().anyMatch(action -> action.target().equals(protectedReplacement)),
         "unprotected surface obstructions can be replaced"
      );
      require(plan.tillActions().stream().noneMatch(action -> action.target().equals(farmland)), "existing farmland is not re-tilled");
      require(
         plan.tillActions().stream().anyMatch(action -> action.target().equals(gap))
            && plan.tillActions().stream().anyMatch(action -> action.target().equals(replacement)),
         "regenerated till targets include every dirt repair"
      );
      FarmPlotRepairPlanSelfTest.FakeWorldView sourceView = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());
      sourceView.set(CENTER, sourceView.cell(FarmSiteWorldView.BlockKind.STANDALONE_WATER));
      FarmPlotRepairPlan sourcePlan = ready(sourceView);
      require(sourcePlan.centerHasWaterSource(), "standalone center source is accepted");
      require(sourcePlan.clearActions().stream().noneMatch(action -> action.target().equals(CENTER)), "accepted center source is never cleared");
      FarmPlotRepairPlan grassCenter = ready(FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY()));
      require(!grassCenter.centerHasWaterSource(), "ordinary center soil still needs water");
      require(
         grassCenter.clearActions()
            .stream()
            .anyMatch(action -> action.target().equals(CENTER) && action.purpose() == FarmPlotRepairPlan.ClearPurpose.OPEN_CENTER),
         "ordinary center soil is safely opened without consuming a repair column"
      );
      require(grassCenter.repairColumns() == 0, "expected center opening is not surface damage");
   }

   private static void cropsArePreservedOnlyOnCanonicalFarmland() {
      require(
         FarmSiteWorldView.classifyBlock(Blocks.WHEAT.defaultBlockState(), Blocks.WHEAT.defaultBlockState().getFluidState())
            == FarmSiteWorldView.BlockKind.CROP,
         "HarvestBehaviorRegistry-recognized CropBlock is classified as a crop"
      );
      BlockPos soil = CENTER.offset(2, 0, 1);
      BlockPos crop = soil.above();
      FarmPlotRepairPlanSelfTest.FakeWorldView planted = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());
      planted.set(soil, planted.cell(FarmSiteWorldView.BlockKind.FARMLAND));
      planted.set(crop, planted.withProtected(planted.cell(FarmSiteWorldView.BlockKind.CROP)));
      FarmPlotRepairPlan plantedPlan = ready(planted);
      require(plantedPlan.preservedCrops().equals(Set.of(crop)), "canonical crop cell is preserved");
      require(plantedPlan.clearActions().stream().noneMatch(action -> action.target().equals(crop)), "preserved crop never becomes a clear action");
      require(plantedPlan.tillActions().stream().noneMatch(action -> action.target().equals(soil)), "farmland below a preserved crop is not mutated");
      require(plantedPlan.returnStanceCandidates().contains(crop), "collision-empty preserved crops remain non-mutating stance cells");
      FarmPlotRepairPlanSelfTest.FakeWorldView fullyPlanted = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());
      fullyPlanted.set(CENTER, fullyPlanted.cell(FarmSiteWorldView.BlockKind.STANDALONE_WATER));

      for (BlockPos plantedSoil : FarmPlotGeometry.soilCells(CENTER)) {
         fullyPlanted.set(plantedSoil, fullyPlanted.cell(FarmSiteWorldView.BlockKind.FARMLAND));
         fullyPlanted.set(plantedSoil.above(), fullyPlanted.withProtected(fullyPlanted.cell(FarmSiteWorldView.BlockKind.CROP)));
      }

      FarmPlotRepairPlan fullPlan = ready(fullyPlanted);
      require(
         fullPlan.preservedCrops().size() == 80 && fullPlan.returnStanceCandidates().size() == 80,
         "a fully planted farm remains returnable without clearing any crop"
      );
      require(
         fullPlan.clearActions().isEmpty() && fullPlan.fillActions().isEmpty() && fullPlan.tillActions().isEmpty(),
         "fully planted valid farmland is a no-op repair"
      );
      FarmPlotRepairPlanSelfTest.FakeWorldView plantedRepair = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());
      plantedRepair.set(CENTER, plantedRepair.cell(FarmSiteWorldView.BlockKind.STANDALONE_WATER));

      for (BlockPos plantedSoil : FarmPlotGeometry.soilCells(CENTER)) {
         plantedRepair.set(plantedSoil, plantedRepair.cell(FarmSiteWorldView.BlockKind.FARMLAND));
         plantedRepair.set(plantedSoil.above(), plantedRepair.withProtected(plantedRepair.cell(FarmSiteWorldView.BlockKind.CROP)));
      }

      BlockPos sabotagedSoil = CENTER.offset(3, 0, 2);
      plantedRepair.set(sabotagedSoil, plantedRepair.cell(FarmSiteWorldView.BlockKind.STONE));
      plantedRepair.set(sabotagedSoil.above(), plantedRepair.cell(FarmSiteWorldView.BlockKind.AIR));
      FarmPlotRepairPlan plantedRepairPlan = ready(plantedRepair);
      require(
         plantedRepairPlan.clearActions().size() == 1 && plantedRepairPlan.fillActions().size() == 1 && plantedRepairPlan.tillActions().size() == 1,
         "one sabotaged cell on a planted farm produces the complete repair sequence"
      );
      require(
         protectedCropAt(plantedRepair, plantedRepairPlan.clearActions().get(0).stance())
            && protectedCropAt(plantedRepair, plantedRepairPlan.fillActions().get(0).stance())
            && protectedCropAt(plantedRepair, plantedRepairPlan.tillActions().get(0).stance()),
         "every repair mutation can use a protected preserved crop as its frozen stance"
      );
      FarmPlotRepairPlanSelfTest.FakeWorldView wrongSupport = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());
      wrongSupport.set(soil, wrongSupport.cell(FarmSiteWorldView.BlockKind.DIRT));
      wrongSupport.set(crop, wrongSupport.cell(FarmSiteWorldView.BlockKind.CROP));
      assertUnsafe(wrongSupport, FarmPlotRepairPlan.UnsafeReason.CROP_CONFLICT, "crop over non-farmland support");
      FarmPlotRepairPlanSelfTest.FakeWorldView wrongHeight = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());
      wrongHeight.set(soil.above(2), wrongHeight.cell(FarmSiteWorldView.BlockKind.CROP));
      assertUnsafe(wrongHeight, FarmPlotRepairPlan.UnsafeReason.CROP_CONFLICT, "crop outside the canonical occupied cell");
      FarmPlotRepairPlanSelfTest.FakeWorldView centerCrop = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());
      centerCrop.set(CENTER.above(), centerCrop.cell(FarmSiteWorldView.BlockKind.CROP));
      assertUnsafe(centerCrop, FarmPlotRepairPlan.UnsafeReason.CROP_CONFLICT, "crop over the center hole");
   }

   private static void pairedPitcherHalvesArePreservedExactly() {
      BlockPos soil = CENTER.offset(1, 0, 2);
      BlockPos lowerPos = soil.above();
      BlockPos upperPos = soil.above(2);
      BlockState lowerFour = (BlockState)((BlockState)Blocks.PITCHER_CROP.defaultBlockState().setValue(PitcherCropBlock.AGE, 4))
         .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
      BlockState upperFour = (BlockState)lowerFour.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
      FarmPlotRepairPlanSelfTest.FakeWorldView planted = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());
      planted.set(soil, planted.cell(FarmSiteWorldView.BlockKind.FARMLAND));
      planted.set(
         lowerPos,
         planted.withProtected(
            planted.withCollisionEmpty(planted.cellWithState(FarmSiteWorldView.BlockKind.CROP, lowerFour, FarmBreakToolRequirement.none(lowerFour)), false)
         )
      );
      planted.set(upperPos, planted.cellWithState(FarmSiteWorldView.BlockKind.CROP, upperFour, FarmBreakToolRequirement.none(upperFour)));
      FarmPlotRepairPlan plan = ready(planted);
      require(
         plan.preservedCrops().containsAll(List.of(lowerPos, upperPos)) && plan.preservedCrops().size() == 2,
         "matching pitcher lower and upper halves are both preserved"
      );
      require(plan.returnStanceCandidates().contains(lowerPos), "known low pitcher collision remains a non-mutating return stance");
      require(
         plan.clearActions().stream().noneMatch(action -> action.target().equals(lowerPos) || action.target().equals(upperPos)),
         "neither supported pitcher half becomes a repair clear action"
      );
      FarmPlotRepairPlanSelfTest.FakeWorldView orphanUpper = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());
      orphanUpper.set(soil, orphanUpper.cell(FarmSiteWorldView.BlockKind.FARMLAND));
      orphanUpper.set(upperPos, orphanUpper.cellWithState(FarmSiteWorldView.BlockKind.CROP, upperFour, FarmBreakToolRequirement.none(upperFour)));
      assertUnsafe(orphanUpper, FarmPlotRepairPlan.UnsafeReason.CROP_CONFLICT, "orphan pitcher upper half");
      BlockState upperThree = (BlockState)((BlockState)Blocks.PITCHER_CROP.defaultBlockState().setValue(PitcherCropBlock.AGE, 3))
         .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
      FarmPlotRepairPlanSelfTest.FakeWorldView mismatchedAge = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());
      mismatchedAge.set(soil, mismatchedAge.cell(FarmSiteWorldView.BlockKind.FARMLAND));
      mismatchedAge.set(lowerPos, mismatchedAge.cellWithState(FarmSiteWorldView.BlockKind.CROP, lowerFour, FarmBreakToolRequirement.none(lowerFour)));
      mismatchedAge.set(upperPos, mismatchedAge.cellWithState(FarmSiteWorldView.BlockKind.CROP, upperThree, FarmBreakToolRequirement.none(upperThree)));
      assertUnsafe(mismatchedAge, FarmPlotRepairPlan.UnsafeReason.CROP_CONFLICT, "mismatched pitcher ages");
   }

   private static void hazardsAndRepairBoundsAreRejected() {
      BlockPos target = CENTER.offset(3, 2, 0);
      FarmPlotRepairPlanSelfTest.FakeWorldView blockEntity = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());
      blockEntity.set(target, blockEntity.withBlockEntity(blockEntity.cell(FarmSiteWorldView.BlockKind.OTHER)));
      assertUnsafe(blockEntity, FarmPlotRepairPlan.UnsafeReason.UNSAFE_OBSTRUCTION, "block entity");
      FarmPlotRepairPlanSelfTest.FakeWorldView unbreakable = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());
      unbreakable.set(target, unbreakable.withUnbreakable(unbreakable.cell(FarmSiteWorldView.BlockKind.OTHER)));
      assertUnsafe(unbreakable, FarmPlotRepairPlan.UnsafeReason.UNSAFE_OBSTRUCTION, "unbreakable cell");
      FarmPlotRepairPlanSelfTest.FakeWorldView falling = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());
      falling.set(target, falling.withFalling(falling.cell(FarmSiteWorldView.BlockKind.OTHER)));
      assertUnsafe(falling, FarmPlotRepairPlan.UnsafeReason.UNSAFE_OBSTRUCTION, "falling cell");
      FarmPlotRepairPlanSelfTest.FakeWorldView fluid = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());
      fluid.set(target, fluid.withFluid(fluid.cell(FarmSiteWorldView.BlockKind.OTHER), FarmSiteWorldView.FluidKind.FLOWING_WATER));
      assertUnsafe(fluid, FarmPlotRepairPlan.UnsafeReason.NON_CENTER_FLUID, "noncenter fluid remains distinct from unsafe solid obstructions");
      FarmPlotRepairPlanSelfTest.FakeWorldView protectedFoundation = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());
      BlockPos support = CENTER.offset(1, -1, 1);
      protectedFoundation.set(support, protectedFoundation.withProtected(protectedFoundation.cell(FarmSiteWorldView.BlockKind.STONE)));
      assertUnsafe(protectedFoundation, FarmPlotRepairPlan.UnsafeReason.UNSAFE_SUPPORT, "protected foundation support");
      FarmPlotRepairPlanSelfTest.FakeWorldView overLimit = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());
      FarmPlotRepairPlanSelfTest.FakeWorldView exactLimit = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());

      for (int i = 0; i < 16; i++) {
         BlockPos cell = FarmPlotGeometry.soilCells(CENTER).get(i);
         exactLimit.set(cell, exactLimit.cell(FarmSiteWorldView.BlockKind.AIR));
      }

      FarmPlotRepairPlan exactLimitPlan = ready(exactLimit);
      require(
         exactLimitPlan.repairColumns() == 16 && exactLimitPlan.fillActions().size() == 16, "sixteen surface repair columns are accepted at the exact bound"
      );

      for (int i = 0; i <= 16; i++) {
         BlockPos cell = FarmPlotGeometry.soilCells(CENTER).get(i);
         overLimit.set(cell, overLimit.cell(FarmSiteWorldView.BlockKind.AIR));
      }

      FarmPlotRepairPlan seventeen = ready(overLimit);
      require(seventeen.repairColumns() == 17 && seventeen.requiredDirt() == 17, "repair continues beyond sixteen columns");
   }

   private static void largeDirtFoundationBuildsFromGroundUp() {
      FakeWorldView terrain = FakeWorldView.flat(CENTER.getY());
      for (BlockPos soil : FarmPlotGeometry.soilCells(CENTER)) {
         terrain.set(soil, terrain.cell(FarmSiteWorldView.BlockKind.AIR));
      }
      BlockPos top = CENTER.offset(1, -1, 1);
      terrain.set(top, terrain.cell(FarmSiteWorldView.BlockKind.AIR));
      terrain.set(top.below(2), terrain.cell(FarmSiteWorldView.BlockKind.STONE));
      FarmPlotRepairPlan plan = ready(terrain);
      require(plan.requiredDirt() == 82 && plan.fillActions().size() == 80, "the full footprint and foundation contribute to dirt requirements");
      require(plan.foundation().equals(List.of(top.below(), top)), "dirt supports start on solid ground and build upwards");
      require(plan.dirtPlacement(0).equals(top.below()) && plan.dirtPlacement(1).equals(top), "supports precede surface soil");
      terrain.set(top.below(2), terrain.cell(FarmSiteWorldView.BlockKind.STANDALONE_WATER));
      assertUnsafe(terrain, FarmPlotRepairPlan.UnsafeReason.UNSAFE_SUPPORT, "wet deep foundation cannot be filled");
   }

   private static void clearOrderIsTopDownBeforeSurfaceRepair() {
      BlockPos surface = CENTER.offset(2, 0, -2);
      FarmPlotRepairPlanSelfTest.FakeWorldView view = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());
      view.set(surface, view.cell(FarmSiteWorldView.BlockKind.STONE));
      view.set(surface.above(), view.cell(FarmSiteWorldView.BlockKind.OTHER));
      view.set(surface.above(2), view.cell(FarmSiteWorldView.BlockKind.OTHER));
      view.set(surface.above(3), view.cell(FarmSiteWorldView.BlockKind.OTHER));
      FarmPlotRepairPlan plan = ready(view);
      List<FarmPlotRepairPlan.ClearAction> actions = plan.clearActions();
      require(
         actions.get(0).target().equals(surface.above(3))
            && actions.get(1).target().equals(surface.above(2))
            && actions.get(2).target().equals(surface.above()),
         "headroom actions are frozen from +3 through +1"
      );
      int firstSurface = -1;
      int lastHeadroom = -1;

      for (int i = 0; i < actions.size(); i++) {
         if (actions.get(i).purpose() == FarmPlotRepairPlan.ClearPurpose.HEADROOM) {
            lastHeadroom = i;
         } else if (firstSurface < 0) {
            firstSurface = i;
         }
      }

      require(lastHeadroom >= 0 && firstSurface > lastHeadroom, "every headroom clear precedes every surface clear");
      require(actions.size() <= FarmPlotRepairPlan.MAX_CLEAR_ACTIONS && plan.fillActions().size() <= 80 && plan.tillActions().size() <= 80,
         "repair action lists remain inside the plot");
      require(
         plan.fillActions()
            .stream()
            .anyMatch(
               action -> action.target().equals(surface)
                  && action.expectedStateFingerprint().equals(FarmSiteWorldView.stateFingerprint(Blocks.AIR.defaultBlockState()))
            ),
         "break-then-fill action freezes the expected intermediate air fingerprint"
      );
      require(
         plan.tillActions()
            .stream()
            .anyMatch(
               action -> action.target().equals(surface)
                  && action.expectedStateFingerprint().equals(FarmSiteWorldView.stateFingerprint(Blocks.DIRT.defaultBlockState()))
            ),
         "repaired surface regenerates a dirt till target"
      );
   }

   private static void toolFactsComeFromTheFrozenRepairRead() {
      FarmPlotRepairPlanSelfTest.FakeWorldView world = new FarmPlotRepairPlanSelfTest.FakeWorldView(CENTER.getY());
      BlockPos obstruction = CENTER.above(2);
      FarmBreakToolRequirement frozenToolFact = new FarmBreakToolRequirement(
         FarmBreakToolRequirement.Kind.STANDARD,
         FarmBreakToolRequirement.Family.PICKAXE,
         FarmBreakToolRequirement.StandardTier.DIAMOND,
         Blocks.OBSIDIAN.defaultBlockState()
      );
      world.set(obstruction, world.cellWithState(FarmSiteWorldView.BlockKind.OTHER, Blocks.OBSIDIAN.defaultBlockState(), frozenToolFact));
      FarmPlotRepairPlan plan = ready(world);
      require(
         plan.clearActions().stream().anyMatch(action -> action.target().equals(obstruction) && action.breakToolRequirement() == frozenToolFact),
         "the clear action carries the tool fact captured by its exact scan read"
      );
      require(
         plan.toolPlan().standardRequirements().size() == 1
            && plan.toolPlan().standardRequirements().get(0).family() == FarmBreakToolRequirement.Family.PICKAXE,
         "the immutable repair manifest exposes its aggregated pickaxe preflight"
      );
   }

   private static void returnStancesStayOnSafeNoncenterSoil() {
      List<BlockPos> geometric = FarmPlotRepairPlan.farmLevelReturnStanceCandidates(CENTER);
      require(geometric.size() == 80 && new HashSet<>(geometric).size() == 80, "return geometry contains every noncenter soil stance exactly once");
      require(
         geometric.stream().allMatch(feet -> FarmPlotRepairPlan.isFarmLevelReturnStance(CENTER, feet)),
         "every return stance is feet one above current farm surface"
      );
      require(!geometric.contains(CENTER.above()), "center-hole stance is never generated");
      require(geometric.equals(FarmPlotRepairPlan.farmLevelReturnStanceCandidates(CENTER)), "return stance order is deterministic");
      FarmPlotRepairPlan flat = ready(FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY()));
      require(flat.returnStanceCandidates().size() == 80, "flat farm exposes all eighty live safe return stances");
      FarmPlotRepairPlanSelfTest.FakeWorldView plantedSoil = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());

      for (BlockPos soil : FarmPlotGeometry.soilCells(CENTER)) {
         plantedSoil.set(soil, plantedSoil.cell(FarmSiteWorldView.BlockKind.FARMLAND));
      }

      require(ready(plantedSoil).returnStanceCandidates().size() == 80, "farmland's lowered collision top remains a valid farm-level stance support");
      FarmPlotRepairPlanSelfTest.FakeWorldView groundCover = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());
      groundCover.set(CENTER, groundCover.cell(FarmSiteWorldView.BlockKind.AIR));

      for (BlockPos surface : FarmPlotGeometry.allCells(CENTER)) {
         groundCover.set(surface.above(), groundCover.cell(FarmSiteWorldView.BlockKind.REPLACEABLE_VEGETATION));
      }

      FarmPlotRepairPlan groundCoverPlan = ready(groundCover);
      require(
         groundCoverPlan.returnStanceCandidates().size() == 80 && groundCoverPlan.clearActions().size() == 81,
         "collision-empty ground cover remains standable and is still cleared"
      );
      FarmPlotRepairPlanSelfTest.FakeWorldView tallCover = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());
      tallCover.set(CENTER, tallCover.cell(FarmSiteWorldView.BlockKind.AIR));

      for (BlockPos surface : FarmPlotGeometry.allCells(CENTER)) {
         tallCover.set(surface.above(), tallCover.cell(FarmSiteWorldView.BlockKind.REPLACEABLE_VEGETATION));
         tallCover.set(surface.above(2), tallCover.cell(FarmSiteWorldView.BlockKind.REPLACEABLE_VEGETATION));
      }

      FarmPlotRepairPlan tallCoverPlan = ready(tallCover);
      require(
         tallCoverPlan.returnStanceCandidates().size() == 80 && tallCoverPlan.clearActions().size() == 162,
         "collision-empty two-block vegetation remains standable and clears top-down"
      );
      BlockPos protectedSurface = CENTER.offset(-4, 0, -4);
      FarmPlotRepairPlanSelfTest.FakeWorldView protectedTarget = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());
      protectedTarget.set(protectedSurface, protectedTarget.withProtected(protectedTarget.cell(FarmSiteWorldView.BlockKind.DIRT)));
      FarmPlotRepairPlan protectedPlan = ready(protectedTarget);
      require(
         !protectedPlan.returnStanceCandidates().contains(protectedSurface.above()),
         "player-protected surface is accepted but never selected as stance support"
      );
      BlockPos protectedFeet = CENTER.offset(-3, 1, -4);
      FarmPlotRepairPlanSelfTest.FakeWorldView protectedOccupancy = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());
      protectedOccupancy.set(protectedFeet, protectedOccupancy.withProtected(protectedOccupancy.cell(FarmSiteWorldView.BlockKind.AIR)));
      protectedOccupancy.set(protectedFeet.above(), protectedOccupancy.withProtected(protectedOccupancy.cell(FarmSiteWorldView.BlockKind.AIR)));
      FarmPlotRepairPlan protectedOccupancyPlan = ready(protectedOccupancy);
      require(!protectedOccupancyPlan.returnStanceCandidates().contains(protectedFeet), "player-protected feet/head observations are never selected as stances");
      FarmPlotRepairPlanSelfTest.FakeWorldView protectedHeadroomTarget = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());
      BlockPos obstruction = CENTER.offset(1, 3, 1);
      protectedHeadroomTarget.set(obstruction, protectedHeadroomTarget.withProtected(protectedHeadroomTarget.cell(FarmSiteWorldView.BlockKind.OTHER)));
      assertUnsafe(protectedHeadroomTarget, FarmPlotRepairPlan.UnsafeReason.UNSAFE_OBSTRUCTION, "player-placed headroom blocks are protected");
      FarmPlotRepairPlanSelfTest.FakeWorldView noStance = FarmPlotRepairPlanSelfTest.FakeWorldView.flat(CENTER.getY());

      for (BlockPos soil : FarmPlotGeometry.soilCells(CENTER)) {
         noStance.set(soil, noStance.withProtected(noStance.cell(FarmSiteWorldView.BlockKind.GRASS_BLOCK)));
      }

      assertUnsafe(noStance, FarmPlotRepairPlan.UnsafeReason.NO_RETURN_STANCE, "farm with no unprotected return support");
   }

   private static FarmPlotRepairPlan ready(FarmPlotRepairPlanSelfTest.FakeWorldView view) {
      FarmPlotRepairPlan.ScanResult result = FarmPlotRepairPlan.scan(view, CENTER);
      require(result.ready(), "expected ready repair plan but got " + result.reason());
      return result.plan();
   }

   private static boolean protectedCropAt(FarmPlotRepairPlanSelfTest.FakeWorldView view, BlockPos position) {
      FarmSiteWorldView.CellObservation facts = view.observe(position);
      return facts.playerProtected() && facts.blockKind() == FarmSiteWorldView.BlockKind.CROP && facts.collisionEmpty();
   }

   private static void assertUnsafe(FarmPlotRepairPlanSelfTest.FakeWorldView view, FarmPlotRepairPlan.UnsafeReason expected, String label) {
      FarmPlotRepairPlan.ScanResult result = FarmPlotRepairPlan.scan(view, CENTER);
      require(!result.ready() && result.reason() == expected, label + " rejected as " + expected + " but got " + result.reason());
      require(result.plan() == null && result.reads() == 405, label + " returns no plan after one bounded scan");
   }

   private static void require(boolean condition, String message) {
      if (!condition) {
         throw new AssertionError(message);
      }
   }

   private static final class FakeWorldView implements FarmSiteWorldView {
      private final Thread serverThread = Thread.currentThread();
      private final int surfaceY;
      private final Map<BlockPos, FarmSiteWorldView.CellObservation> overrides = new HashMap<>();
      private int reads;

      private FakeWorldView(int surfaceY) {
         this.surfaceY = surfaceY;
      }

      private static FarmPlotRepairPlanSelfTest.FakeWorldView flat(int surfaceY) {
         return new FarmPlotRepairPlanSelfTest.FakeWorldView(surfaceY);
      }

      @Override
      public void assertServerThread() {
         if (Thread.currentThread() != this.serverThread) {
            throw new IllegalStateException("fake repair world read off server thread");
         }
      }

      @Override
      public FarmSiteWorldView.CellObservation observe(BlockPos position) {
         this.assertServerThread();
         this.reads++;
         FarmSiteWorldView.CellObservation overridden = this.overrides.get(position);
         if (overridden != null) {
            return overridden;
         } else if (position.getY() == this.surfaceY - 1) {
            return this.cell(FarmSiteWorldView.BlockKind.STONE);
         } else {
            return position.getY() == this.surfaceY ? this.cell(FarmSiteWorldView.BlockKind.GRASS_BLOCK) : this.cell(FarmSiteWorldView.BlockKind.AIR);
         }
      }

      @Override
      public FarmSiteWorldView.WorldObservation observeWorld(BlockPos center) {
         throw new AssertionError("task-local repair scan must not read world-wide waypoint facts");
      }

      private void set(BlockPos position, FarmSiteWorldView.CellObservation observation) {
         this.overrides.put(position.immutable(), observation);
      }

      private FarmSiteWorldView.CellObservation cell(FarmSiteWorldView.BlockKind kind) {
         FarmSiteWorldView.FluidKind fluid = kind == FarmSiteWorldView.BlockKind.STANDALONE_WATER
            ? FarmSiteWorldView.FluidKind.STANDALONE_WATER_SOURCE
            : FarmSiteWorldView.FluidKind.NONE;
         boolean replaceable = kind == FarmSiteWorldView.BlockKind.AIR || kind == FarmSiteWorldView.BlockKind.REPLACEABLE_VEGETATION;
         boolean emptyCollision = kind == FarmSiteWorldView.BlockKind.AIR
            || kind == FarmSiteWorldView.BlockKind.REPLACEABLE_VEGETATION
            || kind == FarmSiteWorldView.BlockKind.CROP;
         boolean support = kind == FarmSiteWorldView.BlockKind.GRASS_BLOCK
            || kind == FarmSiteWorldView.BlockKind.DIRT
            || kind == FarmSiteWorldView.BlockKind.STONE;
         return new FarmSiteWorldView.CellObservation(
            true,
            true,
            true,
            kind,
            fluid,
            replaceable,
            emptyCollision,
            kind == FarmSiteWorldView.BlockKind.CROP,
            false,
            false,
            false,
            support,
            kind != FarmSiteWorldView.BlockKind.STANDALONE_WATER,
            false,
            true,
            15,
            "fixture:" + kind.name().toLowerCase()
         );
      }

      private FarmSiteWorldView.CellObservation cellWithState(FarmSiteWorldView.BlockKind kind, BlockState state, FarmBreakToolRequirement breakToolRequirement) {
         FarmSiteWorldView.CellObservation base = this.cell(kind);
         return new FarmSiteWorldView.CellObservation(
            base.loaded(),
            base.inBuildHeight(),
            base.withinWorldBorder(),
            base.blockKind(),
            base.fluidKind(),
            base.replaceable(),
            base.collisionEmpty(),
            base.recognizedCrop(),
            base.blockEntity(),
            base.fallingHazard(),
            base.unbreakable(),
            base.solidTopSupport(),
            base.nonWaterloggable(),
            base.playerProtected(),
            base.warmEnoughToRain(),
            base.blockLight(),
            FarmSiteWorldView.stateFingerprint(state),
            breakToolRequirement
         );
      }

      private FarmSiteWorldView.CellObservation withCollisionEmpty(FarmSiteWorldView.CellObservation source, boolean collisionEmpty) {
         return new FarmSiteWorldView.CellObservation(
            source.loaded(),
            source.inBuildHeight(),
            source.withinWorldBorder(),
            source.blockKind(),
            source.fluidKind(),
            source.replaceable(),
            collisionEmpty,
            source.recognizedCrop(),
            source.blockEntity(),
            source.fallingHazard(),
            source.unbreakable(),
            source.solidTopSupport(),
            source.nonWaterloggable(),
            source.playerProtected(),
            source.warmEnoughToRain(),
            source.blockLight(),
            source.stateFingerprint(),
            source.breakToolRequirement()
         );
      }

      private FarmSiteWorldView.CellObservation withProtected(FarmSiteWorldView.CellObservation source) {
         return this.copy(source, source.fluidKind(), source.blockEntity(), source.fallingHazard(), source.unbreakable(), true);
      }

      private FarmSiteWorldView.CellObservation withBlockEntity(FarmSiteWorldView.CellObservation source) {
         return this.copy(source, source.fluidKind(), true, source.fallingHazard(), source.unbreakable(), source.playerProtected());
      }

      private FarmSiteWorldView.CellObservation withFalling(FarmSiteWorldView.CellObservation source) {
         return this.copy(source, source.fluidKind(), source.blockEntity(), true, source.unbreakable(), source.playerProtected());
      }

      private FarmSiteWorldView.CellObservation withUnbreakable(FarmSiteWorldView.CellObservation source) {
         return this.copy(source, source.fluidKind(), source.blockEntity(), source.fallingHazard(), true, source.playerProtected());
      }

      private FarmSiteWorldView.CellObservation withFluid(FarmSiteWorldView.CellObservation source, FarmSiteWorldView.FluidKind fluid) {
         return this.copy(source, fluid, source.blockEntity(), source.fallingHazard(), source.unbreakable(), source.playerProtected());
      }

      private FarmSiteWorldView.CellObservation copy(
         FarmSiteWorldView.CellObservation source,
         FarmSiteWorldView.FluidKind fluid,
         boolean blockEntity,
         boolean falling,
         boolean unbreakable,
         boolean protectedByPlayer
      ) {
         return new FarmSiteWorldView.CellObservation(
            source.loaded(),
            source.inBuildHeight(),
            source.withinWorldBorder(),
            source.blockKind(),
            fluid,
            source.replaceable(),
            source.collisionEmpty(),
            source.recognizedCrop(),
            blockEntity,
            falling,
            unbreakable,
            source.solidTopSupport(),
            source.nonWaterloggable(),
            protectedByPlayer,
            source.warmEnoughToRain(),
            source.blockLight(),
            source.stateFingerprint()
         );
      }
   }
}
