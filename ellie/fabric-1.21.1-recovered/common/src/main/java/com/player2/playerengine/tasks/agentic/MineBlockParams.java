package com.player2.playerengine.tasks.agentic;

public record MineBlockParams(String blockId, int maxBlocks, double radius, double timeoutSeconds, int toolAcquireMaxContainers, int toolAcquireClimbBudget) {
}
