package com.player2.playerengine.agentic;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AgenticRunRegistry {
   private static final ConcurrentHashMap<UUID, AgenticRunRegistry.AgenticRunState> RUNS = new ConcurrentHashMap<>();

   private AgenticRunRegistry() {
   }

   public static void register(UUID botUuid, AgenticRunRegistry.AgenticRunState state) {
      if (botUuid != null && state != null) {
         RUNS.put(botUuid, state);
      }
   }

   public static Optional<AgenticRunRegistry.AgenticRunState> get(UUID botUuid) {
      return Optional.ofNullable(RUNS.get(botUuid));
   }

   public static Optional<AgenticRunSnapshot> snapshot(UUID botUuid) {
      return get(botUuid).map(AgenticRunRegistry.AgenticRunState::toSnapshot);
   }

   public static void clear(UUID botUuid) {
      if (botUuid != null) {
         RUNS.remove(botUuid);
      }
   }

   public static final class AgenticRunState {
      private final String runId;
      private final String goalSummary;
      private final String planningSource;
      private int activeStepIndex = -1;
      private String activeStepKind = "";
      private String state = "planning";
      private String lastMessage = "";
      private String gatherProgress = "";
      private String storageProgress = "";
      private String depositProgress = "";
      private String labelProgress = "";
      private String smeltProgress = "";
      private String smithProgress = "";
      private String mineProgress = "";
      private String farmProgress = "";
      private String storageTargetSummary = "";
      private volatile boolean terminal = false;
      private AgenticRunRegistry.DegradationLevel gatherDegradation = AgenticRunRegistry.DegradationLevel.CLEAN;
      private String gatherDegradationReason = "";
      private AgenticRunRegistry.DegradationLevel depositDegradation = AgenticRunRegistry.DegradationLevel.CLEAN;
      private String depositDegradationReason = "";
      private AgenticRunRegistry.DegradationLevel labelDegradation = AgenticRunRegistry.DegradationLevel.CLEAN;
      private String labelDegradationReason = "";
      private AgenticRunRegistry.DegradationLevel waypointDegradation = AgenticRunRegistry.DegradationLevel.CLEAN;
      private String waypointDegradationReason = "";
      private AgenticRunRegistry.DegradationLevel smeltDegradation = AgenticRunRegistry.DegradationLevel.CLEAN;
      private String smeltDegradationReason = "";
      private AgenticRunRegistry.DegradationLevel smithDegradation = AgenticRunRegistry.DegradationLevel.CLEAN;
      private String smithDegradationReason = "";
      private AgenticRunRegistry.DegradationLevel mineDegradation = AgenticRunRegistry.DegradationLevel.CLEAN;
      private String mineDegradationReason = "";
      private AgenticRunRegistry.DegradationLevel farmDegradation = AgenticRunRegistry.DegradationLevel.CLEAN;
      private String farmDegradationReason = "";

      public AgenticRunState(String runId, String goalSummary, String planningSource) {
         this.runId = runId;
         this.goalSummary = goalSummary;
         this.planningSource = planningSource;
      }

      public void setActiveStep(int index, String kind) {
         this.activeStepIndex = index;
         this.activeStepKind = kind != null ? kind : "";
         this.state = "running";
      }

      public void setGatherProgress(String progress) {
         this.gatherProgress = progress != null ? progress : "";
      }

      public void setStorageProgress(String progress) {
         this.storageProgress = progress != null ? progress : "";
      }

      public void setDepositProgress(String progress) {
         this.depositProgress = progress != null ? progress : "";
      }

      public void setLabelProgress(String progress) {
         this.labelProgress = progress != null ? progress : "";
      }

      public void setSmeltProgress(String progress) {
         this.smeltProgress = progress != null ? progress : "";
      }

      public void setSmithProgress(String progress) {
         this.smithProgress = progress != null ? progress : "";
      }

      public void setMineProgress(String progress) {
         this.mineProgress = progress != null ? progress : "";
      }

      public void setFarmProgress(String progress) {
         this.farmProgress = progress != null ? progress : "";
      }

      public void setStorageTargetSummary(String summary) {
         this.storageTargetSummary = summary != null ? summary : "";
      }

      public void setGatherDegraded(AgenticRunRegistry.DegradationLevel level, String reason) {
         this.gatherDegradation = level != null ? level : AgenticRunRegistry.DegradationLevel.CLEAN;
         this.gatherDegradationReason = reason != null ? reason : "";
      }

      public void setDepositDegraded(AgenticRunRegistry.DegradationLevel level, String reason) {
         this.depositDegradation = level != null ? level : AgenticRunRegistry.DegradationLevel.CLEAN;
         this.depositDegradationReason = reason != null ? reason : "";
      }

      public void setLabelDegraded(AgenticRunRegistry.DegradationLevel level, String reason) {
         this.labelDegradation = level != null ? level : AgenticRunRegistry.DegradationLevel.CLEAN;
         this.labelDegradationReason = reason != null ? reason : "";
      }

      public void setWaypointDegraded(AgenticRunRegistry.DegradationLevel level, String reason) {
         this.waypointDegradation = level != null ? level : AgenticRunRegistry.DegradationLevel.CLEAN;
         this.waypointDegradationReason = reason != null ? reason : "";
      }

      public void setSmeltDegraded(AgenticRunRegistry.DegradationLevel level, String reason) {
         this.smeltDegradation = level != null ? level : AgenticRunRegistry.DegradationLevel.CLEAN;
         this.smeltDegradationReason = reason != null ? reason : "";
      }

      public void setSmithDegraded(AgenticRunRegistry.DegradationLevel level, String reason) {
         this.smithDegradation = level != null ? level : AgenticRunRegistry.DegradationLevel.CLEAN;
         this.smithDegradationReason = reason != null ? reason : "";
      }

      public void setMineDegraded(AgenticRunRegistry.DegradationLevel level, String reason) {
         this.mineDegradation = level != null ? level : AgenticRunRegistry.DegradationLevel.CLEAN;
         this.mineDegradationReason = reason != null ? reason : "";
      }

      public void setFarmDegraded(AgenticRunRegistry.DegradationLevel level, String reason) {
         this.farmDegradation = level != null ? level : AgenticRunRegistry.DegradationLevel.CLEAN;
         this.farmDegradationReason = reason != null ? reason : "";
      }

      public AgenticRunRegistry.DegradationLevel getGatherDegradation() {
         return this.gatherDegradation;
      }

      public String getGatherDegradationReason() {
         return this.gatherDegradationReason;
      }

      public AgenticRunRegistry.DegradationLevel getDepositDegradation() {
         return this.depositDegradation;
      }

      public String getDepositDegradationReason() {
         return this.depositDegradationReason;
      }

      public AgenticRunRegistry.DegradationLevel getLabelDegradation() {
         return this.labelDegradation;
      }

      public String getLabelDegradationReason() {
         return this.labelDegradationReason;
      }

      public AgenticRunRegistry.DegradationLevel getWaypointDegradation() {
         return this.waypointDegradation;
      }

      public String getWaypointDegradationReason() {
         return this.waypointDegradationReason;
      }

      public AgenticRunRegistry.DegradationLevel getSmeltDegradation() {
         return this.smeltDegradation;
      }

      public String getSmeltDegradationReason() {
         return this.smeltDegradationReason;
      }

      public AgenticRunRegistry.DegradationLevel getSmithDegradation() {
         return this.smithDegradation;
      }

      public String getSmithDegradationReason() {
         return this.smithDegradationReason;
      }

      public AgenticRunRegistry.DegradationLevel getMineDegradation() {
         return this.mineDegradation;
      }

      public String getMineDegradationReason() {
         return this.mineDegradationReason;
      }

      public AgenticRunRegistry.DegradationLevel getFarmDegradation() {
         return this.farmDegradation;
      }

      public String getFarmDegradationReason() {
         return this.farmDegradationReason;
      }

      public String getDepositProgress() {
         return this.depositProgress;
      }

      public String getGatherProgress() {
         return this.gatherProgress;
      }

      public String getSmeltProgress() {
         return this.smeltProgress;
      }

      public String getSmithProgress() {
         return this.smithProgress;
      }

      public String getMineProgress() {
         return this.mineProgress;
      }

      public String getFarmProgress() {
         return this.farmProgress;
      }

      public String getStorageTargetSummary() {
         return this.storageTargetSummary;
      }

      public String progressForKind(String kind) {
         if (kind == null) {
            return "";
         } else {
            String k = kind.toLowerCase(Locale.ROOT);
            if (k.contains("gather")) {
               return this.gatherProgress;
            } else if (k.contains("deposit")) {
               return this.depositProgress;
            } else if (k.contains("label")) {
               return this.labelProgress;
            } else if (k.contains("smelt") || k.contains("cook") || k.contains("blast") || k.contains("smoke")) {
               return this.smeltProgress;
            } else if (k.contains("smith")) {
               return this.smithProgress;
            } else if (k.contains("mine")) {
               return this.mineProgress;
            } else {
               return k.contains("farm") ? this.farmProgress : this.storageProgress;
            }
         }
      }

      public void terminal(String state, String message) {
         this.state = state;
         this.lastMessage = message != null ? message : "";
         this.activeStepKind = "";
         this.terminal = true;
      }

      public boolean isTerminal() {
         return this.terminal;
      }

      public AgenticRunSnapshot toSnapshot() {
         StringBuilder msg = new StringBuilder();
         if (!this.lastMessage.isBlank()) {
            msg.append(this.lastMessage);
         }

         if (!this.gatherProgress.isBlank()) {
            if (msg.length() > 0) {
               msg.append(" | ");
            }

            msg.append(this.gatherProgress);
         }

         if (!this.storageProgress.isBlank()) {
            if (msg.length() > 0) {
               msg.append(" | ");
            }

            msg.append(this.storageProgress);
         }

         if (!this.depositProgress.isBlank()) {
            if (msg.length() > 0) {
               msg.append(" | ");
            }

            msg.append(this.depositProgress);
         }

         if (!this.labelProgress.isBlank()) {
            if (msg.length() > 0) {
               msg.append(" | ");
            }

            msg.append(this.labelProgress);
         }

         if (!this.smeltProgress.isBlank()) {
            if (msg.length() > 0) {
               msg.append(" | ");
            }

            msg.append(this.smeltProgress);
         }

         if (!this.smithProgress.isBlank()) {
            if (msg.length() > 0) {
               msg.append(" | ");
            }

            msg.append(this.smithProgress);
         }

         if (!this.mineProgress.isBlank()) {
            if (msg.length() > 0) {
               msg.append(" | ");
            }

            msg.append(this.mineProgress);
         }

         if (!this.farmProgress.isBlank()) {
            if (msg.length() > 0) {
               msg.append(" | ");
            }

            msg.append(this.farmProgress);
         }

         return new AgenticRunSnapshot(
            this.runId,
            this.goalSummary,
            this.activeStepIndex,
            this.activeStepKind,
            this.state,
            msg.toString(),
            this.planningSource,
            this.storageProgress,
            this.depositProgress,
            this.labelProgress,
            this.storageTargetSummary
         );
      }
   }

   public enum DegradationLevel {
      CLEAN,
      PARTIAL,
      SKIPPED;
   }
}
