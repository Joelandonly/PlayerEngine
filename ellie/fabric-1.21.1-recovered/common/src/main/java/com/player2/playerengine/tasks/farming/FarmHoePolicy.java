package com.player2.playerengine.tasks.farming;

import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tiers;

public final class FarmHoePolicy {
   private static final List<Item> MARKED_STORAGE_ITEMS = List.of(
      Items.STONE_HOE, Items.IRON_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE
   );

   private FarmHoePolicy() {
   }

   public static List<Item> markedStorageItems() {
      return MARKED_STORAGE_ITEMS;
   }

   public static boolean isAccepted(ItemStack stack) {
      return stack != null && !stack.isEmpty() && stack.getItem() instanceof HoeItem hoe && isStoneOrBetter(hoe);
   }

   public static boolean isStoneOrBetter(HoeItem hoe) {
      return hoe == null
         ? false
         : hoe.getTier() == Tiers.STONE
            || hoe.getTier() == Tiers.IRON
            || hoe.getTier() == Tiers.DIAMOND
            || hoe.getTier() == Tiers.NETHERITE;
   }

   public static int remainingDurability(LivingEntityInventory inventory) {
      return inventory == null ? 0 : remainingDurability(inventory.main);
   }

   static int remainingDurability(Iterable<ItemStack> stacks) {
      if (stacks == null) {
         return 0;
      }

      long remaining = 0L;

      for (ItemStack stack : stacks) {
         if (isAccepted(stack)) {
            remaining += Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
            if (remaining >= 2147483647L) {
               return Integer.MAX_VALUE;
            }
         }
      }

      return (int)remaining;
   }

   public static boolean hasDurability(LivingEntityInventory inventory, int requiredDurability) {
      return requiredDurability >= 0 && remainingDurability(inventory) >= requiredDurability;
   }
}
