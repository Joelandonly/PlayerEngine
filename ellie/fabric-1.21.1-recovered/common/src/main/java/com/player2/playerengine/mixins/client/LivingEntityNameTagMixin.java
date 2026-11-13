package com.player2.playerengine.mixins.client;

import com.player2.playerengine.client.CompanionNameTag;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityNameTagMixin {
   @ModifyConstant(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z",
      constant = {@Constant(floatValue = 32.0F), @Constant(floatValue = 64.0F)}, require = 2)
   private float playerengine$companionNameTagRange(float range, LivingEntity entity) {
      return CompanionNameTag.isCompanion(entity) ? range * CompanionNameTag.rangeMultiplier() : range;
   }
}
