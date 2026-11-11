package com.player2.playerengine.tasks.construction;

import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;

public class PlaceStructureBlockTask extends PlaceBlockTask {
   public PlaceStructureBlockTask(BlockPos target) {
      super(target, new Block[0], true, true);
   }
}
