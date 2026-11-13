package com.player2.playerengine.tasks.entity;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.movement.GetToEntityTask;
import com.player2.playerengine.util.Debug;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Protect the NPC's owner. Targets are selected using the owner's location,
 * never the bot's location or only a mob class. No global combat behavior changes.
 */
public class HeroTask extends Task {
   private static final double OWNER_REJOIN_RADIUS = 6.0;
   private static final double OWNER_FOLLOW_DISTANCE = 3.0;

   private final HeroTechniques techniques = new HeroTechniques();
   private boolean savedAllowBreak;
   private int lastTargetId = -1;

   @Override
   protected void onStart() {
      this.savedAllowBreak = this.controller.getBaritoneSettings().allowBreak.get();
      this.controller.getBaritoneSettings().allowBreak.set(false);
      this.lastTargetId = -1;
   }

   @Override
   protected Task onTick() {
      PlayerEngineController mod = this.controller;
      Player owner = mod.getOwner();
      if (!canGuard(mod)) {
         this.techniques.cancel();
         this.clearTarget();
         this.setDebugState("Owner not available in this dimension; guarding in place.");
         return null;
      }

      var threats = HeroTechniques.threats(mod, owner);
      if (this.techniques.tick(mod, owner, threats)) {
         this.setDebugState("Hero technique: " + this.techniques.currentTitle());
         return null;
      }
      Mob selected = threats.stream().filter(mod.getEntityTracker()::isEntityReachable).findFirst().orElse(null);
      if (selected != null) {
         if (this.lastTargetId != selected.getId()) {
            Debug.logInternal("[HeroOwnerTarget] id=" + selected.getId()
               + " type=" + selected.getType().getDescriptionId()
               + " ownerDist=" + String.format(java.util.Locale.ROOT, "%.1f", Math.sqrt(selected.distanceToSqr(owner))));
            this.lastTargetId = selected.getId();
         }
         this.setDebugState("Defending owner against " + selected.getType().getDescriptionId());
         return new KillEntityTask(selected);
      }

      this.clearTarget();
      // Do not wander or collect XP while on guard; remain close enough to
      // respond immediately when a hostile approaches the owner.
      if (mod.getPlayer().distanceToSqr(owner) > OWNER_REJOIN_RADIUS * OWNER_REJOIN_RADIUS) {
         this.setDebugState("No threats; rejoining owner.");
         return new GetToEntityTask(owner, OWNER_FOLLOW_DISTANCE);
      }
      this.setDebugState("No threats near owner; standing guard.");
      return null;
   }

   public boolean canGuard(PlayerEngineController mod) {
      Player owner = mod.getOwner();
      return owner != null && owner.isAlive() && owner.level() == mod.getWorld();
   }

   private void clearTarget() {
      if (this.lastTargetId != -1) {
         Debug.logInternal("[HeroOwnerTarget] threat cleared; resuming owner guard.");
         this.lastTargetId = -1;
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.techniques.cancel();
      this.controller.getBaritoneSettings().allowBreak.set(this.savedAllowBreak);
      this.clearTarget();
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof HeroTask;
   }

   @Override
   protected String toDebugString() {
      return "Protecting owner from closest hostile.";
   }
}
