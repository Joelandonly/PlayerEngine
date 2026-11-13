package com.player2.playerengine.mixins.baritone;

import com.player2.playerengine.automaton.api.component.EntityComponentKey;
import com.player2.playerengine.automaton.utils.accessor.ILivingEntityAccessor;
import com.player2.playerengine.progression.CompanionProgression;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity implements ILivingEntityAccessor, EntityComponentKey.Holder {
   @Unique
   private Map<EntityComponentKey<?>, Object> playerengine$entityComponents;

   @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
   private void playerengine$progressionImmunity(DamageSource source, float amount, CallbackInfoReturnable<Boolean> result) {
      var progression = CompanionProgression.of((LivingEntity) (Object) this);
      if (progression != null && progression.preventsDamage(source)) result.setReturnValue(false);
   }

   @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"), cancellable = true)
   private void playerengine$secondWind(DamageSource source, CallbackInfoReturnable<Boolean> result) {
      if (result.getReturnValueZ()) return;
      var progression = CompanionProgression.of((LivingEntity) (Object) this);
      if (progression != null && progression.secondWind(source)) result.setReturnValue(true);
   }

   @Unique
   private boolean playerengine$killRecorded;

   @Inject(method = "die", at = @At("TAIL"))
   private void playerengine$verifiedHuntKill(DamageSource source, CallbackInfo callback) {
      LivingEntity target = (LivingEntity) (Object) this;
      if (!this.playerengine$killRecorded && !target.isAlive()
         && target.level() instanceof net.minecraft.server.level.ServerLevel
         && source.getEntity() instanceof LivingEntity attacker) {
         this.playerengine$killRecorded = true;
         CompanionProgression.killed(attacker, target);
      }
   }

   @Override
   public synchronized Object playerengine$getEntityComponent(EntityComponentKey<?> key) {
      return this.playerengine$entityComponents == null ? null : this.playerengine$entityComponents.get(key);
   }

   @Override
   public synchronized Object playerengine$getOrCreateEntityComponent(EntityComponentKey<?> key, Supplier<?> factory) {
      Object component = this.playerengine$getEntityComponent(key);
      if (component == null) {
         component = factory.get();
         if (this.playerengine$entityComponents == null) this.playerengine$entityComponents = new HashMap<>();
         this.playerengine$entityComponents.put(key, component);
      }
      return component;
   }

   @Invoker("decreaseAirSupply")
   @Override
   public abstract int automatone$getNextAirUnderwater(int var1);

   @Invoker("increaseAirSupply")
   @Override
   public abstract int automatone$getNextAirOnLand(int var1);
}
