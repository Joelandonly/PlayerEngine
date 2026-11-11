package com.player2.playerengine.multiversion.recipemanager;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.resources.ResourceLocation;

public record WrappedRecipeEntry(ResourceLocation id, Recipe<?> value) {
}
