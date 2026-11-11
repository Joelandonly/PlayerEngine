package com.player2.playerengine.tasks.crafting;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.agentic.MaterialReservationService;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.crafting.resolver.RecipeAccess;
import com.player2.playerengine.tasks.crafting.resolver.RecipeAccessImpl;
import com.player2.playerengine.util.ItemTarget;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

public final class CraftMacroTasks {
   private static final RecipeAccess RECIPE_ACCESS = new RecipeAccessImpl();

   private CraftMacroTasks() {
   }

   public static CraftMacroTasks.MacroProbe macroOutcome(PlayerEngineController mod, ItemTarget target) {
      return macroOutcome(mod, target, null);
   }

   public static CraftMacroTasks.MacroProbe macroOutcome(PlayerEngineController mod, ItemTarget target, @Nullable MaterialReservationService ledger) {
      if (!CraftMacroSupport.isMacroEnabled(mod)) {
         return new CraftMacroTasks.MacroProbe(CraftMacroTasks.MacroOutcome.GATE_DISABLED, null);
      }

      MinecraftServer server = mod.getPlayer() != null ? mod.getPlayer().getServer() : null;
      if (server != null) {
         Optional<Item> outputItemOpt = CraftMacroSupport.resolveSingleOutputItem(mod, target);
         if (!outputItemOpt.isPresent()) {
            return new CraftMacroTasks.MacroProbe(CraftMacroTasks.MacroOutcome.NO_CRAFTING_RECIPE, null);
         }

         RecipeManager recipeManager = server.getRecipeManager();
         RegistryAccess registries = server.registryAccess();
         Item out = outputItemOpt.get();
         if (!RECIPE_ACCESS.hasRecipe(recipeManager, out, registries) || allRecipesAreUnfundedUnpacks(mod, recipeManager, registries, out)) {
            return new CraftMacroTasks.MacroProbe(CraftMacroTasks.MacroOutcome.NO_CRAFTING_RECIPE, null);
         }
      } else if (!CraftMacroSupport.isSupportedTarget(mod, target)) {
         return new CraftMacroTasks.MacroProbe(CraftMacroTasks.MacroOutcome.NO_CRAFTING_RECIPE, null);
      }

      CraftMacroSupport.SignRequestAdjustment adj = CraftMacroSupport.adjustSignRequestForFunding(mod, target);
      ResourceTask task = CraftMacroPlanner.plan(mod, adj.target()).map(plan -> {
         CraftMacroResourceTask craftTask = new CraftMacroResourceTask(plan, ledger);
         if (adj.notice() != null && !adj.notice().isBlank()) {
            craftTask.setCreationNote(adj.notice());
            mod.reportAgenticProgress(adj.notice(), true);
         }

         return craftTask;
      }).orElse(null);
      return task == null
         ? new CraftMacroTasks.MacroProbe(CraftMacroTasks.MacroOutcome.NO_CRAFTING_RECIPE, null)
         : new CraftMacroTasks.MacroProbe(CraftMacroTasks.MacroOutcome.MACRO_CREATED, task);
   }

   public static ResourceTask tryCreateMacroTask(PlayerEngineController mod, ItemTarget target) {
      return tryCreateMacroTask(mod, target, null);
   }

   public static ResourceTask tryCreateMacroTask(PlayerEngineController mod, ItemTarget target, @Nullable MaterialReservationService ledger) {
      return macroOutcome(mod, target, ledger).taskOrNull();
   }

   private static boolean allRecipesAreUnfundedUnpacks(PlayerEngineController mod, RecipeManager mgr, RegistryAccess registries, Item target) {
      List<CraftingRecipe> recs = RECIPE_ACCESS.recipesForResult(mgr, target, registries);
      if (recs.isEmpty()) {
         return false;
      }

      for (CraftingRecipe candidate : recs) {
         Optional<Item> ing = RECIPE_ACCESS.storageUnpackIngredient(mgr, target, registries, candidate);
         if (ing.isPresent()) {
            if (mod.getItemStorage().getItemCount(ing.get()) > 0) {
               return false;
            }
         } else if (!isUnfundedCircularSingleIngredient(mod, mgr, registries, target, candidate)) {
            return false;
         }
      }

      return true;
   }

   private static boolean isUnfundedCircularSingleIngredient(
      PlayerEngineController mod, RecipeManager mgr, RegistryAccess registries, Item target, CraftingRecipe candidate
   ) {
      Optional<Item> ingOpt = RECIPE_ACCESS.singleConsumedIngredient(candidate);
      if (ingOpt.isEmpty()) {
         return false;
      }

      Item ingredient = ingOpt.get();
      if (mod.getItemStorage().getItemCount(ingredient) > 0) {
         return false;
      }

      List<CraftingRecipe> ingRecipes = RECIPE_ACCESS.recipesForResult(mgr, ingredient, registries);
      if (ingRecipes.isEmpty()) {
         return false;
      }

      for (CraftingRecipe ingRecipe : ingRecipes) {
         Optional<Item> back = RECIPE_ACCESS.singleConsumedIngredient(ingRecipe);
         if (back.isEmpty() || back.get() != target) {
            return false;
         }
      }

      return true;
   }

   public enum MacroOutcome {
      MACRO_CREATED,
      GATE_DISABLED,
      NO_CRAFTING_RECIPE;
   }

   public record MacroProbe(CraftMacroTasks.MacroOutcome outcome, ResourceTask task) {
      public ResourceTask taskOrNull() {
         return this.outcome == CraftMacroTasks.MacroOutcome.MACRO_CREATED ? this.task : null;
      }
   }
}
