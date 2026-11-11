package com.player2.playerengine.tasks.farming;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelReader;

public interface HarvestBehavior {
   boolean recognizes(BlockState var1);

   boolean isMature(BlockState var1);

   HarvestBehavior.HarvestAction action();

   Optional<ResourceLocation> canonicalCropId(BlockState var1);

   Optional<ResourceLocation> plantingItemId(LevelReader var1, BlockPos var2, BlockState var3);

   default boolean allowsLowCollisionStance(BlockState state) {
      return false;
   }

   enum HarvestAction {
      BREAK;
   }
}
