package com.player2.playerengine.tasks.cooking.resolver;

import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.core.RegistryAccess;

public interface CookingRecipeAccess {
   Optional<CookingRecipeAccess.CookResolution> resolve(RecipeManager var1, Item var2, CookingRecipeAccess.CookKind var3, RegistryAccess var4);

   default Optional<CookingRecipeAccess.CookResolution> resolveAny(RecipeManager mgr, Item input, RegistryAccess registries) {
      Optional<CookingRecipeAccess.CookResolution> smelting = this.resolve(mgr, input, CookingRecipeAccess.CookKind.SMELTING, registries);
      if (smelting.isPresent()) {
         return smelting;
      }

      Optional<CookingRecipeAccess.CookResolution> blasting = this.resolve(mgr, input, CookingRecipeAccess.CookKind.BLASTING, registries);
      return blasting.isPresent() ? blasting : this.resolve(mgr, input, CookingRecipeAccess.CookKind.SMOKING, registries);
   }

   boolean isSmeltingResult(RecipeManager var1, Item var2, RegistryAccess var3);

   enum CookKind {
      SMELTING,
      BLASTING,
      SMOKING;
   }

   record CookResolution(ItemStack output, int cookTicks, CookingRecipeAccess.CookKind kind) {
   }
}
