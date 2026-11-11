package com.player2.playerengine.tasks.farming;

import com.player2.playerengine.agentic.elliegps.FarmCropCount;
import com.player2.playerengine.agentic.elliegps.FarmObservationResult;
import com.player2.playerengine.agentic.elliegps.FarmObservationStatus;
import com.player2.playerengine.agentic.elliegps.FarmWaypointData;
import com.player2.playerengine.agentic.elliegps.FarmWaypointObservation;
import com.player2.playerengine.agentic.elliegps.WaypointRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.Map.Entry;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.core.registries.BuiltInRegistries;

public final class FarmCropScanner {
   private FarmCropScanner() {
   }

   public static FarmObservationResult observe(ServerLevel level, WaypointRecord farm) {
      return scan(level, farm).observationResult();
   }

   public static FarmCropScanner.ScanResult scan(ServerLevel level, WaypointRecord farm) {
      return scan(level, farm, FarmActionStateSource.LIVE);
   }

   public static FarmObservationResult observePrepared(ServerLevel level, BlockPos center) {
      return observePrepared(level, center, FarmActionStateSource.LIVE);
   }

   static FarmObservationResult observePrepared(ServerLevel level, BlockPos center, FarmActionStateSource stateSource) {
      if (level == null) {
         return failure(FarmObservationStatus.HANDLER_UNAVAILABLE, "farm world is unavailable").observationResult();
      } else if (center == null) {
         return failure(FarmObservationStatus.UNSUPPORTED_DATA, "farm center is invalid").observationResult();
      } else if (stateSource == null) {
         return failure(FarmObservationStatus.HANDLER_UNAVAILABLE, "farm state source is unavailable").observationResult();
      } else {
         return !footprintLoaded(level, center)
            ? failure(FarmObservationStatus.UNLOADED, "farm footprint is not fully loaded").observationResult()
            : scanLoadedFootprint(level, center, stateSource).observationResult();
      }
   }

   public static FarmCropScanner.ScanResult scan(ServerLevel level, WaypointRecord farm, FarmActionStateSource stateSource) {
      if (level == null) {
         return failure(FarmObservationStatus.HANDLER_UNAVAILABLE, "farm world is unavailable");
      }

      if (stateSource == null) {
         return failure(FarmObservationStatus.HANDLER_UNAVAILABLE, "farm state source is unavailable");
      }

      if (farm != null && farm.farmData() != null && farm.farmData().isSupportedVersion()) {
         FarmWaypointData data = farm.farmData();
         if (data.radius() != 4) {
            return failure(FarmObservationStatus.UNSUPPORTED_DATA, "farm radius is not supported");
         } else {
            BlockPos center = farm.canonicalBlockPos();
            if (center == null) {
               return failure(FarmObservationStatus.UNSUPPORTED_DATA, "farm center is invalid");
            } else {
               String dimension = level.dimension().location().toString();
               if (!Objects.equals(dimension, farm.dimension)) {
                  return failure(FarmObservationStatus.UNLOADED, "farm is in a different dimension");
               } else if (!footprintLoaded(level, center)) {
                  return failure(FarmObservationStatus.UNLOADED, "farm footprint is not fully loaded");
               } else {
                  return !FarmInteractionPostconditions.isStandaloneWaterSource(level.getBlockState(center))
                     ? failure(FarmObservationStatus.STALE_CENTER, "farm center is no longer a water source")
                     : scanLoadedFootprint(level, center, stateSource);
               }
            }
         }
      } else {
         return failure(FarmObservationStatus.UNSUPPORTED_DATA, "farm waypoint data is unsupported");
      }
   }

   private static boolean footprintLoaded(ServerLevel level, BlockPos center) {
      for (BlockPos cell : FarmPlotGeometry.allCells(center)) {
         if (!level.hasChunkAt(cell) || !level.hasChunkAt(cell.above())) {
            return false;
         }
      }

      return true;
   }

   private static FarmCropScanner.ScanResult scanLoadedFootprint(ServerLevel level, BlockPos center, FarmActionStateSource stateSource) {
      String dimension = level.dimension().location().toString();
      int farmland = 0;
      ArrayList<FarmCropScanner.ObservedCropState> observedCropStates = new ArrayList<>(80);
      ArrayList<BlockPos> openCells = new ArrayList<>(80);

      for (BlockPos soil : FarmPlotGeometry.soilCells(center)) {
         BlockState soilState = level.getBlockState(soil);
         BlockPos cropPosition = soil.above().immutable();
         BlockState cropState = Objects.requireNonNull(stateSource.read(level, cropPosition), "farm state source returned null");
         if (soilState.is(Blocks.FARMLAND)) {
            farmland++;
            if (isOpenCell(soilState, cropState)) {
               openCells.add(cropPosition);
            }
         }

         observedCropStates.add(new FarmCropScanner.ObservedCropState(cropPosition, cropState));
      }

      List<FarmCropScanner.CropCell> cropCells = enumerateRecognizedStates(level, observedCropStates, HarvestBehaviorRegistry.DEFAULT);
      List<FarmCropCount> crops = aggregateCounts(cropCells);
      FarmWaypointObservation observation = new FarmWaypointObservation(dimension, center, 4, farmland, openCells.size(), crops, level.getGameTime());
      return new FarmCropScanner.ScanResult(new FarmObservationResult(FarmObservationStatus.OBSERVED, observation, ""), cropCells, openCells);
   }

