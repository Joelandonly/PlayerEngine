package com.player2.playerengine.tasks.smithing.resolver;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.core.RegistryAccess;

public interface SmithingRecipeAccess {
   Optional<SmithingRecipeAccess.SmithingResolution> resolve(RecipeManager var1, Item var2, RegistryAccess var3);

   record SmithingResolution(ItemStack output, List<Item> template, List<Item> base, List<Item> addition) {
   }
}
