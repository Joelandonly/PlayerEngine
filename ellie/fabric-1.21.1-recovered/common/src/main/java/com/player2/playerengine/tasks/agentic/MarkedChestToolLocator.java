package com.player2.playerengine.tasks.agentic;

import com.player2.playerengine.PlayerEngineController;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

final class MarkedChestToolLocator {
   private MarkedChestToolLocator() {
   }

   static List<BlockPos> candidateCoordinates(PlayerEngineController controller, Item tool, Vec3 origin, double radiusBlocks, String dimensionId) {
      return MarkedChestItemLocator.candidateCoordinates(controller, tool, origin, radiusBlocks, dimensionId);
   }
}
