package com.player2.playerengine.agentic.elliegps;

public record WaypointOriginEvidence(boolean placedByBot, WaypointOriginEvidence.Verdict verdict, String reasonToken, long captureGameTime) {
   public static WaypointOriginEvidence botPlaced(long captureGameTime) {
      return new WaypointOriginEvidence(true, WaypointOriginEvidence.Verdict.REGISTER, "", captureGameTime);
   }

   public boolean permitsRegistration() {
      return this.placedByBot || this.verdict == WaypointOriginEvidence.Verdict.REGISTER;
   }

   public enum Verdict {
      REGISTER,
      REJECT_WORLDGEN,
      REJECT_STRUCTURE,
      UNKNOWN;
   }
}
