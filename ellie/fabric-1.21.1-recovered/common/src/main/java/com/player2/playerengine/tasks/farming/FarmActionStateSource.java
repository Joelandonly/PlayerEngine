package com.player2.playerengine.tasks.farming;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelReader;

@FunctionalInterface
public interface FarmActionStateSource {
   FarmActionStateSource LIVE = BlockGetter::getBlockState;

   BlockState read(LevelReader var1, BlockPos var2);
}
