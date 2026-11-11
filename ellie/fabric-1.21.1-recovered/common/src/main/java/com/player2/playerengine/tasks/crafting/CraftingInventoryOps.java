package com.player2.playerengine.tasks.crafting;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.automaton.api.entity.IInventoryProvider;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.tasks.crafting.resolver.RecipeAccess;
import com.player2.playerengine.tasks.crafting.resolver.RecipeAccessImpl;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.RecipeTarget;
import com.player2.playerengine.util.helpers.StorageHelper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class CraftingInventoryOps {
   private static final RecipeAccess RECIPE_ACCESS = new RecipeAccessImpl();

   private CraftingInventoryOps() {
   }

   public static boolean hasMaterials(PlayerEngineController mod, RecipeTarget target) {
      return StorageHelper.hasRecipeMaterialsOrTarget(mod, new RecipeTarget(target.getOutputItem(), target.getRecipe().outputCount(), target.getRecipe()));
   }

   public static boolean performSingleCraft(PlayerEngineController mod, RecipeTarget oneCraft) {
      if (!hasMaterials(mod, oneCraft)) {
         return false;
      }

      LivingEntityInventory inventory = ((IInventoryProvider)mod.getEntity()).getLivingInventory();
      List<ItemStack> slotContents = null;
      if (oneCraft.hasMcRecipe()) {
         int slotCount = oneCraft.getRecipe().getSlotCount();
         slotContents = new ArrayList<>(slotCount);

         for (ItemTarget ingredient : oneCraft.getRecipe().getSlots()) {
            if (ingredient != null && !ingredient.isEmpty() && ingredient.getMatches().length > 0) {
               slotContents.add(new ItemStack(ingredient.getMatches()[0], 1));
            } else {
               slotContents.add(ItemStack.EMPTY);
            }
         }
      }

      for (ItemTarget ingredient : oneCraft.getRecipe().getSlots()) {
         if (ingredient != null && !ingredient.isEmpty()) {
            inventory.remove(stack -> ingredient.matches(stack.getItem()), ingredient.getTargetCount(), inventory);
         }
      }

      int yield = oneCraft.getRecipe().outputCount();
      ItemStack result;
      if (oneCraft.hasMcRecipe() && oneCraft.getMcResultStack() != null && !oneCraft.getMcResultStack().isEmpty()) {
         result = oneCraft.getMcResultStack().copyWithCount(yield);
      } else {
         result = new ItemStack(oneCraft.getOutputItem(), yield);
      }

      inventory.insertStack(result);
      if (oneCraft.hasMcRecipe() && slotContents != null) {
         int width = oneCraft.getRecipe().getWidth();
         int height = oneCraft.getRecipe().getHeight();

         for (ItemStack remainder : RECIPE_ACCESS.getRemainingItems(oneCraft.getMcRecipe(), slotContents, width, height, oneCraft.getRegistries())) {
            if (!remainder.isEmpty()) {
               inventory.insertStack(remainder);
            }
         }
      }

      mod.getItemStorage().registerSlotAction();
      mod.getEntity().swing(InteractionHand.MAIN_HAND);
      return true;
   }

   public static int performCrafts(PlayerEngineController mod, RecipeTarget target, int craftsNeeded) {
      int completed = 0;
      RecipeTarget single = new RecipeTarget(
         target.getOutputItem(), target.getRecipe().outputCount(), target.getRecipe(), target.getMcRecipe(), target.getMcResultStack(), target.getRegistries()
      );

      for (int i = 0; i < craftsNeeded && performSingleCraft(mod, single); i++) {
         completed++;
      }

      return completed;
   }

   public static int countItem(PlayerEngineController mod, Item item) {
      return mod.getItemStorage().getItemCount(item);
   }
}
