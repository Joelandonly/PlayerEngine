package com.player2.playerengine.util.serialization.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.util.helpers.ItemHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

public class ItemListTypeAdapter extends TypeAdapter<List<Item>> {
   private static final int MAX_RESOLVABLE_ITEM_KEY_LENGTH = 256;

   public void write(JsonWriter out, List<Item> items) throws IOException {
      if (items == null) {
         out.nullValue();
      } else {
         out.beginArray();

         for (Item item : items) {
            if (item != null && item != Items.AIR) {
               ResourceLocation key = BuiltInRegistries.ITEM.getResourceKey(item).map(resourceKey -> resourceKey.location()).orElse(null);
               if (key == null) {
                  Debug.logWarning("Skipping unregistered entry while saving item list");
               } else {
                  out.value(key.toString());
               }
            } else {
               Debug.logWarning("Skipping null/AIR entry while saving item list");
            }
         }

         out.endArray();
      }
   }

   public List<Item> read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
         in.nextNull();
         return null;
      }

      List<Item> result = new ArrayList<>();
      if (in.peek() != JsonToken.BEGIN_ARRAY) {
         Debug.logWarning("Invalid item list JSON: expected an array");
         in.skipValue();
         return result;
      }

      in.beginArray();

      while (in.hasNext()) {
         JsonToken token = in.peek();
         if (token == JsonToken.NULL) {
            in.nextNull();
            Debug.logWarning("Skipping null item-list entry");
         } else if (token != JsonToken.STRING) {
            in.skipValue();
            Debug.logWarning("Skipping non-string item-list entry");
         } else {
            String itemKey = in.nextString();
            Item item = resolveItem(itemKey);
            if (item != null) {
               result.add(item);
            } else {
               Debug.logWarning("Invalid item name: " + safeLogValue(itemKey));
            }
         }
      }

      in.endArray();
      return result;
   }

   public static Item resolveItem(String rawItemKey) {
      if (rawItemKey != null && rawItemKey.length() <= 256) {
         String itemKey = rawItemKey.trim();
         if (itemKey.isEmpty()) {
            return null;
         }

         String normalizedKey = ItemHelper.trimItemName(itemKey);
         ResourceLocation identifier = ResourceLocation.tryParse(normalizedKey);
         Item item = registeredNonAirItem(identifier);
         if (item != null) {
            return item;
         }

         for (Item candidate : BuiltInRegistries.ITEM) {
            if (candidate != null && candidate != Items.AIR && itemKey.equals(candidate.getDescriptionId())) {
               return candidate;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static Item registeredNonAirItem(ResourceLocation identifier) {
      if (identifier != null && BuiltInRegistries.ITEM.containsKey(identifier)) {
         Item item = (Item)BuiltInRegistries.ITEM.get(identifier);
         return item != null && item != Items.AIR ? item : null;
      } else {
         return null;
      }
   }

   private static String safeLogValue(String value) {
      if (value == null) {
         return "<null>";
      }

      String clean = value.replace('\r', ' ').replace('\n', ' ');
      return clean.length() <= 128 ? clean : clean.substring(0, 128) + "...";
   }
}
