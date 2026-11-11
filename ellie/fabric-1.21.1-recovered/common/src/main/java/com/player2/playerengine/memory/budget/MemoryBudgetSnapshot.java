package com.player2.playerengine.memory.budget;

public record MemoryBudgetSnapshot(int callCount, long windowStartMs, long windowEndMs) {
}
