package com.player2.playerengine.agentic;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.PlayerEngineSettings;

public final class AgenticExecutionContext {
   private final PlayerEngineController controller;
   private final PlayerEngineSettings settings;
   private final AgenticRunRegistry.AgenticRunState runState;
   private final AgenticExecutionMemory memory;

   public AgenticExecutionContext(PlayerEngineController controller, AgenticRunRegistry.AgenticRunState runState) {
      this.controller = controller;
      this.settings = controller.getModSettings();
      this.runState = runState;
      this.memory = new AgenticExecutionMemory();
   }

   public PlayerEngineController controller() {
      return this.controller;
   }

   public PlayerEngineSettings settings() {
      return this.settings;
   }

   public AgenticRunRegistry.AgenticRunState runState() {
      return this.runState;
   }

   public AgenticExecutionMemory memory() {
      return this.memory;
   }

   public MaterialReservationService reservations() {
      return this.memory.materialReservations();
   }
}
