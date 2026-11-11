package com.player2.playerengine.tasks.crafting;

import com.player2.playerengine.util.RecipeTarget;
import net.minecraft.world.item.Item;

public record CraftMacroStep(CraftMacroStepKind kind, RecipeTarget recipeTarget, int craftsNeeded, String debugLabel, Item[] outputMatches) {
   public CraftMacroStep(CraftMacroStepKind kind, RecipeTarget recipeTarget, int craftsNeeded, String debugLabel) {
      this(kind, recipeTarget, craftsNeeded, debugLabel, null);
   }
}
