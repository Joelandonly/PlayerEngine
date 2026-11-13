package com.player2.playerengine.tasks.farming;

import com.player2.playerengine.agentic.elliegps.EllieGPSStore;
import com.player2.playerengine.agentic.elliegps.FarmWaypointData;
import com.player2.playerengine.agentic.elliegps.WaypointRecord;
import com.player2.playerengine.structureprotection.PlayerPlacedBlockStore;
import com.player2.playerengine.safezone.SafeZoneService;
import java.util.Objects;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;

public interface FarmSiteWorldView {
   void assertServerThread();

   FarmSiteWorldView.CellObservation observe(BlockPos var1);

   FarmSiteWorldView.WorldObservation observeWorld(BlockPos var1);

   default boolean isConstructionProtected(BlockPos position) {
      return false;
   }

   static FarmSiteWorldView live(ServerLevel level) {
      return new FarmSiteWorldView.ServerWorldView(level, PlayerPlacedBlockStore.get());
   }

   static String stateFingerprint(BlockState state) {
      Objects.requireNonNull(state, "state");
      return state.is(Blocks.FARMLAND)
         ? BuiltInRegistries.BLOCK.getKey(state.getBlock()) + "|farmland"
         : BuiltInRegistries.BLOCK.getKey(state.getBlock()) + "|" + state;
   }

   static boolean isClearableGroundCover(BlockState state, FluidState fluid) {
      Objects.requireNonNull(state, "state");
      Objects.requireNonNull(fluid, "fluid");
      return fluid.isEmpty()
         && (
            state.canBeReplaced()
               || state.is(BlockTags.FLOWERS)
               || state.getBlock() instanceof FlowerBlock
               || state.getBlock() instanceof TallFlowerBlock
         );
   }

   static FarmSiteWorldView.BlockKind classifyBlock(BlockState state, FluidState fluid) {
      Objects.requireNonNull(state, "state");
      Objects.requireNonNull(fluid, "fluid");
      if (state.isAir()) {
         return FarmSiteWorldView.BlockKind.AIR;
      } else if (state.is(Blocks.GRASS_BLOCK)) {
         return FarmSiteWorldView.BlockKind.GRASS_BLOCK;
      } else if (state.is(Blocks.DIRT)) {
         return FarmSiteWorldView.BlockKind.DIRT;
      } else if (state.is(Blocks.STONE)) {
         return FarmSiteWorldView.BlockKind.STONE;
      } else if (state.is(Blocks.FARMLAND)) {
         return FarmSiteWorldView.BlockKind.FARMLAND;
      } else if (HarvestBehaviorRegistry.DEFAULT.resolve(state).isPresent()) {
         return FarmSiteWorldView.BlockKind.CROP;
      } else if (state.is(Blocks.WATER) && fluid.is(FluidTags.WATER) && fluid.isSource()) {
         return FarmSiteWorldView.BlockKind.STANDALONE_WATER;
      } else {
         return isClearableGroundCover(state, fluid) ? FarmSiteWorldView.BlockKind.REPLACEABLE_VEGETATION : FarmSiteWorldView.BlockKind.OTHER;
      }
   }

   enum BlockKind {
      AIR,
      GRASS_BLOCK,
      DIRT,
      STONE,
      FARMLAND,
      CROP,
      STANDALONE_WATER,
      REPLACEABLE_VEGETATION,
      OTHER;
   }

