package com.player2.playerengine.tasks.smithing.resolver;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.slf4j.Logger;

public final class SmithingRecipeAccessImpl implements SmithingRecipeAccess {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Field FIELD_TEMPLATE;
   private static final Field FIELD_BASE;
   private static final Field FIELD_ADDITION;
   private static final boolean REFLECTION_OK;

   @Override
   public Optional<SmithingRecipeAccess.SmithingResolution> resolve(RecipeManager mgr, Item output, RegistryAccess registries) {
      if (!REFLECTION_OK) {
         return Optional.empty();
      }

      for (RecipeHolder<SmithingRecipe> holder : mgr.getAllRecipesFor(RecipeType.SMITHING)) {
         SmithingRecipe recipe = (SmithingRecipe)holder.value();
         if (recipe instanceof SmithingTransformRecipe transform) {
            ItemStack result = transform.getResultItem(registries);
            if (!result.isEmpty() && result.getItem() == output) {
               Optional<SmithingRecipeAccess.SmithingResolution> resolution = extractResolution(transform, result);
               if (resolution.isPresent()) {
                  return resolution;
               }
            }
         }
      }

      return Optional.empty();
   }

   private static Optional<SmithingRecipeAccess.SmithingResolution> extractResolution(SmithingTransformRecipe transform, ItemStack result) {
      try {
         Ingredient templateIngredient = (Ingredient)FIELD_TEMPLATE.get(transform);
         Ingredient baseIngredient = (Ingredient)FIELD_BASE.get(transform);
         Ingredient additionIngredient = (Ingredient)FIELD_ADDITION.get(transform);
         List<Item> templateItems = ingredientToItems(templateIngredient);
         List<Item> baseItems = ingredientToItems(baseIngredient);
         List<Item> additionItems = ingredientToItems(additionIngredient);
         return !templateItems.isEmpty() && !baseItems.isEmpty() && !additionItems.isEmpty()
            ? Optional.of(new SmithingRecipeAccess.SmithingResolution(result.copy(), templateItems, baseItems, additionItems))
            : Optional.empty();
      } catch (IllegalAccessException e) {
         return Optional.empty();
      }
   }

   private static List<Item> ingredientToItems(Ingredient ingredient) {
      if (ingredient == null) {
         return List.of();
      }

      ItemStack[] stacks = ingredient.getItems();
      List<Item> items = new ArrayList<>(stacks.length);

      for (ItemStack stack : stacks) {
         if (!stack.isEmpty()) {
            Item item = stack.getItem();
            if (item != Items.AIR && !items.contains(item)) {
               items.add(item);
            }
         }
      }

      return List.copyOf(items);
   }

   static {
      Field ft = null;
      Field fb = null;
      Field fa = null;
      boolean ok = false;

      try {
         List<Field> ingredientFields = new ArrayList<>(3);

         for (Field f : SmithingTransformRecipe.class.getDeclaredFields()) {
            if (f.getType() == Ingredient.class) {
               ingredientFields.add(f);
            }
         }

         if (ingredientFields.size() != 3) {
            throw new NoSuchFieldException("expected exactly 3 Ingredient fields on SmithingTransformRecipe, found " + ingredientFields.size());
         }

         ft = ingredientFields.get(0);
         fb = ingredientFields.get(1);
         fa = ingredientFields.get(2);
         ft.setAccessible(true);
         fb.setAccessible(true);
         fa.setAccessible(true);
         ok = true;
      } catch (ReflectiveOperationException | RuntimeException e) {
         LOGGER.warn(
            "[PlayerEngine] SmithingRecipeAccessImpl: reflection setup failed — smithing resolution will return empty for every query. Check JVM --add-opens for net.minecraft.world.item.crafting. Error: {}",
            e.toString()
         );
      }

      FIELD_TEMPLATE = ft;
      FIELD_BASE = fb;
      FIELD_ADDITION = fa;
      REFLECTION_OK = ok;
   }
}
