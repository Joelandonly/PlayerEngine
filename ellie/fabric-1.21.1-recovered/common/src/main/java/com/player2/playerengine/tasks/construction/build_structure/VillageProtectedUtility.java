package com.player2.playerengine.tasks.construction.build_structure;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class VillageProtectedUtility {
   private static final Set<String> REUSABLE = Set.of(
      "minecraft:crafting_table",
      "minecraft:furnace",
      "minecraft:blast_furnace",
      "minecraft:smoker",
      "minecraft:chest",
      "minecraft:trapped_chest",
      "minecraft:barrel",
      "minecraft:smithing_table",
      "minecraft:stonecutter",
      "minecraft:grindstone"
   );
   private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

   private VillageProtectedUtility() {
   }

   public static boolean accept(int var0, String var1, boolean var2, int var3, int var4, int var5) {
      if (var0 > 1024 && !var2 && REUSABLE.contains(var1)) {
         String var6 = var1 + " @ " + var3 + "," + var4 + "," + var5;
         if (REPORTED.size() < 10000 && REPORTED.add(var6)) {
            System.out
               .println(
                  "[EllieBlueprint] PRESERVED_UTILITY: "
                     + var6
                     + " (not overwritten; treated as a protected site exception in large construction). The corresponding blueprint cell is NOT an exact match."
               );
         }

         return true;
      } else {
         return false;
      }
   }
}
