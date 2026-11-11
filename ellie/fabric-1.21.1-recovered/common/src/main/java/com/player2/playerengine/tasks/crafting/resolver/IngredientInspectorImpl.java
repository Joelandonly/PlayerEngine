package com.player2.playerengine.tasks.crafting.resolver;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.tags.TagKey;

public class IngredientInspectorImpl implements IngredientInspector {
   @Override
   public IngredientInspector.SlotKind classify(Ingredient slot) {
      if (slot != null && !slot.isEmpty()) {
         int distinct = this.acceptedItems(slot).size();
         if (distinct == 0) {
            return IngredientInspector.SlotKind.CUSTOM;
         } else {
            return distinct > 1 ? IngredientInspector.SlotKind.AGNOSTIC : IngredientInspector.SlotKind.LOCKED;
         }
      } else {
         return IngredientInspector.SlotKind.CUSTOM;
      }
   }

   @Override
   public Optional<TagKey<Item>> tagOf(Ingredient slot) {
      return Optional.empty();
   }

   @Override
   public Set<Item> acceptedItems(Ingredient slot) {
      Set<Item> items = new LinkedHashSet<>();
      if (slot == null) {
         return items;
      }

      for (ItemStack stack : slot.getItems()) {
         if (stack != null && !stack.isEmpty() && stack.getItem() != Items.BARRIER) {
            items.add(stack.getItem());
         }
      }

      return items;
   }
}
