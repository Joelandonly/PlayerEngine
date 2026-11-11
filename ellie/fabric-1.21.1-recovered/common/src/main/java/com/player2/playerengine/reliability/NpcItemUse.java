package com.player2.playerengine.reliability;

import com.player2.playerengine.observatory.ObservatoryTelemetry;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Vanilla axe stripping requires a player; NPC block placement must not
 * trigger player-only axe interactions through a playerless context.
 */
public final class NpcItemUse {
   private NpcItemUse() {}

   /** Typed source entry point: no reflective method strings or checked exceptions. */
   public static InteractionResult use(ItemStack stack, UseOnContext context) {
      if (context.getPlayer() == null && stack.getItem() instanceof AxeItem) {
         ObservatoryTelemetry.event("NPC_ITEM_USE_SKIPPED",
                 "Skipped player-only axe stripping on a non-player NPC");
         return InteractionResult.PASS;
      }
      return stack.useOn(context);
   }

   /** Retain original erased descriptor for callers built against the existing JAR. */
   public static Object use(Object stack, Object context) throws Exception {
      return use((ItemStack) stack, (UseOnContext) context);
   }
}
