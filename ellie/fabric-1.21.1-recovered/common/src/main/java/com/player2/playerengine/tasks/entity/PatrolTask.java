package com.player2.playerengine.tasks.entity;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.pathing.goals.GoalBlock;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.base.TrackedTaskOutcomeProvider;
import com.player2.playerengine.tasks.movement.CustomBaritoneGoalTask;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class PatrolTask extends Task implements TrackedTaskOutcomeProvider {
   private static final double INTERCEPT_RADIUS = 10;
   private static final double CHASE_RADIUS = 12;
   private static final int CHASE_TICKS = 160;
   private static final int STUCK_TICKS = 240;
   private final BlockPos start;
   private final BlockPos end;
   private final ResourceKey<Level> dimension;
   private final HeroTechniques techniques = new HeroTechniques();
   private BlockPos destination;
   private BlockPos resumeAt;
   private Mob engaged;
   private long chaseUntil;
   private long guardAgainAt;
   private Vec3 lastMovement;
   private int stoppedTicks;
   private boolean allowBreak;
   private boolean allowPlace;
   private String failure;

   public PatrolTask(BlockPos start, BlockPos end, ResourceKey<Level> dimension) {
      this.start = start.immutable();
      this.end = end.immutable();
      this.destination = this.end;
      this.dimension = dimension;
   }

   @Override
   protected void onStart() {
      var settings = this.controller.getBaritoneSettings();
      this.allowBreak = settings.allowBreak.get();
      this.allowPlace = settings.allowPlace.get();
      settings.allowBreak.set(false);
      settings.allowPlace.set(false);
      this.lastMovement = this.controller.getEntity().position();
      this.stoppedTicks = 0;
   }

   public boolean canGuard(PlayerEngineController mod) {
      var owner = mod.getOwner();
      return this.failure == null && mod.getWorld().dimension().equals(this.dimension)
         && owner != null && owner.isAlive();
   }

   @Override
   protected Task onTick() {
      var mod = this.controller;
      if (!mod.getWorld().dimension().equals(this.dimension)) {
         this.failure = "Patrol left its original dimension.";
         return null;
      }
      var ellie = mod.getEntity();
      var feet = ellie.blockPosition();
      long now = mod.getWorld().getGameTime();
      if (this.resumeAt != null && this.engaged != null
         && (!this.engaged.isAlive() || now >= this.chaseUntil
            || !insideLeash(this.resumeAt, this.engaged.position(), CHASE_RADIUS)
            || !insideLeash(this.resumeAt, ellie.position(), CHASE_RADIUS))) {
         endInterception(now);
      }

      if (this.resumeAt != null && this.engaged == null && feet.equals(this.resumeAt)) {
         this.resumeAt = null;
         resetMovement();
      }

      if (now >= this.guardAgainAt && canGuard(mod) && (this.resumeAt == null || this.engaged != null)) {
         BlockPos post = this.resumeAt == null ? feet : this.resumeAt;
         double radius = this.resumeAt == null ? INTERCEPT_RADIUS : CHASE_RADIUS;
         Vec3 center = Vec3.atBottomCenterOf(post);
         List<Mob> threats = HeroTechniques.threats(mod, mod.getOwner(), center, radius,
            mob -> insideLeash(post, mob.position(), radius));
         if (this.engaged != null && !threats.contains(this.engaged)) {
            endInterception(now);
         } else {
            if (this.engaged == null && !threats.isEmpty()) {
               this.engaged = threats.getFirst();
               this.resumeAt = feet;
               this.chaseUntil = now + CHASE_TICKS;
            }
            if (this.engaged != null) {
               if (this.techniques.tick(mod, mod.getOwner(), threats)) {
                  this.setDebugState("Patrol technique " + this.techniques.currentTitle());
                  return null;
               }
               if (mod.getEntityTracker().isEntityReachable(this.engaged)) {
                  this.setDebugState("Intercepting " + this.engaged.getType().getDescriptionId());
                  return new KillEntityTask(this.engaged);
               }
               endInterception(now);
            }
         }
      } else if (this.engaged != null) {
         endInterception(now);
      }

      if (this.resumeAt == null && feet.equals(this.destination)) {
         this.destination = this.destination.equals(this.end) ? this.start : this.end;
         resetMovement();
      }

      BlockPos goal = this.resumeAt == null ? this.destination : this.resumeAt;
      if (ellie.position().distanceToSqr(this.lastMovement) > 1) resetMovement();
      if (++this.stoppedTicks >= STUCK_TICKS) {
         this.failure = "No walkable patrol progress from " + feet.toShortString() + " toward " + goal.toShortString();
         return null;
      }
      this.setDebugState((this.resumeAt == null ? "Patrolling toward " : "Returning to patrol at ") + goal.toShortString());
      return new WalkToPost(goal);
   }

   static boolean insideLeash(BlockPos post, Vec3 position, double radius) {
      Vec3 offset = position.subtract(Vec3.atBottomCenterOf(post));
      return offset.horizontalDistanceSqr() <= radius * radius && Math.abs(offset.y) <= 3;
   }

   private void endInterception(long now) {
      this.engaged = null;
      this.techniques.cancel();
      this.guardAgainAt = now + 20;
      resetMovement();
   }

   private void resetMovement() {
      this.lastMovement = this.controller.getEntity().position();
      this.stoppedTicks = 0;
   }

   public String status() {
      return "Patrol " + this.start.toShortString() + " ↔ " + this.end.toShortString()
         + ", heading toward " + this.destination.toShortString();
   }

   @Override protected void onStop(Task next) {
      this.techniques.cancel();
      this.controller.getBaritoneSettings().allowBreak.set(this.allowBreak);
      this.controller.getBaritoneSettings().allowPlace.set(this.allowPlace);
   }

   @Override public boolean isFinished() { return this.failure != null; }
   @Override public boolean isTerminal() { return this.isFinished(); }
   @Override public boolean isSuccessful() { return false; }
   @Override public String controlledReason() { return this.failure; }
   @Override protected boolean isEqual(Task other) {
      return other instanceof PatrolTask task && this.start.equals(task.start) && this.end.equals(task.end)
         && this.dimension.equals(task.dimension);
   }
   @Override protected String toDebugString() { return status(); }

   private static final class WalkToPost extends CustomBaritoneGoalTask {
      private final BlockPos pos;
      WalkToPost(BlockPos pos) { super(false); this.pos = pos; }
      @Override protected Goal newGoal(PlayerEngineController mod) { return new GoalBlock(this.pos); }
      @Override protected boolean isEqual(Task other) { return other instanceof WalkToPost walk && this.pos.equals(walk.pos); }
      @Override protected String toDebugString() { return "Walking to patrol post " + this.pos.toShortString(); }
   }
}
