package com.player2.playerengine.tasks.agentic;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.MiningRequirement;
import com.player2.playerengine.util.helpers.StorageHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ToolRequirementResolver {
   private ToolRequirementResolver() {
   }

   public static MiningRequirement requirementFor(Block block) {
      return MiningRequirement.getMinimumRequirementForBlock(block);
   }

   public static boolean isHeld(PlayerEngineController controller, MiningRequirement req) {
      return StorageHelper.miningRequirementMet(controller, req);
   }

   public static boolean isHeldInventoryOnly(PlayerEngineController controller, MiningRequirement req) {
      return StorageHelper.miningRequirementMetInventory(controller, req);
   }

   public static Item minimumPickaxe(MiningRequirement req) {
      return req.getMinimumPickaxe();
   }

   public static boolean isHandOnly(MiningRequirement req) {
      return req == MiningRequirement.HAND;
   }
}
