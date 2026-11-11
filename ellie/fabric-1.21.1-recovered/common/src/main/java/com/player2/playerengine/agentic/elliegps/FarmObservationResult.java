package com.player2.playerengine.agentic.elliegps;

import java.util.Objects;

public record FarmObservationResult(FarmObservationStatus status, FarmWaypointObservation observation, String controlledReason) {
   public static final int MAX_REASON_LENGTH = 160;

   public FarmObservationResult {
      status = Objects.requireNonNull(status, "status");
      if (status == FarmObservationStatus.OBSERVED != (observation != null)) {
         throw new IllegalArgumentException("only OBSERVED results carry an observation");
      }

      controlledReason = bound(controlledReason);
   }

   private static String bound(String reason) {
      String value = reason == null ? "" : reason.strip();
      return value.length() <= 160 ? value : value.substring(0, 160);
   }
}
