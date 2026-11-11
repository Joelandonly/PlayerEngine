package com.player2.playerengine.agentic.elliegps;

import java.util.Objects;

public record WaypointCountResult(int count, WaypointSearchStatus status) {
   public WaypointCountResult {
      if (count < 0) {
         throw new IllegalArgumentException("count must be non-negative");
      }

      status = Objects.requireNonNull(status, "status");
   }
}
