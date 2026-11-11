package com.player2.playerengine.util;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.component.DataComponents;

public class EnchantmentUtils {
   public static int getEnchantmentLevel(ItemStack stack, ResourceKey<Enchantment> enchantment) {
      try {
         return stack.has(DataComponents.ENCHANTMENTS)
            ? ((ItemEnchantments)stack.get(DataComponents.ENCHANTMENTS))
               .entrySet()
               .stream()
               .filter(e -> ((ResourceKey)((Holder)e.getKey()).unwrapKey().get()).equals(Enchantments.EFFICIENCY))
               .findFirst()
               .get()
               .getIntValue()
            : 0;
      } catch (Exception e) {
         return 0;
      }
   }
}
