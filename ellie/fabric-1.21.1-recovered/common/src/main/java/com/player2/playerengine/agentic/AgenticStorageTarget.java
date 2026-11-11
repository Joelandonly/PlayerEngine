package com.player2.playerengine.agentic;

import net.minecraft.core.BlockPos;

public record AgenticStorageTarget(BlockPos pos, String dimension, String blockId, StorageTargetSource source, boolean placedByBot, long resolvedAtGameTime) {
}
