package com.player2.playerengine.util.equip;

import com.player2.playerengine.multiversion.equip.WeaponStatsVer;
import com.player2.playerengine.multiversion.equip.WeaponVer;
import net.minecraft.world.item.ItemStack;

public final class WeaponEquipScorer {
   private WeaponEquipScorer() {
   }

   public static boolean isUpgrade(ItemStack candidate, ItemStack currentlyHeld) {
      if (candidate.isEmpty() || !WeaponVer.isMeleeWeapon(candidate)) {
         return false;
      } else if (currentlyHeld.isEmpty()) {
         return true;
      } else {
         return !WeaponVer.isMeleeWeapon(currentlyHeld) ? true : WeaponStatsVer.read(candidate).isBetterThan(WeaponStatsVer.read(currentlyHeld));
      }
   }
}
