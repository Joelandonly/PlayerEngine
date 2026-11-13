package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.pathing.goals.GoalNear;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.eventbus.EventBus;
import com.player2.playerengine.eventbus.Subscription;
import com.player2.playerengine.eventbus.events.EntityDeathEvent;
import com.player2.playerengine.supervisor.ProjectTaskGateway;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.base.TrackedTaskOutcomeProvider;
import com.player2.playerengine.tasks.entity.KillEntitiesTask;
import com.player2.playerengine.tasks.movement.CustomBaritoneGoalTask;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.time.TimerGame;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;

public class AttackPlayerOrMobCommand extends Command {
   public AttackPlayerOrMobCommand() throws CommandException {
      super(
         "attack",
         "Attacks a specified player or mob. Example usages: @attack zombie 5 to attack and kill 5 zombies, @attack Player to attack a player with username=Player",
         new Arg<>(String.class, "name"),
         new Arg<>(Integer.class, "count", 1, 1)
      );
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      if (mod.getPlayer().getVehicle() instanceof Boat) {
         throw new CommandException("Cannot attack while riding a boat. Run `leaveboat` then retry.");
      }

      String nameToAttack = parser.get(String.class);
      int countToAttack = parser.get(Integer.class);
      ProjectTaskGateway.run(mod, new AttackPlayerOrMobCommand.AttackAndGetDropsTask(nameToAttack, countToAttack), () -> this.finish());
   }

   private static class AttackAndGetDropsTask extends ResourceTask implements TrackedTaskOutcomeProvider {
      private final String toKill;
      private final Task killTask;
      private final LocalMobHunt localHunt;
      private int mobsKilledCount;
      private int mobKillTargetCount;
      private TimerGame forceCollectTimer = new TimerGame(2.0);
      private Subscription<EntityDeathEvent> onMobDied;
      private Predicate<Entity> shouldAttackPredicate;
      private Set<Entity> trackedDeadEntities = new HashSet<>();
      private static ItemTarget[] drops = new ItemTarget[]{
         new ItemTarget("rotten_flesh", 9999),
         new ItemTarget("bone", 9999),
         new ItemTarget("string", 9999),
         new ItemTarget("spider_eye", 9999),
         new ItemTarget("gunpowder", 9999),
         new ItemTarget("slime_ball", 9999),
         new ItemTarget("ender_pearl", 9999),
         new ItemTarget("blaze_powder", 9999),
         new ItemTarget("ghast_tear", 9999),
         new ItemTarget("magma_cream", 9999),
         new ItemTarget("ender_eye", 9999),
         new ItemTarget("speckled_melon", 9999),
         new ItemTarget("gold_nugget", 9999),
         new ItemTarget("iron_nugget", 9999),
         new ItemTarget("porkchop", 9999),
         new ItemTarget("beef", 9999),
         new ItemTarget("chicken", 9999),
         new ItemTarget("mutton", 9999),
         new ItemTarget("rabbit", 9999)
      };

      public AttackAndGetDropsTask(String toKill, int killCount) {
         super(drops);
         this.toKill = toKill;
         this.mobKillTargetCount = killCount;
         EntityType<?> targetType = EntityType.byString(toKill.toLowerCase(Locale.ROOT)).orElse(null);
         this.shouldAttackPredicate = entity -> {
            if (this.mobsKilledCount >= this.mobKillTargetCount) {
               return false;
            }

            if (entity instanceof Player) {
               String playerName = entity.getName().getString();
               if (playerName != null && playerName.equalsIgnoreCase(toKill)) {
                  return true;
               }
            }

            String name = entity.getType().toShortString();
            return entity.getType() == targetType || name != null && name.equals(toKill);
         };
         this.localHunt = targetType != null && targetType != EntityType.PLAYER ? new LocalMobHunt() : null;
         this.killTask = this.localHunt == null ? new KillEntitiesTask(this.shouldAttackPredicate) : this.localHunt;
      }

      @Override
      protected boolean shouldAvoidPickingUp(PlayerEngineController mod) {
         return this.localHunt != null && !this.localHunt.inArea(mod.getPlayer());
      }

      @Override
      protected double getPickupRange(PlayerEngineController mod) {
         double range = super.getPickupRange(mod);
         return this.localHunt == null ? range : range < 0 ? 10 : Math.min(10, range);
      }

      @Override
      protected void onResourceStart(PlayerEngineController mod) {
         if (this.localHunt != null) this.localHunt.ensureArea(mod);
         this.forceCollectTimer.reset();
         this.onMobDied = EventBus.subscribe(EntityDeathEvent.class, evt -> {
            Entity diedEntity = evt.entity;
            if (!this.trackedDeadEntities.contains(diedEntity) && this.canHunt(diedEntity)) {
               this.markEntityDead(diedEntity);
            }
         });
      }

      private void markEntityDead(Entity entity) {
         this.trackedDeadEntities.add(entity);
         this.mobsKilledCount++;
      }

      @Override
      public boolean isFinished() {
         return this.failureReason != null || this.mobsKilledCount >= this.mobKillTargetCount && this.forceCollectTimer.elapsed();
      }

      @Override
      public boolean isTerminal() {
         return this.isFinished();
      }

      @Override
      public boolean isSuccessful() {
         return this.failureReason == null && this.isFinished();
      }

      @Override
      public String controlledReason() {
         return this.failureReason == null ? "Killed " + this.mobsKilledCount + " " + this.toKill : this.failureReason;
      }

      private boolean canHunt(Entity entity) {
         return this.shouldAttackPredicate.test(entity) && (this.localHunt == null || this.localHunt.inArea(entity));
      }

