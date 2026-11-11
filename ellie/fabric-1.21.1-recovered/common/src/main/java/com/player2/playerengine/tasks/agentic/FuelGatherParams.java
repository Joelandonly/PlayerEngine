package com.player2.playerengine.tasks.agentic;

import net.minecraft.world.item.Item;

public record FuelGatherParams(Item targetFuelItem, int neededCount, double radius, double timeoutSeconds, int maxContainers, int climbBudget) {
}
