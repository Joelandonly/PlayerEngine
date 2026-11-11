package com.player2.playerengine.tasks.farming;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.agentic.elliegps.EllieGPSStore;
import com.player2.playerengine.commands.BlockScanner;
import com.player2.playerengine.structureprotection.PlayerPlacedBlockStore;
import com.player2.playerengine.tasks.agentic.MarkedChestItemLocator;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;

public final class PlantableSourcePlanner {
   public static final int MAX_MARKED_CHESTS = 32;
   public static final int MAX_SCANNER_VISITS = 512;
   public static final int MAX_NATURAL_GRASS = 512;
   public static final int MAX_VILLAGE_CROPS = 512;
   public static final int MAX_VILLAGE_CHESTS = 512;
   public static final int MAX_SCANNER_CYCLE_VISITS = 8192;
   public static final int MAX_RECOGNIZED_BLOCK_TYPES = 4096;
   public static final int MAX_STRUCTURE_REFERENCE_TYPES = 256;
   public static final int MAX_STRUCTURE_REFERENCES = 512;
   public static final double WORLD_SEARCH_RADIUS = 115.0;

   private PlantableSourcePlanner() {
   }

   public static PlantableSourcePlanner.MarkedManifest freezeMarked(
      PlayerEngineController controller, Item plantingItem, BlockPos origin, String dimensionId
   ) {
      Objects.requireNonNull(controller, "controller");
      Objects.requireNonNull(plantingItem, "plantingItem");
      BlockPos frozenOrigin = Objects.requireNonNull(origin, "origin").immutable();
      if (dimensionId != null && !dimensionId.isBlank()) {
         Vec3 originVec = Vec3.atCenterOf(frozenOrigin);
         return new PlantableSourcePlanner.MarkedManifest(
            capped(MarkedChestItemLocator.candidateCoordinates(controller, plantingItem, originVec, 115.0, dimensionId), 32)
         );
      } else {
         throw new IllegalArgumentException("dimensionId cannot be blank");
      }
   }

   public static PlantableSourcePlanner.WorldRoundManifest freezeWorldRound(
      PlayerEngineController controller,
      Item plantingItem,
      BlockPos scanOrigin,
      String dimensionId,
      Set<BlockPos> attemptedNaturalGrass,
      Set<BlockPos> attemptedVillageCrops,
      Set<BlockPos> attemptedVillageChests,
      int naturalScanCursor,
      int cropScanCursor,
      int villageChestScanCursor
   ) {
      Objects.requireNonNull(controller, "controller");
      Objects.requireNonNull(plantingItem, "plantingItem");
      BlockPos frozenScanOrigin = Objects.requireNonNull(scanOrigin, "scanOrigin").immutable();
      if (dimensionId != null && !dimensionId.isBlank()) {
         Set<BlockPos> attemptedGrass = immutablePositionSet(attemptedNaturalGrass);
         Set<BlockPos> attemptedCrops = immutablePositionSet(attemptedVillageCrops);
         Set<BlockPos> attemptedChests = immutablePositionSet(attemptedVillageChests);
         if (naturalScanCursor >= 0 && cropScanCursor >= 0 && villageChestScanCursor >= 0) {
            RegisteredFarmExclusionSnapshot farms = RegisteredFarmExclusionSnapshot.capture(dimensionId);
            PlayerPlacedBlockStore playerPlaced = PlayerPlacedBlockStore.get();
            MarkedChestItemLocator.MarkedPositionSnapshot markedPositions = MarkedChestItemLocator.markedPositions(dimensionId);
            boolean guardsAvailable = farms.available() && playerPlaced != null && markedPositions.available();
            if (!guardsAvailable) {
               return new PlantableSourcePlanner.WorldRoundManifest(
                  frozenScanOrigin, List.of(), List.of(), List.of(), false, false, naturalScanCursor, cropScanCursor, villageChestScanCursor
               );
            }

            ServerLevel level = controller.getWorld();
            BlockScanner scanner = controller.getBlockScanner();
            PlantableSourcePlanner.BreakWindow grass = plantingItem == Items.WHEAT_SEEDS
               ? naturalGrass(level, scanner, playerPlaced, farms, frozenScanOrigin, attemptedGrass, naturalScanCursor)
               : new PlantableSourcePlanner.BreakWindow(List.of(), naturalScanCursor);
            PlantableSourcePlanner.RecognizedBlocks recognized = recognizedBlocks();
            PlantableSourcePlanner.BreakWindow crops = villageCrops(
               level, scanner, plantingItem, recognized.blocks(), playerPlaced, farms, frozenScanOrigin, attemptedCrops, cropScanCursor
            );
            PlantableSourcePlanner.ChestWindow chests = villageChests(
               level, scanner, playerPlaced, farms, markedPositions.positions(), frozenScanOrigin, attemptedChests, villageChestScanCursor
            );
            return new PlantableSourcePlanner.WorldRoundManifest(
               frozenScanOrigin,
               grass.candidates(),
               crops.candidates(),
               chests.candidates(),
               true,
               recognized.truncated(),
               grass.nextCursor(),
               crops.nextCursor(),
               chests.nextCursor()
            );
         } else {
            throw new IllegalArgumentException("scanner cursors cannot be negative");
         }
      } else {
         throw new IllegalArgumentException("dimensionId cannot be blank");
      }
   }

