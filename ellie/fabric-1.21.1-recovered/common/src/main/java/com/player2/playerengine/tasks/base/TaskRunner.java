package com.player2.playerengine.tasks.base;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.observatory.ObservatoryTelemetry;
import com.player2.playerengine.util.Debug;
import java.util.ArrayList;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TaskRunner {
   private static final Logger LOGGER = LogManager.getLogger();
   private final ArrayList<TaskChain> chains = new ArrayList<>();
   private final PlayerEngineController mod;
   private boolean active;
   private TaskChain cachedCurrentTaskChain = null;
   public String statusReport = " (no chain running) ";
   private String lastLoggedChainName = null;

   public TaskRunner(PlayerEngineController mod) {
      this.mod = mod;
      this.active = false;
   }

   public void tick() {
      ObservatoryTelemetry.onRunnerTick(this);
      if (this.active && PlayerEngineController.inGame()) {
         TaskChain maxChain = null;
         float maxPriority = Float.NEGATIVE_INFINITY;

         for (TaskChain chain : this.chains) {
            if (chain.isActive()) {
               float priority = chain.getPriority();
               if (priority > maxPriority) {
                  maxPriority = priority;
                  maxChain = chain;
               }
            }
         }

         if (this.cachedCurrentTaskChain != null && maxChain != this.cachedCurrentTaskChain) {
            this.cachedCurrentTaskChain.onInterrupt(maxChain);
         }

         String newChainName = maxChain != null ? maxChain.getName() : "(none)";
         if (!newChainName.equals(this.lastLoggedChainName)) {
            String oldChainName = this.lastLoggedChainName != null ? this.lastLoggedChainName : "(none)";
            String loggedPriority = maxChain != null ? String.valueOf(maxPriority) : "N/A";
            LOGGER.info(
               "[FollowDiag] CHAIN-CHANGE controller={}: {} -> {} (newPriority={})",
               diagnosticControllerIdentity(this.mod),
               oldChainName,
               newChainName,
               loggedPriority
            );
            this.lastLoggedChainName = newChainName;
         }

         this.cachedCurrentTaskChain = maxChain;
         if (maxChain != null) {
            this.statusReport = "Chain: " + maxChain.getName() + ", priority: " + maxPriority;
            maxChain.tick();
         } else {
            this.statusReport = " (no chain running) ";
         }
      } else {
         this.statusReport = " (no chain running) ";
      }
   }

   public void addTaskChain(TaskChain chain) {
      this.chains.add(chain);
   }

   public void enable() {
      if (!this.active) {
         this.mod.getBehaviour().push();
         this.mod.getBehaviour().setPauseOnLostFocus(false);
      }

      this.active = true;
   }

   public void disable() {
      if (this.active) {
         this.mod.getBehaviour().pop();
      }

      for (TaskChain chain : this.chains) {
         chain.stop();
      }

      this.active = false;
      Debug.logMessage("Stopped");
   }

   public boolean isActive() {
      return this.active;
   }

   public TaskChain getCurrentTaskChain() {
      return this.cachedCurrentTaskChain;
   }

   public PlayerEngineController getMod() {
      return this.mod;
   }

   static String diagnosticControllerIdentity(PlayerEngineController controller) {
      if (controller == null) {
         return formatDiagnosticIdentity(null, null);
      }

      String displayName = null;
      UUID entityId = null;

      try {
         if (controller.getAIPersistantData() != null && controller.getAIPersistantData().getCharacter() != null) {
            displayName = controller.getAIPersistantData().getCharacter().shortName();
         }
      } catch (RuntimeException var4) {
      }

      try {
         if (controller.getEntity() != null) {
            entityId = controller.getEntity().getUUID();
            if (displayName == null || displayName.isBlank()) {
               displayName = controller.getEntity().getName().getString();
            }
         }
      } catch (RuntimeException var5) {
      }

      return formatDiagnosticIdentity(displayName, entityId);
   }

   static String formatDiagnosticIdentity(String displayName, UUID entityId) {
      String name = displayName == null ? "" : displayName.strip().replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
      if (name.isBlank()) {
         name = "unknown";
      } else if (name.length() > 32) {
         name = name.substring(0, 32);
      }

      String shortId = entityId == null ? "unknown" : entityId.toString().substring(0, 8);
      return name + " (" + shortId + ")";
   }
}