   static List<FarmCropCount> aggregateCounts(List<FarmCropScanner.CropCell> cropCells) {
      Objects.requireNonNull(cropCells, "cropCells");
      TreeMap<FarmCropScanner.CropKey, Integer> counts = new TreeMap<>();

      for (FarmCropScanner.CropCell cropCell : cropCells) {
         Objects.requireNonNull(cropCell, "cropCell");
         counts.merge(new FarmCropScanner.CropKey(cropCell.blockId(), cropCell.plantingItemId()), 1, Math::addExact);
      }

      ArrayList<FarmCropCount> crops = new ArrayList<>(counts.size());

      for (Entry<FarmCropScanner.CropKey, Integer> entry : counts.entrySet()) {
         crops.add(new FarmCropCount(entry.getKey().blockId(), entry.getKey().plantingItemId(), entry.getValue()));
      }

      return List.copyOf(crops);
   }

   static List<FarmCropScanner.CropCell> enumerateRecognized(
      LevelReader level, Iterable<BlockPos> cropPositions, FarmActionStateSource stateSource, HarvestBehaviorRegistry registry
   ) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(cropPositions, "cropPositions");
      Objects.requireNonNull(stateSource, "stateSource");
      Objects.requireNonNull(registry, "registry");
      ArrayList<FarmCropScanner.ObservedCropState> observed = new ArrayList<>();

      for (BlockPos position : cropPositions) {
         BlockPos cropPos = Objects.requireNonNull(position, "crop position").immutable();
         BlockState cropState = Objects.requireNonNull(stateSource.read(level, cropPos), "farm state source returned null");
         observed.add(new FarmCropScanner.ObservedCropState(cropPos, cropState));
      }

      return enumerateRecognizedStates(level, observed, registry);
   }

   private static List<FarmCropScanner.CropCell> enumerateRecognizedStates(
      LevelReader level, Iterable<FarmCropScanner.ObservedCropState> observedStates, HarvestBehaviorRegistry registry
   ) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(observedStates, "observedStates");
      Objects.requireNonNull(registry, "registry");
      ArrayList<FarmCropScanner.CropCell> cells = new ArrayList<>();
      ResourceLocation airId = BuiltInRegistries.BLOCK.getKey(Blocks.AIR);

      for (FarmCropScanner.ObservedCropState observed : observedStates) {
         BlockPos cropPos = observed.position();
         BlockState cropState = observed.state();
         HarvestBehavior behavior = registry.resolve(cropState).orElse(null);
         if (behavior != null) {
            ResourceLocation blockId = behavior.canonicalCropId(cropState).orElse(null);
            if (blockId != null && !blockId.equals(airId)) {
               String plantingItemId = behavior.plantingItemId(level, cropPos, cropState).<String>map(ResourceLocation::toString).orElse(null);
               cells.add(new FarmCropScanner.CropCell(cropPos, blockId.toString(), plantingItemId, behavior.isMature(cropState)));
            }
         }
      }

      return List.copyOf(cells);
   }

   static boolean isOpenCell(BlockState soilState, BlockState cropState) {
      return Objects.requireNonNull(soilState, "soilState").is(Blocks.FARMLAND)
         && Objects.requireNonNull(cropState, "cropState").isAir();
   }

   static boolean isFullyGrown(CropBlock crop, BlockState state) {
      return Objects.requireNonNull(crop, "crop").isMaxAge(Objects.requireNonNull(state, "state"));
   }

   private static FarmCropScanner.ScanResult failure(FarmObservationStatus status, String reason) {
      return new FarmCropScanner.ScanResult(new FarmObservationResult(status, null, reason), List.of(), List.of());
   }

   public record CropCell(BlockPos position, String blockId, String plantingItemId, boolean fullyGrown) {
      public CropCell {
         position = Objects.requireNonNull(position, "position").immutable();
         blockId = Objects.requireNonNull(blockId, "blockId");
      }
   }

   private record CropKey(String blockId, String plantingItemId) implements Comparable<FarmCropScanner.CropKey> {
      public int compareTo(FarmCropScanner.CropKey other) {
         int blockOrder = this.blockId.compareTo(other.blockId);
         return blockOrder != 0 ? blockOrder : Objects.toString(this.plantingItemId, "").compareTo(Objects.toString(other.plantingItemId, ""));
      }
   }

   private record ObservedCropState(BlockPos position, BlockState state) {
      private ObservedCropState {
         position = Objects.requireNonNull(position, "position").immutable();
         state = Objects.requireNonNull(state, "state");
      }
   }

   public record ScanResult(FarmObservationResult observationResult, List<FarmCropScanner.CropCell> cropCells, List<BlockPos> openCells) {
      public ScanResult {
         observationResult = Objects.requireNonNull(observationResult, "observationResult");
         cropCells = List.copyOf(Objects.requireNonNull(cropCells, "cropCells"));
         openCells = Objects.requireNonNull(openCells, "openCells")
            .stream()
            .map(position -> Objects.requireNonNull(position, "open cell").immutable())
            .toList();
      }

      public int matureCount() {
         return (int)this.cropCells.stream().filter(FarmCropScanner.CropCell::fullyGrown).count();
      }

      public int recognizedCount() {
         return this.cropCells.size();
      }

      public int openCount() {
         return this.openCells.size();
      }

      public List<BlockPos> matureCandidates() {
         return this.cropCells.stream().filter(FarmCropScanner.CropCell::fullyGrown).map(FarmCropScanner.CropCell::position).toList();
      }
   }
}
