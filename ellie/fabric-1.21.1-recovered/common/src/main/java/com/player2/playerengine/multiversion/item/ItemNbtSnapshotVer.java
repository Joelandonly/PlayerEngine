package com.player2.playerengine.multiversion.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.Tag;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderLookup.Provider;

public final class ItemNbtSnapshotVer {
   private ItemNbtSnapshotVer() {
   }

   public static Map<String, String> snapshot(ItemStack stack, Provider registryAccess) {
      Map<String, String> out = new LinkedHashMap<>();
      if (stack != null && !stack.isEmpty()) {
         out.put("id", String.valueOf(stack.getItem()));
         out.put("count", String.valueOf(stack.getCount()));
         if (stack.has(DataComponents.FOOD)) {
            FoodProperties food = (FoodProperties)stack.get(DataComponents.FOOD);
            if (food != null) {
               out.put("food", "nutrition=" + food.nutrition() + ",saturation=" + food.saturation());
            }
         }

         if (stack.isDamageableItem()) {
            out.put("maxDamage", String.valueOf(stack.getMaxDamage()));
         }

         try {
            Tag tag = stack.save(registryAccess);
            String json = tag.toString();
            if (json.length() <= 512) {
               out.put("tag", json);
            } else {
               flattenJson(json, out, "component.");
            }
         } catch (Exception var5) {
         }

         return out;
      } else {
         return out;
      }
   }

   private static void flattenJson(String json, Map<String, String> out, String prefix) {
      try {
         JsonElement el = JsonParser.parseString(json);
         if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();

            for (Entry<String, JsonElement> e : obj.entrySet()) {
               String key = prefix + e.getKey();
               String val = e.getValue().toString();
               if (val.length() > 256) {
                  val = val.substring(0, 256) + "...";
               }

               out.put(key, val);
            }
         }
      } catch (Exception var9) {
      }
   }
}
