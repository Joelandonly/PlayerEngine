package com.player2.playerengine.multiversion.equip;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemAttributeModifiers.Entry;

public final class WeaponStatsVer {
   private WeaponStatsVer() {
   }

   public static WeaponStats read(ItemStack stack) {
      return new WeaponStats(readAttackDamage(stack));
   }

   public static double readAttackDamage(ItemStack stack) {
      if (stack.isEmpty()) {
         return 0.0;
      }

      double attack = 0.0;
      ItemAttributeModifiers modifiers = (ItemAttributeModifiers)stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

      for (Entry entry : modifiers.modifiers()) {
         if (entry.slot().test(EquipmentSlot.MAINHAND) && entry.attribute().is(Attributes.ATTACK_DAMAGE)) {
            attack += entry.modifier().amount();
         }
      }

      return attack;
   }
}
