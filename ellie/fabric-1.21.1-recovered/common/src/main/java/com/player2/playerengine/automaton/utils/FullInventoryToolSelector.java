package com.player2.playerengine.automaton.utils;

import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.automaton.api.utils.IEntityContext;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Full-inventory tool choice for ACTIVE block breaking only. Path calculation
 * continues to call ToolSet's existing read-only methods. This does not modify
 * ToolSet or take control of placement/combat/schematic tasks.
 */
public final class FullInventoryToolSelector {
   private FullInventoryToolSelector() {}

   public static void equipForMining(IEntityContext ctx, BlockState state, ToolSet toolSet, boolean preferSilkTouch) {
      LivingEntityInventory inv = ctx.inventory();
      if (inv == null || state == null || toolSet == null) return;
      if (ctx.baritone().settings().disableAutoTool.get() || ctx.baritone().settings().assumeExternalAutoTool.get()) return;
      if (state.getBlock().defaultDestroyTime() <= 0.0F) return;

      // FULL_INVENTORY_TOOL_SCAN: never touch the offhand or armour inventories.
      int limit = ctx.baritone().settings().allowInventory.get() ? 36 : 9;
      limit = Math.min(limit, inv.main.size());
      if (limit < 1) return;

      int selected = inv.selectedSlot;
      if (selected < 0 || selected >= 9 || selected >= limit) selected = 0;
      int bestSlot = -1;
      double bestSpeed = Double.NEGATIVE_INFINITY;
      boolean bestHarvest = false;
      boolean bestSilk = false;
      boolean bestEmpty = false;
      for (int i = 0; i < limit; i++) {
         ItemStack stack = inv.getItem(i);
         if (!usable(stack, ctx)) continue;
         boolean harvest = !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state);
         double speed = ToolSet.calculateSpeedVsBlock(stack, state);
         if (!Double.isFinite(speed) || speed < 0.0) continue;
         boolean silk = toolSet.hasSilkTouch(stack);
         boolean empty = stack.isEmpty();
         if (bestSlot == -1 || harvest && !bestHarvest
               || harvest == bestHarvest && (speed > bestSpeed + 1.0E-6
                  || Math.abs(speed - bestSpeed) <= 1.0E-6 &&
                     ((preferSilkTouch && silk && !bestSilk)
                        || (silk == bestSilk || !preferSilkTouch) &&
                           (i == selected && bestSlot != selected
                              || i != selected && bestSlot != selected && !empty && bestEmpty)))) {
            bestSlot = i;
            bestSpeed = speed;
            bestHarvest = harvest;
            bestSilk = silk;
            bestEmpty = empty;
         }
      }

      if (bestSlot < 0) return;
      if (bestSlot < 9) {
         inv.selectedSlot = bestSlot;
         return;
      }

      // FULL_INVENTORY_TOOL_SWAP: a two-way swap guarantees no item loss.
      // The live mining hook is server-side; path estimators never call this.
      int destination = hotbarDestination(inv);
      ItemStack incoming = inv.getItem(bestSlot);
      ItemStack outgoing = inv.getItem(destination);
      inv.setItem(destination, incoming);
      inv.setItem(bestSlot, outgoing);
      inv.selectedSlot = destination;
   }

   private static boolean usable(ItemStack stack, IEntityContext ctx) {
      if (stack.isEmpty()) return true;
      if (!ctx.baritone().settings().useSwordToMine.get() && stack.getItem() instanceof SwordItem) return false;
      return !ctx.baritone().settings().itemSaver.get() || stack.getMaxDamage() <= 1
         || stack.getMaxDamage() - stack.getDamageValue() > 3;
   }

   private static int hotbarDestination(LivingEntityInventory inv) {
      for (int i = 0; i < 9; i++) if (inv.getItem(i).isEmpty()) return i;
      if (inv.selectedSlot >= 0 && inv.selectedSlot < 9 && !protectedHotbarItem(inv.getItem(inv.selectedSlot)))
         return inv.selectedSlot;
      for (int i = 0; i < 9; i++) if (!protectedHotbarItem(inv.getItem(i))) return i;
      return inv.selectedSlot >= 0 && inv.selectedSlot < 9 ? inv.selectedSlot : 0;
   }

   private static boolean protectedHotbarItem(ItemStack stack) {
      return stack.getItem() instanceof BucketItem || stack.getItem() instanceof ShieldItem
         || stack.getItem() instanceof TieredItem;
   }
}
