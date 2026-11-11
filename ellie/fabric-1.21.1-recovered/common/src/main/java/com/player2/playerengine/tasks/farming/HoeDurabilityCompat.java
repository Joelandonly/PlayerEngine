package com.player2.playerengine.tasks.farming;

import java.util.Objects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class HoeDurabilityCompat {
   private HoeDurabilityCompat() {
   }

   public static boolean damageOnce(ItemStack stack, LivingEntity npc) {
      Objects.requireNonNull(stack, "stack");
      Objects.requireNonNull(npc, "npc");
      if (!stack.isEmpty() && stack.isDamageableItem()) {
         stack.hurtAndBreak(1, npc, EquipmentSlot.MAINHAND);
         return true;
      } else {
         return false;
      }
   }
}
