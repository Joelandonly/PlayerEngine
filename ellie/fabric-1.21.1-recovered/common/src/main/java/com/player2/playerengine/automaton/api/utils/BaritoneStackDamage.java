package com.player2.playerengine.automaton.api.utils;

import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;

public final class BaritoneStackDamage {
   private BaritoneStackDamage() {
   }

   public static int storedDamage(ItemStack stack) {
      return stack.has(DataComponents.DAMAGE) ? (Integer)stack.get(DataComponents.DAMAGE) : 0;
   }
}
