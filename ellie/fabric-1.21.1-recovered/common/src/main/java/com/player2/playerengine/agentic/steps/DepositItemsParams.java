package com.player2.playerengine.agentic.steps;

import java.util.List;

public record DepositItemsParams(List<String> itemIds, boolean depositAll, boolean keepTools, double timeoutSeconds) {
}