      @Override
      protected Task onResourceTick(PlayerEngineController mod) {
         if (this.localHunt != null) this.localHunt.ensureArea(mod);
         for (Entity entity : mod.getWorld().getAllEntities()) {
            if (!entity.isAlive() && !this.trackedDeadEntities.contains(entity) && this.canHunt(entity)) {
               this.markEntityDead(entity);
            }
         }

         this.trackedDeadEntities.removeIf(entityx -> entityx.isAlive());
         if (this.mobsKilledCount < this.mobKillTargetCount) {
            this.forceCollectTimer.reset();
         }

         return this.mobsKilledCount < this.mobKillTargetCount ? this.killTask : null;
      }

      @Override
      protected void onResourceStop(PlayerEngineController mod, Task interruptTask) {
         EventBus.unsubscribe(this.onMobDied);
         this.trackedDeadEntities.clear();
      }

      @Override
      protected boolean isEqualResource(ResourceTask other) {
         return other instanceof AttackPlayerOrMobCommand.AttackAndGetDropsTask task
            ? task.toKill.equals(this.toKill) && task.mobKillTargetCount == this.mobKillTargetCount
            : false;
      }

      @Override
      protected String toDebugStringName() {
         return "Attacking and collect items from " + this.toKill + " x " + this.mobKillTargetCount;
      }

      private final class LocalMobHunt extends KillEntitiesTask {
         private static final Direction[] SEARCH_DIRECTIONS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
         };
         private static final int[] SEARCH_RADII = {12, 24, 36};
         private static final int SEARCH_STEPS = SEARCH_DIRECTIONS.length * SEARCH_RADII.length;
         private static final int SEARCH_RADIUS = 40;
         private static final int SEARCH_HEIGHT = 16;
         private static final int WAYPOINT_TOLERANCE = 4;
         private static final int WAYPOINT_TICKS = 20 * 12;
         private static final int WAIT_FOR_SPAWN_TICKS = 20 * 15;

         private ResourceKey<Level> dimension;
         private BlockPos center;
         private boolean anchoredOnTarget;
         private int step;
         private HuntWaypoint waypoint;
         private long deadline;

         LocalMobHunt() {
            super(AttackAndGetDropsTask.this::canHunt);
         }

         void ensureArea(PlayerEngineController mod) {
            ResourceKey<Level> current = mod.getWorld().dimension();
            if (!current.equals(this.dimension)) {
               this.dimension = current;
               this.center = mod.getPlayer().blockPosition();
               this.anchoredOnTarget = false;
               this.resetSearch();
               this.restartSweep();
            }
         }

         boolean inArea(Entity entity) {
            return this.center != null && entity.level().dimension().equals(this.dimension)
               && insideHuntArea(this.center, entity.blockPosition(), SEARCH_RADIUS, SEARCH_HEIGHT);
         }

         @Override
         protected boolean isValid(PlayerEngineController mod, Entity target) {
            return this.inArea(target) && super.isValid(mod, target);
         }

         @Override
         protected Task getGoalTask(Entity target) {
            if (!this.anchoredOnTarget) {
               this.center = target.blockPosition();
               this.anchoredOnTarget = true;
            }
            this.restartSweep();
            return super.getGoalTask(target);
         }

         private void restartSweep() {
            this.step = 0;
            this.waypoint = null;
            this.deadline = 0;
         }

         @Override
         protected Task getWanderTask(PlayerEngineController mod) {
            long tick = mod.getWorld().getGameTime();
            BlockPos feet = mod.getPlayer().blockPosition();
            while (this.step <= SEARCH_STEPS) {
               if (this.waypoint == null) {
                  BlockPos next = this.step == SEARCH_STEPS ? this.center : this.center.relative(
                     SEARCH_DIRECTIONS[this.step % SEARCH_DIRECTIONS.length],
                     SEARCH_RADII[this.step / SEARCH_DIRECTIONS.length]
                  );
                  this.waypoint = new HuntWaypoint(next, WAYPOINT_TOLERANCE);
                  this.deadline = tick + WAYPOINT_TICKS;
               }
               if (!this.waypoint.goal.isInGoal(feet) && tick < this.deadline) {
                  this.setDebugState("Searching for " + toKill + " near " + this.center.toShortString());
                  return this.waypoint;
               }
               this.waypoint = null;
               this.step++;
               if (this.step > SEARCH_STEPS) this.deadline = tick + WAIT_FOR_SPAWN_TICKS;
            }
            if (tick >= this.deadline) {
               recordFailureReason("Local search near " + this.center.toShortString()
                  + " ended after " + mobsKilledCount + "/" + mobKillTargetCount + " " + toKill + " kills");
            } else {
               this.setDebugState("Waiting for " + toKill + " near " + this.center.toShortString());
            }
            return null;
         }
      }
   }

   private static boolean insideHuntArea(BlockPos center, BlockPos position, int radius, int height) {
      long dx = (long) position.getX() - center.getX();
      long dz = (long) position.getZ() - center.getZ();
      return Math.abs(position.getY() - center.getY()) <= height && dx * dx + dz * dz <= (long) radius * radius;
   }

   private static final class HuntWaypoint extends CustomBaritoneGoalTask {
      private final GoalNear goal;

      HuntWaypoint(BlockPos position, int tolerance) {
         super(false);
         this.goal = new GoalNear(position, tolerance);
      }

      @Override
      protected Goal newGoal(PlayerEngineController mod) {
         return this.goal;
      }

      @Override
      protected boolean isEqual(Task other) {
         return other instanceof HuntWaypoint waypoint && this.goal.getGoalPos().equals(waypoint.goal.getGoalPos());
      }

      @Override
      protected String toDebugString() {
         return "Scouting near " + this.goal.getGoalPos().toShortString();
      }
   }
}