   record CellObservation(
      boolean loaded,
      boolean inBuildHeight,
      boolean withinWorldBorder,
      FarmSiteWorldView.BlockKind blockKind,
      FarmSiteWorldView.FluidKind fluidKind,
      boolean replaceable,
      boolean collisionEmpty,
      boolean recognizedCrop,
      boolean blockEntity,
      boolean fallingHazard,
      boolean unbreakable,
      boolean solidTopSupport,
      boolean nonWaterloggable,
      boolean playerProtected,
      boolean warmEnoughToRain,
      int blockLight,
      String stateFingerprint,
      FarmBreakToolRequirement breakToolRequirement
   ) {
      public CellObservation(
         boolean loaded,
         boolean inBuildHeight,
         boolean withinWorldBorder,
         FarmSiteWorldView.BlockKind blockKind,
         FarmSiteWorldView.FluidKind fluidKind,
         boolean replaceable,
         boolean collisionEmpty,
         boolean recognizedCrop,
         boolean blockEntity,
         boolean fallingHazard,
         boolean unbreakable,
         boolean solidTopSupport,
         boolean nonWaterloggable,
         boolean playerProtected,
         boolean warmEnoughToRain,
         int blockLight,
         String stateFingerprint,
         FarmBreakToolRequirement breakToolRequirement
      ) {
         Objects.requireNonNull(blockKind, "blockKind");
         Objects.requireNonNull(fluidKind, "fluidKind");
         Objects.requireNonNull(breakToolRequirement, "breakToolRequirement");
         stateFingerprint = stateFingerprint == null ? "" : stateFingerprint;
         if (blockLight >= 0 && blockLight <= 15) {
            this.loaded = loaded;
            this.inBuildHeight = inBuildHeight;
            this.withinWorldBorder = withinWorldBorder;
            this.blockKind = blockKind;
            this.fluidKind = fluidKind;
            this.replaceable = replaceable;
            this.collisionEmpty = collisionEmpty;
            this.recognizedCrop = recognizedCrop;
            this.blockEntity = blockEntity;
            this.fallingHazard = fallingHazard;
            this.unbreakable = unbreakable;
            this.solidTopSupport = solidTopSupport;
            this.nonWaterloggable = nonWaterloggable;
            this.playerProtected = playerProtected;
            this.warmEnoughToRain = warmEnoughToRain;
            this.blockLight = blockLight;
            this.stateFingerprint = stateFingerprint;
            this.breakToolRequirement = breakToolRequirement;
         } else {
            throw new IllegalArgumentException("blockLight must be in [0,15]");
         }
      }

      public CellObservation(
         boolean loaded,
         boolean inBuildHeight,
         boolean withinWorldBorder,
         FarmSiteWorldView.BlockKind blockKind,
         FarmSiteWorldView.FluidKind fluidKind,
         boolean replaceable,
         boolean collisionEmpty,
         boolean recognizedCrop,
         boolean blockEntity,
         boolean fallingHazard,
         boolean unbreakable,
         boolean solidTopSupport,
         boolean nonWaterloggable,
         boolean playerProtected,
         boolean warmEnoughToRain,
         int blockLight,
         String stateFingerprint
      ) {
         this(
            loaded,
            inBuildHeight,
            withinWorldBorder,
            blockKind,
            fluidKind,
            replaceable,
            collisionEmpty,
            recognizedCrop,
            blockEntity,
            fallingHazard,
            unbreakable,
            solidTopSupport,
            nonWaterloggable,
            playerProtected,
            warmEnoughToRain,
            blockLight,
            stateFingerprint,
            FarmBreakToolRequirement.none(Blocks.AIR.defaultBlockState())
         );
      }

      public boolean hasFluid() {
         return this.fluidKind != FarmSiteWorldView.FluidKind.NONE;
      }

      public boolean isStandaloneWaterSource() {
         return this.blockKind == FarmSiteWorldView.BlockKind.STANDALONE_WATER && this.fluidKind == FarmSiteWorldView.FluidKind.STANDALONE_WATER_SOURCE;
      }

      public boolean isReadable() {
         return this.loaded && this.inBuildHeight && this.withinWorldBorder;
      }
   }

   enum FluidKind {
      NONE,
      STANDALONE_WATER_SOURCE,
      OTHER_WATER_SOURCE,
      FLOWING_WATER,
      OTHER;
   }

   final class ServerWorldView implements FarmSiteWorldView {
      private final ServerLevel level;
      private final PlayerPlacedBlockStore protectionStore;
      private final String dimensionId;

      private ServerWorldView(ServerLevel level, PlayerPlacedBlockStore protectionStore) {
         this.level = Objects.requireNonNull(level, "level");
         this.protectionStore = protectionStore;
         this.dimensionId = level.dimension().location().toString();
      }

      @Override
      public void assertServerThread() {
         MinecraftServer server = this.level.getServer();
         if (server == null || !server.isSameThread()) {
            throw new IllegalStateException("farm site planning must run on the server thread");
         }
      }

