package com.player2.playerengine.tasks.movement;

import com.player2.playerengine.FollowMode;
import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.executor.StopReason;
import com.player2.playerengine.executor.TaskStepExecutorAdapter;
import com.player2.playerengine.player2api.AiConversationFeedback;
import com.player2.playerengine.tasks.base.SurvivalInterruptTask;
import com.player2.playerengine.tasks.base.Task;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FollowPlayerTask extends Task {
   private static final Logger LOGGER = LogManager.getLogger();
   private static final double cowardMaxLeash = 12.0;
   private static final double cowardFleeDistance = 12.0;
   private static final double defenderMaxChase = 16.0;
   private static final long MIN_REPORT_INTERVAL_MS = 1500L;
   private final String playerName;
   private final double followDistance;
   private final boolean followOwnerAcrossDimensions;
   private boolean targetGone = false;
   private String lastCowardDecision = null;
   private boolean lastDefenderBrokeOff = false;
   private long lastDegradationReportMs = 0L;
   private String lastDegradationKey = null;
   private boolean requestedDimensionTransfer;
   private BlockPos ownerLastSeen;
   private BlockPos ownerPortal;

   public FollowPlayerTask(String playerName, double followDistance) {
      this(playerName, followDistance, false);
   }

   private FollowPlayerTask(String playerName, double followDistance, boolean followOwnerAcrossDimensions) {
      this.playerName = playerName;
      this.followDistance = followDistance;
      this.followOwnerAcrossDimensions = followOwnerAcrossDimensions;
   }

   public FollowPlayerTask(String playerName) {
      this(playerName, 2.0, true);
   }

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      PlayerEngineController mod = this.controller;
      if (this.followOwnerAcrossDimensions && mod.getOwner() instanceof ServerPlayer owner
         && owner.getServer() != null && owner.getServer().getPlayerList().getPlayer(owner.getUUID()) == owner
         && owner.getGameProfile().getName().equalsIgnoreCase(this.playerName)) {
         if (owner.level() == mod.getWorld()) {
            this.ownerLastSeen = owner.blockPosition();
            this.requestedDimensionTransfer = false;
            if (OwnerFollowTransfer.inPortal(owner.serverLevel(), this.ownerLastSeen)) {
               this.setDebugState("Waiting for the owner to enter the Nether portal");
               return null;
            }
         } else if (OwnerFollowTransfer.netherCrossing(mod.getWorld().dimension(), owner.level().dimension())) {
            if (!this.requestedDimensionTransfer) {
               this.requestedDimensionTransfer = true;
               this.ownerPortal = this.ownerLastSeen == null ? null : OwnerFollowTransfer.findPortal(mod.getWorld(), this.ownerLastSeen);
               if (this.ownerPortal == null) {
                  mod.reportAgenticProgress("Ellie could not find the Nether portal you used.", true);
               } else {
                  OwnerFollowTransfer.request(mod, owner, this);
               }
            }
            if (this.ownerPortal == null) return null;
            if (OwnerFollowTransfer.inPortal(mod.getWorld(), mod.getEntity().blockPosition())) {
               this.setDebugState("Waiting inside the owner's Nether portal");
               return null;
            }
            this.setDebugState("Walking to the owner's Nether portal");
            return new GetToBlockTask(this.ownerPortal);
         }
      }
      Optional<Vec3> lastPos = mod.getEntityTracker().getPlayerMostRecentPosition(this.playerName);
      if (lastPos.isEmpty()) {
         this.setDebugState("No player found/detected. Doing nothing until player loads into render distance.");
         return null;
      }

      Vec3 target = lastPos.get();
      if (target.closerThan(mod.getPlayer().position(), 1.0) && !mod.getEntityTracker().isPlayerLoaded(this.playerName)) {
         mod.logWarning("Failed to get to player \"" + this.playerName + "\". We moved to where we last saw them but now have no idea where they are.");
         this.targetGone = true;
         this.stop();
         return null;
      }

      Optional<Player> player = mod.getEntityTracker().getPlayerEntity(this.playerName);
      if (player.isEmpty()) {
         return new GetToBlockTask(new BlockPos((int)target.x, (int)target.y, (int)target.z), false);
      }

      Player targetPlayer = player.get();
      FollowMode mode = mod.getFollowMode();
      double distToTarget = mod.getPlayer().distanceTo(targetPlayer);
      if (mode == FollowMode.COWARD) {
         Task fleeTask = this.tickCoward(mod, distToTarget);
         if (fleeTask != null) {
            return fleeTask;
         }
      } else if (mode == FollowMode.DEFENDER) {
         this.tickDefender(mod, distToTarget);
      }

      Entity ownerVehicle = targetPlayer.getVehicle();
      Entity myVehicle = mod.getPlayer().getVehicle();
      if (myVehicle instanceof Boat && myVehicle != ownerVehicle) {
         mod.getPlayer().stopRiding();
      }

      return ownerVehicle instanceof Boat && myVehicle != ownerVehicle
         ? new EnterBoatWithOwnerTask(targetPlayer, this.followDistance)
         : new GetToEntityTask(targetPlayer, this.followDistance);
   }

   private Task tickCoward(PlayerEngineController mod, double distToTarget) {
      boolean inDanger = mod.getMobDefenseChain().isInDangerNow();
      Task fleeTask = null;
      String decision;
      if (!inDanger) {
         decision = "CLEAR";
      } else if (distToTarget < 12.0) {
         decision = "FLEE";
         fleeTask = new RunAwayFromHostilesTask(12.0, true);
         this.reportDegradation(
            mod,
            "message.playerengine.follow.coward_fleeing",
            "follow(COWARD): a hostile is pursuing me; I am fleeing while staying within leash of you and not fighting it."
         );
      } else {
         decision = "LEASH-CAP";
         this.reportDegradation(
            mod,
            "message.playerengine.follow.coward_cornered",
            "follow(COWARD): cornered — a threat is near but I am at my leash limit from you, so I stopped fleeing and stayed close instead of running off (I did not fight it)."
         );
      }

      if (!decision.equals(this.lastCowardDecision)) {
         LOGGER.info("[FollowDiag] COWARD-EVADE: {} (distToTarget={} maxLeash={})", decision, String.format("%.1f", distToTarget), 12.0);
         this.lastCowardDecision = decision;
      }

      return fleeTask;
   }

   private void tickDefender(PlayerEngineController mod, double distToTarget) {
      if (distToTarget > 16.0) {
         if (!this.lastDefenderBrokeOff) {
            LOGGER.info("[FollowDiag] DEFENDER-BREAKOFF: distToTarget={} maxChase={} (returning to leash)", String.format("%.1f", distToTarget), 16.0);
            this.lastDefenderBrokeOff = true;
            this.reportDegradation(
               mod,
               "message.playerengine.follow.defender_broke_off",
               "follow(DEFENDER): I reached my max-chase limit from you while fighting, so I broke off the chase and am returning to stay near you."
            );
         }
      } else if (this.lastDefenderBrokeOff && distToTarget <= this.followDistance) {
         this.lastDefenderBrokeOff = false;
      }
   }

   private void reportDegradation(PlayerEngineController mod, String playerKey, String modelMessage) {
      long now = System.currentTimeMillis();
      if (!playerKey.equals(this.lastDegradationKey) || now - this.lastDegradationReportMs >= 1500L) {
         this.lastDegradationReportMs = now;
         this.lastDegradationKey = playerKey;
         ServerPlayer target;
         if (mod.getOwner() instanceof ServerPlayer sp) {
            target = sp;
         } else {
            target = mod.getClosestPlayer().orElse(null);
         }

         if (target != null) {
            target.displayClientMessage(Component.translatable(playerKey), false);
         }

         AiConversationFeedback.enqueueInfo(mod, modelMessage);
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      LOGGER.info(
         "[FollowDiag] FOLLOW-TASK-STOP: targetGone={} interruptTask={} isFinished={}",
         this.targetGone,
         interruptTask != null ? interruptTask.getClass().getSimpleName() : "(none)",
         this.isFinished()
      );
      if (this.targetGone && this.controller != null && this.controller.getStepExecutorAdapter() instanceof TaskStepExecutorAdapter adapter) {
         adapter.armPendingChainCancel(StopReason.FOLLOWED_TARGET_GONE);
      } else if (interruptTask instanceof SurvivalInterruptTask
         && this.controller != null
         && this.controller.getStepExecutorAdapter() instanceof TaskStepExecutorAdapter adapter) {
         adapter.armPendingChainCancel(StopReason.CANCELLED_SUPERSEDED_BY_SURVIVAL);
      }
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof FollowPlayerTask task
         ? task.playerName.equals(this.playerName) && task.followOwnerAcrossDimensions == this.followOwnerAcrossDimensions
            && Math.abs(this.followDistance - task.followDistance) < 0.1
         : false;
   }

   @Override
   protected String toDebugString() {
      return "Going to player " + this.playerName;
   }
}
