package com.player2.playerengine.tasks.crafting;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.automaton.api.entity.IInventoryProvider;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.tasks.crafting.resolver.RecipeAccess;
import com.player2.playerengine.tasks.crafting.resolver.RecipeAccessImpl;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.RecipeTarget;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class CraftingInventoryOps {
   private static final RecipeAccess RECIPE_ACCESS = new RecipeAccessImpl();

   private CraftingInventoryOps() {
   }

   public static boolean hasMaterials(PlayerEngineController mod, RecipeTarget target) {
      return matchInputs(mod.getInventory().main, target.getRecipe().getSlots()) != null;
   }

   static List<ItemStack> matchInputs(List<ItemStack> carried, ItemTarget[] ingredients) {
      List<ItemStack> available = carried.stream().map(ItemStack::copy).toList();
      List<ItemStack> matched = new ArrayList<>(java.util.Collections.nCopies(ingredients.length, ItemStack.EMPTY));
      Integer[] order = java.util.stream.IntStream.range(0, ingredients.length).boxed().toArray(Integer[]::new);
      Arrays.sort(order, java.util.Comparator.comparingInt(i ->
         ItemTarget.nullOrEmpty(ingredients[i]) ? 0 : ingredients[i].getMatches().length));
      for (int index : order) {
         ItemTarget ingredient = ingredients[index];
         if (ItemTarget.nullOrEmpty(ingredient)) continue;
         int count = ingredient.getTargetCount();
         ItemStack first = ItemStack.EMPTY;
         for (ItemStack stack : available) {
            if (stack.isEmpty() || !ingredient.matches(stack.getItem())) continue;
            if (first.isEmpty()) first = stack.copyWithCount(1);
            if (!ItemStack.isSameItemSameComponents(first, stack)) continue;
            int used = Math.min(count, stack.getCount());
            stack.shrink(used);
            count -= used;
            if (count == 0) break;
         }
         if (count > 0) return null;
         matched.set(index, first.copyWithCount(ingredient.getTargetCount()));
      }
      return matched;
   }

   public static boolean performSingleCraft(PlayerEngineController mod, RecipeTarget oneCraft) {
      LivingEntityInventory inventory = ((IInventoryProvider)mod.getEntity()).getLivingInventory();
      List<ItemStack> slotContents = matchInputs(inventory.main, oneCraft.getRecipe().getSlots());
      if (slotContents == null) return false;
      for (ItemStack ingredient : slotContents) {
         if (ingredient.isEmpty()) continue;
         int remaining = ingredient.getCount();
         for (ItemStack stack : inventory.main) {
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, ingredient)) continue;
            int consumed = Math.min(remaining, stack.getCount());
            stack.shrink(consumed);
            remaining -= consumed;
            if (remaining == 0) break;
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
      if (oneCraft.hasMcRecipe()) {
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
