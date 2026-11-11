package com.player2.playerengine.tasks.crafting.resolver;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.core.RegistryAccess;

public interface RecipeAccess {
   List<CraftingRecipe> getCraftingRecipes(RecipeManager var1);

   Optional<CraftingRecipe> selectRecipeForResult(RecipeManager var1, Item var2, RegistryAccess var3);

   List<CraftingRecipe> recipesForResult(RecipeManager var1, Item var2, RegistryAccess var3);

   int outputCountOf(CraftingRecipe var1, RegistryAccess var2);

   default boolean hasRecipe(RecipeManager mgr, Item target, RegistryAccess registries) {
      return !this.recipesForResult(mgr, target, registries).isEmpty();
   }

   NonNullList<ItemStack> getRemainingItems(CraftingRecipe var1, List<ItemStack> var2, int var3, int var4, RegistryAccess var5);

   default Optional<Item> storageUnpackIngredient(RecipeManager mgr, Item want, RegistryAccess registries, CraftingRecipe candidate) {
      Optional<Item> ingredientOpt = singleDistinctIngredient(candidate);
      if (ingredientOpt.isEmpty()) {
         return Optional.empty();
      }

      Item ingredient = ingredientOpt.get();
      int yield = Math.max(1, this.outputCountOf(candidate, registries));
      if (yield < 2) {
         return Optional.empty();
      }

      for (CraftingRecipe reverse : this.recipesForResult(mgr, ingredient, registries)) {
         Optional<Item> reverseIngredient = singleDistinctIngredient(reverse);
         if (reverseIngredient.isPresent() && reverseIngredient.get() == want) {
            return Optional.of(ingredient);
         }
      }

      return Optional.empty();
   }

   default Optional<Item> singleConsumedIngredient(CraftingRecipe recipe) {
      return singleDistinctIngredient(recipe);
   }

   private static Optional<Item> singleDistinctIngredient(CraftingRecipe recipe) {
      Item single = null;
      boolean sawNonEmpty = false;

      for (Ingredient ing : recipe.getIngredients()) {
         if (ing != null && !ing.isEmpty()) {
            Set<Item> accepted = new HashSet<>();

            for (ItemStack stack : ing.getItems()) {
               accepted.add(stack.getItem());
            }

            if (accepted.size() != 1) {
               return Optional.empty();
            }

            Item slotItem = accepted.iterator().next();
            if (!sawNonEmpty) {
               single = slotItem;
               sawNonEmpty = true;
            } else if (single != slotItem) {
               return Optional.empty();
            }
         }
      }

      return sawNonEmpty ? Optional.of(single) : Optional.empty();
   }
}
