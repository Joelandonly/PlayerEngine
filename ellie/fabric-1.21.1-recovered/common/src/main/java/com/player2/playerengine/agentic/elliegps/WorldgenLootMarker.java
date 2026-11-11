package com.player2.playerengine.agentic.elliegps;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.RandomizableContainer;

public final class WorldgenLootMarker {
   private WorldgenLootMarker() {
   }

   public static boolean hasUnopenedLootTable(BlockEntity blockEntity) {
      if (blockEntity instanceof RandomizableContainer rc) {
         ResourceKey<LootTable> lootTable = rc.getLootTable();
         return lootTable != null;
      } else {
         return false;
      }
   }

   public static boolean hasUnopenedLootTable(ServerLevel level, BlockPos pos) {
      BlockEntity be = level.getBlockEntity(pos);
      return hasUnopenedLootTable(be);
   }
}
