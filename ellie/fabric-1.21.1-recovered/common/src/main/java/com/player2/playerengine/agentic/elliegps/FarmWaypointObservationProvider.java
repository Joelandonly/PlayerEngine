package com.player2.playerengine.agentic.elliegps;

import com.player2.playerengine.PlayerEngineController;

@FunctionalInterface
public interface FarmWaypointObservationProvider {
   FarmObservationResult observe(PlayerEngineController var1, WaypointRecord var2);
}
