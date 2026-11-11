package com.player2.playerengine.util.helpers;

import com.player2.playerengine.util.ItemTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;

public final class AutomaticMiningSourcePolicy {
   private AutomaticMiningSourcePolicy() {
   }

   public static Block[] blocksFor(Item... items) {
      if (items != null && items.length != 0) {
         List<Block> result = new ArrayList<>(items.length);

         for (Item item : items) {
            if (item != null && !isProtectedAcquisitionItem(item)) {
               Block block = Block.byItem(item);
               if (block != null && block != Blocks.AIR) {
                  result.add(block);
               }
            }
         }

         return result.toArray(Block[]::new);
      } else {
         return new Block[0];
      }
   }

   public static Block[] blocksFor(ItemTarget... targets) {
      if (targets != null && targets.length != 0) {
         List<Block> result = new ArrayList<>(targets.length);

         for (ItemTarget target : targets) {
            if (target != null) {
               for (Block block : blocksFor(target.getMatches())) {
                  result.add(block);
               }
            }
         }

         return result.toArray(Block[]::new);
      } else {
         return new Block[0];
      }
   }

   public static boolean containsProtectedAcquisitionItem(ItemTarget... targets) {
      if (targets == null) {
         return false;
      }

      for (ItemTarget target : targets) {
         if (target != null) {
            for (Item item : target.getMatches()) {
               if (isProtectedAcquisitionItem(item)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public static boolean isProtectedAgenticMineTarget(String rawBlockId) {
      if (rawBlockId == null) {
         return false;
      }

      String normalized = rawBlockId.trim().toLowerCase(Locale.ROOT);

      while (normalized.startsWith("#")) {
         normalized = normalized.substring(1);
      }

      int namespaceSeparator = normalized.indexOf(58);
      String path = namespaceSeparator >= 0 ? normalized.substring(namespaceSeparator + 1) : normalized;
      return normalized.equals("chest") || normalized.equals("minecraft:chest") || path.equals("chests") || path.startsWith("chests/");
   }

   private static boolean isProtectedAcquisitionItem(Item item) {
      return item == Items.CHEST;
   }
}
