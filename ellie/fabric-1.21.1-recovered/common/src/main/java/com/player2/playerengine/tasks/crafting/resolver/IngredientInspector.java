package com.player2.playerengine.tasks.crafting.resolver;

import java.util.Optional;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.tags.TagKey;

public interface IngredientInspector {
   IngredientInspector.SlotKind classify(Ingredient var1);

   Optional<TagKey<Item>> tagOf(Ingredient var1);

   Set<Item> acceptedItems(Ingredient var1);

   enum SlotKind {
      AGNOSTIC,
      LOCKED,
      CUSTOM;
   }
}
