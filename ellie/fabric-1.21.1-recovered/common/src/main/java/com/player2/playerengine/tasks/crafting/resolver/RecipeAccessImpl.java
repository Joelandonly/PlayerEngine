package com.player2.playerengine.tasks.crafting.resolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.CraftingInput;

public final class RecipeAccessImpl implements RecipeAccess {
   @Override
   public List<CraftingRecipe> getCraftingRecipes(RecipeManager mgr) {
      List<RecipeHolder<CraftingRecipe>> holders = mgr.getAllRecipesFor(RecipeType.CRAFTING);
      List<CraftingRecipe> result = new ArrayList<>(holders.size());

      for (RecipeHolder<CraftingRecipe> holder : holders) {
         CraftingRecipe recipe = (CraftingRecipe)holder.value();
         if (!recipe.isSpecial() && !recipe.isIncomplete()) {
            result.add(recipe);
         }
      }

      return result;
   }

   @Override
   public Optional<CraftingRecipe> selectRecipeForResult(RecipeManager mgr, Item target, RegistryAccess registries) {
      CraftingRecipe firstMatch = null;

      for (CraftingRecipe recipe : this.getCraftingRecipes(mgr)) {
         if (recipe.getResultItem(registries).getItem() == target) {
            if (recipe.canCraftInDimensions(2, 2)) {
               return Optional.of(recipe);
            }

            if (firstMatch == null) {
               firstMatch = recipe;
            }
         }
      }

      return Optional.ofNullable(firstMatch);
   }

   @Override
   public List<CraftingRecipe> recipesForResult(RecipeManager mgr, Item target, RegistryAccess registries) {
      List<CraftingRecipe> out = new ArrayList<>();

      for (CraftingRecipe recipe : this.getCraftingRecipes(mgr)) {
         if (recipe.getResultItem(registries).getItem() == target) {
            out.add(recipe);
         }
      }

      return out;
   }

   @Override
   public int outputCountOf(CraftingRecipe recipe, RegistryAccess registries) {
      return recipe.getResultItem(registries).getCount();
   }

   @Override
   public NonNullList<ItemStack> getRemainingItems(CraftingRecipe recipe, List<ItemStack> slotContents, int width, int height, RegistryAccess registries) {
      CraftingInput input = CraftingInput.of(width, height, slotContents);
      return recipe.getRemainingItems(input);
   }
}
