package com.player2.playerengine.tasks.cooking.resolver;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.RecipeHolder;

public final class CookingRecipeAccessImpl implements CookingRecipeAccess {
   @Override
   public Optional<CookingRecipeAccess.CookResolution> resolve(RecipeManager mgr, Item input, CookingRecipeAccess.CookKind kind, RegistryAccess registries) {
      switch (kind) {
         case SMELTING:
            return this.resolveFrom(mgr.getAllRecipesFor(RecipeType.SMELTING), input, kind, registries);
         case BLASTING:
            return this.resolveFrom(mgr.getAllRecipesFor(RecipeType.BLASTING), input, kind, registries);
         case SMOKING:
            return this.resolveFrom(mgr.getAllRecipesFor(RecipeType.SMOKING), input, kind, registries);
         default:
            return Optional.empty();
      }
   }

   @Override
   public boolean isSmeltingResult(RecipeManager mgr, Item output, RegistryAccess registries) {
      for (RecipeHolder<SmeltingRecipe> holder : mgr.getAllRecipesFor(RecipeType.SMELTING)) {
         SmeltingRecipe recipe = (SmeltingRecipe)holder.value();
         if (recipe.getResultItem(registries).getItem() == output) {
            return true;
         }
      }

      return false;
   }

   private <R extends AbstractCookingRecipe> Optional<CookingRecipeAccess.CookResolution> resolveFrom(
      List<RecipeHolder<R>> recipes, Item input, CookingRecipeAccess.CookKind kind, RegistryAccess registries
   ) {
      ItemStack probe = new ItemStack(input);

      for (RecipeHolder<R> holder : recipes) {
         R recipe = (R)holder.value();
         if (!recipe.getIngredients().isEmpty() && ((Ingredient)recipe.getIngredients().get(0)).test(probe)) {
            ItemStack output = recipe.getResultItem(registries).copy();
            int cookTicks = recipe.getCookingTime();
            return Optional.of(new CookingRecipeAccess.CookResolution(output, cookTicks, kind));
         }
      }

      return Optional.empty();
   }
}
