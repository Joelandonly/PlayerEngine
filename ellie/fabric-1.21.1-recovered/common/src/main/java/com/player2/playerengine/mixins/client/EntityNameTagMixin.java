package com.player2.playerengine.mixins.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.player2.playerengine.client.CompanionNameTag;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(EntityRenderer.class)
abstract class EntityNameTagMixin {
   @Shadow @Final
   protected EntityRenderDispatcher entityRenderDispatcher;

   @ModifyConstant(method = "renderNameTag", constant = @Constant(doubleValue = 4096.0))
   private double playerengine$companionNameTagDistance(double distanceSquared, Entity entity, Component name,
      PoseStack poseStack, MultiBufferSource buffers, int light, float partialTick) {
      float multiplier = CompanionNameTag.rangeMultiplier();
      return CompanionNameTag.isCompanion(entity) ? distanceSquared * multiplier * multiplier : distanceSquared;
   }

   @ModifyArgs(method = "renderNameTag", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
   private void playerengine$scaleDistantCompanionName(Args args, Entity entity, Component name,
      PoseStack poseStack, MultiBufferSource buffers, int light, float partialTick) {
      if (CompanionNameTag.isCompanion(entity)) {
         float scale = CompanionNameTag.textScale(this.entityRenderDispatcher.distanceToSqr(entity));
         args.setAll(args.<Float>get(0) * scale, args.<Float>get(1) * scale, args.<Float>get(2) * scale);
      }
   }
}
