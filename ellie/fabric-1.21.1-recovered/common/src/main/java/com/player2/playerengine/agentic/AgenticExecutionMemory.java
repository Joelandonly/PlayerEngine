package com.player2.playerengine.agentic;

import com.player2.playerengine.agentic.elliegps.WaypointOriginEvidence;
import java.util.Optional;

public final class AgenticExecutionMemory {
   private AgenticStorageTarget storageTarget;
   private WaypointOriginEvidence waypointOriginEvidence;
   private MaterialReservationService materialReservations;

   public Optional<AgenticStorageTarget> storageTarget() {
      return Optional.ofNullable(this.storageTarget);
   }

   public void setStorageTarget(AgenticStorageTarget target) {
      this.storageTarget = target;
   }

   public void clearStorageTarget() {
      this.storageTarget = null;
   }

   public Optional<WaypointOriginEvidence> waypointOriginEvidence() {
      return Optional.ofNullable(this.waypointOriginEvidence);
   }

   public void setWaypointOriginEvidence(WaypointOriginEvidence evidence) {
      this.waypointOriginEvidence = evidence;
   }

   public void clearWaypointOriginEvidence() {
      this.waypointOriginEvidence = null;
   }

   public MaterialReservationService materialReservations() {
      if (this.materialReservations == null) {
         this.materialReservations = new MaterialReservationService();
      }

      return this.materialReservations;
   }

   public void clearMaterialReservations() {
      if (this.materialReservations != null) {
         this.materialReservations.clear();
      }
   }
}
