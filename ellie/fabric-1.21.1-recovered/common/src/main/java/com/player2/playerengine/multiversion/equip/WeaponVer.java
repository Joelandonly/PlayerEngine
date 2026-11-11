package com.player2.playerengine.multiversion.equip;

import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.MaceItem;

public final class WeaponVer {
   private WeaponVer() {
   }

   public static boolean isMeleeWeapon(ItemStack stack) {
      if (stack.isEmpty() || EquipVer.isBodyArmor(stack)) {
         return false;
      } else {
         return !(stack.getItem() instanceof SwordItem)
               && !(stack.getItem() instanceof AxeItem)
               && !(stack.getItem() instanceof TridentItem)
               && !(stack.getItem() instanceof MaceItem)
            ? WeaponStatsVer.readAttackDamage(stack) > 0.0
            : true;
      }
   }
}
