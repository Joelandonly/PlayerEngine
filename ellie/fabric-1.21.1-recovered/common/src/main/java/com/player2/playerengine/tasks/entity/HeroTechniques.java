package com.player2.playerengine.tasks.entity;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.observatory.ObservatoryTelemetry;
import com.player2.playerengine.util.helpers.EntityHelper;
import com.player2.playerengine.util.helpers.LookHelper;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

final class HeroTechniques {
   private static final DustParticleOptions CRIMSON = new DustParticleOptions(new Vector3f(0.92F, 0.02F, 0.13F), 1.3F);
   private static final DustParticleOptions INK = new DustParticleOptions(new Vector3f(0.12F, 0.025F, 0.18F), 1.6F);
   private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.62F, 0.18F, 0.95F), 1.45F);
   private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.63F, 0.12F), 1.25F);
   private static final double HORIZONTAL_AGGRO = 32.0;
   private static final double VERTICAL_AGGRO = 3.0;
   private static final double CAST_RANGE = 14.0;
   private static final int MAX_HITS = 6;
   private Technique previous;
   private Technique casting;
   private Mob focus;
   private long nextCast;
   private long started;

   enum Technique {
      CRIMSON_GAZE("Crimson Gaze", 10, 64, 6, SoundEvents.AMETHYST_BLOCK_CHIME),
      CROW_MURDER("Crow Murder", 14, 90, 8, SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM),
      BLACK_FLAME("Black Flame", 12, 90, 13, SoundEvents.BLAZE_SHOOT),
      PHANTOM_BLADE("Phantom Blade", 6, 42, 11, SoundEvents.PLAYER_ATTACK_SWEEP),
      MOON_PRISON("Moon Prison", 16, 110, 7, SoundEvents.EVOKER_CAST_SPELL),
      SPIRIT_ARMOR("Spirit Armor", 13, 125, 9, SoundEvents.RAVAGER_ROAR),
      SCARLET_LANCE("Scarlet Lance", 11, 72, 12, SoundEvents.WARDEN_SONIC_BOOM),
      MIRROR_STEP("Mirror Step", 8, 54, 10, SoundEvents.ENDERMAN_TELEPORT),
      GRAVITY_WELL("Gravity Well", 16, 104, 6, SoundEvents.WITHER_SHOOT),
      THUNDER_CHAIN("Thunder Chain", 12, 95, 8, SoundEvents.LIGHTNING_BOLT_IMPACT),
      COMET_FALL("Comet Fall", 17, 92, 13, SoundEvents.DRAGON_FIREBALL_EXPLODE),
      CRIMSON_FAN("Crimson Fan", 9, 68, 9, SoundEvents.ARROW_SHOOT);

      final String title;
      final int windup;
      final int recovery;
      final float damage;
      final SoundEvent impact;

      Technique(String title, int windup, int recovery, float damage, SoundEvent impact) {
         this.title = title;
         this.windup = windup;
         this.recovery = recovery;
         this.damage = damage;
         this.impact = impact;
      }
   }

   static List<Mob> threats(PlayerEngineController mod, Player owner) {
      return threats(mod, owner, owner.position(), HORIZONTAL_AGGRO, mob -> withinAggro(mob.position().subtract(owner.position())));
   }

   static List<Mob> threats(PlayerEngineController mod, Player owner, Vec3 center, double radius, Predicate<Mob> withinGuard) {
      var area = net.minecraft.world.phys.AABB.ofSize(center, radius * 2 + 2, 8, radius * 2 + 2);
      return mod.getWorld().getEntitiesOfClass(Mob.class, area, mob -> withinGuard.test(mob) && threatens(mod, owner, mob))
         .stream().sorted(Comparator.comparingDouble((Mob mob) -> mob.distanceToSqr(center)).thenComparingInt(Mob::getId)).toList();
   }

   private static boolean threatens(PlayerEngineController mod, Player owner, Mob mob) {
      LivingEntity ellie = mod.getEntity();
      if (!mob.isAlive() || mob.level() != ellie.level() || mob.isAlliedTo(owner) || mob.isAlliedTo(ellie)
         || owner.isAlliedTo(mob) || ellie.isAlliedTo(mob) || mob instanceof OwnableEntity
         || EntityHelper.isZombifiedPiglinFamily(mob)) return false;
      if (mob instanceof NeutralMob && mob.getTarget() != owner && mob.getTarget() != ellie) return false;
      return EntityHelper.isProbablyHostileToPlayer(mod, mob);
   }

   static boolean withinAggro(Vec3 offset) {
      return offset.horizontalDistanceSqr() <= HORIZONTAL_AGGRO * HORIZONTAL_AGGRO
         && Math.abs(offset.y) <= VERTICAL_AGGRO;
   }

   private static boolean visible(LivingEntity ellie, Mob mob, double range) {
      return ellie.distanceToSqr(mob) <= range * range && ellie.hasLineOfSight(mob);
   }

   boolean tick(PlayerEngineController mod, Player owner, List<Mob> threats) {
      LivingEntity ellie = mod.getEntity();
      if (!(ellie.level() instanceof ServerLevel level)) return false;
      long now = level.getGameTime();
      if (!ellie.isAlive() || !ellie.onGround() && !ellie.isInWater() || mod.getFoodChain().needsToEat()
         || !mod.getBaritone().getPathingBehavior().isSafeToCancel()) {
         cancel();
         return false;
      }

      if (casting == null) {
         if (now < nextCast) return false;
         focus = threats.stream().filter(mob -> visible(ellie, mob, CAST_RANGE)).findFirst().orElse(null);
         if (focus == null) return false;
         casting = chooseTechnique(threats, ellie);
         started = now;
         LookHelper.lookAt(mod, focus.getEyePosition());
         sound(level, ellie.position(), SoundEvents.WARDEN_SONIC_CHARGE, 0.6F, 1.6F);
         eyeSeal(level, ellie);
      }

      double range = casting == Technique.MIRROR_STEP ? 5 : CAST_RANGE;
      if (!threats.contains(focus) || !visible(ellie, focus, range)) {
         cancel();
         nextCast = now + 12;
         return false;
      }
      LookHelper.lookAt(mod, focus.getEyePosition());
      int age = (int)(now - started);
      if (age < casting.windup) {
         if ((age & 1) == 0) windup(level, ellie, age);
         return true;
      }

      ellie.swing(InteractionHand.MAIN_HAND);
      impact(mod, owner, level, ellie, threats);
      previous = casting;
      nextCast = now + casting.recovery;
      cancel();
      return true;
   }

   String currentTitle() {
      return casting == null ? previous.title : casting.title;
   }

   void cancel() {
      focus = null;
      casting = null;
   }

   private Technique chooseTechnique(List<Mob> threats, LivingEntity ellie) {
      double distance = ellie.distanceTo(focus);
      long crowd = threats.stream().filter(mob -> visible(ellie, mob, CAST_RANGE)
         && mob.distanceToSqr(focus) <= 25).count();
      boolean fusing = focus instanceof Creeper creeper && creeper.getSwellDir() > 0;
      return select(previous, distance, crowd, ellie.getHealth() <= ellie.getMaxHealth() * 0.4F, fusing);
   }

   static Technique select(Technique previous, double distance, long crowd, boolean lowHealth, boolean fusing) {
      if (lowHealth && previous != Technique.SPIRIT_ARMOR) return Technique.SPIRIT_ARMOR;
      if (fusing && previous != Technique.PHANTOM_BLADE && distance <= 5) {
         return Technique.PHANTOM_BLADE;
      }
      if (crowd >= 3) {
         return switch (previous) {
            case MOON_PRISON -> Technique.GRAVITY_WELL;
            case GRAVITY_WELL -> Technique.THUNDER_CHAIN;
            case THUNDER_CHAIN -> Technique.CROW_MURDER;
            case null, default -> Technique.MOON_PRISON;
         };
      }
      if (distance <= 4.5) return previous == Technique.PHANTOM_BLADE ? Technique.MIRROR_STEP : Technique.PHANTOM_BLADE;
      if (distance >= 8) {
         return switch (previous) {
            case SCARLET_LANCE -> Technique.COMET_FALL;
            case COMET_FALL -> Technique.CRIMSON_FAN;
            case null, default -> Technique.SCARLET_LANCE;
         };
      }
      if (crowd == 2) return previous == Technique.THUNDER_CHAIN ? Technique.CROW_MURDER : Technique.THUNDER_CHAIN;
      return previous == Technique.BLACK_FLAME ? Technique.CRIMSON_GAZE : Technique.BLACK_FLAME;
   }

   private void windup(ServerLevel level, LivingEntity ellie, int age) {
      Vec3 start = ellie.position().add(0, 0.15, 0);
      Vec3 end = center(focus);
      double t = (double) age / casting.windup;
      switch (casting) {
         case CRIMSON_GAZE -> ring(level, end, 0.95 - t * 0.65, 12, t * 3, CRIMSON);
         case CROW_MURDER -> {
            for (int i = 0; i < 4; i++) {
               double theta = age * 0.55 + i * Math.PI / 2;
               Vec3 point = end.add(Math.cos(theta) * (2.8 - t * 2.4), 1.1 - t, Math.sin(theta) * (2.8 - t * 2.4));
               dot(level, INK, point);
               dot(level, ParticleTypes.SQUID_INK, point);
            }
         }
         case BLACK_FLAME -> {
            ring(level, end.add(0, -0.5 + t, 0), 0.8 - t * 0.45, 10, -age * 0.3, INK);
            dot(level, ParticleTypes.SOUL_FIRE_FLAME, end.add(0, t, 0));
         }
         case PHANTOM_BLADE -> arc(level, start.add(0, 0.9, 0), end.subtract(start), 0.8 + t * 1.2, INK);
         case MOON_PRISON -> ring(level, end.add(0, t - 0.5, 0), 3.5 - t * 2.4, 16, t * 4, VIOLET);
         case SPIRIT_ARMOR -> guardian(level, ellie, 0.55 + t * 0.45);
         case SCARLET_LANCE -> {
            Vec3 eye = ellie.getEyePosition();
            beam(level, eye, eye.lerp(end, 0.2 + t * 0.5), INK, CRIMSON);
         }
         case MIRROR_STEP -> {
            Vec3 side = horizontalSide(end.subtract(start));
            for (int sign : new int[]{-1, 1}) {
               ring(level, end.add(side.scale(sign * (2.6 - t * 1.7))), 0.6, 9, age * 0.35, INK);
            }
         }
         case GRAVITY_WELL -> {
            ring(level, end, 4.3 - t * 3.5, 18, age * 0.4, VIOLET);
            ring(level, end.add(0, 0.55, 0), 3.6 - t * 3, 12, -age * 0.32, INK);
         }
         case THUNDER_CHAIN -> {
            ring(level, end.add(0, 0.65, 0), 0.9, 10, age * 0.7, GOLD);
            dot(level, ParticleTypes.ELECTRIC_SPARK, end.add(0, t, 0));
         }
         case COMET_FALL -> {
            Vec3 comet = end.add(1.7 * (1 - t), 4.8 * (1 - t), -1.3 * (1 - t));
            beam(level, comet.add(0.5, 1, -0.35), comet, GOLD, CRIMSON);
            dot(level, ParticleTypes.FLAME, comet);
         }
         case CRIMSON_FAN -> arc(level, start.add(0, 1, 0), end.subtract(start), 1 + t * 1.8, CRIMSON);
      }
   }

   private void impact(PlayerEngineController mod, Player owner, ServerLevel level, LivingEntity ellie, List<Mob> threats) {
      Vec3 source = center(ellie);
      Vec3 target = center(focus);
      Vec3 aim = target.subtract(source);
      sound(level, casting == Technique.SPIRIT_ARMOR ? source : target, casting.impact, 1.0F, 0.82F);
      ObservatoryTelemetry.event("HERO_TECHNIQUE", casting.title + " -> " + focus.getType().getDescriptionId());
      switch (casting) {
         case CRIMSON_GAZE -> {
            beam(level, ellie.getEyePosition(), target, CRIMSON, INK);
            ring(level, target, 1.4, 22, 0, CRIMSON);
         }
         case CROW_MURDER -> {
            for (int i = 0; i < 5; i++) {
               double angle = i * Math.PI * 2 / 5;
               beam(level, target.add(Math.cos(angle) * 2.6, 1.5, Math.sin(angle) * 2.6), target, INK, ParticleTypes.SQUID_INK);
            }
         }
         case BLACK_FLAME -> {
            for (int layer = 0; layer < 4; layer++) {
               ring(level, target.add(0, layer * 0.55 - 0.65, 0), 1.2 - layer * 0.22, 12, layer * 0.65, INK);
            }
            burst(level, ParticleTypes.SOUL_FIRE_FLAME, target, 28, 0.36);
         }
         case PHANTOM_BLADE -> {
            arc(level, source, aim, 3.7, CRIMSON);
            dot(level, ParticleTypes.SWEEP_ATTACK, target);
         }
         case MOON_PRISON -> {
            for (int y = 0; y < 4; y++) ring(level, target.add(0, y * 0.43 - 0.65, 0), 3.5 - y * 0.36, 16, y * 0.28, VIOLET);
            burst(level, ParticleTypes.REVERSE_PORTAL, target, 20, 0.5);
         }
         case SPIRIT_ARMOR -> {
            guardian(level, ellie, 1.25);
            ring(level, ellie.position().add(0, 0.3, 0), 4.6, 30, 0, VIOLET);
            ellie.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0, false, false));
         }
         case SCARLET_LANCE -> {
            beam(level, ellie.getEyePosition(), target, CRIMSON, VIOLET);
            ring(level, target, 1.2, 20, 0, CRIMSON);
            burst(level, ParticleTypes.CRIT, target, 26, 0.42);
         }
         case MIRROR_STEP -> {
            Vec3 side = horizontalSide(aim);
            for (int sign : new int[]{-1, 1}) {
               Vec3 echo = target.add(side.scale(sign * 2.0));
               burst(level, ParticleTypes.REVERSE_PORTAL, echo, 14, 0.24);
               beam(level, echo.add(0, 0.7, 0), target, INK, VIOLET);
            }
            dot(level, ParticleTypes.SWEEP_ATTACK, target);
         }
         case GRAVITY_WELL -> {
            for (int i = 1; i <= 3; i++) ring(level, target, i * 1.3, 14, i * 0.4, VIOLET);
            burst(level, ParticleTypes.REVERSE_PORTAL, target, 28, 0.45);
         }
         case THUNDER_CHAIN -> thunderChain(mod, owner, level, ellie, threats, source);
         case COMET_FALL -> {
            beam(level, target.add(2.2, 5.4, -1.5), target, GOLD, CRIMSON);
            ring(level, target.add(0, -0.45, 0), 1.7, 24, 0, GOLD);
            burst(level, ParticleTypes.FLAME, target, 24, 0.45);
         }
         case CRIMSON_FAN -> {
            Vec3 side = horizontalSide(aim);
            for (int i = -2; i <= 2; i++) {
               beam(level, source.add(side.scale(i * 0.23)), target.add(side.scale(i * 1.35)), CRIMSON, GOLD);
            }
         }
      }

      if (casting == Technique.THUNDER_CHAIN) return;
      int hits = 0;
      if (casting != Technique.SPIRIT_ARMOR || inAttackShape(focus, ellie, source, target, aim)) {
         if (hit(mod, owner, ellie, focus)) hits++;
      }
      for (Mob mob : threats) {
         if (hits >= MAX_HITS) break;
         if (mob == focus || !inAttackShape(mob, ellie, source, target, aim) || !hit(mod, owner, ellie, mob)) continue;
         if (casting == Technique.GRAVITY_WELL && mob.isAlive()) {
            Vec3 pull = target.subtract(center(mob)).multiply(1, 0, 1).normalize();
            mob.push(pull.x * 0.45, 0, pull.z * 0.45);
         }
         hits++;
      }
   }

   private boolean inAttackShape(Mob mob, LivingEntity ellie, Vec3 source, Vec3 target, Vec3 aim) {
      return switch (casting) {
         case CROW_MURDER -> center(mob).distanceToSqr(target) <= 9;
         case PHANTOM_BLADE -> ellie.distanceToSqr(mob) <= 25
            && center(mob).subtract(source).normalize().dot(aim.normalize()) >= 0.4;
         case MOON_PRISON, GRAVITY_WELL -> center(mob).distanceToSqr(target) <= 16;
         case CRIMSON_FAN -> {
            Vec3 offset = center(mob).subtract(source);
            Vec3 direction = aim.normalize();
            double along = offset.dot(direction);
            yield along > 0 && along <= aim.length() + 1 && offset.subtract(direction.scale(along)).lengthSqr() <= 0.08 * along * along;
         }
         case SPIRIT_ARMOR -> ellie.distanceToSqr(mob) <= 25;
         case SCARLET_LANCE -> {
            Vec3 offset = center(mob).subtract(source);
            Vec3 direction = aim.normalize();
            double along = offset.dot(direction);
            yield along > 0 && along < aim.length() + 1.5 && offset.subtract(direction.scale(along)).lengthSqr() <= 1;
         }
         default -> false;
      };
   }

   private boolean hit(PlayerEngineController mod, Player owner, LivingEntity ellie, Mob mob) {
      if (!threatens(mod, owner, mob) || !visible(ellie, mob, CAST_RANGE)
         || !mob.hurt(ellie.damageSources().mobAttack(ellie), casting.damage)) return false;
      switch (casting) {
         case CRIMSON_GAZE -> {
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 85, 2));
            mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 85, 1));
         }
         case MIRROR_STEP -> mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 65, 1));
         case THUNDER_CHAIN -> mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 45, 1));
         case MOON_PRISON -> mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 90, 3));
         case PHANTOM_BLADE, SPIRIT_ARMOR -> mob.knockback(1.25, ellie.getX() - mob.getX(), ellie.getZ() - mob.getZ());
         default -> {}
      }
      return true;
   }

   private void thunderChain(PlayerEngineController mod, Player owner, ServerLevel level, LivingEntity ellie,
                             List<Mob> threats, Vec3 source) {
      var struck = new java.util.HashSet<Mob>();
      Mob next = focus;
      for (int jump = 0; next != null && jump < 4; jump++) {
         Vec3 destination = center(next);
         beam(level, source, destination, GOLD, ParticleTypes.ELECTRIC_SPARK);
         hit(mod, owner, ellie, next);
         struck.add(next);
         Mob previousTarget = next;
         next = threats.stream().filter(mob -> !struck.contains(mob) && threatens(mod, owner, mob)
            && visible(ellie, mob, CAST_RANGE) && previousTarget.distanceToSqr(mob) <= 25
            && previousTarget.hasLineOfSight(mob))
            .min(Comparator.comparingDouble(previousTarget::distanceToSqr)).orElse(null);
         source = destination;
      }
   }

   private static Vec3 horizontalSide(Vec3 aim) {
      return new Vec3(-aim.z, 0, aim.x).normalize();
   }

   private static void eyeSeal(ServerLevel level, LivingEntity ellie) {
      Vec3 forward = ellie.getLookAngle().multiply(1, 0, 1).normalize();
      Vec3 side = new Vec3(forward.z, 0, -forward.x);
      Vec3 eye = ellie.getEyePosition().add(forward.scale(0.48));
      for (int i = 0; i < 16; i++) {
         double a = Math.PI * 2 * i / 16;
         dot(level, CRIMSON, eye.add(side.scale(Math.cos(a) * 0.35)).add(0, Math.sin(a) * 0.18, 0));
      }
      for (int i = 0; i < 3; i++) {
         double a = Math.PI * 2 * i / 3;
         dot(level, INK, eye.add(side.scale(Math.cos(a) * 0.20)).add(0, Math.sin(a) * 0.11, 0));
      }
      dot(level, CRIMSON, eye);
   }

   private static void guardian(ServerLevel level, LivingEntity ellie, double scale) {
      Vec3 root = ellie.position();
      Vec3 forward = ellie.getLookAngle().multiply(1, 0, 1).normalize();
      Vec3 side = new Vec3(forward.z, 0, -forward.x);
      for (int i = 0; i < 5; i++) ring(level, root.add(0, (0.55 + i * 0.36) * scale, 0),
         (1.1 - i * 0.07) * scale, 12, i * 0.2, VIOLET);
      for (int sign : new int[]{-1, 1}) {
         Vec3 shoulder = root.add(side.scale(sign * scale)).add(0, 2.45 * scale, 0);
         Vec3 hand = root.add(side.scale(sign * 1.65 * scale)).add(forward.scale(0.65 * scale)).add(0, 0.85 * scale, 0);
         beam(level, shoulder, hand, VIOLET, INK);
         dot(level, CRIMSON, root.add(forward.scale(0.42 * scale)).add(side.scale(sign * 0.25 * scale)).add(0, 2.9 * scale, 0));
      }
      ring(level, root.add(0, 3.03 * scale, 0), 0.49 * scale, 14, 0, VIOLET);
   }

   private static void arc(ServerLevel level, Vec3 origin, Vec3 aim, double radius, ParticleOptions particle) {
      double heading = Math.atan2(aim.z, aim.x);
      for (int i = 0; i <= 20; i++) {
         double step = i / 20.0;
         double angle = heading + (step - 0.5) * 2.5;
         dot(level, particle, origin.add(Math.cos(angle) * radius, (step - 0.5) * 1.3, Math.sin(angle) * radius));
      }
   }

   private static void ring(ServerLevel level, Vec3 origin, double radius, int count, double phase, ParticleOptions particle) {
      for (int i = 0; i < count; i++) {
         double angle = phase + Math.PI * 2 * i / count;
         dot(level, particle, origin.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
      }
   }

   private static void beam(ServerLevel level, Vec3 start, Vec3 end, ParticleOptions first, ParticleOptions second) {
      int count = Math.max(1, (int) Math.ceil(start.distanceTo(end) * 3));
      for (int i = 0; i <= count; i++) dot(level, i % 3 == 0 ? second : first, start.lerp(end, (double)i / count));
   }

   private static Vec3 center(LivingEntity entity) {
      return entity.position().add(0, entity.getBbHeight() * 0.52, 0);
   }

   private static void dot(ServerLevel level, ParticleOptions particle, Vec3 point) {
      level.sendParticles(particle, point.x, point.y, point.z, 1, 0, 0, 0, 0);
   }

   private static void burst(ServerLevel level, ParticleOptions particle, Vec3 point, int count, double spread) {
      level.sendParticles(particle, point.x, point.y, point.z, count, spread, spread, spread, 0.03);
   }

   private static void sound(ServerLevel level, Vec3 point, SoundEvent sound, float volume, float pitch) {
      level.playSound(null, point.x, point.y, point.z, sound, SoundSource.HOSTILE, volume, pitch);
   }
}
