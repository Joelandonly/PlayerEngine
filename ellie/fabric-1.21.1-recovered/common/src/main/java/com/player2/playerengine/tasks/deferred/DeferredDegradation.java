package com.player2.playerengine.tasks.deferred;

public enum DeferredDegradation {
   STATION_GONE,
   OUT_OF_FUEL,
   TAMPERED_OUTPUT,
   STALLED_RETRYING,
   STALLED_TIMEOUT;

   public String label() {
      return this.name().toLowerCase();
   }
}
