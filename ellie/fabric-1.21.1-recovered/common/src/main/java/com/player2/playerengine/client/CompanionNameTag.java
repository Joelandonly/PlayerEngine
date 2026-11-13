package com.player2.playerengine.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public final class CompanionNameTag {
   private static final ResourceLocation COMPANION_TYPE = ResourceLocation.fromNamespaceAndPath("player2npc", "aicompanion");

   private CompanionNameTag() {}

   public static boolean isCompanion(Entity entity) {
      return COMPANION_TYPE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
   }

   public static float rangeMultiplier() {
      return 2.0F;
   }

   public static float textScale(double distanceSquared) {
      return (float)Math.clamp(Math.sqrt(distanceSquared) / 32.0, 1.0, 3.0);
   }
}
