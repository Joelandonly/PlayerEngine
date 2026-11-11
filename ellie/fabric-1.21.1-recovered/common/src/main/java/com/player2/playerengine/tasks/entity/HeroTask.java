package com.player2.playerengine.tasks.entity;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.movement.GetToEntityTask;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.util.helpers.EntityHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Protect the NPC's owner. Targets are selected using the owner's location,
 * never the bot's location or only a mob class. No global combat behavior changes.
 */
public class HeroTask extends Task {
   private static final double HERO_DEFENSE_RADIUS = 16.0;
   private static final double OWNER_REJOIN_RADIUS = 6.0;
   private static final double OWNER_FOLLOW_DISTANCE = 3.0;

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
      if (owner == null || !owner.isAlive() || owner.level() != mod.getWorld()) {
         this.clearTarget();
         this.setDebugState("Owner not available in this dimension; guarding in place.");
         return null;
      }

      // Scan around the human owner, not around Ellie. This also finds targets
      // near the owner which may lie outside Ellie's cached entity tracker.
      AABB ownerArea = owner.getBoundingBox().inflate(HERO_DEFENSE_RADIUS);
      Entity selected = null;
      for (Monster monster : mod.getWorld().getEntitiesOfClass(Monster.class, ownerArea)) {
         selected = closerOwnerThreat(mod, owner, selected, monster);
      }
      for (Slime slime : mod.getWorld().getEntitiesOfClass(Slime.class, ownerArea)) {
         selected = closerOwnerThreat(mod, owner, selected, slime);
      }

      // Pass the exact entity to combat; passing only its Java class was the
      // original reason the downstream task attacked the wrong monster.
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

   private Entity closerOwnerThreat(PlayerEngineController mod, Player owner, Entity current, Entity candidate) {
      if (candidate == null || !candidate.isAlive()
         || EntityHelper.isZombifiedPiglinFamily(candidate)
         || !mod.getEntityTracker().isEntityReachable(candidate)) {
         return current;
      }
      double candidateDistance = candidate.distanceToSqr(owner);
      if (candidateDistance > HERO_DEFENSE_RADIUS * HERO_DEFENSE_RADIUS) {
         return current;
      }
      if (current == null) {
         return candidate;
      }
      double currentDistance = current.distanceToSqr(owner);
      return candidateDistance < currentDistance
         || candidateDistance == currentDistance && candidate.getId() < current.getId()
         ? candidate : current;
   }

   private void clearTarget() {
      if (this.lastTargetId != -1) {
         Debug.logInternal("[HeroOwnerTarget] threat cleared; resuming owner guard.");
         this.lastTargetId = -1;
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
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