      @Override
      public FarmSiteWorldView.CellObservation observe(BlockPos requestedPosition) {
         this.assertServerThread();
         BlockPos position = requestedPosition.immutable();
         boolean inBuildHeight = this.level.isInWorldBounds(position);
         boolean loaded = inBuildHeight && this.level.isLoaded(position);
         boolean withinBorder = inBuildHeight && this.level.getWorldBorder().isWithinBounds(position);
         if (loaded && withinBorder) {
            BlockState state = this.level.getBlockState(position);
            FluidState fluid = state.getFluidState();
            FarmSiteWorldView.BlockKind blockKind = FarmSiteWorldView.classifyBlock(state, fluid);
            FarmSiteWorldView.FluidKind fluidKind = classifyFluid(state, fluid);
            boolean fluidEmpty = fluid.isEmpty();
            boolean protectedByPlayer = this.protectionStore != null && this.protectionStore.contains(this.dimensionId, position);
            boolean hasBlockEntity = state.hasBlockEntity();
            boolean solidTop = fluidEmpty && !state.canBeReplaced() && state.isFaceSturdy(this.level, position, Direction.UP);
            boolean nonWaterloggable = !(state.getBlock() instanceof LiquidBlockContainer);
            String fingerprint = FarmSiteWorldView.stateFingerprint(state);
            return new FarmSiteWorldView.CellObservation(
               true,
               true,
               true,
               blockKind,
               fluidKind,
               state.canBeReplaced(),
               state.getCollisionShape(this.level, position).isEmpty(),
               HarvestBehaviorRegistry.DEFAULT.resolve(state).isPresent(),
               hasBlockEntity,
               state.getBlock() instanceof Fallable,
               state.getDestroySpeed(this.level, position) < 0.0F,
               solidTop,
               nonWaterloggable,
               protectedByPlayer,
               ((Biome)this.level.getBiome(position).value()).warmEnoughToRain(position),
               this.level.getBrightness(LightLayer.BLOCK, position),
               fingerprint,
               FarmBreakToolRequirement.resolve(state)
            );
         } else {
            return unavailable(loaded, inBuildHeight, withinBorder);
         }
      }

      @Override
      public boolean isConstructionProtected(BlockPos position) {
         return SafeZoneService.protectedAt(this.level, position);
      }

      @Override
      public FarmSiteWorldView.WorldObservation observeWorld(BlockPos requestedCenter) {
         this.assertServerThread();
         BlockPos center = requestedCenter.immutable();
         EllieGPSStore waypointStore = EllieGPSStore.get();
         boolean protectionAvailable = this.protectionStore != null && PlayerPlacedBlockStore.get() == this.protectionStore;
         WaypointRecord existing = waypointStore == null ? null : waypointStore.byPosition(this.dimensionId, center);
         FarmWaypointData existingFarm = existing == null ? null : existing.farmData();
         return new FarmSiteWorldView.WorldObservation(
            this.level.dimensionType().ultraWarm(),
            protectionAvailable,
            waypointStore != null,
            existing != null && (!"farm".equals(existing.type) || existingFarm == null || !existingFarm.isSupportedVersion())
         );
      }

      private static FarmSiteWorldView.CellObservation unavailable(boolean loaded, boolean inBuildHeight, boolean withinBorder) {
         return new FarmSiteWorldView.CellObservation(
            loaded,
            inBuildHeight,
            withinBorder,
            FarmSiteWorldView.BlockKind.OTHER,
            FarmSiteWorldView.FluidKind.NONE,
            false,
            false,
            false,
            false,
            false,
            true,
            false,
            false,
            false,
            true,
            15,
            "unavailable",
            FarmBreakToolRequirement.custom(Blocks.BEDROCK.defaultBlockState())
         );
      }

      private static FarmSiteWorldView.FluidKind classifyFluid(BlockState state, FluidState fluid) {
         if (fluid.isEmpty()) {
            return FarmSiteWorldView.FluidKind.NONE;
         }

         if (fluid.is(FluidTags.WATER)) {
            if (fluid.isSource()) {
               return state.is(Blocks.WATER)
                  ? FarmSiteWorldView.FluidKind.STANDALONE_WATER_SOURCE
                  : FarmSiteWorldView.FluidKind.OTHER_WATER_SOURCE;
            } else {
               return FarmSiteWorldView.FluidKind.FLOWING_WATER;
            }
         } else {
            return FarmSiteWorldView.FluidKind.OTHER;
         }
      }
   }

   record WorldObservation(boolean ultraWarm, boolean protectionStoreAvailable, boolean waypointStoreAvailable, boolean waypointCollision) {
   }
}
