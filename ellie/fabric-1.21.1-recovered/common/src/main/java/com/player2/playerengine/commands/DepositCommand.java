package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.commands.base.ItemList;
import com.player2.playerengine.supervisor.ProjectTaskGateway;
import com.player2.playerengine.tasks.container.StoreInAnyContainerTask;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import com.player2.playerengine.util.helpers.StorageHelper;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;

public class DepositCommand extends Command {
   private static final int NEARBY_RANGE = 20;
   private static final Block[] VALID_CONTAINERS = Stream.concat(
         Arrays.stream(new Block[]{Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL}),
         Arrays.stream(ItemHelper.itemsToBlocks(ItemHelper.SHULKER_BOXES))
      )
      .toArray(Block[]::new);

   public DepositCommand() throws CommandException {
      super(
         "deposit",
         "Deposit our items to a nearby chest, making a chest if one doesn't exist. Pass no arguments to deposit all non-tool/armor/weapon items (tools, armor, and weapons are kept). Examples: `deposit` deposits all non-gear items, `deposit diamond 2` deposits 2 diamonds.",
         new Arg<>(ItemList.class, "items (empty for ALL non gear items)", null, 0, false)
      );
   }

   public static ItemTarget[] getAllNonEquippedOrToolItemsAsTarget(PlayerEngineController mod) {
      return StorageHelper.getAllInventoryItemsAsTargets(mod, slot -> {
         if (slot.getInventory().size() == 4) {
            return false;
         } else {
            ItemStack stack = StorageHelper.getItemStackInSlot(slot);
            if (!stack.isEmpty()) {
               Item item = stack.getItem();
               return !(item instanceof TieredItem);
            } else {
               return false;
            }
         }
      });
   }

   public static ItemTarget[] getDepositAllItemsAsTarget(PlayerEngineController mod) {
      return StorageHelper.getAllInventoryItemsAsTargets(mod, slot -> {
         if (slot.getInventory().size() == 4) {
            return false;
         }

         ItemStack stack = StorageHelper.getItemStackInSlot(slot);
         return stack.isEmpty() ? false : !StorageHelper.isEssentialItem(stack.getItem());
      });
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      ItemList itemList = parser.get(ItemList.class);
      if (itemList != null) {
         Map<Item, Integer> countsLeftover = new HashMap<>();

         for (ItemTarget itemTarget : itemList.items) {
            for (Item matchItem : itemTarget.getMatches()) {
               if (matchItem != null) {
                  countsLeftover.merge(matchItem, itemTarget.getTargetCount(), Integer::sum);
               }
            }
         }

         for (int i = 0; i < mod.getInventory().getContainerSize(); i++) {
            ItemStack stack = mod.getInventory().getItem(i);
            if (!stack.isEmpty()) {
               Item heldItem = stack.getItem();
               int count = stack.getCount();
               if (countsLeftover.containsKey(heldItem)) {
                  countsLeftover.merge(heldItem, -count, Integer::sum);
                  if (countsLeftover.get(heldItem) <= 0) {
                     countsLeftover.remove(heldItem);
                  }
               }
            }
         }

         if (!countsLeftover.isEmpty()) {
            String leftover = String.join(
               ",", countsLeftover.entrySet().stream().map(e -> ItemHelper.stripItemName(e.getKey()) + " x " + e.getValue().toString()).toList()
            );
            mod.log("Insuffucient items in inventory to deposit. We still need: " + leftover + ".");
            this.finishWithError("insufficient_items: still need " + leftover);
            return;
         }
      }

      ItemTarget[] items;
      if (itemList == null) {
         items = getDepositAllItemsAsTarget(mod);
      } else {
         items = itemList.items;
      }

      if (items.length == 0) {
         mod.log("Nothing to deposit.");
         this.finish();
      } else {
         StoreInAnyContainerTask task = new StoreInAnyContainerTask(false, items);
         ProjectTaskGateway.run(mod, task, () -> {
            if (task.succeeded()) {
               this.finish();
            } else {
               this.finishWithError(escalationMessage(task.reason()));
            }
         });
      }
   }

   private static String escalationMessage(String reason) {
      String r = reason != null && !reason.isBlank() ? reason : "could_not_complete";
      return "Couldn't finish depositing ("
         + r
         + "). Try `agentic store <items>` — it can gather materials, craft or place a chest, search farther, and plan a multi-step storage solution.";
   }
}