   public static List<PlantableSourcePlanner.SourceKind> sourceOrder(boolean wheatSeeds) {
      return wheatSeeds
         ? List.of(
            PlantableSourcePlanner.SourceKind.MARKED_CHEST,
            PlantableSourcePlanner.SourceKind.NATURAL_GRASS,
            PlantableSourcePlanner.SourceKind.VILLAGE_CROP,
            PlantableSourcePlanner.SourceKind.VILLAGE_CHEST
         )
         : List.of(
            PlantableSourcePlanner.SourceKind.MARKED_CHEST, PlantableSourcePlanner.SourceKind.VILLAGE_CROP, PlantableSourcePlanner.SourceKind.VILLAGE_CHEST
         );
   }

   public static boolean isInsideVillageStructure(ServerLevel level, BlockPos position) {
      if (level != null && position != null) {
         try {
            return !allReferencedStructureStartsLoaded(level, position)
               ? false
               : level.structureManager().getStructureWithPieceAt(position, StructureTags.VILLAGE).isValid();
         } catch (RuntimeException structureFailure) {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean allReferencedStructureStartsLoaded(ServerLevel level, BlockPos position) {
      ChunkPos targetChunk = new ChunkPos(position);
      LevelChunk loadedTarget = level.getChunkSource().getChunkNow(targetChunk.x, targetChunk.z);
      if (loadedTarget == null) {
         return false;
      }

      Map<Structure, LongSet> referenceMap = loadedTarget.getAllReferences();
      int referenceTypeCount = referenceMap.size();
      if (!loadedStructureLookupPermitted(referenceTypeCount, 0, true)) {
         return false;
      }

      Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
      int villageReferenceCount = 0;

      for (Entry<Structure, LongSet> entry : referenceMap.entrySet()) {
         Structure structure = entry.getKey();
         boolean villageStructure = structureRegistry.getHolder(structureRegistry.getId(structure))
            .map(holder -> holder.is(StructureTags.VILLAGE))
            .orElse(false);
         if (villageStructure) {
            LongIterator iterator = entry.getValue().iterator();

            while (iterator.hasNext()) {
               if (!loadedStructureLookupPermitted(referenceTypeCount, ++villageReferenceCount, true)) {
                  return false;
               }

               ChunkPos startChunk = new ChunkPos(iterator.nextLong());
               if (level.getChunkSource().getChunkNow(startChunk.x, startChunk.z) == null) {
                  return false;
               }
            }
         }
      }

      return loadedStructureLookupPermitted(referenceTypeCount, villageReferenceCount, true);
   }

   static boolean loadedStructureLookupPermitted(int referenceTypeCount, int villageReferenceCount, boolean allReferencedStartsLoaded) {
      return allReferencedStartsLoaded && referenceTypeCount >= 0 && referenceTypeCount <= 256 && villageReferenceCount >= 0 && villageReferenceCount <= 512;
   }

   public static boolean worldGuardsAvailable(String dimensionId) {
      return PlayerPlacedBlockStore.get() != null
         && RegisteredFarmExclusionSnapshot.capture(dimensionId).available()
         && MarkedChestItemLocator.markedPositions(dimensionId).available();
   }

   public static boolean isLiveBreakCandidate(ServerLevel level, Item plantingItem, String dimensionId, PlantableSourcePlanner.BreakCandidate candidate) {
      if (level != null
         && plantingItem != null
         && candidate != null
         && level.hasChunkAt(candidate.position())
         && level.getBlockState(candidate.position()).equals(candidate.expectedState())) {
         PlayerPlacedBlockStore playerPlaced = PlayerPlacedBlockStore.get();
         RegisteredFarmExclusionSnapshot farms = RegisteredFarmExclusionSnapshot.capture(dimensionId);
         if (protectionDenied(playerPlaced, farms, dimensionId, candidate.position(), candidate.position().below())) {
            return false;
         } else if (candidate.kind() == PlantableSourcePlanner.SourceKind.NATURAL_GRASS) {
            BlockState state = candidate.expectedState();
            return plantingItem == Items.WHEAT_SEEDS && (state.is(Blocks.SHORT_GRASS) || isLowerTallGrass(state));
         } else {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(plantingItem);
            return itemId != null
               && isInsideVillageStructure(level, candidate.position())
               && matchesPlantingItem(level, candidate.position(), candidate.expectedState(), itemId);
         }
      } else {
         return false;
      }
   }

   public static boolean isLiveVillageChestCandidate(ServerLevel level, String dimensionId, PlantableSourcePlanner.ChestCandidate candidate) {
      if (level != null
         && candidate != null
         && level.hasChunkAt(candidate.position())
         && level.getBlockState(candidate.position()).is(Blocks.CHEST)
         && (
            candidate.secondaryPosition() == null
               || level.hasChunkAt(candidate.secondaryPosition()) && level.getBlockState(candidate.secondaryPosition()).is(Blocks.CHEST)
         )) {
         PlayerPlacedBlockStore playerPlaced = PlayerPlacedBlockStore.get();
         RegisteredFarmExclusionSnapshot farms = RegisteredFarmExclusionSnapshot.capture(dimensionId);
         EllieGPSStore store = EllieGPSStore.get();
         return store != null
            && !store.hasTypeAtPosition("inventory", dimensionId, candidate.position())
            && (candidate.secondaryPosition() == null || !store.hasTypeAtPosition("inventory", dimensionId, candidate.secondaryPosition()))
            && !protectionDenied(playerPlaced, farms, dimensionId, candidate.position(), candidate.secondaryPosition())
            && isInsideVillageStructure(level, candidate.position())
            && (candidate.secondaryPosition() == null || isInsideVillageStructure(level, candidate.secondaryPosition()));
      } else {
         return false;
      }
   }

   static boolean protectionDenied(
      PlayerPlacedBlockStore playerPlaced, RegisteredFarmExclusionSnapshot farms, String dimensionId, BlockPos primary, BlockPos secondary
   ) {
      return playerPlaced != null && farms != null && farms.available()
         ? playerPlaced.contains(dimensionId, primary)
            || secondary != null && playerPlaced.contains(dimensionId, secondary)
            || farms.contains(primary)
            || secondary != null && farms.contains(secondary)
         : true;
   }

   static boolean matchesPlantingItem(ServerLevel level, BlockPos position, BlockState state, ResourceLocation targetItemId) {
      HarvestBehavior behavior = HarvestBehaviorRegistry.DEFAULT.resolve(state).orElse(null);
      return behavior != null && behavior.isMature(state) && behavior.plantingItemId(level, position, state).<Boolean>map(targetItemId::equals).orElse(false);
   }

   private static PlantableSourcePlanner.BreakWindow naturalGrass(
      ServerLevel level,
      BlockScanner scanner,
      PlayerPlacedBlockStore playerPlaced,
      RegisteredFarmExclusionSnapshot farms,
      BlockPos origin,
      Set<BlockPos> attemptedPositions,
      int scanCursor
   ) {
      ArrayList<PlantableSourcePlanner.BreakCandidate> candidates = new ArrayList<>();
      HashSet<BlockPos> seen = new HashSet<>();
      String dimensionId = level.dimension().location().toString();
      BlockScanner.KnownLocationWindow window = scanner.getKnownLocationsWindow(scanCursor, 512, 8192, naturalGrassBlocks());

      for (BlockPos raw : window.locations()) {
         BlockPos position = canonicalGrassPosition(level, raw);
         if (position != null && !attemptedPositions.contains(position) && seen.add(position) && withinRadius(origin, position)) {
            BlockState state = level.getBlockState(position);
            if ((state.is(Blocks.SHORT_GRASS) || isLowerTallGrass(state))
               && !protectionDenied(playerPlaced, farms, dimensionId, position, position.below())) {
               candidates.add(new PlantableSourcePlanner.BreakCandidate(PlantableSourcePlanner.SourceKind.NATURAL_GRASS, position, state));
            }
         }
      }

      candidates.sort(breakComparator(origin));
      return new PlantableSourcePlanner.BreakWindow(capped(candidates, 512), window.nextCursor());
   }

   private static PlantableSourcePlanner.BreakWindow villageCrops(
      ServerLevel level,
      BlockScanner scanner,
      Item plantingItem,
      List<Block> recognizedBlocks,
      PlayerPlacedBlockStore playerPlaced,
      RegisteredFarmExclusionSnapshot farms,
      BlockPos origin,
      Set<BlockPos> attemptedPositions,
      int scanCursor
   ) {
      if (recognizedBlocks.isEmpty()) {
         return new PlantableSourcePlanner.BreakWindow(List.of(), scanCursor);
      }

      ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(plantingItem);
      if (itemId == null) {
         return new PlantableSourcePlanner.BreakWindow(List.of(), scanCursor);
      }

      String dimensionId = level.dimension().location().toString();
      ArrayList<PlantableSourcePlanner.BreakCandidate> candidates = new ArrayList<>();
      HashSet<BlockPos> seen = new HashSet<>();
      Block[] blocks = recognizedBlocks.toArray(Block[]::new);
      BlockScanner.KnownLocationWindow window = scanner.getKnownLocationsWindow(scanCursor, 512, 8192, blocks);

      for (BlockPos raw : window.locations()) {
         BlockPos position = raw.immutable();
         if (!attemptedPositions.contains(position)
            && seen.add(position)
            && withinRadius(origin, position)
            && level.hasChunkAt(position)
            && isInsideVillageStructure(level, position)
            && !protectionDenied(playerPlaced, farms, dimensionId, position, position.below())) {
            BlockState state = level.getBlockState(position);
            if (matchesPlantingItem(level, position, state, itemId)) {
               candidates.add(new PlantableSourcePlanner.BreakCandidate(PlantableSourcePlanner.SourceKind.VILLAGE_CROP, position, state));
            }
         }
      }

      candidates.sort(breakComparator(origin));
      return new PlantableSourcePlanner.BreakWindow(capped(candidates, 512), window.nextCursor());
   }

   private static PlantableSourcePlanner.ChestWindow villageChests(
      ServerLevel level,
      BlockScanner scanner,
      PlayerPlacedBlockStore playerPlaced,
      RegisteredFarmExclusionSnapshot farms,
      Set<BlockPos> markedPositions,
      BlockPos origin,
      Set<BlockPos> attemptedPositions,
      int scanCursor
   ) {
      String dimensionId = level.dimension().location().toString();
      ArrayList<PlantableSourcePlanner.ChestCandidate> candidates = new ArrayList<>();
      HashSet<BlockPos> seenCanonical = new HashSet<>();
      BlockScanner.KnownLocationWindow window = scanner.getKnownLocationsWindow(scanCursor, 512, 8192, Blocks.CHEST);

      for (BlockPos raw : window.locations()) {
         PlantableSourcePlanner.ChestCandidate candidate = canonicalChest(level, raw);
         if (candidate != null
            && !attemptedPositions.contains(candidate.position())
            && seenCanonical.add(candidate.position())
            && withinRadius(origin, candidate.position())
            && !markedPositions.contains(candidate.position())
            && (candidate.secondaryPosition() == null || !markedPositions.contains(candidate.secondaryPosition()))
            && !protectionDenied(playerPlaced, farms, dimensionId, candidate.position(), candidate.secondaryPosition())
            && isInsideVillageStructure(level, candidate.position())
            && (candidate.secondaryPosition() == null || isInsideVillageStructure(level, candidate.secondaryPosition()))) {
            candidates.add(candidate);
         }
      }

      candidates.sort(chestComparator(origin));
      return new PlantableSourcePlanner.ChestWindow(capped(candidates, 512), window.nextCursor());
   }

   private static PlantableSourcePlanner.ChestCandidate canonicalChest(ServerLevel level, BlockPos raw) {
      if (raw != null && level.hasChunkAt(raw)) {
         BlockState state = level.getBlockState(raw);
         if (!state.is(Blocks.CHEST)) {
            return null;
         } else if (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return new PlantableSourcePlanner.ChestCandidate(raw, null);
         } else {
            BlockPos partner = raw.relative(ChestBlock.getConnectedDirection(state));
            if (level.hasChunkAt(partner) && level.getBlockState(partner).is(Blocks.CHEST)) {
               BlockPos first = comparePositions(raw, partner) <= 0 ? raw : partner;
               BlockPos second = first.equals(raw) ? partner : raw;
               return new PlantableSourcePlanner.ChestCandidate(first, second);
            } else {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   private static BlockPos canonicalGrassPosition(ServerLevel level, BlockPos raw) {
      if (raw != null && level.hasChunkAt(raw)) {
         BlockState state = level.getBlockState(raw);
         if (state.is(Blocks.SHORT_GRASS)) {
            return raw.immutable();
         }

         if (!state.is(Blocks.TALL_GRASS)) {
            return null;
         }

         BlockPos canonical = state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER ? raw.below() : raw;
         if (!level.hasChunkAt(canonical)) {
            return null;
         }

         BlockState lower = level.getBlockState(canonical);
         return isLowerTallGrass(lower) ? canonical.immutable() : null;
      } else {
         return null;
      }
   }

   private static boolean isLowerTallGrass(BlockState state) {
      return state.is(Blocks.TALL_GRASS) && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER;
   }

   static Block[] naturalGrassBlocks() {
      return new Block[]{Blocks.SHORT_GRASS, Blocks.TALL_GRASS};
   }

   private static PlantableSourcePlanner.RecognizedBlocks recognizedBlocks() {
      ArrayList<Block> blocks = new ArrayList<>();
      boolean truncated = false;

      for (Block block : BuiltInRegistries.BLOCK) {
         if (!HarvestBehaviorRegistry.DEFAULT.resolve(block.defaultBlockState()).isEmpty()) {
            if (blocks.size() >= 4096) {
               truncated = true;
               break;
            }

            blocks.add(block);
         }
      }

      return new PlantableSourcePlanner.RecognizedBlocks(List.copyOf(blocks), truncated);
   }

   private static Comparator<PlantableSourcePlanner.BreakCandidate> breakComparator(BlockPos origin) {
      return Comparator.<PlantableSourcePlanner.BreakCandidate>comparingLong(candidate -> distanceSquared(origin, candidate.position()))
         .thenComparing(candidate -> candidate.position(), PlantableSourcePlanner::comparePositions);
   }

   private static Comparator<PlantableSourcePlanner.ChestCandidate> chestComparator(BlockPos origin) {
      return Comparator.<PlantableSourcePlanner.ChestCandidate>comparingLong(candidate -> distanceSquared(origin, candidate.position()))
         .thenComparing(candidate -> candidate.position(), PlantableSourcePlanner::comparePositions);
   }

   private static boolean withinRadius(BlockPos origin, BlockPos position) {
      double radius = 115.0;
      return distanceSquared(origin, position) <= radius * radius;
   }

   private static long distanceSquared(BlockPos first, BlockPos second) {
      long dx = (long)first.getX() - second.getX();
      long dy = (long)first.getY() - second.getY();
      long dz = (long)first.getZ() - second.getZ();
      return dx * dx + dy * dy + dz * dz;
   }

   private static int comparePositions(BlockPos first, BlockPos second) {
      int x = Integer.compare(first.getX(), second.getX());
      if (x != 0) {
         return x;
      }

      int y = Integer.compare(first.getY(), second.getY());
      return y != 0 ? y : Integer.compare(first.getZ(), second.getZ());
   }

   private static <T> List<T> capped(List<T> source, int cap) {
      return source != null && !source.isEmpty() ? List.copyOf(source.subList(0, Math.min(cap, source.size()))) : List.of();
   }

   private static List<BlockPos> immutablePositions(List<BlockPos> positions) {
      return positions != null && !positions.isEmpty()
         ? positions.stream().filter(Objects::nonNull).<BlockPos>map(BlockPos::immutable).toList()
         : List.of();
   }

   private static Set<BlockPos> immutablePositionSet(Set<BlockPos> positions) {
      if (positions != null && !positions.isEmpty()) {
         HashSet<BlockPos> copy = new HashSet<>();
         positions.stream().filter(Objects::nonNull).map(BlockPos::immutable).forEach(copy::add);
         return Set.copyOf(copy);
      } else {
         return Set.of();
      }
   }

   public record BreakCandidate(PlantableSourcePlanner.SourceKind kind, BlockPos position, BlockState expectedState) {
      public BreakCandidate {
         if (kind != PlantableSourcePlanner.SourceKind.NATURAL_GRASS && kind != PlantableSourcePlanner.SourceKind.VILLAGE_CROP) {
            throw new IllegalArgumentException("break candidate requires a break source kind");
         }

         position = Objects.requireNonNull(position, "position").immutable();
         expectedState = Objects.requireNonNull(expectedState, "expectedState");
      }
   }

   private record BreakWindow(List<PlantableSourcePlanner.BreakCandidate> candidates, int nextCursor) {
   }

   public record ChestCandidate(BlockPos position, BlockPos secondaryPosition) {
      public ChestCandidate {
         position = Objects.requireNonNull(position, "position").immutable();
         secondaryPosition = secondaryPosition == null ? null : secondaryPosition.immutable();
      }
   }

   private record ChestWindow(List<PlantableSourcePlanner.ChestCandidate> candidates, int nextCursor) {
   }

   public record MarkedManifest(List<BlockPos> markedChests) {
      public MarkedManifest {
         markedChests = PlantableSourcePlanner.immutablePositions(markedChests);
      }
   }

   private record RecognizedBlocks(List<Block> blocks, boolean truncated) {
   }

   public enum SourceKind {
      MARKED_CHEST,
      NATURAL_GRASS,
      VILLAGE_CROP,
      VILLAGE_CHEST;
   }

   public record WorldRoundManifest(
      BlockPos scanOrigin,
      List<PlantableSourcePlanner.BreakCandidate> naturalGrass,
      List<PlantableSourcePlanner.BreakCandidate> villageCrops,
      List<PlantableSourcePlanner.ChestCandidate> villageChests,
      boolean worldGuardsAvailable,
      boolean discoveryTruncated,
      int nextNaturalScanCursor,
      int nextCropScanCursor,
      int nextVillageChestScanCursor
   ) {
      public WorldRoundManifest(
         BlockPos scanOrigin,
         List<PlantableSourcePlanner.BreakCandidate> naturalGrass,
         List<PlantableSourcePlanner.BreakCandidate> villageCrops,
         List<PlantableSourcePlanner.ChestCandidate> villageChests,
         boolean worldGuardsAvailable,
         boolean discoveryTruncated,
         int nextNaturalScanCursor,
         int nextCropScanCursor,
         int nextVillageChestScanCursor
      ) {
         scanOrigin = Objects.requireNonNull(scanOrigin, "scanOrigin").immutable();
         naturalGrass = List.copyOf(naturalGrass == null ? List.of() : naturalGrass);
         villageCrops = List.copyOf(villageCrops == null ? List.of() : villageCrops);
         villageChests = List.copyOf(villageChests == null ? List.of() : villageChests);
         if (nextNaturalScanCursor >= 0 && nextCropScanCursor >= 0 && nextVillageChestScanCursor >= 0) {
            this.scanOrigin = scanOrigin;
            this.naturalGrass = naturalGrass;
            this.villageCrops = villageCrops;
            this.villageChests = villageChests;
            this.worldGuardsAvailable = worldGuardsAvailable;
            this.discoveryTruncated = discoveryTruncated;
            this.nextNaturalScanCursor = nextNaturalScanCursor;
            this.nextCropScanCursor = nextCropScanCursor;
            this.nextVillageChestScanCursor = nextVillageChestScanCursor;
         } else {
            throw new IllegalArgumentException("scanner cursors cannot be negative");
         }
      }
   }
}
