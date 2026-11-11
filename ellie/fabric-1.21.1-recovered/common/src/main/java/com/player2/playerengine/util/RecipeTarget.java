package com.player2.playerengine.util;

import java.util.Objects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.player2.playerengine.util.CraftingRecipe;
import net.minecraft.core.RegistryAccess;

public class RecipeTarget {
   private final CraftingRecipe recipe;
   private final Item item;
   private final int targetCount;
   private final net.minecraft.world.item.crafting.CraftingRecipe mcRecipe;
   private final ItemStack mcResultStack;
   private final RegistryAccess registries;

   public RecipeTarget(Item item, int targetCount, CraftingRecipe recipe) {
      this.item = item;
      this.targetCount = targetCount;
      this.recipe = recipe;
      this.mcRecipe = null;
      this.mcResultStack = null;
      this.registries = null;
   }

   public RecipeTarget(Item item, int targetCount, CraftingRecipe recipe, net.minecraft.world.item.crafting.CraftingRecipe mcRecipe, ItemStack mcResultStack, RegistryAccess registries) {
      this.item = item;
      this.targetCount = targetCount;
      this.recipe = recipe;
      this.mcRecipe = mcRecipe;
      this.mcResultStack = mcResultStack;
      this.registries = registries;
   }

   public CraftingRecipe getRecipe() {
      return this.recipe;
   }

   public Item getOutputItem() {
      return this.item;
   }

   public int getTargetCount() {
      return this.targetCount;
   }

   public net.minecraft.world.item.crafting.CraftingRecipe getMcRecipe() {
      return this.mcRecipe;
   }

   public ItemStack getMcResultStack() {
      return this.mcResultStack;
   }

   public RegistryAccess getRegistries() {
      return this.registries;
   }

   public boolean hasMcRecipe() {
      return this.mcRecipe != null;
   }

   @Override
   public String toString() {
      return this.targetCount == 1 ? "Recipe{" + this.item + "}" : "Recipe{" + this.item + " x " + this.targetCount + "}";
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         RecipeTarget that = (RecipeTarget)o;
         return this.targetCount == that.targetCount && this.recipe.equals(that.recipe) && Objects.equals(this.item, that.item);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.recipe, this.item);
   }
}
