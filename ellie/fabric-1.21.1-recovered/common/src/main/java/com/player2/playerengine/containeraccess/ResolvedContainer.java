package com.player2.playerengine.containeraccess;

import net.minecraft.world.Container;
import net.minecraft.core.BlockPos;

public record ResolvedContainer(Container container, ContainerKind kind, BlockPos canonicalPos, BlockPos secondaryPos, int totalSlots) {
}
