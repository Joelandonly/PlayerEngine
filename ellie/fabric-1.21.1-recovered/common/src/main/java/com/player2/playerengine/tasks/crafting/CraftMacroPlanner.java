package com.player2.playerengine.tasks.crafting;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.crafting.resolver.IngredientInspector;
import com.player2.playerengine.tasks.crafting.resolver.IngredientInspectorImpl;
import com.player2.playerengine.tasks.crafting.resolver.MaterialResolver;
import com.player2.playerengine.tasks.crafting.resolver.RecipeAccess;
import com.player2.playerengine.tasks.crafting.resolver.RecipeAccessImpl;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.RecipeTarget;
import com.player2.playerengine.util.WoodType;
import com.player2.playerengine.util.helpers.ItemHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;

public final class CraftMacroPlanner {
   private static final RecipeAccess RECIPE_ACCESS = new RecipeAccessImpl();
   private static final IngredientInspector INGREDIENT_INSPECTOR = new IngredientInspectorImpl();

   private CraftMacroPlanner() {
   }

   public static Optional<CraftMacroPlan> plan(PlayerEngineController mod, ItemTarget requested) {
      Optional<Item> outputOpt = CraftMacroSupport.resolveSingleOutputItem(mod, requested);
      if (outputOpt.isEmpty()) {
         return Optional.empty();
      }

      Item outputItem = outputOpt.get();
      int targetCount = requested.getTargetCount();
      int have = mod.getItemStorage().getItemCount(outputItem);
      if (have >= targetCount) {
         return Optional.of(new CraftMacroPlan(requested, outputItem, targetCount, List.of(), List.of(), false, null, null));
      }

      if (outputItem == Items.CHEST) {
         return Optional.of(seed(requested, outputItem, targetCount, true, anyPlankChestRecipe()));
      }

      if (Arrays.asList(ItemHelper.WOOD_SIGN).contains(outputItem)) {
         ItemHelper.WoodItems wood = findWoodForItem(outputItem);
         if (wood == null) {
            wood = ItemHelper.getWoodItems(WoodType.OAK);
         }

         return Optional.of(seed(requested, outputItem, targetCount, true, signRecipe(wood)));
      } else {
         if (outputItem == Items.CRAFTING_TABLE) {
            return Optional.of(seed(requested, outputItem, targetCount, false, null));
         }

         if (outputItem == Items.STICK) {
            return Optional.of(seed(requested, outputItem, targetCount, false, null));
         }

         if (Arrays.asList(ItemHelper.PLANKS).contains(outputItem)) {
            return findWoodForItem(outputItem) == null ? Optional.empty() : Optional.of(seed(requested, outputItem, targetCount, false, null));
         }

         MinecraftServer server = mod.getPlayer() != null ? mod.getPlayer().getServer() : null;
         if (server == null) {
            return Optional.empty();
         }

         RecipeManager mgr = server.getRecipeManager();
         RegistryAccess registries = server.registryAccess();
         Optional<CraftingRecipe> recipeOpt = RECIPE_ACCESS.selectRecipeForResult(mgr, outputItem, registries);
         if (recipeOpt.isEmpty()) {
            return Optional.empty();
         }

         CraftingRecipe mcRecipe = recipeOpt.get();
         int yield = Math.max(1, RECIPE_ACCESS.outputCountOf(mcRecipe, registries));
         boolean requiresCraftingTable = !mcRecipe.canCraftInDimensions(2, 2);
         NonNullList<Ingredient> ingredients = mcRecipe.getIngredients();
         List<ItemTarget> slotTargets = new ArrayList<>(ingredients.size());

         for (Ingredient ing : ingredients) {
            if (ing != null && !ing.isEmpty()) {
               Set<Item> accepted = INGREDIENT_INSPECTOR.acceptedItems(ing);
               if (accepted.isEmpty()) {
                  slotTargets.add(ItemTarget.EMPTY);
               } else {
                  Item[] matchSet = accepted.toArray(new Item[0]);
                  slotTargets.add(new ItemTarget(matchSet, 1));
               }
            } else {
               slotTargets.add(ItemTarget.EMPTY);
            }
         }

         RecipeTarget finalTableRecipe = MaterialResolver.toRecipeTarget(outputItem, targetCount, yield, slotTargets, mcRecipe, registries);
         return Optional.of(seed(requested, outputItem, targetCount, requiresCraftingTable, finalTableRecipe));
      }
   }

   private static CraftMacroPlan seed(ItemTarget requested, Item outputItem, int targetCount, boolean requiresTable, RecipeTarget finalRecipe) {
      return new CraftMacroPlan(requested, outputItem, targetCount, List.of(), List.of(), requiresTable, null, finalRecipe);
   }

   private static ItemHelper.WoodItems findWoodForItem(Item item) {
      for (ItemHelper.WoodItems wood : ItemHelper.getWoodItems()) {
         if (wood.planks == item || wood.sign == item || wood.log == item) {
            return wood;
         }
      }

      return null;
   }

   private static RecipeTarget anyPlankChestRecipe() {
      ItemTarget p = new ItemTarget(ItemHelper.PLANKS, 1);
      return new RecipeTarget(Items.CHEST, 1, com.player2.playerengine.util.CraftingRecipe.newShapedRecipe(new ItemTarget[]{p, p, p, p, ItemTarget.EMPTY, p, p, p, p}, 1));
   }

   private static RecipeTarget signRecipe(ItemHelper.WoodItems wood) {
      ItemTarget planks = new ItemTarget(wood.planks, 1);
      ItemTarget stick = TaskCatalogue.getItemTarget("stick", 1);
      return new RecipeTarget(
         wood.sign, 3, com.player2.playerengine.util.CraftingRecipe.newShapedRecipe(new ItemTarget[]{planks, planks, planks, planks, planks, planks, null, stick, null}, 3)
      );
   }
}
