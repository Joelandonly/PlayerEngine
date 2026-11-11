package com.player2.playerengine.containeraccess;

import java.util.List;
import net.minecraft.core.BlockPos;

public record ContainerSnapshot(
   String dimensionId,
   BlockPos canonicalPos,
   BlockPos secondaryPos,
   ContainerKind kind,
   int totalSlots,
   int emptySlots,
   List<ItemCount> aggregate,
   List<IndexedSlot> slots,
   long gameTime
) {
}
