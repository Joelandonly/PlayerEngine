package com.player2.playerengine.util.helpers;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;

public final class AutomaticMiningSourcePolicySelfTest {
   private AutomaticMiningSourcePolicySelfTest() {
   }

   public static void main(String[] args) {
      assert AutomaticMiningSourcePolicy.blocksFor(Items.CHEST).length == 0;
      assertOnly(Blocks.OAK_LOG, AutomaticMiningSourcePolicy.blocksFor(Items.CHEST, Items.OAK_LOG));
      assertOnly(Blocks.TRAPPED_CHEST, AutomaticMiningSourcePolicy.blocksFor(Items.TRAPPED_CHEST));
      assertOnly(Blocks.BARREL, AutomaticMiningSourcePolicy.blocksFor(Items.BARREL));
      assertOnly(Blocks.CHEST, ItemHelper.itemsToBlocks(new Item[]{Items.CHEST}));
      assert AutomaticMiningSourcePolicy.isProtectedAgenticMineTarget("chest");
      assert AutomaticMiningSourcePolicy.isProtectedAgenticMineTarget("minecraft:chest");
      assert AutomaticMiningSourcePolicy.isProtectedAgenticMineTarget("#minecraft:chests");
      assert AutomaticMiningSourcePolicy.isProtectedAgenticMineTarget("#forge:chests/wooden");
      assert AutomaticMiningSourcePolicy.isProtectedAgenticMineTarget("#c:chests/wooden");
      assert !AutomaticMiningSourcePolicy.isProtectedAgenticMineTarget("minecraft:trapped_chest");
      assert !AutomaticMiningSourcePolicy.isProtectedAgenticMineTarget("minecraft:oak_log");
      System.out.println("AutomaticMiningSourcePolicySelfTest: PASS");
   }

   private static void assertOnly(Block expected, Block[] actual) {
      assert actual.length == 1;
      assert actual[0] == expected;
   }
}
