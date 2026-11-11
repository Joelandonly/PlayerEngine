package com.player2.playerengine.agentic.steps;

import java.util.List;

public record LabelChestParams(List<String> lines, boolean autoLabel, String signItemId, double timeoutSeconds) {
}
