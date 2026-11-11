package com.player2.playerengine.modintelligence.inspect;

import com.player2.playerengine.multiversion.item.ItemNbtSnapshotVer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.HolderLookup.Provider;

public final class SanitizedNbtSnapshot {
   private static final Set<String> ALLOWED_KEYS = Set.of("id", "count", "components", "tag", "food", "damage", "durability", "maxDamage");
   private static final int MAX_VALUE_LEN = 256;

   private SanitizedNbtSnapshot() {
   }

   public static Map<String, String> fromDefaultStack(Item item, Provider registryAccess) {
      if (item == null) {
         return Map.of();
      }

      try {
         ItemStack stack = new ItemStack(item);
         Map<String, String> raw = ItemNbtSnapshotVer.snapshot(stack, registryAccess);
         Map<String, String> out = new LinkedHashMap<>();

         for (Entry<String, String> e : raw.entrySet()) {
            String key = e.getKey().toLowerCase();
            if (ALLOWED_KEYS.contains(key) || key.startsWith("component.")) {
               String val = e.getValue();
               if (val != null) {
                  if (val.length() > 256) {
                     val = val.substring(0, 256) + "...";
                  }

                  out.put(key, val);
               }
            }
         }

         return out;
      } catch (Exception ignored) {
         return Map.of();
      }
   }
}
